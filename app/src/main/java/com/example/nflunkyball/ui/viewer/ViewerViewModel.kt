package com.example.nflunkyball.ui.viewer

import com.example.nflunkyball.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nflunkyball.ble.LiveReceiver
import com.example.nflunkyball.ble.RoomCode
import com.example.nflunkyball.ble.ChunkProgress
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.persistence.AppSettings
import com.example.nflunkyball.persistence.SavedTournament
import com.example.nflunkyball.persistence.ViewerLibrary
import com.example.nflunkyball.persistence.ViewerTournamentsStore
import com.example.nflunkyball.qr.JoinPayload
import com.example.nflunkyball.server.CompetitorDetailStats
import com.example.nflunkyball.server.CompetitorStats
import com.example.nflunkyball.server.CredentialsStore
import com.example.nflunkyball.server.MatchDetail
import com.example.nflunkyball.server.ServerApi
import com.example.nflunkyball.server.ServerApiFactory
import com.example.nflunkyball.server.TournamentDetail
import com.example.nflunkyball.server.TournamentSummary
import com.example.nflunkyball.server.ServerResult
import com.example.nflunkyball.server.StatsMode
import com.example.nflunkyball.sync.LiveSyncViewer
import com.example.nflunkyball.ui.LoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** An archived tournament as the detail screen shows it: the body plus, when it came from the
 *  backend rather than the offline cache, the metadata and id maps that make things tappable. */
data class ArchivedTournament(val tournament: Tournament, val detail: TournamentDetail?)

/** Dependencies come from [com.example.nflunkyball.AppContainer]; every one has a plain-JVM
 *  substitute so this class is unit-testable. [receiver] is null on a device without Bluetooth. */
class ViewerViewModel(
    private val receiver: LiveReceiver?,
    private val credentialsStore: CredentialsStore,
    private val settings: AppSettings,
    private val tournamentsStore: ViewerTournamentsStore,
    private val serverApi: ServerApiFactory
) : ViewModel() {

    private val liveSync = LiveSyncViewer(settings, receiver, serverApi, viewModelScope)
    private val library = ViewerLibrary(tournamentsStore)

    /** The currently watched room's live state, over BLE or server polling — see [LiveSyncViewer]. */
    val tournament: StateFlow<Tournament?> = liveSync.tournament

    /** BLE reassembly progress for the "reading state of version N" bar — see [ViewerScoreboardScreen]. */
    val receiveProgress: StateFlow<ChunkProgress?> = liveSync.receiveProgress

    /** Tournaments this viewer has joined live (reconnectable) or downloaded from the backend
     *  (viewable offline) — survives the app being killed. See [ViewerTournamentsStore]. */
    val savedTournaments: StateFlow<List<SavedTournament>> = library.tournaments

    private val _joinPayload = MutableStateFlow<JoinPayload?>(null)
    /** The join code of the room currently being watched, if any. */
    val joinPayload: StateFlow<JoinPayload?> = _joinPayload

    private val _history = MutableStateFlow<LoadState<List<TournamentSummary>>>(LoadState.Idle)
    /** The backend's archive list — its value feeds [savedTournaments] via [ViewerLibrary]; the
     *  screen only needs the load state. */
    val history: StateFlow<LoadState<List<TournamentSummary>>> = _history

    private val _competitors = MutableStateFlow<List<CompetitorStats>>(emptyList())
    val competitors: StateFlow<List<CompetitorStats>> = _competitors

    private val _playerStats = MutableStateFlow<LoadState<CompetitorDetailStats>>(LoadState.Idle)
    val playerStats: StateFlow<LoadState<CompetitorDetailStats>> = _playerStats

    private val _tournamentDetail = MutableStateFlow<LoadState<ArchivedTournament>>(LoadState.Idle)
    val tournamentDetail: StateFlow<LoadState<ArchivedTournament>> = _tournamentDetail

    private val _matchDetail = MutableStateFlow<LoadState<MatchDetail>>(LoadState.Idle)
    val matchDetail: StateFlow<LoadState<MatchDetail>> = _matchDetail

    /** Which matches the leaderboard and player pages count (all / singles only / team matches
     *  only) — a view-time choice sent to the backend, which recomputes everything per request.
     *  Session-scoped on purpose: it's a lens, not a setting. */
    private val _statsMode = MutableStateFlow(StatsMode.ALL)
    val statsMode: StateFlow<StatsMode> = _statsMode

    private var lastPlayerStatsId: Int? = null
    private var lastMatchId: Int? = null

    /** Switches the lens and refreshes whatever is currently loaded under it. */
    fun selectStatsMode(mode: StatsMode) {
        if (mode == _statsMode.value) return
        _statsMode.value = mode
        loadHistory()
        lastPlayerStatsId?.let { loadPlayerStats(it) }
        lastMatchId?.let { loadMatchDetail(it) }
    }

    init {
        library.trackLive(liveSync.tournament, currentPayload = { _joinPayload.value }, viewModelScope)
    }

    /** Read fresh each time rather than cached at construction — this ViewModel outlives a
     *  single visit to the Settings screen, so a toggle flipped there mid-session must be seen
     *  the next time a room is joined. */
    fun useBleSync(): Boolean = settings.useBleSync()

    fun join(payload: JoinPayload) {
        _joinPayload.value = payload
        payload.pw?.let { credentialsStore.saveReadPassword(it) }
        payload.server?.let { credentialsStore.saveViewerServerUrl(it) }
        if (RoomCode.decode(payload.room) == null) return

        // Saved regardless of whether any state ever arrives, so reconnecting later (even after
        // a mode switch in Settings) can still find this room.
        library.rememberJoin(payload)
        liveSync.join(payload)
    }

    /** Resume watching a previously-joined tournament without re-scanning its QR code. */
    fun reconnect(entry: SavedTournament) {
        entry.joinPayload?.let { join(it) }
    }

    /** Everything a read-only backend call needs — null when this device has no way to reach a
     *  server yet (never joined via QR, never linked, never redeemed a viewer invite). */
    private class ReadAccess(val api: ServerApi, val password: String)

    // Server URL falls back to the organizer account's own server, if this device has one
    // linked — an organizer who's never separately joined a tournament as a viewer (e.g. just
    // wants to check the Leaderboard) otherwise had no server URL on record at all, silently
    // breaking history/leaderboard loading for them despite being fully linked to the group.
    // The password likewise prefers the live join code's over the stored one.
    private fun readAccess(): ReadAccess? {
        val payload = _joinPayload.value
        val server = payload?.server
            ?: credentialsStore.loadViewerServerUrl()
            ?: credentialsStore.loadAccount()?.serverUrl
            ?: return null
        val password = payload?.pw ?: credentialsStore.loadReadPassword() ?: return null
        return ReadAccess(serverApi(server), password)
    }

    fun sendEmoji(emoji: String) = liveSync.sendEmoji(emoji)

    fun historyAvailable(): Boolean = readAccess() != null

    fun loadHistory() {
        val access = readAccess() ?: return
        viewModelScope.launch {
            _history.value = LoadState.Loading
            when (val result = access.api.listTournaments(access.password)) {
                is ServerResult.Success -> {
                    _history.value = LoadState.Loaded(result.value)
                    library.cacheFromServer(access.api, access.password, result.value)
                }
                is ServerResult.Failure -> _history.value = LoadState.Failed(result.message)
            }
            when (val result = access.api.listCompetitors(access.password, _statsMode.value)) {
                is ServerResult.Success -> _competitors.value = result.value
                is ServerResult.Failure -> Unit
            }
        }
    }

    fun loadPlayerStats(competitorId: Int) {
        lastPlayerStatsId = competitorId
        // Reset up front so switching players can't show the old data under the new route.
        load(_playerStats) { api, password -> api.getCompetitorStats(competitorId, password, _statsMode.value) }
    }

    fun loadMatchDetail(matchId: Int) {
        lastMatchId = matchId
        load(_matchDetail) { api, password -> api.getMatchDetail(matchId, password, _statsMode.value) }
    }

    /**
     * One archived tournament, addressed by its saved-list id ([savedId]) or its backend id
     * ([serverId], e.g. from a player's Elo history). With a server id on hand the backend's
     * detail endpoint is preferred — it carries the finish metadata and the id maps that make
     * match rows and player names tappable; the locally cached body is the offline fallback.
     */
    fun loadTournamentDetail(savedId: String?, serverId: Int?) {
        val saved = savedTournaments.value
        val entry = saved.find { savedId != null && it.id == savedId } ?: saved.find { serverId != null && it.serverId == serverId }
        val resolvedServerId = serverId ?: entry?.serverId
        _tournamentDetail.value = LoadState.Loading
        viewModelScope.launch {
            var failure: String? = null
            if (resolvedServerId != null) {
                when (val result = withReadAccess { api, password -> api.getTournamentDetail(resolvedServerId, password) }) {
                    is ServerResult.Success -> {
                        _tournamentDetail.value = LoadState.Loaded(ArchivedTournament(result.value.tournament, result.value))
                        return@launch
                    }
                    is ServerResult.Failure -> failure = result.message.takeUnless { it == NO_ACCESS }
                }
            }
            val cached = entry?.cachedTournamentJson?.let { library.decode(it) }
            _tournamentDetail.value = when {
                cached != null -> LoadState.Loaded(ArchivedTournament(cached, detail = null))
                entry?.cachedTournamentJson != null -> LoadState.Failed(reasonRes = R.string.error_cached_unreadable)
                failure != null -> LoadState.Failed(failure)
                else -> LoadState.Failed(reasonRes = R.string.error_tournament_unavailable)
            }
        }
    }

    private fun <T> load(target: MutableStateFlow<LoadState<T>>, call: suspend (ServerApi, String) -> ServerResult<T>) {
        target.value = LoadState.Loading
        viewModelScope.launch {
            target.value = withReadAccess(call).toLoadState()
        }
    }

    private suspend fun <T> withReadAccess(
        call: suspend (ServerApi, String) -> ServerResult<T>
    ): ServerResult<T> {
        val access = readAccess() ?: return ServerResult.Failure(NO_ACCESS)
        return call(access.api, access.password)
    }

    private fun <T> ServerResult<T>.toLoadState(): LoadState<T> = when (this) {
        is ServerResult.Success -> LoadState.Loaded(value)
        is ServerResult.Failure -> if (message == NO_ACCESS) LoadState.Failed(reasonRes = R.string.error_no_server_access) else LoadState.Failed(message)
    }

    private companion object {
        /** Sentinel so the one app-originated read failure can be shown localised (see [toLoadState]). */
        const val NO_ACCESS = "no-server-access"
    }

    override fun onCleared() {
        super.onCleared()
        liveSync.stop()
    }
}
