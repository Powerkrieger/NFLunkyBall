package com.example.nflunkyball.server

import com.example.nflunkyball.model.AppJson
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.Serializable

/**
 * What an admin's `python -m scripts.create_invite --server-url ...` invite code decodes to —
 * bundles the server's own address with the raw token so the app never has to hardcode a
 * server URL anywhere in its (public) source. Matches app/invite_code.py on the backend:
 * base64(json({"server": ..., "token": ...})). The invite QR carries that code wrapped as a
 * `https://<server>/login?code=<code>` link (so a plain camera scan opens the backend's web
 * viewer); [InvitePayloadCodec.decode] unwraps that form too.
 *
 * Uses kotlin.io.encoding.Base64 (pure Kotlin, not android.util.Base64) specifically so this
 * is exercisable — and cross-checked against real Python output — by plain JVM unit tests
 * (InvitePayloadTest), not just at runtime on a device.
 */
@Serializable
data class InvitePayload(val server: String, val token: String)

object InvitePayloadCodec {
    private val json = AppJson.lenient

    @OptIn(ExperimentalEncodingApi::class)
    fun decode(code: String): InvitePayload? = runCatching {
        val decodedBytes = Base64.UrlSafe.decode(unwrapLink(code.trim()))
        json.decodeFromString(InvitePayload.serializer(), decodedBytes.decodeToString())
    }.getOrNull()

    /** The bare code from a `…/login?code=<code>` invite link; anything else is returned as is.
     *  Hand-parsed rather than via java.net.URI so a JVM unit test covers it exactly like the
     *  base64 step. */
    private fun unwrapLink(text: String): String {
        if (!text.startsWith("http://") && !text.startsWith("https://")) return text
        val query = text.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
        return query.split('&')
            .firstOrNull { it.startsWith("code=") }
            ?.removePrefix("code=")
            ?: text
    }
}
