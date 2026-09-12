package com.example.nflunkyball.ble

data class StateChunkPacket(
    val roomId: Int,
    val version: Int,
    val chunkIndex: Int,
    val chunkCount: Int,
    val data: ByteArray,
    /**
     * Distinguishes the legacy small-chunk stream ([BleConstants.MAX_CHUNK_PAYLOAD_BYTES]) from the (much shorter) extended-advertising
     * stream — both broadcast the same tournament state in parallel so every receiver, capable
     * of decoding extended manufacturer data or not, gets a working path. Never mix chunkIndex
     * values from the two: they're reassembled completely independently on the receive side.
     */
    val packetType: Byte = BleConstants.TYPE_STATE_CHUNK
)

data class EmojiPacket(
    val roomId: Int,
    val emojiCode: Byte
)

object PacketCodec {

    fun encodeStateChunk(packet: StateChunkPacket): ByteArray {
        // Bounded by the extended-advertising ceiling, not the legacy one — callers choose
        // per-chunk size based on BleCapability.supportsExtendedAdvertising.
        require(packet.data.size <= BleConstants.MAX_EXTENDED_CHUNK_PAYLOAD_BYTES) { "Chunk payload too large" }
        val buffer = ByteArray(BleConstants.HEADER_SIZE_BYTES + packet.data.size)
        buffer[0] = packet.packetType
        buffer[1] = (packet.roomId shr 8).toByte()
        buffer[2] = packet.roomId.toByte()
        buffer[3] = packet.version.toByte()
        buffer[4] = packet.chunkIndex.toByte()
        buffer[5] = packet.chunkCount.toByte()
        packet.data.copyInto(buffer, destinationOffset = BleConstants.HEADER_SIZE_BYTES)
        return buffer
    }

    fun decodeStateChunk(bytes: ByteArray): StateChunkPacket? {
        if (bytes.size < BleConstants.HEADER_SIZE_BYTES) return null
        val type = bytes[0]
        if (type != BleConstants.TYPE_STATE_CHUNK && type != BleConstants.TYPE_STATE_CHUNK_EXTENDED) return null
        val roomId = ((bytes[1].toInt() and 0xFF) shl 8) or (bytes[2].toInt() and 0xFF)
        val version = bytes[3].toInt() and 0xFF
        val chunkIndex = bytes[4].toInt() and 0xFF
        val chunkCount = bytes[5].toInt() and 0xFF
        val data = bytes.copyOfRange(BleConstants.HEADER_SIZE_BYTES, bytes.size)
        return StateChunkPacket(roomId, version, chunkIndex, chunkCount, data, packetType = type)
    }

    fun encodeEmoji(packet: EmojiPacket): ByteArray = byteArrayOf(
        BleConstants.TYPE_EMOJI,
        (packet.roomId shr 8).toByte(),
        packet.roomId.toByte(),
        packet.emojiCode
    )

    fun decodeEmoji(bytes: ByteArray): EmojiPacket? {
        if (bytes.size < BleConstants.EMOJI_PACKET_SIZE_BYTES) return null
        if (bytes[0] != BleConstants.TYPE_EMOJI) return null
        val roomId = ((bytes[1].toInt() and 0xFF) shl 8) or (bytes[2].toInt() and 0xFF)
        return EmojiPacket(roomId, bytes[3])
    }
}
