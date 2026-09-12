package com.example.nflunkyball.ui.viewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nflunkyball.ble.LiveReceiver
import com.example.nflunkyball.ble.RoomCode
import com.example.nflunkyball.ble.ChunkProgress
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.model.TournamentPhase
import com.example.nflunkyball.persistence.AppSettings
import com.example.nflunkyball.persistence.SavedTournament
import com.example.nflunkyball.persistence.ViewerTournamentsStore
import com.example.nflunkyball.qr.JoinPayload
import com.example.nflunkyball.server.CompetitorDetailStats
import com.example.nflunkyball.server.CompetitorStats
import com.example.nflunkyball.server.CredentialsStore
import com.example.nflunkyball.server.MatchDetail
import com.example.nflunkyball.server.ServerApi
import com.example.nflunkyball.server.ServerApiFactory
import com.example.nflunkyball.server.TournamentDetail
import com.example.nflunkyball.server.ServerResult
import com.example.nflunkyball.server.StatsMode
import com.example.nflunkyball.server.TournamentSummary
import com.example.nflunkyball.sync.LiveSyncViewer
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Dependencies come from [com.example.nflunkyball.AppContainer]; every one has a plain-JVM
 *  substitute so this class is unit-testable. [receiver] is null on a device without Bluetooth. */
class ViewerViewModel(
    private val receiver: LiveReceiver?,
    private val credentialsStore: CredentialsStore,
    private val settings: AppSettings,
    private val tournamentsStore: ViewerTournamentsStore,
    private val serverApi: ServerApiFactory
) : ViewModel() {

    private val fetchJson = Json { ignoreUnknownKeys = true }
    private val liveSync = LiveSyncViewer(settings, receiver, serverApi, viewModelScope)

    /** The currently watched room's live state, over BLE or server polling — see [LiveSyncViewer]. */
    val tournament: StateFlow<Tournament?> = liveSync.tournament

    /** BLE reassembly progress for the "reading state of version N" bar — see [ViewerScoreboardScreen]. */
    val receiveProgress: StateFlow<ChunkProgress?> = liveSync.receiveProgress

    /** Tournaments this viewer has joined live (reconnectable) or downloaded from the backend
     *  (viewable offline) — survives the app being killed. See [ViewerTournamentsStore]. */
    val savedTournaments: StateFlow<List<SavedTournament>> = tournamentsStore.tournaments

    var joinPayload by mutableStateOf<JoinPayload?>(null)
        private set

    var historyTournaments by mutableStateOf<List<TournamentSummary>>(emptyList())
        private set
    var competitors by mutableStateOf<List<CompetitorStats>>(emptyList())
        private set
    var historyStatus by mutableStateOf<String?>(null)
        private set
    var playerStats by mutableStateOf<CompetitorDetailStats?>(null)
        private set
    var playerStatsStatus by mutableStateOf<String?>(null)
        private set

    /** Which matches the leaderboard and player pages count (all / singles only / team matches
     *  only) — a view-time choice sent to the backend, which recomputes everything per request.
     *  Session-scoped on purpose: it's a lens, not a setting. */
    var statsMode by mutableStateOf(StatsMode.ALL)
        private set

    private var lastPlayerStatsId: Int? = null

    /** Switches the lens and refreshes whatever is currently loaded under it. */
    fun selectStatsMode(mode: StatsMode) {
        if (mode == statsMode) return
        statsMode = mode
        loadHistory()
        lastPlayerStatsId?.let { loadPlayerStats(it) }
    }

    init {
        // Keep the saved entry for the current room in sync with live BLE state, so a viewer who
        // was watching when a tournament finished already has it cached — no server round-trip.
        viewModelScope.launch {
            tournament.collect { t ->
                if (t == null) return@collect
                val payload = joinPayload ?: return@collect
                val entryId = payload.room.uppercase()
                val existing = tournamentsStore.tournaments.value.find { it.id == entryId }
                val alreadyCaptured = existing != null && existing.phase == t.phase && existing.name == t.name &&
                    (t.phase != TournamentPhase.FINISHED || existing.cachedTournamentJson != null)
                if (alreadyCaptured) return@collect

                val cachedJson = if (t.phase == TournamentPhase.FINISHED) {
                    fetchJson.encodeToString(Tournament.serializer(), t)
                } else {
                    existing?.cachedTournamentJson
                }
                tournamentsStore.upsert(
                    SavedTournament(
                        id = entryId,
                        serverId = existing?.serverId,
                        name = t.name,
                        phase = t.phase,
                        joinPayload = payload,
                        lastUpdated = System.currentTimeMillis(),
                        cachedTournamentJson = cachedJson
                    )
                )
            }
        }
    }

    /** Read fresh each time rather than cached at construction — this ViewModel outlives a
     *  single visit to the Settings screen, so a toggle flipped there mid-session must be seen
     *  the next time a room is joined. */
    fun useBleSync(): Boolean = settings.useBleSync()

    fun join(payload: JoinPayload) {
        joinPayload = payload
        payload.pw?.let { credentialsStore.saveReadPassword(it) }
        payload.server?.let { credentialsStore.saveViewerServerUrl(it) }
        if (RoomCode.decode(payload.room) == null) return

        val entryId = payload.room.uppercase()
        val existing = tournamentsStore.tournaments.value.find { it.id == entryId }
        tournamentsStore.upsert(
            SavedTournament(
                id = entryId,
                serverId = existing?.serverId,
                name = existing?.name ?: "Room $entryId",
                phase = existing?.phase ?: TournamentPhase.SETUP,
                joinPayload = payload,
                lastUpdated = System.currentTimeMillis(),
                cachedTournamentJson = existing?.cachedTournamentJson
            )
        )

        // Saving the entry above happens regardless, so reconnecting later (even after a mode
        // switch in Settings) can still find this room.
        liveSync.join(payload)
    }

    /** Resume watching a previously-joined tournament without re-scanning its QR code. */
    fun reconnect(entry: SavedTournament) {
        entry.joinPayload?.let { join(it) }
    }

    fun decodeCachedTournament(cachedJson: String): Tournament? =
        runCatching { fetchJson.decodeFromString(Tournament.serializer(), cachedJson) }.getOrNull()

    /** Everything a read-only backend call needs — null when this device has no way to reach a
     *  server yet (never joined via QR, never linked, never redeemed a viewer invite). */
    private class ReadAccess(val api: ServerApi, val password: String)

    // Server URL falls back to the organizer account's own server, if this device has one
    // linked — an organizer who's never separately joined a tournament as a viewer (e.g. just
    // wants to check the Leaderboard) otherwise had no server URL on record at all, silently
    // breaking history/leaderboard loading for them despite being fully linked to the group.
    // The password likewise prefers the live join code's over the stored one.
    private fun readAccess(): ReadAccess? {
        val server = joinPayload?.server
            ?: credentialsStore.loadViewerServerUrl()
            ?: credentialsStore.loadAccount()?.serverUrl
            ?: return null
        val password = joinPayload?.pw ?: credentialsStore.loadReadPassword() ?: return null
        return ReadAccess(serverApi(server), password)
    }

    fun sendEmoji(emoji: String) = liveSync.sendEmoji(emoji)

    fun historyAvailable(): Boolean = readAccess() != null

    fun loadHistory() {
        val access = readAccess() ?: return
        viewModelScope.launch {
            historyStatus = "Loading…"
            when (val result = access.api.listTournaments(access.password)) {
                is ServerResult.Success -> {
                    historyTournaments = result.value
                    historyStatus = null
                    result.value.forEach { summary -> cacheFinishedTournament(access.api, access.password, summary) }
                }
                is ServerResult.Failure -> historyStatus = result.message
            }
            when (val result = access.api.listCompetitors(access.password, statsMode)) {
                is ServerResult.Success -> competitors = result.value
                is ServerResult.Failure -> Unit
            }
        }
    }

    fun loadPlayerStats(competitorId: Int) {
        val access = readAccess() ?: return
        // Drop the previous player's stats up front so switching players can't show the old
        // data under the new route (or hide a load failure behind it).
        playerStats = null
        playerStatsStatus = "Loading…"
        lastPlayerStatsId = competitorId
        viewModelScope.launch {
            when (val result = access.api.getCompetitorStats(competitorId, access.password, statsMode)) {
                is ServerResult.Success -> {
                    playerStats = result.value
                    playerStatsStatus = null
                }
                is ServerResult.Failure -> playerStatsStatus = result.message
            }
        }
    }

    /** Downloads and locally caches a finished tournament's full body, so it appears in
     *  [savedTournaments] and can be viewed offline afterwards — skips it if already cached.
     *
     *  The uploaded body carries the tournament's own UUID, so once downloaded we can derive its
     *  room code the same way the live-BLE path does ([RoomCode.forTournament]) and store it
     *  under that same id. That's what folds this into any stale local entry for the same
     *  tournament (e.g. one left behind at BRACKET because the app was killed before it finished)
     *  instead of creating a second, disconnected entry — [ViewerTournamentsStore.upsert] replaces
     *  by id, so the stale unfinished copy is simply gone once this upsert lands. */
    private suspend fun cacheFinishedTournament(api: ServerApi, password: String, summary: TournamentSummary) {
        val existingByServerId = tournamentsStore.tournaments.value.find { it.serverId == summary.id }
        if (existingByServerId?.cachedTournamentJson != null) return
        when (val detail = api.getTournamentJson(summary.id, password)) {
            is ServerResult.Success -> {
                val decoded = decodeCachedTournament(detail.value)
                val entryId = decoded?.let { RoomCode.encode(RoomCode.forTournament(it.id)) }
                    ?: existingByServerId?.id
                    ?: "server:${summary.id}"
                val existing = tournamentsStore.tournaments.value.find { it.id == entryId }
                tournamentsStore.upsert(
                    SavedTournament(
                        id = entryId,
                        serverId = summary.id,
                        name = decoded?.name ?: summary.name,
                        phase = TournamentPhase.FINISHED,
                        joinPayload = existing?.joinPayload,
                        lastUpdated = System.currentTimeMillis(),
                        cachedTournamentJson = detail.value
                    )
                )
            }
            is ServerResult.Failure -> Unit
        }
    }

    /** The archived tournament plus its metadata and link maps (see [TournamentDetail]). */
    suspend fun fetchTournamentDetail(id: Int): ServerResult<TournamentDetail> =
        withReadAccess { api, password -> api.getTournamentDetail(id, password) }

    suspend fun fetchMatchDetail(id: Int): ServerResult<MatchDetail> =
        withReadAccess { api, password -> api.getMatchDetail(id, password, statsMode) }

    private suspend fun <T> withReadAccess(
        call: suspend (ServerApi, String) -> ServerResult<T>
    ): ServerResult<T> {
        val access = readAccess() ?: return ServerResult.Failure("No server access — join a tournament or log in first")
        return call(access.api, access.password)
    }

    override fun onCleared() {
        super.onCleared()
        liveSync.stop()
    }
}
