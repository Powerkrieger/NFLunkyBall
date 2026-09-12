package com.example.nflunkyball.ui.organizer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nflunkyball.ble.LiveBroadcaster
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
import com.example.nflunkyball.sync.LiveSyncHost
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

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

    private val accountManager = AccountManager(credentialsStore, serverApi, viewModelScope)
    private val liveSync = LiveSyncHost(settings, broadcaster, account = { accountManager.account.value }, serverApi, viewModelScope)
    private val uploader = ArchiveUploader(
        repository, drinkStore, finishInfoStore, account = { accountManager.account.value }, serverApi, viewModelScope
    )

    val tournament: StateFlow<Tournament?> = repository.tournament

    // Live sync — see LiveSyncHost. BLE mode moves broadcastVersion, server mode serverSyncStatus.
    val broadcastVersion: StateFlow<Int?> = liveSync.broadcastVersion
    val serverSyncStatus: StateFlow<String?> = liveSync.serverSyncStatus
    val emojiEvents: SharedFlow<String> = liveSync.emojiEvents

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

    /** (Re)starts live sync for the current tournament over whichever transport Settings
     *  selects — safe to call again after a mode switch or once an account gets linked. */
    fun startHosting() {
        val id = tournament.value?.id ?: return
        liveSync.start(id, repository.tournament.filterNotNull())
    }

    fun stopHosting() = liveSync.stop()

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
        liveSync.stopServerSync()
        accountManager.unlink()
        knownCompetitors = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        stopHosting()
    }
}
