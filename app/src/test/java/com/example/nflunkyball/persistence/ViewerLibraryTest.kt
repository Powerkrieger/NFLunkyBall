package com.example.nflunkyball.persistence

import com.example.nflunkyball.ble.RoomCode
import com.example.nflunkyball.fakes.FakeServerApi
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.model.TournamentPhase
import com.example.nflunkyball.qr.JoinPayload
import com.example.nflunkyball.server.ServerResult
import com.example.nflunkyball.server.TournamentSummary
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ViewerLibraryTest {

    private val dir: File = Files.createTempDirectory("library").toFile()
    private val store = ViewerTournamentsStore(dir)
    private var clock = 100L
    private val library = ViewerLibrary(store, now = { clock++ })
    private val room = RoomCode.encode(RoomCode.forTournament("t1"))
    private val payload = JoinPayload(room = room.lowercase(), server = "https://srv", pw = "pw", tid = "t1")

    @After
    fun tearDown() {
        JsonFile.awaitIdle()
        dir.deleteRecursively()
    }

    private fun tournamentJson(t: Tournament) = Json.encodeToString(Tournament.serializer(), t)

    @Test
    fun `joining records the room under its upper-cased code before any state arrives`() {
        library.rememberJoin(payload)
        val entry = store.tournaments.value.single()
        assertEquals(room, entry.id)
        assertEquals("Room $room", entry.name)
        assertEquals(TournamentPhase.SETUP, entry.phase)
        assertEquals(payload, entry.joinPayload)
    }

    @Test
    fun `live state updates the entry and caches the body once finished`() = runTest {
        library.rememberJoin(payload)
        val live = MutableStateFlow<Tournament?>(null)
        val tracking = library.trackLive(live, currentPayload = { payload }, scope = this)

        live.value = Tournament(id = "t1", name = "Live", phase = TournamentPhase.GROUP_STAGE)
        runCurrent()
        var entry = store.tournaments.value.single()
        assertEquals("Live", entry.name)
        assertEquals(TournamentPhase.GROUP_STAGE, entry.phase)
        assertNull(entry.cachedTournamentJson)

        live.value = live.value!!.copy(phase = TournamentPhase.FINISHED)
        runCurrent()
        entry = store.tournaments.value.single()
        assertEquals(TournamentPhase.FINISHED, entry.phase)
        assertEquals("Live", library.decode(entry.cachedTournamentJson!!)?.name)
        val stamp = entry.lastUpdated

        // Same state again is a no-op — no churn on every BLE re-broadcast.
        live.value = live.value!!.copy()
        runCurrent()
        assertEquals(stamp, store.tournaments.value.single().lastUpdated)
        tracking.cancel()
    }

    @Test
    fun `a downloaded archive folds into the live entry for the same tournament`() = runTest {
        library.rememberJoin(payload) // stale in-progress entry left behind, keyed by room code
        val api = FakeServerApi()
        api.tournamentJsonResult = ServerResult.Success(tournamentJson(Tournament(id = "t1", name = "Archived", phase = TournamentPhase.FINISHED)))

        library.cacheFromServer(api, "pw", listOf(TournamentSummary(id = 9, name = "Archived", date = "2026-01-01", phase = "FINISHED")))

        val entry = store.tournaments.value.single()
        assertEquals(room, entry.id)
        assertEquals(9, entry.serverId)
        assertEquals(TournamentPhase.FINISHED, entry.phase)
        assertEquals(payload, entry.joinPayload) // kept from the live entry
        assertNotNull(entry.cachedTournamentJson)
    }

    @Test
    fun `already cached archives are not re-downloaded and undecodable ones get a server id key`() = runTest {
        val api = FakeServerApi()
        api.tournamentJsonResult = ServerResult.Success("{not a tournament")
        val summary = TournamentSummary(id = 9, name = "Odd", date = "2026-01-01", phase = "FINISHED")

        library.cacheFromServer(api, "pw", listOf(summary))
        assertEquals("server:9", store.tournaments.value.single().id)
        assertEquals("Odd", store.tournaments.value.single().name)

        api.tournamentJsonResult = ServerResult.Failure("must not be called")
        library.cacheFromServer(api, "pw", listOf(summary))
        assertEquals(1, store.tournaments.value.size)
    }
}
