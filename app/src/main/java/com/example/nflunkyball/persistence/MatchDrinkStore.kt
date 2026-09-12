package com.example.nflunkyball.persistence

import com.example.nflunkyball.model.AppJson
import android.content.Context
import com.example.nflunkyball.model.MatchDrinks
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * What each match's teams drank, recorded by the organizer — deliberately kept out of the
 * [com.example.nflunkyball.model.Tournament] model itself (matchId -> drinks, not part of
 * [com.example.nflunkyball.model.MatchResult]), so it's structurally impossible for BLE
 * broadcasting or server-backed live sync (both of which serialize the real Tournament object
 * directly) to ever carry it. Only merged in by [com.example.nflunkyball.ui.organizer.OrganizerViewModel.uploadToHistory]
 * via [com.example.nflunkyball.server.toUploadPayload], at the very end of a tournament.
 */
class MatchDrinkStore(dir: File) {
    constructor(context: Context) : this(context.filesDir)

    private val file = JsonFile(File(dir, "match_drinks.json"))
    private val json = AppJson.lenient

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
        val text = file.readOrNull() ?: return mutableMapOf()
        // Falls back to empty on any parse failure, including the old single-drink-per-match
        // shape from before drinks were tracked per team — this is scratch data for the current
        // in-progress tournament only (wiped by clear() after every upload), so losing a
        // not-yet-uploaded tournament's drinks across an app update that changed this format is
        // an acceptable, narrow edge case rather than something worth a real migration for.
        return runCatching { json.decodeFromString<Map<String, MatchDrinks>>(text).toMutableMap() }
            .getOrDefault(mutableMapOf())
    }

    private fun save() {
        file.write(json.encodeToString(cache))
    }
}
