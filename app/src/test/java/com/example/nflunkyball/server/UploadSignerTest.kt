package com.example.nflunkyball.server

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalEncodingApi::class)
class UploadSignerTest {

    @Test
    fun `message is id, timestamp and sha256 of the body joined by pipes`() {
        // echo -n '{}' | sha256sum
        assertEquals(
            "abc|1700000000|44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a",
            UploadSigner.message("abc", 1_700_000_000L, "{}")
        )
    }

    @Test
    fun `signature verifies with the matching public key and not with a tampered body`() {
        val keyPair = Ed25519.generateKeyPair()
        val signed = UploadSigner.sign(keyPair.privateKeySeed, "t-1", """{"name":"x"}""", timestamp = 42L)

        assertEquals(42L, signed.timestamp)
        val signature = Base64.decode(signed.signatureBase64)
        assertEquals(64, signature.size)
        assertTrue(
            Ed25519.verify(keyPair.publicKeyBytes, UploadSigner.message("t-1", 42L, """{"name":"x"}""").toByteArray(), signature)
        )
        assertFalse(
            Ed25519.verify(keyPair.publicKeyBytes, UploadSigner.message("t-1", 42L, """{"name":"y"}""").toByteArray(), signature)
        )
    }
}
