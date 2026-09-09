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
     * 24 bytes are left for our own header+payload — 18 uses every remaining byte with zero
     * margin. An earlier round of testing (before dual-stream broadcasting existed, when this
     * was the only path and any drop was fatal) found that unreliable and backed off to 12 for
     * safety margin. Re-tested live now that legacy chunking runs alongside extended
     * advertising as a fallback path (see TournamentBroadcaster) — 18 worked cleanly.
     */
    const val MAX_CHUNK_PAYLOAD_BYTES = 18

    /**
     * Bluetooth 5 extended advertising drops the 31-byte legacy ceiling entirely — real device
     * max (queried via BluetoothAdapter.getLeMaximumAdvertisingDataLength()) can reach ~1650B,
     * but we stay well under whatever that reports, same "leave real headroom" lesson as the
     * legacy case above. Most phones from the last several years support extended advertising
     * at the chipset level (it's a Bluetooth 5.0 baseline feature), so this collapses what
     * would be dozens of legacy chunks into a small handful.
     */
    const val MAX_EXTENDED_CHUNK_PAYLOAD_BYTES = 200

    const val MAX_CHUNK_COUNT = 256

    const val TYPE_STATE_CHUNK: Byte = 1
    const val TYPE_EMOJI: Byte = 2

    /**
     * Same wire shape as [TYPE_STATE_CHUNK], broadcast over extended advertising with a much
     * larger per-chunk payload. Kept as a distinct type (not just a bigger legacy chunk) so a
     * receiver getting both streams at once never mixes chunkIndex values from the two
     * independently-sized chunkings of the same tournament version.
     */
    const val TYPE_STATE_CHUNK_EXTENDED: Byte = 3

    /** type(1) + roomId(2) + emojiCode(1). */
    const val EMOJI_PACKET_SIZE_BYTES = 4

    /** How long each chunk stays "on air" before the broadcaster moves to the next one. */
    const val CHUNK_INTERVAL_MS = 200L

    /** How long a one-shot emoji advertisement stays on air. */
    const val EMOJI_BURST_MS = 1000L
}
