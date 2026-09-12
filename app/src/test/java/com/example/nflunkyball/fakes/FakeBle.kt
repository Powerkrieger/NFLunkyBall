package com.example.nflunkyball.fakes

import com.example.nflunkyball.ble.ChunkProgress
import com.example.nflunkyball.ble.EmojiPacket
import com.example.nflunkyball.ble.LiveBroadcaster
import com.example.nflunkyball.ble.LiveReceiver
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.persistence.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAppSettings(private var bleSync: Boolean = false) : AppSettings {
    override fun useBleSync() = bleSync
    override fun setUseBleSync(enabled: Boolean) { bleSync = enabled }
}

class FakeBroadcaster : LiveBroadcaster {
    override val emojiEvents = MutableSharedFlow<EmojiPacket>(extraBufferCapacity = 32)
    override val broadcastVersion = MutableStateFlow<Int?>(null)
    val startedRooms = mutableListOf<Int>()
    var stops = 0
    var updates: Flow<Tournament>? = null

    override fun start(roomId: Int, tournamentUpdates: Flow<Tournament>, scope: CoroutineScope) {
        startedRooms += roomId
        updates = tournamentUpdates
        broadcastVersion.value = startedRooms.size
    }

    override fun stop() {
        stops++
        broadcastVersion.value = null
    }
}

class FakeReceiver : LiveReceiver {
    override val tournament = MutableStateFlow<Tournament?>(null)
    override val receiveProgress = MutableStateFlow<ChunkProgress?>(null)
    val startedRooms = mutableListOf<Int>()
    val sentEmoji = mutableListOf<Pair<Int, Byte>>()
    var stops = 0

    override fun start(roomId: Int, scope: CoroutineScope) {
        startedRooms += roomId
        tournament.value = null
    }

    override fun stop() { stops++ }

    override suspend fun sendEmoji(roomId: Int, emojiCode: Byte) { sentEmoji += roomId to emojiCode }
}
