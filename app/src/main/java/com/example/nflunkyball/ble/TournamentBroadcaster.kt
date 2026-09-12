package com.example.nflunkyball.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.util.Log
import com.example.nflunkyball.model.Tournament
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private const val TAG = "TournamentBroadcaster"

/** Organizer-side live transport over BLE, as the sync layer sees it — see [TournamentBroadcaster]. */
interface LiveBroadcaster {
    /** Viewers' emoji reactions for the room being broadcast. */
    val emojiEvents: SharedFlow<EmojiPacket>
    /** The version currently being cycled out over BLE — null whenever nothing is on air. */
    val broadcastVersion: StateFlow<Int?>
    /** Starts (re)broadcasting on [roomId] with every new value from [tournamentUpdates]. */
    fun start(roomId: Int, tournamentUpdates: Flow<Tournament>, scope: CoroutineScope)
    fun stop()
}

/**
 * Organizer role: continuously broadcasts the current [Tournament] state as a room, and
 * listens for viewers' emoji reactions. Connectionless in both directions — see
 * `ble/` package docs / the project plan for why (needs to scale to ~20 simultaneous viewers,
 * past Bluetooth Classic's ~7-connection piconet limit).
 */
class TournamentBroadcaster(private val adapter: BluetoothAdapter, private val context: Context) : LiveBroadcaster {

    // encodeDefaults=false (the kotlinx default) — every default-valued field omitted from the
    // wire payload shrinks the chunk count on this bandwidth-starved transport; decoding still
    // fills defaults back in regardless of whether the JSON was explicit about them.
    private val json = Json

    private val _emojiEvents = MutableSharedFlow<EmojiPacket>(extraBufferCapacity = 32)
    override val emojiEvents: SharedFlow<EmojiPacket> = _emojiEvents

    private val _broadcastVersion = MutableStateFlow<Int?>(null)
    override val broadcastVersion: StateFlow<Int?> = _broadcastVersion

    private var broadcastJob: Job? = null
    private var scanJob: Job? = null

    @SuppressLint("MissingPermission")
    override fun start(roomId: Int, tournamentUpdates: Flow<Tournament>, scope: CoroutineScope) {
        stop()

        scanJob = scope.launch {
            val scanner = adapter.bluetoothLeScanner ?: return@launch
            try {
                scanner.manufacturerDataFlow()
                    .mapNotNull { PacketCodec.decodeEmoji(it) }
                    .filter { it.roomId == roomId }
                    .collect { _emojiEvents.emit(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Emoji scan for room $roomId stopped unexpectedly", e)
            }
        }

        var version = 0
        broadcastJob = scope.launch {
            tournamentUpdates.collectLatest { tournament ->
                version = (version + 1) % 256
                _broadcastVersion.value = version
                broadcastCycle(roomId, version, tournament)
            }
        }
    }

    override fun stop() {
        broadcastJob?.cancel()
        scanJob?.cancel()
        broadcastJob = null
        scanJob = null
        _broadcastVersion.value = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun broadcastCycle(roomId: Int, version: Int, tournament: Tournament) {
        val advertiser = adapter.bluetoothLeAdvertiser ?: return
        val rawBytes = json.encodeToString(Tournament.serializer(), tournament).encodeToByteArray()
        // Tournament JSON is heavily repetitive (field names, UUID-shaped ids) and gzips down to
        // a fraction of its raw size — the difference between a real multi-group tournament
        // fitting in BleConstants.MAX_CHUNK_COUNT legacy chunks and it not (which used to throw
        // out of ChunkedMessage.chunk and crash the whole app, see chunkOrNull below).
        val bytes = GzipCodec.compress(rawBytes)
        val useExtended = BleCapability.supportsExtendedAdvertising(context)
        Log.d(TAG, "Broadcasting v=$version roomId=$roomId rawBytes=${rawBytes.size} gzipBytes=${bytes.size} extended=$useExtended")

        val legacyChunks = chunkOrNull(roomId, version, bytes, BleConstants.MAX_CHUNK_PAYLOAD_BYTES, BleConstants.TYPE_STATE_CHUNK)
        val extendedChunks = if (useExtended) {
            chunkOrNull(roomId, version, bytes, BleCapability.extendedChunkPayloadBytes(context), BleConstants.TYPE_STATE_CHUNK_EXTENDED)
        } else null

        // Always cycle the legacy stream when it fits — every device, extended-capable or not,
        // can decode it. Extended devices ALSO get the much-shorter extended stream in parallel;
        // whichever finishes reassembling first wins (see TournamentReceiver). Broadcasting
        // extended-only would silently strand any receiver whose BLE stack can't parse extended
        // manufacturer data, which is a real failure mode observed on real (if older) hardware,
        // not a hypothetical one.
        coroutineScope {
            if (legacyChunks != null) {
                launch { cycleChunks(advertiser, legacyChunks, useExtended = false) }
            }
            if (extendedChunks != null) {
                launch { cycleChunks(advertiser, extendedChunks, useExtended = true) }
            }
        }
    }

    /** Null (with a logged warning) instead of throwing when [bytes] still doesn't fit in
     *  [BleConstants.MAX_CHUNK_COUNT] chunks even after gzip. Skipping just this stream for this
     *  version beats crashing the whole broadcaster — the other stream (or a smaller future
     *  version) may still get through. */
    private fun chunkOrNull(
        roomId: Int,
        version: Int,
        bytes: ByteArray,
        maxChunkPayloadBytes: Int,
        packetType: Byte
    ): List<StateChunkPacket>? = try {
        ChunkedMessage.chunk(roomId, version, bytes, maxChunkPayloadBytes, packetType)
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "v=$version (${bytes.size} gzip bytes) too large for packetType=$packetType: ${e.message}")
        null
    }

    @SuppressLint("MissingPermission")
    private suspend fun cycleChunks(
        advertiser: BluetoothLeAdvertiser,
        chunks: List<StateChunkPacket>,
        useExtended: Boolean
    ) {
        Log.d(TAG, "Cycling ${chunks.size} chunks extended=$useExtended")
        // Keep cycling this version's chunks until a newer Tournament value replaces it
        // (collectLatest cancels the enclosing broadcastCycle coroutine as soon as that happens,
        // which cancels both this and its sibling stream together).
        while (currentCoroutineContext().isActive) {
            for (chunkPacket in chunks) {
                val packetBytes = PacketCodec.encodeStateChunk(chunkPacket)
                if (useExtended) {
                    advertiser.burstExtended(packetBytes, BleConstants.CHUNK_INTERVAL_MS)
                } else {
                    advertiser.burst(packetBytes, BleConstants.CHUNK_INTERVAL_MS)
                }
            }
        }
    }
}
