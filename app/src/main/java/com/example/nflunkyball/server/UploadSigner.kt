package com.example.nflunkyball.server

import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** What every signed organizer request carries alongside its body — see [UploadSigner.sign]. */
data class SignedUpload(val timestamp: Long, val signatureBase64: String)

/**
 * Signs an organizer upload (archive upload and live push share the exact same scheme) the way
 * the backend's app/crypto.py verifies it: the Ed25519 signature over
 * `"<tournamentId>|<unix seconds>|<sha256 hex of the JSON body>"`, base64-encoded (standard
 * alphabet, padded, no line breaks — identical to android.util.Base64.NO_WRAP).
 *
 * Pure JVM (kotlin.io.encoding.Base64, not android.util.Base64) so it's covered by plain unit
 * tests (UploadSignerTest), same reasoning as [InvitePayloadCodec].
 */
object UploadSigner {

    fun message(tournamentId: String, timestamp: Long, bodyJson: String): String =
        "$tournamentId|$timestamp|${sha256Hex(bodyJson)}"

    @OptIn(ExperimentalEncodingApi::class)
    fun sign(
        privateKeySeed: ByteArray,
        tournamentId: String,
        bodyJson: String,
        timestamp: Long = System.currentTimeMillis() / 1000
    ): SignedUpload {
        val signature = Ed25519.sign(privateKeySeed, message(tournamentId, timestamp, bodyJson).toByteArray())
        return SignedUpload(timestamp, Base64.encode(signature))
    }

    fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
