package com.example.nflunkyball.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import com.example.nflunkyball.model.Tournament
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Organizer role: continuously broadcasts the current [Tournament] state as a room, and
 * listens for viewers' emoji reactions. Connectionless in both directions — see
 * `ble/` package docs / the project plan for why (needs to scale to ~20 simultaneous viewers,
 * past Bluetooth Classic's ~7-connection piconet limit).
 */
class TournamentBroadcaster(private val adapter: BluetoothAdapter) {

    private val json = Json { encodeDefaults = true }

    private val _emojiEvents = MutableSharedFlow<EmojiPacket>(extraBufferCapacity = 32)
    val emojiEvents: SharedFlow<EmojiPacket> = _emojiEvents

    private var broadcastJob: Job? = null
    private var scanJob: Job? = null

    /** Starts (re)broadcasting on [roomId] with every new value from [tournamentUpdates]. */
    @SuppressLint("MissingPermission")
    fun start(roomId: Int, tournamentUpdates: Flow<Tournament>, scope: CoroutineScope) {
        stop()

        scanJob = scope.launch {
            val scanner = adapter.bluetoothLeScanner ?: return@launch
            scanner.manufacturerDataFlow()
                .mapNotNull { PacketCodec.decodeEmoji(it) }
                .filter { it.roomId == roomId }
                .collect { _emojiEvents.emit(it) }
        }

        var version = 0
        broadcastJob = scope.launch {
            tournamentUpdates.collectLatest { tournament ->
                version = (version + 1) % 256
                broadcastCycle(roomId, version, tournament)
            }
        }
    }

    fun stop() {
        broadcastJob?.cancel()
        scanJob?.cancel()
        broadcastJob = null
        scanJob = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun broadcastCycle(roomId: Int, version: Int, tournament: Tournament) {
        val advertiser = adapter.bluetoothLeAdvertiser ?: return
        val bytes = json.encodeToString(Tournament.serializer(), tournament).encodeToByteArray()
        val chunks = ChunkedMessage.chunk(roomId, version, bytes)

        // Keep cycling this version's chunks until a newer Tournament value replaces it
        // (collectLatest cancels this coroutine as soon as that happens).
        while (currentCoroutineContext().isActive) {
            for (chunkPacket in chunks) {
                advertiser.burst(PacketCodec.encodeStateChunk(chunkPacket), BleConstants.CHUNK_INTERVAL_MS)
            }
        }
    }
}
