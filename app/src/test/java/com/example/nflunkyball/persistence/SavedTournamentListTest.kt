package com.example.nflunkyball.persistence

import com.example.nflunkyball.model.TournamentPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedTournamentListTest {

    private fun entry(id: String, lastUpdated: Long, phase: TournamentPhase = TournamentPhase.SETUP) =
        SavedTournament(id = id, name = id, phase = phase, lastUpdated = lastUpdated)

    @Test
    fun `upsert adds a new entry and sorts by most recently updated first`() {
        val list = SavedTournamentList.upsert(emptyList(), entry("A", 100))
        val updated = SavedTournamentList.upsert(list, entry("B", 200))
        assertEquals(listOf("B", "A"), updated.map { it.id })
    }

    @Test
    fun `upsert replaces an existing entry with the same id instead of duplicating it`() {
        val list = SavedTournamentList.upsert(emptyList(), entry("A", 100, TournamentPhase.SETUP))
        val updated = SavedTournamentList.upsert(list, entry("A", 200, TournamentPhase.FINISHED))
        assertEquals(1, updated.size)
        assertEquals(TournamentPhase.FINISHED, updated.single().phase)
    }

    @Test
    fun `upsert caps the list at MAX_ENTRIES, dropping the oldest`() {
        var list = emptyList<SavedTournament>()
        for (i in 0 until SavedTournamentList.MAX_ENTRIES + 5) {
            list = SavedTournamentList.upsert(list, entry("T$i", lastUpdated = i.toLong()))
        }
        assertEquals(SavedTournamentList.MAX_ENTRIES, list.size)
        assertTrue(list.none { it.id == "T0" })
        assertTrue(list.any { it.id == "T${SavedTournamentList.MAX_ENTRIES + 4}" })
    }

    @Test
    fun `remove drops only the matching id`() {
        val list = SavedTournamentList.upsert(
            SavedTournamentList.upsert(emptyList(), entry("A", 100)),
            entry("B", 200)
        )
        val updated = SavedTournamentList.remove(list, "A")
        assertEquals(listOf("B"), updated.map { it.id })
    }

    @Test
    fun `remove is a no-op when the id isn't present`() {
        val list = SavedTournamentList.upsert(emptyList(), entry("A", 100))
        assertEquals(list, SavedTournamentList.remove(list, "does-not-exist"))
    }
}
