package com.example.nflunkyball.server

import com.example.nflunkyball.model.Group
import com.example.nflunkyball.model.Match
import com.example.nflunkyball.model.MatchResult
import com.example.nflunkyball.model.Team
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.model.TournamentPhase
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UploadPayloadTest {

    private val tournament = Tournament(
        id = "t1",
        name = "Test Cup",
        teams = listOf(Team("a", "Alpha"), Team("b", "Beta")),
        groups = listOf(
            Group(
                id = "g1",
                name = "Group A",
                teamIds = listOf("a", "b"),
                matches = listOf(
                    Match(id = "m1", teamAId = "a", teamBId = "b", result = MatchResult("a", 12)),
                    Match(id = "m2", teamAId = "a", teamBId = "b") // unplayed, no result
                )
            )
        ),
        bracketMatches = listOf(
            Match(id = "m3", teamAId = "a", teamBId = "b", result = MatchResult("b", 300))
        ),
        phase = TournamentPhase.FINISHED
    )

    @Test
    fun `merges a drink into the matching match's result only`() {
        val payload = tournament.toUploadPayload(mapOf("m1" to "IPA"))

        assertEquals("IPA", payload.groups[0].matches[0].result?.drink)
        assertNull(payload.bracketMatches[0].result?.drink)
    }

    @Test
    fun `a match with no result gets no drink even if one is recorded for its id`() {
        val payload = tournament.toUploadPayload(mapOf("m2" to "Cola"))

        assertNull(payload.groups[0].matches[1].result)
    }

    @Test
    fun `no drinks recorded leaves every result's drink null`() {
        val payload = tournament.toUploadPayload(emptyMap())

        assertNull(payload.groups[0].matches[0].result?.drink)
        assertNull(payload.bracketMatches[0].result?.drink)
    }

    @Test
    fun `preserves every other field unchanged`() {
        val payload = tournament.toUploadPayload(mapOf("m3" to "Water"))

        assertEquals(tournament.id, payload.id)
        assertEquals(tournament.name, payload.name)
        assertEquals(tournament.teams, payload.teams)
        assertEquals(tournament.phase, payload.phase)
        assertEquals("b", payload.bracketMatches[0].result?.winnerId)
        assertEquals(300, payload.bracketMatches[0].result?.winnerScore)
    }

    @Test
    fun `serializes to JSON shaped like the backend's TournamentIn schema`() {
        val payload = tournament.toUploadPayload(mapOf("m1" to "IPA"))
        val json = Json.encodeToString(UploadTournament.serializer(), payload)

        assertEquals(true, json.contains("\"drink\":\"IPA\""))
    }
}
