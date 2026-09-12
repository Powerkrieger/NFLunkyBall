package com.example.nflunkyball.persistence

import android.content.Context
import com.example.nflunkyball.model.TournamentFinishInfo
import java.io.File
import kotlinx.serialization.json.Json

/**
 * The finish-dialog answers for the organizer's current tournament, held only between "Finish"
 * being confirmed and the archive upload succeeding. Exists so an upload that fails (offline,
 * revoked account, app killed mid-upload) can be retried later from My tournaments without
 * asking for date/location/referees again — and so the finished tournament itself is never
 * discarded until the server actually has it. Cleared together with [MatchDrinkStore].
 */
class FinishInfoStore(dir: File) {
    constructor(context: Context) : this(context.filesDir)

    private val file = JsonFile(File(dir, "finish_info.json"))
    private val json = Json { ignoreUnknownKeys = true }

    private var cache: TournamentFinishInfo? = load()

    fun get(): TournamentFinishInfo? = cache

    fun set(info: TournamentFinishInfo) {
        cache = info
        file.write(json.encodeToString(TournamentFinishInfo.serializer(), info))
    }

    fun clear() {
        cache = null
        file.delete()
    }

    private fun load(): TournamentFinishInfo? {
        val text = file.readOrNull() ?: return null
        return runCatching { json.decodeFromString(TournamentFinishInfo.serializer(), text) }.getOrNull()
    }
}
