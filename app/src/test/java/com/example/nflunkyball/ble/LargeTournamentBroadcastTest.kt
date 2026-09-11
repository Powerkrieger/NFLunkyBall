package com.example.nflunkyball.ble

import com.example.nflunkyball.model.Group
import com.example.nflunkyball.model.MatchResult
import com.example.nflunkyball.model.PlayerResult
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

    /** Same shape, but every team is a two-player squad and every match already has a result
     *  with per-loser counters — the biggest payload a squad tournament realistically produces. */
    private fun fourGroupSquadTournament(): Tournament {
        var player = 0
        val teams = (1..20).map {
            val members = listOf("Player ${++player}", "Player ${++player}")
            Team(id = UUID.randomUUID().toString(), name = Team.autoName(members), members = members)
        }
        val membersById = teams.associate { it.id to it.memberNames }
        val groups = teams.chunked(5).mapIndexed { index, groupTeams ->
            val bare = Group(
                id = UUID.randomUUID().toString(),
                name = "Group ${'A' + index}",
                teamIds = groupTeams.map { it.id }
            )
            bare.copy(
                matches = bare.generateRoundRobinMatches().map { match ->
                    val losers = membersById.getValue(match.teamBId).map { PlayerResult(it, 45, forfeitedDrinks = 1) }
                    match.copy(result = MatchResult.ofLosers(match.teamAId, losers))
                }
            )
        }
        return Tournament(
            id = UUID.randomUUID().toString(),
            name = "BLE Squad Cup",
            teams = teams,
            groups = groups,
            phase = TournamentPhase.GROUP_STAGE,
            squadSize = 2
        )
    }

    @Test
    fun `a fully-scored 4-group squad tournament still fits within the legacy chunk budget once compressed`() {
        val rawBytes = json.encodeToString(Tournament.serializer(), fourGroupSquadTournament()).encodeToByteArray()
        val compressed = GzipCodec.compress(rawBytes)

        val chunks = ChunkedMessage.chunk(roomId = 1, version = 1, payload = compressed, maxChunkPayloadBytes = BleConstants.MAX_CHUNK_PAYLOAD_BYTES)

        assertTrue(
            "expected fewer than ${BleConstants.MAX_CHUNK_COUNT} chunks, got ${chunks.size} (${rawBytes.size} raw / ${compressed.size} gz bytes)",
            chunks.size < BleConstants.MAX_CHUNK_COUNT
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
