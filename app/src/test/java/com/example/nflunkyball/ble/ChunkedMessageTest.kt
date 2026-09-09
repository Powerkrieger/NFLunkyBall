package com.example.nflunkyball.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class ChunkedMessageTest {

    @Test
    fun `chunk and reassemble round trips in order`() {
        val payload = "the quick brown fox jumps over the lazy dog many times".toByteArray()
        val chunks = ChunkedMessage.chunk(roomId = 0x1234, version = 7, payload = payload)

        val reassembler = ChunkReassembler()
        var result: ByteArray? = null
        for (chunk in chunks) {
            result = reassembler.receive(chunk) ?: continue
        }

        assertArrayEquals(payload, result)
    }

    @Test
    fun `reassembly tolerates out-of-order and duplicate chunks`() {
        val payload = ByteArray(200) { it.toByte() }
        val chunks = ChunkedMessage.chunk(roomId = 1, version = 1, payload = payload).shuffled(Random(42))

        val reassembler = ChunkReassembler()
        var result: ByteArray? = null
        for (chunk in chunks + chunks) { // duplicates shouldn't break anything
            result = reassembler.receive(chunk) ?: result
        }

        assertArrayEquals(payload, result)
    }

    @Test
    fun `newer version discards in-progress reassembly of the old one`() {
        val oldPayload = "old state".toByteArray()
        val newPayload = "new state".toByteArray()
        val oldChunks = ChunkedMessage.chunk(roomId = 1, version = 1, payload = oldPayload)
        val newChunks = ChunkedMessage.chunk(roomId = 1, version = 2, payload = newPayload)

        val reassembler = ChunkReassembler()
        // Only receive some of the old version's chunks, then the new version arrives fully.
        reassembler.receive(oldChunks.first())
        var result: ByteArray? = null
        for (chunk in newChunks) {
            result = reassembler.receive(chunk) ?: continue
        }

        assertArrayEquals(newPayload, result)
    }

    @Test
    fun `packet codec round trips state chunk header fields`() {
        val packet = StateChunkPacket(roomId = 0xABCD, version = 200, chunkIndex = 5, chunkCount = 10, data = byteArrayOf(1, 2, 3))
        val decoded = PacketCodec.decodeStateChunk(PacketCodec.encodeStateChunk(packet))

        assertEquals(packet.roomId, decoded?.roomId)
        assertEquals(packet.version, decoded?.version)
        assertEquals(packet.chunkIndex, decoded?.chunkIndex)
        assertEquals(packet.chunkCount, decoded?.chunkCount)
        assertArrayEquals(packet.data, decoded?.data)
    }

    @Test
    fun `packet codec round trips emoji packet`() {
        val packet = EmojiPacket(roomId = 0x00FF, emojiCode = 3)
        val decoded = PacketCodec.decodeEmoji(PacketCodec.encodeEmoji(packet))

        assertEquals(packet, decoded)
    }

    @Test
    fun `decode rejects wrong packet type`() {
        val emojiBytes = PacketCodec.encodeEmoji(EmojiPacket(roomId = 1, emojiCode = 1))
        assertNull(PacketCodec.decodeStateChunk(emojiBytes))
    }
}
