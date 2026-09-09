package com.example.nflunkyball.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import com.example.nflunkyball.model.Tournament
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Viewer role: scans for a room's broadcast [Tournament] state and can send emoji reactions.
 * Connectionless in both directions — see [TournamentBroadcaster].
 */
class TournamentReceiver(private val adapter: BluetoothAdapter) {

    private val json = Json { ignoreUnknownKeys = true }

    private val _tournament = MutableStateFlow<Tournament?>(null)
    val tournament: StateFlow<Tournament?> = _tournament

    private var receiveJob: Job? = null

    @SuppressLint("MissingPermission")
    fun start(roomId: Int, scope: CoroutineScope) {
        stop()
        _tournament.value = null
        val reassembler = ChunkReassembler()

        receiveJob = scope.launch {
            val scanner = adapter.bluetoothLeScanner ?: return@launch
            scanner.manufacturerDataFlow()
                .mapNotNull { PacketCodec.decodeStateChunk(it) }
                .filter { it.roomId == roomId }
                .collect { packet ->
                    val complete = reassembler.receive(packet) ?: return@collect
                    runCatching {
                        json.decodeFromString(Tournament.serializer(), complete.decodeToString())
                    }.onSuccess { _tournament.value = it }
                }
        }
    }

    fun stop() {
        receiveJob?.cancel()
        receiveJob = null
    }

    @SuppressLint("MissingPermission")
    suspend fun sendEmoji(roomId: Int, emojiCode: Byte) {
        val advertiser = adapter.bluetoothLeAdvertiser ?: return
        advertiser.burst(
            PacketCodec.encodeEmoji(EmojiPacket(roomId, emojiCode)),
            BleConstants.EMOJI_BURST_MS
        )
    }
}
