package com.example.nflunkyball.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.util.Log
import com.example.nflunkyball.model.Tournament
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private const val TAG = "TournamentReceiver"

/**
 * Viewer role: scans for a room's broadcast [Tournament] state and can send emoji reactions.
 * Connectionless in both directions — see [TournamentBroadcaster].
 */
class TournamentReceiver(private val adapter: BluetoothAdapter) {

    private val json = Json { ignoreUnknownKeys = true }

    private val _tournament = MutableStateFlow<Tournament?>(null)
    val tournament: StateFlow<Tournament?> = _tournament

    /** How much of the in-flight version has arrived so far, for a "reading state of version N"
     *  UI — null whenever nothing has been received yet (or since the last [stop]). */
    private val _receiveProgress = MutableStateFlow<ChunkProgress?>(null)
    val receiveProgress: StateFlow<ChunkProgress?> = _receiveProgress

    private var receiveJob: Job? = null

    @SuppressLint("MissingPermission")
    fun start(roomId: Int, scope: CoroutineScope) {
        stop()
        _tournament.value = null
        // Legacy and extended-advertising chunk streams carry the same tournament version but
        // are chunked independently (different max payload sizes), so each needs its own
        // reassembler — mixing chunkIndex values from the two would corrupt both. Whichever
        // stream this device can actually decode (every device should manage legacy; extended
        // is a bonus where supported) completes and updates the shared state first.
        val reassemblers = mutableMapOf<Byte, ChunkReassembler>()

        receiveJob = scope.launch {
            val scanner = adapter.bluetoothLeScanner ?: return@launch
            try {
                scanner.manufacturerDataFlow()
                    .mapNotNull { PacketCodec.decodeStateChunk(it) }
                    .filter { it.roomId == roomId }
                    .collect { packet ->
                        val reassembler = reassemblers.getOrPut(packet.packetType) { ChunkReassembler() }
                        val complete = reassembler.receive(packet)
                        _receiveProgress.value = bestProgress(reassemblers)
                        if (complete == null) return@collect
                        runCatching {
                            val decompressed = GzipCodec.decompress(complete)
                            json.decodeFromString(Tournament.serializer(), decompressed.decodeToString())
                        }.onSuccess { _tournament.value = it }
                            .onFailure { Log.w(TAG, "Failed to parse reassembled tournament state", it) }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Scan for room $roomId stopped unexpectedly", e)
            }
        }
    }

    /** Prefer the extended stream once it has any data — it always has far fewer chunks, so
     *  it's both a faster read on capable hardware and a nicer bar to watch. Devices that can't
     *  decode extended manufacturer data at all (a real hardware gap, not hypothetical — see
     *  BleConstants) simply never populate that reassembler, so this naturally falls back to
     *  the legacy stream's progress for them. */
    private fun bestProgress(reassemblers: Map<Byte, ChunkReassembler>): ChunkProgress? {
        reassemblers[BleConstants.TYPE_STATE_CHUNK_EXTENDED]?.progress()
            ?.takeIf { it.receivedIndices.isNotEmpty() }
            ?.let { return it }
        return reassemblers[BleConstants.TYPE_STATE_CHUNK]?.progress()
    }

    fun stop() {
        receiveJob?.cancel()
        receiveJob = null
        _receiveProgress.value = null
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
