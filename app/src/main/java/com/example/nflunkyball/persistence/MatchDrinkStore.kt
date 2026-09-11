package com.example.nflunkyball.persistence

import android.content.Context
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Each player can be drinking something different, so this is never tied to who won.
 *  [teamA]/[teamB] are the singles-era per-side fields (still what the current result dialog
 *  records); [byPlayer] is the per-player record squads need and takes precedence per player
 *  when present. */
@Serializable
data class MatchDrinks(
    val teamA: String? = null,
    val teamB: String? = null,
    val byPlayer: Map<String, String> = emptyMap()
)

/**
 * What each match's teams drank, recorded by the organizer — deliberately kept out of the
 * [com.example.nflunkyball.model.Tournament] model itself (matchId -> drinks, not part of
 * [com.example.nflunkyball.model.MatchResult]), so it's structurally impossible for BLE
 * broadcasting or server-backed live sync (both of which serialize the real Tournament object
 * directly) to ever carry it. Only merged in by [com.example.nflunkyball.ui.organizer.OrganizerViewModel.uploadToHistory]
 * via [com.example.nflunkyball.server.toUploadPayload], at the very end of a tournament.
 */
class MatchDrinkStore(context: Context) {
    private val file = File(context.filesDir, "match_drinks.json")
    private val json = Json { ignoreUnknownKeys = true }

    private var cache: MutableMap<String, MatchDrinks> = load()

    fun all(): Map<String, MatchDrinks> = cache.toMap()

    fun set(matchId: String, drinks: MatchDrinks) {
        val cleaned = MatchDrinks(
            teamA = drinks.teamA?.trim()?.ifBlank { null },
            teamB = drinks.teamB?.trim()?.ifBlank { null },
            byPlayer = drinks.byPlayer.mapValues { it.value.trim() }.filterValues { it.isNotBlank() }
        )
        if (cleaned.teamA == null && cleaned.teamB == null && cleaned.byPlayer.isEmpty()) {
            cache.remove(matchId)
        } else {
            cache[matchId] = cleaned
        }
        save()
    }

    fun clear() {
        cache = mutableMapOf()
        file.delete()
    }

    private fun load(): MutableMap<String, MatchDrinks> {
        if (!file.exists()) return mutableMapOf()
        // Falls back to empty on any parse failure, including the old single-drink-per-match
        // shape from before drinks were tracked per team — this is scratch data for the current
        // in-progress tournament only (wiped by clear() after every upload), so losing a
        // not-yet-uploaded tournament's drinks across an app update that changed this format is
        // an acceptable, narrow edge case rather than something worth a real migration for.
        return runCatching { json.decodeFromString<Map<String, MatchDrinks>>(file.readText()).toMutableMap() }
            .getOrDefault(mutableMapOf())
    }

    private fun save() {
        file.writeText(json.encodeToString(cache))
    }
}
