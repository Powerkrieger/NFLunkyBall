package com.example.nflunkyball.ble

object ChunkedMessage {

    /** Splits [payload] into a sequence of [StateChunkPacket]s, all sharing the same [version]. */
    fun chunk(
        roomId: Int,
        version: Int,
        payload: ByteArray,
        maxChunkPayloadBytes: Int = BleConstants.MAX_CHUNK_PAYLOAD_BYTES,
        packetType: Byte = BleConstants.TYPE_STATE_CHUNK
    ): List<StateChunkPacket> {
        val pieces = if (payload.isEmpty()) {
            listOf(ByteArray(0))
        } else {
            payload.toList().chunked(maxChunkPayloadBytes).map { it.toByteArray() }
        }
        require(pieces.size <= BleConstants.MAX_CHUNK_COUNT) { "Payload too large to broadcast in chunks" }
        val count = pieces.size
        return pieces.mapIndexed { index, bytes ->
            StateChunkPacket(roomId, version, index, count, bytes, packetType)
        }
    }
}

/** Snapshot of how much of [version]'s [totalCount] chunks have arrived so far, and exactly
 *  which indices — enough for a UI to draw a per-chunk "which packets are missing" bar rather
 *  than just a plain percentage. */
data class ChunkProgress(val version: Int, val receivedIndices: Set<Int>, val totalCount: Int)

/**
 * Buffers incoming [StateChunkPacket]s for a single room and reassembles the full payload once
 * every chunk of the current [version] has arrived. Not thread-safe — callers should confine
 * use to a single coroutine/dispatcher.
 */
class ChunkReassembler {
    private var currentVersion: Int = -1
    private var expectedCount: Int = -1
    private val chunks = mutableMapOf<Int, ByteArray>()

    /** Returns the reassembled payload once complete for the packet's version, else null. */
    fun receive(packet: StateChunkPacket): ByteArray? {
        if (packet.version != currentVersion) {
            currentVersion = packet.version
            expectedCount = packet.chunkCount
            chunks.clear()
        }
        chunks[packet.chunkIndex] = packet.data
        if (chunks.size < expectedCount) return null

        val buffer = java.io.ByteArrayOutputStream()
        for (index in 0 until expectedCount) {
            buffer.write(chunks[index] ?: return null)
        }
        return buffer.toByteArray()
    }

    /** Null until the first chunk of any version has arrived. */
    fun progress(): ChunkProgress? {
        if (currentVersion < 0) return null
        return ChunkProgress(currentVersion, chunks.keys.toSet(), expectedCount)
    }
}
