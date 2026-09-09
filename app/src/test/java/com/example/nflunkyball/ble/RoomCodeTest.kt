package com.example.nflunkyball.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test
    fun `forTournament is deterministic for the same tournament id`() {
        val id = "c2f2d160-3339-4f35-b1ab-8a43ad5a049e"
        assertEquals(RoomCode.forTournament(id), RoomCode.forTournament(id))
    }

    @Test
    fun `forTournament stays within the 16-bit room id range`() {
        val ids = listOf("a", "some-tournament-uuid", "", "!!!", "🍺🍺🍺")
        for (id in ids) {
            val roomId = RoomCode.forTournament(id)
            assert(roomId in 0..0xFFFF) { "roomId $roomId for \"$id\" is out of range" }
        }
    }

    @Test
    fun `forTournament differs for different tournament ids (in practice)`() {
        // Not a strict guarantee (it's a hash), but collisions between two arbitrary UUIDs
        // should be vanishingly rare -- this just catches an accidental constant-output bug.
        assertNotEquals(
            RoomCode.forTournament("11111111-1111-1111-1111-111111111111"),
            RoomCode.forTournament("22222222-2222-2222-2222-222222222222")
        )
    }
}
