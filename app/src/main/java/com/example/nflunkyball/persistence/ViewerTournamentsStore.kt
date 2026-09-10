package com.example.nflunkyball.persistence

import android.content.Context
import com.example.nflunkyball.model.TournamentPhase
import com.example.nflunkyball.qr.JoinPayload
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * One tournament a viewer knows about, either because they joined it live over BLE ([joinPayload]
 * lets them reconnect) or because it was downloaded from the backend ([serverId] lets it be
 * re-fetched). [id] is the dedupe key: the 4-char room code, known the instant a [JoinPayload] is
 * decoded (before any BLE data arrives) for anything joined live, and derived from the
 * downloaded body's own tournament id (see `ViewerViewModel.cacheFinishedTournament`) for
 * anything discovered via the backend — both paths land on the same code for the same tournament,
 * so a backend sync naturally folds into (replaces) a stale local entry rather than duplicating
 * it. Falls back to `"server:<id>"` only if a downloaded body somehow fails to decode.
 */
@Serializable
data class SavedTournament(
    val id: String,
    val serverId: Int? = null,
    val name: String,
    val phase: TournamentPhase,
    val joinPayload: JoinPayload? = null,
    val lastUpdated: Long,
    val cachedTournamentJson: String? = null
)

/** Pure merge logic, kept separate from file I/O so it's unit-testable without an Android Context. */
object SavedTournamentList {
    const val MAX_ENTRIES = 30

    fun upsert(current: List<SavedTournament>, entry: SavedTournament): List<SavedTournament> =
        (current.filterNot { it.id == entry.id } + entry)
            .sortedByDescending { it.lastUpdated }
            .take(MAX_ENTRIES)

    fun remove(current: List<SavedTournament>, id: String): List<SavedTournament> =
        current.filterNot { it.id == id }
}

/** Viewer-side persistence: recently joined and downloaded tournaments survive the app being
 *  killed, so reconnecting or browsing history never requires re-scanning a QR code. */
class ViewerTournamentsStore(context: Context) {
    private val file = File(context.filesDir, "viewer_tournaments.json")
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(SavedTournament.serializer())

    private val _tournaments = MutableStateFlow(load())
    val tournaments: StateFlow<List<SavedTournament>> = _tournaments

    fun upsert(entry: SavedTournament) {
        val updated = SavedTournamentList.upsert(_tournaments.value, entry)
        _tournaments.value = updated
        save(updated)
    }

    fun remove(id: String) {
        val updated = SavedTournamentList.remove(_tournaments.value, id)
        _tournaments.value = updated
        save(updated)
    }

    private fun load(): List<SavedTournament> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString(listSerializer, file.readText()) }.getOrNull() ?: emptyList()
    }

    private fun save(list: List<SavedTournament>) {
        file.writeText(json.encodeToString(listSerializer, list))
    }
}
