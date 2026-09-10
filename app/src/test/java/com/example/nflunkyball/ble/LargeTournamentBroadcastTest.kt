package com.example.nflunkyball.ble

import com.example.nflunkyball.model.Group
import com.example.nflunkyball.model.Team
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.model.TournamentPhase
import com.example.nflunkyball.model.generateRoundRobinMatches
import java.util.UUID
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue

/** Regression test for a real crash report: hosting a tournament with several groups of
 *  returning players threw out of [ChunkedMessage.chunk] on the legacy stream (its 18-byte
 *  chunks ran past [BleConstants.MAX_CHUNK_COUNT]) and took the whole app down, because that
 *  exception was never caught. The fix is gzip-compressing the wire payload before chunking
 *  (see [TournamentBroadcaster.broadcastCycle]) — this proves that compression is actually
 *  enough to bring a realistically large tournament back under the chunk-count ceiling. */
class LargeTournamentBroadcastTest {

    private val json = Json

    private fun fourGroupTournament(): Tournament {
        val teams = (1..20).map { Team(id = UUID.randomUUID().toString(), name = "Player $it") }
        val groups = teams.chunked(5).mapIndexed { index, groupTeams ->
            val bare = Group(
                id = UUID.randomUUID().toString(),
                name = "Group ${'A' + index}",
                teamIds = groupTeams.map { it.id }
            )
            bare.copy(matches = bare.generateRoundRobinMatches())
        }
        return Tournament(
            id = UUID.randomUUID().toString(),
            name = "BLE Test Cup",
            teams = teams,
            groups = groups,
            phase = TournamentPhase.GROUP_STAGE
        )
    }

    @Test
    fun `raw JSON for a 4-group tournament overflows the legacy chunk budget (documents the original crash)`() {
        val bytes = json.encodeToString(Tournament.serializer(), fourGroupTournament()).encodeToByteArray()

        assertThrows(IllegalArgumentException::class.java) {
            ChunkedMessage.chunk(roomId = 1, version = 1, payload = bytes, maxChunkPayloadBytes = BleConstants.MAX_CHUNK_PAYLOAD_BYTES)
        }
    }

    @Test
    fun `gzip-compressed JSON for the same tournament fits comfortably within the legacy chunk budget`() {
        val rawBytes = json.encodeToString(Tournament.serializer(), fourGroupTournament()).encodeToByteArray()
        val compressed = GzipCodec.compress(rawBytes)

        val chunks = ChunkedMessage.chunk(roomId = 1, version = 1, payload = compressed, maxChunkPayloadBytes = BleConstants.MAX_CHUNK_PAYLOAD_BYTES)

        assertTrue("expected fewer than ${BleConstants.MAX_CHUNK_COUNT} chunks, got ${chunks.size}", chunks.size < BleConstants.MAX_CHUNK_COUNT)

        val reassembler = ChunkReassembler()
        var result: ByteArray? = null
        for (chunk in chunks) result = reassembler.receive(chunk) ?: continue
        assertTrue(result != null && GzipCodec.decompress(result).contentEquals(rawBytes))
    }
}
