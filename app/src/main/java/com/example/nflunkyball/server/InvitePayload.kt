package com.example.nflunkyball.server

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What an admin's `python -m scripts.create_invite --server-url ...` invite code decodes to —
 * bundles the server's own address with the raw token so the app never has to hardcode a
 * server URL anywhere in its (public) source. Matches app/invite_code.py on the backend:
 * base64(json({"server": ..., "token": ...})).
 *
 * Uses kotlin.io.encoding.Base64 (pure Kotlin, not android.util.Base64) specifically so this
 * is exercisable — and cross-checked against real Python output — by plain JVM unit tests
 * (InvitePayloadTest), not just at runtime on a device.
 */
@Serializable
data class InvitePayload(val server: String, val token: String)

object InvitePayloadCodec {
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalEncodingApi::class)
    fun decode(code: String): InvitePayload? = runCatching {
        val decodedBytes = Base64.UrlSafe.decode(code.trim())
        json.decodeFromString(InvitePayload.serializer(), decodedBytes.decodeToString())
    }.getOrNull()
}
