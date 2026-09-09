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

    /**
     * Legacy BLE advertising payload is 31 bytes total. After the mandatory 3-byte Flags AD
     * structure and the 4-byte Manufacturer Specific Data header (length+type+company ID),
     * 24 bytes are left for our own header+payload — 18 would use every remaining byte with
     * zero margin, which real devices have been observed to silently reject (advertising just
     * never goes out, no error surfaced) rather than reliably erroring. Leaving real headroom.
     */
    const val MAX_CHUNK_PAYLOAD_BYTES = 12

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
