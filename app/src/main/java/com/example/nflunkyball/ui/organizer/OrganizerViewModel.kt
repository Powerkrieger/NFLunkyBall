package com.example.nflunkyball.ui.organizer

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nflunkyball.ble.EmojiPalette
import com.example.nflunkyball.ble.RoomCode
import com.example.nflunkyball.ble.TournamentBroadcaster
import com.example.nflunkyball.model.MatchDrinks
import com.example.nflunkyball.model.MatchResult
import com.example.nflunkyball.model.Team
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.model.TournamentFinishInfo
import com.example.nflunkyball.model.TournamentPhase
import com.example.nflunkyball.model.canRemoveGroup
import com.example.nflunkyball.model.canRemoveTeam
import com.example.nflunkyball.model.newTournament
import com.example.nflunkyball.model.withBracketMatchAdded
import com.example.nflunkyball.model.withBracketMatchResult
import com.example.nflunkyball.model.withGroupAdded
import com.example.nflunkyball.model.withGroupMatchResult
import com.example.nflunkyball.model.withGroupRemoved
import com.example.nflunkyball.model.withGroupRenamed
import com.example.nflunkyball.model.withPhase
import com.example.nflunkyball.model.withTeamAdded
import com.example.nflunkyball.model.withTeamRemoved
import com.example.nflunkyball.model.withTeamRenamed
import com.example.nflunkyball.persistence.AppSettingsStore
import com.example.nflunkyball.persistence.FinishInfoStore
import com.example.nflunkyball.persistence.MatchDrinkStore
import com.example.nflunkyball.persistence.TournamentRepository
import com.example.nflunkyball.server.CompetitorStats
import com.example.nflunkyball.server.Ed25519
import com.example.nflunkyball.server.InvitePayloadCodec
import com.example.nflunkyball.server.OrganizerAccount
import com.example.nflunkyball.server.ServerCredentialsStore
import com.example.nflunkyball.server.ServerApi
import com.example.nflunkyball.server.ServerResult
import com.example.nflunkyball.server.UploadSigner
import com.example.nflunkyball.server.UploadTournament
import com.example.nflunkyball.server.toUploadPayload
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Whether the currently linked account can actually reach the server and sync, as opposed to
 *  merely having credentials stored locally — see [OrganizerViewModel.checkAccountSyncStatus]
 *  and [SettingsScreen]'s "Organizer account" section, which surfaces this. */
enum class AccountSyncStatus { CHECKING, CAN_SYNC, REVOKED, UNKNOWN }

class OrganizerViewModel(application: Application) : AndroidViewModel(application) {

    private val uploadJson = Json { encodeDefaults = true }
    private val repository = TournamentRepository(application)
    private val credentialsStore = ServerCredentialsStore(application)
    private val settingsStore = AppSettingsStore(application)
    private val drinkStore = MatchDrinkStore(application)
    private val finishInfoStore = FinishInfoStore(application)
    private val bluetoothAdapter: BluetoothAdapter? =
        application.getSystemService(BluetoothManager::class.java)?.adapter
    private val broadcaster = bluetoothAdapter?.let { TournamentBroadcaster(it, application) }

    val tournament: StateFlow<Tournament?> = repository.tournament

    /** The version currently on air, or null while hosting is off/not yet started — see
     *  [HostingScreen]'s "broadcasting version N" status. Only moves in BLE mode. */
    val broadcastVersion: StateFlow<Int?> = broadcaster?.broadcastVersion ?: MutableStateFlow(null)

    /** Server-mode counterpart to [broadcastVersion] — null until the first push attempt.
     *  Only moves in server mode (see [useBleSync]). */
    private val _serverSyncStatus = MutableStateFlow<String?>(null)
    val serverSyncStatus: StateFlow<String?> = _serverSyncStatus

    private var serverSyncJob: Job? = null

    /** Bridges [TournamentBroadcaster.emojiEvents] into [emojiEvents] while hosting over BLE.
     *  Tracked so a repeat [startHosting] (HostingScreen is re-entered every time it's shown,
     *  and linking an account mid-tournament calls it again) replaces the bridge instead of
     *  stacking another collector — which would emit every reaction N times. */
    private var emojiBridgeJob: Job? = null

    var organizerAccount by mutableStateOf(credentialsStore.loadAccount())
        private set

    var readPassword by mutableStateOf(credentialsStore.loadReadPassword())
        private set

    /** Null until [checkAccountSyncStatus] has been called (or when there's no linked account
     *  to check at all). */
    private val _accountSyncStatus = MutableStateFlow<AccountSyncStatus?>(null)
    val accountSyncStatus: StateFlow<AccountSyncStatus?> = _accountSyncStatus

    /** Outcome of the last archive upload attempt for the current tournament, or null if none
     *  has been made yet — shown on My tournaments while a finished tournament is still waiting
     *  to be uploaded (see [finishAndUpload]). */
    var uploadStatus by mutableStateOf<String?>(null)
        private set

    /** True while a finished tournament is still on this device because its upload hasn't
     *  succeeded yet — it stays in My tournaments with retry/discard until it has. */
    val hasPendingUpload: Boolean
        get() = tournament.value?.phase == TournamentPhase.FINISHED

    /** Sorted by Elo desc (ties broken by name) so the SetupScreen suggestion chips read as a
     *  rough skill ranking rather than an alphabetical list. */
    var knownCompetitors by mutableStateOf<List<CompetitorStats>>(emptyList())
        private set

    private val _emojiEvents = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val emojiEvents: SharedFlow<String> = _emojiEvents

    /** Actually asks the server whether the linked account can sync (not revoked), rather than
     *  just trusting that credentials exist locally — an admin revoking it from the other end
     *  leaves no local trace otherwise (see [AccountSyncStatus]). */
    fun checkAccountSyncStatus() {
        val account = organizerAccount
        val password = readPassword
        if (account == null || password == null) {
            _accountSyncStatus.value = null
            return
        }
        _accountSyncStatus.value = AccountSyncStatus.CHECKING
        viewModelScope.launch {
            _accountSyncStatus.value = when (val result = ServerApi(account.serverUrl).getAccountStatus(account.accountId, password)) {
                is ServerResult.Success -> if (result.value.revoked) AccountSyncStatus.REVOKED else AccountSyncStatus.CAN_SYNC
                is ServerResult.Failure -> AccountSyncStatus.UNKNOWN
            }
        }
    }

    /** Read fresh each time rather than cached at construction — this ViewModel outlives a
     *  single visit to the Settings screen, so a toggle flipped there mid-session must be seen
     *  the next time hosting actually starts. */
    fun useBleSync(): Boolean = settingsStore.useBleSync()

    /** Known players from past tournaments this group has recorded, so the organizer can pick
     *  existing ones instead of retyping — also how a returning organizer confirms their
     *  account is actually registered with the group before starting a new tournament. */
    fun loadKnownCompetitors() {
        val account = organizerAccount ?: return
        val password = readPassword ?: return
        viewModelScope.launch {
            when (val result = ServerApi(account.serverUrl).listCompetitors(password)) {
                is ServerResult.Success ->
                    knownCompetitors = result.value.sortedWith(compareByDescending<CompetitorStats> { it.elo }.thenBy { it.name })
                is ServerResult.Failure -> Unit
            }
        }
    }

    fun startTournament(
        name: String,
        teams: List<Team>,
        groupAssignments: Map<String, List<String>>,
        squadSize: Int = 1
    ) {
        repository.start(newTournament(name, teams, groupAssignments, squadSize))
    }

    fun startHosting() {
        if (useBleSync()) {
            val adapter = bluetoothAdapter ?: return
            val id = tournament.value?.id?.let { RoomCode.forTournament(it) } ?: return
            broadcaster?.start(id, repository.tournament.filterNotNull(), viewModelScope)
            emojiBridgeJob?.cancel()
            emojiBridgeJob = viewModelScope.launch {
                broadcaster?.emojiEvents?.collect { packet ->
                    _emojiEvents.emit(EmojiPalette.emojiFor(packet.emojiCode))
                }
            }
        } else {
            startServerSync()
        }
    }

    fun stopHosting() {
        broadcaster?.stop()
        emojiBridgeJob?.cancel()
        emojiBridgeJob = null
        stopServerSync()
    }

    private fun stopServerSync() {
        serverSyncJob?.cancel()
        serverSyncJob = null
        _serverSyncStatus.value = null
    }

    /** Server-mode counterpart to [TournamentBroadcaster.start] — pushes the current state to
     *  the live-sync endpoint every time it changes, instead of advertising it over BLE. Needs
     *  a linked account to sign with — unlike hosting over BLE, a tournament can exist locally
     *  with none linked yet (e.g. credentials got lost, or the organizer skipped linking and
     *  wants to add it later — see [SettingsScreen]'s account section, [TournamentSettingsScreen]'s
     *  "Add invite token" row, and [BracketScreen]'s finish-without-linking warning), so this
     *  surfaces that state instead of silently doing nothing. Called again once linking
     *  completes to actually start syncing. */
    private fun startServerSync() {
        val account = organizerAccount
        if (account == null) {
            stopServerSync()
            _serverSyncStatus.value = "Not linked — link an organizer account to sync"
            return
        }
        serverSyncJob?.cancel()
        _serverSyncStatus.value = "Starting sync…"
        serverSyncJob = viewModelScope.launch {
            repository.tournament.filterNotNull().collectLatest { current ->
                pushLiveState(account, current)
            }
        }
    }

    private suspend fun pushLiveState(account: OrganizerAccount, current: Tournament) {
        _serverSyncStatus.value = "Syncing…"
        val bodyJson = uploadJson.encodeToString(Tournament.serializer(), current)
        val signed = UploadSigner.sign(account.privateKeySeed, current.id, bodyJson)
        val result = ServerApi(account.serverUrl)
            .pushLiveTournament(current.id, account.accountId, signed.timestamp, signed.signatureBase64, bodyJson)
        _serverSyncStatus.value = when (result) {
            is ServerResult.Success -> "Synced"
            is ServerResult.Failure -> "Sync failed: ${result.message}"
        }
    }

    // Every edit below is a pure transform in model/TournamentEdits.kt — see there for the rules.

    fun recordGroupMatchResult(groupId: String, matchId: String, result: MatchResult?) =
        repository.update { it.withGroupMatchResult(groupId, matchId, result) }

    fun recordBracketMatchResult(matchId: String, result: MatchResult?) =
        repository.update { it.withBracketMatchResult(matchId, result) }

    fun advanceToBracket() = repository.update { it.withPhase(TournamentPhase.BRACKET) }

    fun addPlayer(groupId: String, playerName: String) = repository.update { it.withTeamAdded(groupId, playerName) }

    fun addGroup(groupName: String) = repository.update { it.withGroupAdded(groupName) }

    fun renamePlayer(teamId: String, newName: String) = repository.update { it.withTeamRenamed(teamId, newName) }

    fun renameGroup(groupId: String, newName: String) = repository.update { it.withGroupRenamed(groupId, newName) }

    /** For the UI to grey the action out; [removePlayer] checks again itself. */
    fun canRemovePlayer(teamId: String): Boolean = tournament.value?.canRemoveTeam(teamId) == true

    fun removePlayer(teamId: String) = repository.update { it.withTeamRemoved(teamId) }

    fun canRemoveGroup(groupId: String): Boolean = tournament.value?.canRemoveGroup(groupId) == true

    fun removeGroup(groupId: String) = repository.update { it.withGroupRemoved(groupId) }

    fun addBracketMatch(teamAId: String, teamBId: String, roundLabel: String) =
        repository.update { it.withBracketMatchAdded(teamAId, teamBId, roundLabel) }

    /**
     * Marks the tournament finished and uploads it to history. The local copy is only cleared once
     * the server has confirmed the upload — on failure (offline, revoked account, killed
     * mid-upload) it stays put, [uploadStatus] carries the error, and My tournaments offers
     * [retryUpload] / [discardFinishedTournament]. With no account linked there's nothing to
     * upload to (the organizer explicitly chose "finish without saving" to get here), so the
     * tournament is simply cleared.
     */
    fun finishAndUpload(finishInfo: TournamentFinishInfo) {
        repository.update { it.withPhase(TournamentPhase.FINISHED) }
        stopHosting()
        if (organizerAccount == null) {
            clearTournament()
            return
        }
        finishInfoStore.set(finishInfo)
        uploadToHistory(finishInfo)
    }

    /** Re-attempts the upload of a finished tournament using the finish info saved with it. */
    fun retryUpload() {
        if (!hasPendingUpload) return
        // A tournament finished by an older version (or whose finish-info file was lost) has no
        // saved info: upload it with just today's date rather than blocking on it.
        val info = finishInfoStore.get() ?: TournamentFinishInfo(System.currentTimeMillis(), "", "", "")
        uploadToHistory(info)
    }

    /** Gives up on uploading a finished tournament and drops it from this device. */
    fun discardFinishedTournament() {
        if (!hasPendingUpload) return
        clearTournament()
    }

    /** Called once the organizer is done with a finished tournament (after upload), or when
     *  abandoning an in-progress one from the settings menu, so the next "Host a tournament"
     *  starts fresh instead of resuming a dead one. */
    fun clearTournament() {
        stopHosting()
        repository.clear()
        drinkStore.clear()
        finishInfoStore.clear()
        uploadStatus = null
    }

    /** Recorded locally only (see [MatchDrinkStore]) — never touches [repository], so it's never
     *  part of what BLE broadcasting or live sync serialize. Only reaches the server via
     *  [uploadToHistory]. Each team can be drinking something different, so both are recorded
     *  independently; a match with neither entered isn't stored at all. */
    fun recordDrinks(matchId: String, drinks: MatchDrinks) {
        drinkStore.set(matchId, drinks)
    }

    fun drinksFor(matchId: String): MatchDrinks? = drinkStore.all()[matchId]

    /** Distinct previously-entered drinks (any player), for autocomplete suggestions when
     *  recording a new one. */
    fun knownDrinks(): List<String> =
        drinkStore.all().values.flatMap { listOfNotNull(it.teamA, it.teamB) + it.byPlayer.values }.distinct().sorted()

    /** [inviteCode] is the whole code an admin generated (bundles the server URL + token) —
     *  see InvitePayload for why the app never hardcodes a server address itself. */
    @OptIn(ExperimentalEncodingApi::class)
    fun linkAccount(
        inviteCode: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val invite = InvitePayloadCodec.decode(inviteCode)
        if (invite == null) {
            onResult(false, "That doesn't look like a valid invite code")
            return
        }
        viewModelScope.launch {
            val keyPair = Ed25519.generateKeyPair()
            val publicKeyB64 = Base64.encode(keyPair.publicKeyBytes)
            when (val result = ServerApi(invite.server).register(invite.token, publicKeyB64)) {
                is ServerResult.Success -> {
                    val accountId = result.value.accountId
                    val displayName = result.value.displayName
                    if (accountId != null && displayName != null) {
                        val account = OrganizerAccount(
                            accountId = accountId,
                            displayName = displayName,
                            serverUrl = invite.server,
                            privateKeySeed = keyPair.privateKeySeed,
                            publicKeyBytes = keyPair.publicKeyBytes
                        )
                        credentialsStore.saveAccount(account)
                        credentialsStore.saveReadPassword(result.value.readPassword)
                        organizerAccount = account
                        readPassword = result.value.readPassword
                        onResult(true, "Linked as $displayName")
                    } else {
                        // A viewer invite: no Account/keypair, just standing read access — same
                        // two calls ViewerViewModel.join makes when a tournament's QR embeds them.
                        credentialsStore.saveReadPassword(result.value.readPassword)
                        credentialsStore.saveViewerServerUrl(invite.server)
                        readPassword = result.value.readPassword
                        onResult(true, "Logged in as viewer")
                    }
                }
                is ServerResult.Failure -> onResult(false, result.message)
            }
        }
    }

    /** Forgets this device's organizer identity — local credentials only, nothing server-side
     *  (an admin revoking/deleting the account is a separate, deliberate action). Only touches
     *  the account, never [repository]/[tournament]: an in-progress tournament keeps hosting
     *  over BLE untouched, and simply loses server sync (see [startServerSync]) until relinked.
     *  [readPassword] deliberately isn't cleared here — it unlocks history/leaderboard viewing
     *  (see [ServerCredentialsStore.clearAccount]'s doc), which isn't specific to being an
     *  organizer, so it stays usable even while unlinked. */
    fun unlinkAccount() {
        stopServerSync()
        credentialsStore.clearAccount()
        organizerAccount = null
        knownCompetitors = emptyList()
        _accountSyncStatus.value = null
    }

    private fun uploadToHistory(finishInfo: TournamentFinishInfo) {
        val account = organizerAccount ?: run {
            uploadStatus = "Not linked — link an organizer account to upload"
            return
        }
        val current = tournament.value ?: return
        if (uploadStatus == UPLOADING) return
        viewModelScope.launch {
            uploadStatus = UPLOADING
            // The one and only place drink choices/finish metadata ever leave this device — see
            // MatchDrinkStore and FinishTournamentDialog.
            val payload = current.toUploadPayload(drinkStore.all(), finishInfo)
            val bodyJson = uploadJson.encodeToString(UploadTournament.serializer(), payload)
            val signed = UploadSigner.sign(account.privateKeySeed, current.id, bodyJson)
            val result = ServerApi(account.serverUrl)
                .uploadTournament(account.accountId, signed.timestamp, signed.signatureBase64, bodyJson)
            when (result) {
                is ServerResult.Success -> clearTournament()
                is ServerResult.Failure -> uploadStatus = "Upload failed: ${result.message}"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopHosting()
    }

    private companion object {
        const val UPLOADING = "Uploading…"
    }
}
