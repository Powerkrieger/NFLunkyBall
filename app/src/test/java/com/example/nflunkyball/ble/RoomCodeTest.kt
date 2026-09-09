package com.example.nflunkyball.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoomCodeTest {

    @Test
    fun `encode decode round trips across the full 16-bit range`() {
        for (roomId in listOf(0, 1, 255, 256, 4095, 32768, 0xFFFF)) {
            val code = RoomCode.encode(roomId)
            assertEquals(4, code.length)
            assertEquals(roomId, RoomCode.decode(code))
        }
    }

    @Test
    fun `decode is case insensitive and trims whitespace`() {
        val code = RoomCode.encode(12345)
        assertEquals(12345, RoomCode.decode(" ${code.lowercase()} "))
    }

    @Test
    fun `decode rejects invalid input`() {
        assertNull(RoomCode.decode("ABC")) // too short
        assertNull(RoomCode.decode("ABCDE")) // too long
        assertNull(RoomCode.decode("ILOU")) // characters excluded from Crockford base32
    }
}
