package com.example.nflunkyball.ui.viewer

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
import com.example.nflunkyball.ble.TournamentReceiver
import com.example.nflunkyball.ble.ChunkProgress
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.model.TournamentPhase
import com.example.nflunkyball.persistence.AppSettingsStore
import com.example.nflunkyball.persistence.SavedTournament
import com.example.nflunkyball.persistence.ViewerTournamentsStore
import com.example.nflunkyball.qr.JoinPayload
import com.example.nflunkyball.server.CompetitorStats
import com.example.nflunkyball.server.ServerApi
import com.example.nflunkyball.server.ServerCredentialsStore
import com.example.nflunkyball.server.ServerResult
import com.example.nflunkyball.server.TournamentSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class ViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val fetchJson = Json { ignoreUnknownKeys = true }
    private val bluetoothAdapter: BluetoothAdapter? =
        application.getSystemService(BluetoothManager::class.java)?.adapter
    private val receiver = bluetoothAdapter?.let { TournamentReceiver(it) }
    private val credentialsStore = ServerCredentialsStore(application)
    private val settingsStore = AppSettingsStore(application)
    private val tournamentsStore = ViewerTournamentsStore(application)
    private val fallbackTournamentFlow = MutableStateFlow<Tournament?>(null)

    val tournament: StateFlow<Tournament?> = receiver?.tournament ?: fallbackTournamentFlow

    /** How much of the in-flight version has arrived, for a "reading state of version N" +
     *  missing-chunks bar — see [ViewerScoreboardScreen]. */
    val receiveProgress: StateFlow<ChunkProgress?> = receiver?.receiveProgress ?: MutableStateFlow(null)

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
    fun bleEnabled(): Boolean = settingsStore.isBleEnabled()

    fun join(payload: JoinPayload) {
        joinPayload = payload
        payload.pw?.let { credentialsStore.saveReadPassword(it) }
        payload.server?.let { credentialsStore.saveViewerServerUrl(it) }
        val roomId = RoomCode.decode(payload.room) ?: return

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

        // Saving the entry above happens regardless, so reconnecting later (once BLE is turned
        // on, or via a future server-backed fetch) can still find this room.
        if (bleEnabled()) receiver?.start(roomId, viewModelScope)
    }

    /** Resume watching a previously-joined tournament without re-scanning its QR code. */
    fun reconnect(entry: SavedTournament) {
        entry.joinPayload?.let { join(it) }
    }

    /** Manually forget a saved tournament — the one way to clear a duplicate entry (see
     *  [SavedTournament]'s doc on why the same tournament can end up listed twice). */
    fun removeSavedTournament(id: String) {
        tournamentsStore.remove(id)
    }

    fun decodeCachedTournament(cachedJson: String): Tournament? =
        runCatching { fetchJson.decodeFromString(Tournament.serializer(), cachedJson) }.getOrNull()

    private fun resolveServerUrl(): String? = joinPayload?.server ?: credentialsStore.loadViewerServerUrl()

    fun sendEmoji(emoji: String) {
        val roomId = joinPayload?.let { RoomCode.decode(it.room) } ?: return
        viewModelScope.launch { receiver?.sendEmoji(roomId, EmojiPalette.codeFor(emoji)) }
    }

    fun historyAvailable(): Boolean =
        (joinPayload?.pw ?: credentialsStore.loadReadPassword()) != null && resolveServerUrl() != null

    fun loadHistory() {
        val server = resolveServerUrl() ?: return
        val password = joinPayload?.pw ?: credentialsStore.loadReadPassword() ?: return
        val api = ServerApi(server)
        viewModelScope.launch {
            historyStatus = "Loading…"
            when (val result = api.listTournaments(password)) {
                is ServerResult.Success -> {
                    historyTournaments = result.value
                    historyStatus = null
                    result.value.forEach { summary -> cacheFinishedTournament(api, password, summary) }
                }
                is ServerResult.Failure -> historyStatus = result.message
            }
            when (val result = api.listCompetitors(password)) {
                is ServerResult.Success -> competitors = result.value
                is ServerResult.Failure -> Unit
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

    suspend fun fetchTournamentDetail(id: Int): ServerResult<Tournament> {
        val server = resolveServerUrl() ?: return ServerResult.Failure("No server address available")
        val password = joinPayload?.pw ?: credentialsStore.loadReadPassword()
            ?: return ServerResult.Failure("No read password available")
        return when (val result = ServerApi(server).getTournamentJson(id, password)) {
            is ServerResult.Success -> runCatching {
                fetchJson.decodeFromString(Tournament.serializer(), result.value)
            }.fold(
                onSuccess = { ServerResult.Success(it) },
                onFailure = { ServerResult.Failure(it.message ?: "Failed to parse tournament") }
            )
            is ServerResult.Failure -> result
        }
    }

    override fun onCleared() {
        receiver?.stop()
    }
}
