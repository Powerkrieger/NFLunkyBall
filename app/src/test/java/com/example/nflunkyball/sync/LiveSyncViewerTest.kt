package com.example.nflunkyball.sync

import com.example.nflunkyball.ble.RoomCode
import com.example.nflunkyball.fakes.FakeAppSettings
import com.example.nflunkyball.fakes.FakeReceiver
import com.example.nflunkyball.fakes.FakeServerApi
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.qr.JoinPayload
import com.example.nflunkyball.server.ServerResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSyncViewerTest {

    private val receiver = FakeReceiver()
    private val api = FakeServerApi()
    private val room = RoomCode.encode(RoomCode.forTournament("t1"))
    private val fullPayload = JoinPayload(room = room, server = "https://srv", pw = "pw", tid = "t1")

    private fun viewer(scope: CoroutineScope, ble: Boolean) =
        LiveSyncViewer(FakeAppSettings(bleSync = ble), receiver, serverApi = { api }, scope = scope)

    @Test
    fun `server mode polls, decodes and keeps polling`() = runTest {
        api.liveJsonResult = ServerResult.Success(Json.encodeToString(Tournament.serializer(), Tournament(id = "t1", name = "Live")))
        val viewer = viewer(this, ble = false)

        assertTrue(viewer.join(fullPayload))
        runCurrent()
        assertEquals("Live", viewer.tournament.value?.name)
        assertEquals(1, api.liveJsonRequests)

        advanceTimeBy(LIVE_POLL_INTERVAL_MS + 1)
        assertEquals(2, api.liveJsonRequests)
        assertTrue(receiver.startedRooms.isEmpty())

        viewer.stop()
        assertNull(viewer.tournament.value)
        advanceUntilIdle()
        assertEquals(2, api.liveJsonRequests)
    }

    @Test
    fun `server mode with a bare room code never polls`() = runTest {
        val viewer = viewer(this, ble = false)
        assertTrue(viewer.join(JoinPayload(room = room)))
        advanceUntilIdle()
        assertEquals(0, api.liveJsonRequests)
        viewer.stop()
    }

    @Test
    fun `BLE mode bridges the receiver and sends reactions to the joined room`() = runTest {
        val viewer = viewer(this, ble = true)
        assertTrue(viewer.join(JoinPayload(room = room)))
        assertEquals(listOf(RoomCode.forTournament("t1")), receiver.startedRooms)

        receiver.tournament.value = Tournament(id = "t1", name = "Over BLE")
        runCurrent()
        assertEquals("Over BLE", viewer.tournament.value?.name)

        viewer.sendEmoji("🔥")
        runCurrent()
        assertEquals(listOf(RoomCode.forTournament("t1") to 1.toByte()), receiver.sentEmoji)
        viewer.stop()
    }

    @Test
    fun `an undecodable room code is rejected`() = runTest {
        assertFalse(viewer(this, ble = true).join(JoinPayload(room = "!!!!")))
    }
}
