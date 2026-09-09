package com.example.nflunkyball.ble

object BleConstants {
    /**
     * Placeholder/unregistered manufacturer ID used to tag our packets in BLE advertising
     * Manufacturer Specific Data — common practice for private, non-commercial BLE protocols
     * that aren't registering with the Bluetooth SIG.
     */
    const val MANUFACTURER_ID = 0xFFFF

    /** type(1) + roomId(2) + version(1) + chunkIndex(1) + chunkCount(1). */
    const val HEADER_SIZE_BYTES = 6

    /** Legacy BLE advertising payload is 31 bytes total; this is what's left after AD/manufacturer overhead. */
    const val MAX_CHUNK_PAYLOAD_BYTES = 18

    const val MAX_CHUNK_COUNT = 256

    const val TYPE_STATE_CHUNK: Byte = 1
    const val TYPE_EMOJI: Byte = 2

    /** type(1) + roomId(2) + emojiCode(1). */
    const val EMOJI_PACKET_SIZE_BYTES = 4

    /** How long each chunk stays "on air" before the broadcaster moves to the next one. */
    const val CHUNK_INTERVAL_MS = 200L

    /** How long a one-shot emoji advertisement stays on air. */
    const val EMOJI_BURST_MS = 1000L
}
