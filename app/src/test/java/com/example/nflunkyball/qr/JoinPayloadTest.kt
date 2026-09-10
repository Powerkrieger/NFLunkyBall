package com.example.nflunkyball.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JoinPayloadTest {

    @Test
    fun `encode decode round trips a full payload`() {
        val payload = JoinPayload(
            room = "ABCD",
            server = "https://REDACTED-SERVER-HOST",
            pw = "secret",
            tid = "11111111-1111-1111-1111-111111111111"
        )
        val decoded = JoinPayloadCodec.decode(JoinPayloadCodec.encode(payload))
        assertEquals(payload, decoded)
    }

    @Test
    fun `encode decode round trips a room-only payload`() {
        val payload = JoinPayload(room = "ABCD")
        val decoded = JoinPayloadCodec.decode(JoinPayloadCodec.encode(payload))
        assertEquals(payload, decoded)
    }

    @Test
    fun `bare room code is treated as a room-only payload`() {
        val decoded = JoinPayloadCodec.decode("abcd")
        assertEquals(JoinPayload(room = "ABCD"), decoded)
    }

    @Test
    fun `blank input decodes to null`() {
        assertNull(JoinPayloadCodec.decode("   "))
    }

    @Test
    fun `malformed json decodes to null`() {
        assertNull(JoinPayloadCodec.decode("{not valid json"))
    }
}
