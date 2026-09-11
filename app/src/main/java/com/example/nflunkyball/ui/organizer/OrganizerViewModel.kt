package com.example.nflunkyball.ui.organizer

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nflunkyball.ble.BleCapability
import com.example.nflunkyball.ble.EmojiPalette
import com.example.nflunkyball.ble.RoomCode
import com.example.nflunkyball.ble.TournamentBroadcaster
import com.example.nflunkyball.model.Group
import com.example.nflunkyball.model.Match
import com.example.nflunkyball.model.MatchResult
import com.example.nflunkyball.model.Team
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.model.TournamentPhase
import com.example.nflunkyball.model.generateRoundRobinMatches
import com.example.nflunkyball.persistence.AppSettingsStore
import com.example.nflunkyball.persistence.MatchDrinkStore
import com.example.nflunkyball.persistence.TournamentRepository
import com.example.nflunkyball.server.CompetitorStats
import com.example.nflunkyball.server.Ed25519
import com.example.nflunkyball.server.InvitePayloadCodec
import com.example.nflunkyball.server.OrganizerAccount
import com.example.nflunkyball.server.ServerCredentialsStore
import com.example.nflunkyball.server.ServerApi
import com.example.nflunkyball.server.ServerResult
import com.example.nflunkyball.server.UploadTournament
import com.example.nflunkyball.server.toUploadPayload
import java.security.MessageDigest
import java.util.UUID
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

    var organizerAccount by mutableStateOf(credentialsStore.loadAccount())
        private set

    var readPassword by mutableStateOf(credentialsStore.loadReadPassword())
        private set

    /** Null until [checkAccountSyncStatus] has been called (or when there's no linked account
     *  to check at all). */
    private val _accountSyncStatus = MutableStateFlow<AccountSyncStatus?>(null)
    val accountSyncStatus: StateFlow<AccountSyncStatus?> = _accountSyncStatus

    var uploadStatus by mutableStateOf<String?>(null)
        private set

    /** Sorted by Elo desc (ties broken by name) so the SetupScreen suggestion chips read as a
     *  rough skill ranking rather than an alphabetical list. */
    var knownCompetitors by mutableStateOf<List<CompetitorStats>>(emptyList())
        private set

    private val _emojiEvents = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val emojiEvents: SharedFlow<String> = _emojiEvents

    fun canHost(): Boolean = BleCapability.canAdvertise(getApplication())

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

    fun startTournament(name: String, teams: List<Team>, groupAssignments: Map<String, List<String>>) {
        val groups = groupAssignments.map { (groupName, teamIds) ->
            val bareGroup = Group(id = UUID.randomUUID().toString(), name = groupName, teamIds = teamIds)
            bareGroup.copy(matches = bareGroup.generateRoundRobinMatches())
        }
        repository.start(
            Tournament(
                id = UUID.randomUUID().toString(),
                name = name,
                teams = teams,
                groups = groups,
                phase = TournamentPhase.GROUP_STAGE
            )
        )
    }

    fun startHosting() {
        if (useBleSync()) {
            val adapter = bluetoothAdapter ?: return
            val id = tournament.value?.id?.let { RoomCode.forTournament(it) } ?: return
            broadcaster?.start(id, repository.tournament.filterNotNull(), viewModelScope)
            viewModelScope.launch {
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
        val timestamp = System.currentTimeMillis() / 1000
        val message = "${current.id}|$timestamp|${sha256Hex(bodyJson)}"
        val signature = Ed25519.sign(account.privateKeySeed, message.toByteArray())
        val signatureB64 = Base64.encodeToString(signature, Base64.NO_WRAP)
        val result = ServerApi(account.serverUrl)
            .pushLiveTournament(current.id, account.accountId, timestamp, signatureB64, bodyJson)
        _serverSyncStatus.value = when (result) {
            is ServerResult.Success -> "Synced"
            is ServerResult.Failure -> "Sync failed: ${result.message}"
        }
    }

    fun recordGroupMatchResult(groupId: String, matchId: String, result: MatchResult) {
        repository.update { t ->
            t.copy(
                groups = t.groups.map { g ->
                    if (g.id != groupId) g
                    else g.copy(matches = g.matches.map { m -> if (m.id == matchId) m.copy(result = result) else m })
                }
            )
        }
    }

    fun advanceToBracket() {
        repository.update { it.copy(phase = TournamentPhase.BRACKET) }
    }

    /** Adds a new team to an in-progress group, generating matches against every team already
     *  in it — existing results are untouched, only the new pairings are appended. */
    fun addPlayer(groupId: String, playerName: String) {
        val name = playerName.trim()
        if (name.isBlank()) return
        repository.update { t ->
            val newTeam = Team(id = UUID.randomUUID().toString(), name = name)
            t.copy(
                teams = t.teams + newTeam,
                groups = t.groups.map { g ->
                    if (g.id != groupId) g
                    else g.copy(
                        teamIds = g.teamIds + newTeam.id,
                        matches = g.matches + g.teamIds.map { existingTeamId ->
                            Match(id = UUID.randomUUID().toString(), teamAId = existingTeamId, teamBId = newTeam.id)
                        }
                    )
                }
            )
        }
    }

    /** Starts a new, empty round-robin group mid-tournament — e.g. once enough late arrivals
     *  show up to field a second group. Add players to it afterwards via [addPlayer]. */
    fun addGroup(groupName: String) {
        val name = groupName.trim()
        if (name.isBlank()) return
        repository.update { t ->
            t.copy(groups = t.groups + Group(id = UUID.randomUUID().toString(), name = name, teamIds = emptyList()))
        }
    }

    fun renamePlayer(teamId: String, newName: String) {
        val name = newName.trim()
        if (name.isBlank()) return
        repository.update { t ->
            t.copy(teams = t.teams.map { if (it.id == teamId) it.copy(name = name) else it })
        }
    }

    /** True if [teamId] can be safely removed — only ever false once a match involving them
     *  actually has a recorded result, since dropping them past that point would corrupt
     *  standings/history rather than just tidying up an unplayed pairing. Checked both here (for
     *  the UI to grey the action out) and again inside [removePlayer] itself. */
    fun canRemovePlayer(teamId: String): Boolean {
        val current = tournament.value ?: return false
        return (current.groups.flatMap { it.matches } + current.bracketMatches)
            .none { (it.teamAId == teamId || it.teamBId == teamId) && it.result != null }
    }

    /** No-ops instead of removing once [canRemovePlayer] would say no — defense in depth, not
     *  just relying on the UI having disabled the action. Drops the team, its group membership,
     *  and any of its still-unplayed matches (group-stage or bracket). */
    fun removePlayer(teamId: String) {
        if (!canRemovePlayer(teamId)) return
        repository.update { t ->
            t.copy(
                teams = t.teams.filterNot { it.id == teamId },
                groups = t.groups.map { g ->
                    g.copy(
                        teamIds = g.teamIds - teamId,
                        matches = g.matches.filterNot { it.teamAId == teamId || it.teamBId == teamId }
                    )
                },
                bracketMatches = t.bracketMatches.filterNot { it.teamAId == teamId || it.teamBId == teamId }
            )
        }
    }

    fun renameGroup(groupId: String, newName: String) {
        val name = newName.trim()
        if (name.isBlank()) return
        repository.update { t ->
            t.copy(groups = t.groups.map { if (it.id == groupId) it.copy(name = name) else it })
        }
    }

    /** Only an empty group (no teams) can be removed — one with teams in it would silently
     *  strand their matches/results, so removing those first (see [removePlayer]) is required. */
    fun canRemoveGroup(groupId: String): Boolean =
        tournament.value?.groups?.find { it.id == groupId }?.teamIds?.isEmpty() == true

    fun removeGroup(groupId: String) {
        if (!canRemoveGroup(groupId)) return
        repository.update { t -> t.copy(groups = t.groups.filterNot { it.id == groupId }) }
    }

    fun addBracketMatch(teamAId: String, teamBId: String, roundLabel: String) {
        repository.update { t ->
            t.copy(
                bracketMatches = t.bracketMatches + Match(
                    id = UUID.randomUUID().toString(),
                    teamAId = teamAId,
                    teamBId = teamBId,
                    roundLabel = roundLabel
                )
            )
        }
    }

    fun recordBracketMatchResult(matchId: String, result: MatchResult) {
        repository.update { t ->
            t.copy(bracketMatches = t.bracketMatches.map { m -> if (m.id == matchId) m.copy(result = result) else m })
        }
    }

    fun finishTournament() {
        repository.update { it.copy(phase = TournamentPhase.FINISHED) }
    }

    /** Called once the organizer is done with a finished tournament (after upload), or when
     *  abandoning an in-progress one from the settings menu, so the next "Host a tournament"
     *  starts fresh instead of resuming a dead one. */
    fun clearTournament() {
        stopHosting()
        repository.clear()
        drinkStore.clear()
    }

    /** Recorded locally only (see [MatchDrinkStore]) — never touches [repository], so it's never
     *  part of what BLE broadcasting or live sync serialize. Only reaches the server via
     *  [uploadToHistory]. */
    fun recordDrink(matchId: String, drink: String) {
        val trimmed = drink.trim()
        if (trimmed.isBlank()) return
        drinkStore.set(matchId, trimmed)
    }

    /** Distinct previously-entered drinks, for autocomplete suggestions when recording a new one. */
    fun knownDrinks(): List<String> = drinkStore.all().values.distinct().sorted()

    /** [inviteCode] is the whole code an admin generated (bundles the server URL + token) —
     *  see InvitePayload for why the app never hardcodes a server address itself. */
    fun linkAccount(
        displayName: String,
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
            val publicKeyB64 = Base64.encodeToString(keyPair.publicKeyBytes, Base64.NO_WRAP)
            when (val result = ServerApi(invite.server).register(displayName, invite.token, publicKeyB64)) {
                is ServerResult.Success -> {
                    val account = OrganizerAccount(
                        accountId = result.value.accountId,
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

    fun uploadToHistory() {
        val account = organizerAccount ?: return
        val current = tournament.value ?: return
        viewModelScope.launch {
            uploadStatus = "Uploading…"
            // The one and only place drink choices ever leave this device — see MatchDrinkStore.
            val payload = current.toUploadPayload(drinkStore.all())
            val bodyJson = uploadJson.encodeToString(UploadTournament.serializer(), payload)
            val timestamp = System.currentTimeMillis() / 1000
            val message = "${current.id}|$timestamp|${sha256Hex(bodyJson)}"
            val signature = Ed25519.sign(account.privateKeySeed, message.toByteArray())
            val signatureB64 = Base64.encodeToString(signature, Base64.NO_WRAP)
            val result = ServerApi(account.serverUrl)
                .uploadTournament(account.accountId, timestamp, signatureB64, bodyJson)
            uploadStatus = when (result) {
                is ServerResult.Success -> "Uploaded"
                is ServerResult.Failure -> "Failed: ${result.message}"
            }
        }
    }

    private fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }

    override fun onCleared() {
        broadcaster?.stop()
        serverSyncJob?.cancel()
    }
}
