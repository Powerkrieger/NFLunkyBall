package com.example.nflunkyball.persistence

import android.content.Context
import com.example.nflunkyball.model.Tournament
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json

/** Organizer-side persistence: the in-progress tournament survives the app being killed. */
class TournamentRepository(dir: File) {
    constructor(context: Context) : this(context.filesDir)

    private val file = JsonFile(File(dir, "tournament.json"))
    private val json = Json { encodeDefaults = true }

    private val _tournament = MutableStateFlow(load())
    val tournament: StateFlow<Tournament?> = _tournament

    fun start(newTournament: Tournament) {
        _tournament.value = newTournament
        save(newTournament)
    }

    fun update(transform: (Tournament) -> Tournament) {
        val updated = transform(_tournament.value ?: return)
        _tournament.value = updated
        save(updated)
    }

    fun clear() {
        _tournament.value = null
        file.delete()
    }

    private fun load(): Tournament? {
        val text = file.readOrNull() ?: return null
        return runCatching { json.decodeFromString(Tournament.serializer(), text) }.getOrNull()
    }

    private fun save(tournament: Tournament) {
        file.write(json.encodeToString(Tournament.serializer(), tournament))
    }
}
