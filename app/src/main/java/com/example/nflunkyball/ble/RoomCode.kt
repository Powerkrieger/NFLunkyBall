package com.example.nflunkyball.ble

/** Crockford base32 — excludes I, L, O, U to avoid visual ambiguity with 1/0/V. */
private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

/**
 * Rooms are identified by a random 16-bit value, used directly on the wire (see
 * [BleConstants.HEADER_SIZE_BYTES]) and shown to people as a 4-character code (via QR or typed
 * manually) so joining is just "start scanning and filter for this id" — no pairing handshake.
 */
object RoomCode {

    fun generate(): Int = (0..0xFFFF).random()

    fun encode(roomId: Int): String {
        require(roomId in 0..0xFFFF) { "roomId must fit in 16 bits" }
        val chars = CharArray(4)
        var value = roomId
        for (i in 3 downTo 0) {
            chars[i] = ALPHABET[value and 0x1F]
            value = value shr 5
        }
        return String(chars)
    }

    fun decode(code: String): Int? {
        val normalized = code.trim().uppercase()
        if (normalized.length != 4) return null
        var value = 0
        for (c in normalized) {
            val index = ALPHABET.indexOf(c)
            if (index == -1) return null
            value = (value shl 5) or index
        }
        return value.takeIf { it in 0..0xFFFF }
    }
}
