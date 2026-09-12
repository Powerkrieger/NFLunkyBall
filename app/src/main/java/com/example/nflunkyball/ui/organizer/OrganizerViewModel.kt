package com.example.nflunkyball.ui.organizer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nflunkyball.ble.EmojiPalette
import com.example.nflunkyball.ble.LiveBroadcaster
import com.example.nflunkyball.ble.RoomCode
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
import com.example.nflunkyball.persistence.AppSettings
import com.example.nflunkyball.persistence.FinishInfoStore
import com.example.nflunkyball.persistence.MatchDrinkStore
import com.example.nflunkyball.persistence.TournamentRepository
import com.example.nflunkyball.server.AccountManager
import com.example.nflunkyball.server.AccountSyncStatus
import com.example.nflunkyball.server.ArchiveUploader
import com.example.nflunkyball.server.CompetitorStats
import com.example.nflunkyball.server.CredentialsStore
import com.example.nflunkyball.server.OrganizerAccount
import com.example.nflunkyball.server.ServerApiFactory
import com.example.nflunkyball.server.ServerResult
import com.example.nflunkyball.server.UploadSigner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Dependencies come from [com.example.nflunkyball.AppContainer]; every one has a plain-JVM
 *  substitute so this class is unit-testable. [broadcaster] is null on a device without
 *  Bluetooth. */
class OrganizerViewModel(
    private val repository: TournamentRepository,
    private val credentialsStore: CredentialsStore,
    private val settings: AppSettings,
    private val drinkStore: MatchDrinkStore,
    private val finishInfoStore: FinishInfoStore,
    private val broadcaster: LiveBroadcaster?,
    private val serverApi: ServerApiFactory
) : ViewModel() {

    private val liveJson = Json { encodeDefaults = true }
    private val accountManager = AccountManager(credentialsStore, serverApi, viewModelScope)
    private val uploader = ArchiveUploader(
        repository, drinkStore, finishInfoStore, account = { accountManager.account.value }, serverApi, viewModelScope
    )

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

    // Account + archive upload — see AccountManager / ArchiveUploader for the rules.
    val organizerAccount: StateFlow<OrganizerAccount?> = accountManager.account
    val readPassword: StateFlow<String?> = accountManager.readPassword
    val accountSyncStatus: StateFlow<AccountSyncStatus?> = accountManager.syncStatus
    val uploadStatus: StateFlow<String?> = uploader.uploadStatus
    val hasPendingUpload: Boolean get() = uploader.hasPendingUpload

    /** Sorted by Elo desc (ties broken by name) so the SetupScreen suggestion chips read as a
     *  rough skill ranking rather than an alphabetical list. */
    var knownCompetitors by mutableStateOf<List<CompetitorStats>>(emptyList())
        private set

    private val _emojiEvents = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val emojiEvents: SharedFlow<String> = _emojiEvents

    fun checkAccountSyncStatus() = accountManager.checkSyncStatus()

    /** Read fresh each time rather than cached at construction — this ViewModel outlives a
     *  single visit to the Settings screen, so a toggle flipped there mid-session must be seen
     *  the next time hosting actually starts. */
    fun useBleSync(): Boolean = settings.useBleSync()

    fun setUseBleSync(enabled: Boolean) = settings.setUseBleSync(enabled)

    /** Known players from past tournaments this group has recorded, so the organizer can pick
     *  existing ones instead of retyping — also how a returning organizer confirms their
     *  account is actually registered with the group before starting a new tournament. */
    fun loadKnownCompetitors() {
        val account = organizerAccount.value ?: return
        val password = readPassword.value ?: return
        viewModelScope.launch {
            when (val result = serverApi(account.serverUrl).listCompetitors(password)) {
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
            val broadcaster = broadcaster ?: return
            val id = tournament.value?.id?.let { RoomCode.forTournament(it) } ?: return
            broadcaster.start(id, repository.tournament.filterNotNull(), viewModelScope)
            emojiBridgeJob?.cancel()
            emojiBridgeJob = viewModelScope.launch {
                broadcaster.emojiEvents.collect { packet ->
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
        val account = organizerAccount.value
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
        val bodyJson = liveJson.encodeToString(Tournament.serializer(), current)
        val signed = UploadSigner.sign(account.privateKeySeed, current.id, bodyJson)
        val result = serverApi(account.serverUrl)
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

    /** Finishes and uploads; the local copy is only cleared once the server confirms — see
     *  [ArchiveUploader]. Hosting stops either way. */
    fun finishAndUpload(finishInfo: TournamentFinishInfo) {
        stopHosting()
        uploader.finishAndUpload(finishInfo)
    }

    fun retryUpload() = uploader.retry()

    fun discardFinishedTournament() = uploader.discard()

    /** Abandons an in-progress tournament (settings menu) so the next "Host a tournament"
     *  starts fresh instead of resuming a dead one. */
    fun clearTournament() {
        stopHosting()
        uploader.clearLocal()
    }

    /** Recorded locally only (see [MatchDrinkStore]) — never touches [repository], so it's never
     *  part of what BLE broadcasting or live sync serialize. Only reaches the server via
     *  [ArchiveUploader]. Each team can be drinking something different, so both are recorded
     *  independently; a match with neither entered isn't stored at all. */
    fun recordDrinks(matchId: String, drinks: MatchDrinks) {
        drinkStore.set(matchId, drinks)
    }

    fun drinksFor(matchId: String): MatchDrinks? = drinkStore.all()[matchId]

    /** Distinct previously-entered drinks (any player), for autocomplete suggestions when
     *  recording a new one. */
    fun knownDrinks(): List<String> =
        drinkStore.all().values.flatMap { listOfNotNull(it.teamA, it.teamB) + it.byPlayer.values }.distinct().sorted()

    /** See [AccountManager.link]; the message is user-facing either way. */
    suspend fun linkAccount(inviteCode: String): Result<String> = accountManager.link(inviteCode)

    /** See [AccountManager.unlink]. An in-progress tournament keeps hosting over BLE untouched
     *  and simply loses server sync until relinked. */
    fun unlinkAccount() {
        stopServerSync()
        accountManager.unlink()
        knownCompetitors = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        stopHosting()
    }
}
