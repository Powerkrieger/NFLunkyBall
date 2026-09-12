package com.example.nflunkyball.persistence

import com.example.nflunkyball.model.MatchDrinks
import com.example.nflunkyball.model.Team
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.model.TournamentFinishInfo
import com.example.nflunkyball.model.TournamentPhase
import com.example.nflunkyball.qr.JoinPayload
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The real file-backed stores, exercised on the JVM against a temp directory: every write goes
 *  through [JsonFile]'s background writer, so each check re-opens a *second* store instance to
 *  prove the data actually reached disk. */
class FileStoresTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("nflunkyball-stores").toFile()
    }

    @After
    fun tearDown() {
        JsonFile.awaitIdle()
        dir.deleteRecursively()
    }

    private val tournament = Tournament(id = "t1", name = "Test", teams = listOf(Team("a", "Anna")), phase = TournamentPhase.BRACKET)

    @Test
    fun `tournament repository round-trips start, update and clear`() {
        val repo = TournamentRepository(dir)
        assertNull(repo.tournament.value)

        repo.start(tournament)
        repo.update { it.copy(name = "Renamed") }
        JsonFile.awaitIdle()
        assertEquals("Renamed", TournamentRepository(dir).tournament.value?.name)
        assertEquals(TournamentPhase.BRACKET, TournamentRepository(dir).tournament.value?.phase)

        repo.clear()
        JsonFile.awaitIdle()
        assertNull(TournamentRepository(dir).tournament.value)
    }

    @Test
    fun `update on an empty repository is a no-op`() {
        val repo = TournamentRepository(dir)
        repo.update { it.copy(name = "x") }
        JsonFile.awaitIdle()
        assertNull(repo.tournament.value)
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `viewer tournaments store persists upserts and removals`() {
        val store = ViewerTournamentsStore(dir)
        val entry = SavedTournament(
            id = "AB12", serverId = 3, name = "Saved", phase = TournamentPhase.FINISHED,
            joinPayload = JoinPayload(room = "AB12"), lastUpdated = 5L, cachedTournamentJson = null
        )
        store.upsert(entry)
        store.upsert(entry.copy(name = "Saved again", lastUpdated = 6L))
        JsonFile.awaitIdle()
        assertEquals(listOf("Saved again"), ViewerTournamentsStore(dir).tournaments.value.map { it.name })

        store.remove("AB12")
        JsonFile.awaitIdle()
        assertTrue(ViewerTournamentsStore(dir).tournaments.value.isEmpty())
    }

    @Test
    fun `drink store trims, drops empty entries and survives reopen`() {
        val store = MatchDrinkStore(dir)
        store.set("m1", MatchDrinks(teamA = " Beer ", byPlayer = mapOf("Anna" to "Cider", "Ben" to "  ")))
        store.set("m2", MatchDrinks(teamA = "  ", teamB = null))
        JsonFile.awaitIdle()

        val reopened = MatchDrinkStore(dir).all()
        assertEquals(setOf("m1"), reopened.keys)
        assertEquals(MatchDrinks(teamA = "Beer", byPlayer = mapOf("Anna" to "Cider")), reopened["m1"])

        store.clear()
        JsonFile.awaitIdle()
        assertTrue(MatchDrinkStore(dir).all().isEmpty())
    }

    @Test
    fun `finish info store round-trips and clears`() {
        val store = FinishInfoStore(dir)
        val info = TournamentFinishInfo(dateMillis = 123L, location = "Garden", referees = "Ref", comment = "")
        store.set(info)
        JsonFile.awaitIdle()
        assertEquals(info, FinishInfoStore(dir).get())

        store.clear()
        JsonFile.awaitIdle()
        assertNull(FinishInfoStore(dir).get())
    }

    @Test
    fun `a corrupt file loads as empty instead of crashing`() {
        File(dir, "tournament.json").writeText("{not json")
        File(dir, "viewer_tournaments.json").writeText("[1,2")
        assertNull(TournamentRepository(dir).tournament.value)
        assertTrue(ViewerTournamentsStore(dir).tournaments.value.isEmpty())
    }
}
