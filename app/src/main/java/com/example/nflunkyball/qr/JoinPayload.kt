package com.example.nflunkyball.qr

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the organizer's QR code (or manually-typed fallback) carries. [room] is always present
 * and is all a viewer needs for live BLE score viewing — [server]/[pw] are only included once
 * the organizer has linked an account and synced history, giving scanning viewers automatic
 * read access to the history server too.
 */
@Serializable
data class JoinPayload(
    val room: String,
    val server: String? = null,
    val pw: String? = null
)

object JoinPayloadCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(payload: JoinPayload): String = json.encodeToString(JoinPayload.serializer(), payload)

    /**
     * Accepts either a scanned QR payload (JSON) or a manually-typed bare room code — manual
     * entry only ever needs to unlock live BLE viewing, never the history server, so it's
     * treated as `JoinPayload(room = <the typed text>)`.
     */
    fun decode(text: String): JoinPayload? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("{")) {
            return runCatching { json.decodeFromString(JoinPayload.serializer(), trimmed) }.getOrNull()
        }
        return JoinPayload(room = trimmed.uppercase())
    }
}
