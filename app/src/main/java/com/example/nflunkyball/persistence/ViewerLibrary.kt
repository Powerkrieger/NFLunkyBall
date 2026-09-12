package com.example.nflunkyball.persistence

import com.example.nflunkyball.model.AppJson
import com.example.nflunkyball.ble.RoomCode
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.model.TournamentPhase
import com.example.nflunkyball.qr.JoinPayload
import com.example.nflunkyball.server.ServerApi
import com.example.nflunkyball.server.ServerResult
import com.example.nflunkyball.server.TournamentSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The rules for what lands in a viewer's saved-tournament list ([ViewerTournamentsStore]) and
 * under which id — see [SavedTournament] for the dedupe key. Three feeders: a room being joined
 * ([rememberJoin]), live state for that room ([trackLive]), and the backend's archive
 * ([cacheFromServer]). Pure JVM apart from the file store, so it's covered by ViewerLibraryTest.
 */
class ViewerLibrary(private val store: ViewerTournamentsStore, private val now: () -> Long = System::currentTimeMillis) {
    private val json = AppJson.lenient

    val tournaments: StateFlow<List<SavedTournament>> = store.tournaments

    fun decode(tournamentJson: String): Tournament? =
        runCatching { json.decodeFromString(Tournament.serializer(), tournamentJson) }.getOrNull()

    /** Records a room the instant it's joined — before any state arrives — so reconnecting
     *  later (even after a transport switch in Settings) can still find it. */
    fun rememberJoin(payload: JoinPayload) {
        val entryId = payload.room.uppercase()
        val existing = store.tournaments.value.find { it.id == entryId }
        store.upsert(
            SavedTournament(
                id = entryId,
                serverId = existing?.serverId,
                name = existing?.name ?: "Room $entryId",
                phase = existing?.phase ?: TournamentPhase.SETUP,
                joinPayload = payload,
                lastUpdated = now(),
                cachedTournamentJson = existing?.cachedTournamentJson
            )
        )
    }

    /** Keeps the saved entry for the current room in sync with live state, so a viewer who was
     *  watching when a tournament finished already has it cached — no server round-trip. */
    fun trackLive(live: Flow<Tournament?>, currentPayload: () -> JoinPayload?, scope: CoroutineScope): Job =
        scope.launch { live.collect { t -> if (t != null) currentPayload()?.let { captureLive(it, t) } } }

    private fun captureLive(payload: JoinPayload, t: Tournament) {
        val entryId = payload.room.uppercase()
        val existing = store.tournaments.value.find { it.id == entryId }
        val alreadyCaptured = existing != null && existing.phase == t.phase && existing.name == t.name &&
            (t.phase != TournamentPhase.FINISHED || existing.cachedTournamentJson != null)
        if (alreadyCaptured) return

        val cachedJson = if (t.phase == TournamentPhase.FINISHED) {
            json.encodeToString(Tournament.serializer(), t)
        } else {
            existing?.cachedTournamentJson
        }
        store.upsert(
            SavedTournament(
                id = entryId,
                serverId = existing?.serverId,
                name = t.name,
                phase = t.phase,
                joinPayload = payload,
                lastUpdated = now(),
                cachedTournamentJson = cachedJson
            )
        )
    }

    /**
     * Downloads and caches each finished tournament's full body so it can be viewed offline —
     * skipping any already cached. The uploaded body carries the tournament's own UUID, so its
     * room code can be derived the same way the live path does ([RoomCode.forTournament]) and
     * the entry stored under that same id: a backend sync folds into (replaces) any stale local
     * entry for the same tournament instead of creating a second, disconnected one. Falls back
     * to `"server:<id>"` only if a downloaded body somehow fails to decode.
     */
    suspend fun cacheFromServer(api: ServerApi, password: String, summaries: List<TournamentSummary>) {
        for (summary in summaries) {
            val existingByServerId = store.tournaments.value.find { it.serverId == summary.id }
            if (existingByServerId?.cachedTournamentJson != null) continue
            val body = when (val result = api.getTournamentJson(summary.id, password)) {
                is ServerResult.Success -> result.value
                is ServerResult.Failure -> continue
            }
            val decoded = decode(body)
            val entryId = decoded?.let { RoomCode.encode(RoomCode.forTournament(it.id)) }
                ?: existingByServerId?.id
                ?: "server:${summary.id}"
            val existing = store.tournaments.value.find { it.id == entryId }
            store.upsert(
                SavedTournament(
                    id = entryId,
                    serverId = summary.id,
                    name = decoded?.name ?: summary.name,
                    phase = TournamentPhase.FINISHED,
                    joinPayload = existing?.joinPayload,
                    lastUpdated = now(),
                    cachedTournamentJson = body
                )
            )
        }
    }
}
