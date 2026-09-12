package com.example.nflunkyball.sync

import com.example.nflunkyball.ble.EmojiPacket
import com.example.nflunkyball.ble.RoomCode
import com.example.nflunkyball.fakes.FakeAppSettings
import com.example.nflunkyball.fakes.FakeBroadcaster
import com.example.nflunkyball.fakes.FakeServerApi
import com.example.nflunkyball.fakes.testAccount
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.server.Ed25519
import com.example.nflunkyball.server.OrganizerAccount
import com.example.nflunkyball.server.ServerResult
import com.example.nflunkyball.server.UploadSigner
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalEncodingApi::class)
class LiveSyncHostTest {

    private val broadcaster = FakeBroadcaster()
    private val api = FakeServerApi()
    private val account = MutableStateFlow<OrganizerAccount?>(testAccount())
    private val updates = MutableStateFlow<Tournament?>(Tournament(id = "t1", name = "T"))

    private fun host(scope: CoroutineScope, ble: Boolean) =
        LiveSyncHost(FakeAppSettings(bleSync = ble), broadcaster, account, serverApi = { api }, scope = scope)

    @Test
    fun `BLE mode starts the broadcaster on the tournament's room and relays reactions once`() = runTest {
        val host = host(this, ble = true)
        val received = mutableListOf<String>()
        val collector = launch { host.emojiEvents.collect { received += it } }

        host.start("t1", updates.filterNotNull())
        host.start("t1", updates.filterNotNull()) // HostingScreen re-entered: must not stack a second bridge
        advanceUntilIdle()

        assertEquals(listOf(RoomCode.forTournament("t1"), RoomCode.forTournament("t1")), broadcaster.startedRooms)
        assertEquals(2, host.broadcastVersion.value)
        broadcaster.emojiEvents.emit(EmojiPacket(roomId = 0, emojiCode = 1))
        advanceUntilIdle()
        assertEquals(listOf("🔥"), received)
        assertTrue(api.livePushes.isEmpty())

        host.stop()
        assertNull(host.broadcastVersion.value)
        collector.cancel()
    }

    @Test
    fun `server mode without an account reports not linked and pushes nothing`() = runTest {
        account.value = null
        val host = host(this, ble = false)
        host.start("t1", updates.filterNotNull())
        advanceUntilIdle()

        assertEquals("Not linked — link an organizer account to sync", host.serverSyncStatus.value)
        assertTrue(api.livePushes.isEmpty())
        assertTrue(broadcaster.startedRooms.isEmpty())
        host.stop()
    }

    @Test
    fun `server mode pushes a signed body for every update`() = runTest {
        val host = host(this, ble = false)
        host.start("t1", updates.filterNotNull())
        advanceUntilIdle()
        assertEquals("Synced", host.serverSyncStatus.value)

        updates.value = updates.value!!.copy(name = "Renamed")
        advanceUntilIdle()

        assertEquals(2, api.livePushes.size)
        val push = api.livePushes[1]
        assertEquals("t1", push.tournamentId)
        assertEquals(7, push.accountId)
        assertTrue(push.bodyJson.contains("\"name\":\"Renamed\""))
        val message = UploadSigner.message("t1", push.timestamp, push.bodyJson).toByteArray()
        assertTrue(Ed25519.verify(account.value!!.publicKeyBytes, message, Base64.decode(push.signatureBase64)))

        api.livePushResult = ServerResult.Failure("500")
        updates.value = updates.value!!.copy(name = "Again")
        advanceUntilIdle()
        assertEquals("Sync failed: 500", host.serverSyncStatus.value)

        host.stop()
        assertNull(host.serverSyncStatus.value)
    }

    @Test
    fun `server mode follows the account - unlinking stops pushes, relinking resumes them`() = runTest {
        val host = host(this, ble = false)
        host.start("t1", updates.filterNotNull())
        advanceUntilIdle()
        assertEquals(1, api.livePushes.size)

        account.value = null
        advanceUntilIdle()
        assertEquals("Not linked — link an organizer account to sync", host.serverSyncStatus.value)
        updates.value = updates.value!!.copy(name = "While unlinked")
        advanceUntilIdle()
        assertEquals(1, api.livePushes.size)

        account.value = testAccount()
        advanceUntilIdle()
        assertEquals(2, api.livePushes.size)
        assertEquals("Synced", host.serverSyncStatus.value)
        host.stop()
    }
}
