package com.example.nflunkyball.persistence

import android.content.Context
import java.io.File
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * What each match's loser drank, recorded by the organizer — deliberately kept out of the
 * [com.example.nflunkyball.model.Tournament] model itself (matchId -> drink, not part of
 * [com.example.nflunkyball.model.MatchResult]), so it's structurally impossible for BLE
 * broadcasting or server-backed live sync (both of which serialize the real Tournament object
 * directly) to ever carry it. Only merged in by [com.example.nflunkyball.ui.organizer.OrganizerViewModel.uploadToHistory]
 * via [com.example.nflunkyball.server.toUploadPayload], at the very end of a tournament.
 */
class MatchDrinkStore(context: Context) {
    private val file = File(context.filesDir, "match_drinks.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    private var cache: MutableMap<String, String> = load()

    fun all(): Map<String, String> = cache.toMap()

    fun set(matchId: String, drink: String) {
        cache[matchId] = drink
        save()
    }

    fun clear() {
        cache = mutableMapOf()
        file.delete()
    }

    private fun load(): MutableMap<String, String> {
        if (!file.exists()) return mutableMapOf()
        return runCatching { json.decodeFromString(mapSerializer, file.readText()).toMutableMap() }
            .getOrDefault(mutableMapOf())
    }

    private fun save() {
        file.writeText(json.encodeToString(mapSerializer, cache))
    }
}
