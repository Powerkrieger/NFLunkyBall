package com.example.nflunkyball.server

import com.example.nflunkyball.model.Group
import com.example.nflunkyball.model.Match
import com.example.nflunkyball.model.MatchResult
import com.example.nflunkyball.model.PlayerResult
import com.example.nflunkyball.model.Team
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.model.TournamentFinishInfo
import com.example.nflunkyball.model.TournamentPhase
import com.example.nflunkyball.model.MatchDrinks
import com.example.nflunkyball.model.PenaltyDrinks
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
    fun `merges per-team drinks into the matching match's result only`() {
        val payload = tournament.toUploadPayload(mapOf("m1" to MatchDrinks(teamA = "IPA", teamB = "Cider")))

        assertEquals("IPA", payload.groups[0].matches[0].result?.drinkA)
        assertEquals("Cider", payload.groups[0].matches[0].result?.drinkB)
        assertNull(payload.bracketMatches[0].result?.drinkA)
        assertNull(payload.bracketMatches[0].result?.drinkB)
    }

    @Test
    fun `a match with no result gets no drinks even if some are recorded for its id`() {
        val payload = tournament.toUploadPayload(mapOf("m2" to MatchDrinks(teamA = "Cola")))

        assertNull(payload.groups[0].matches[1].result)
    }

    @Test
    fun `no drinks recorded leaves every result's drinks null`() {
        val payload = tournament.toUploadPayload(emptyMap())

        assertNull(payload.groups[0].matches[0].result?.drinkA)
        assertNull(payload.groups[0].matches[0].result?.drinkB)
        assertNull(payload.bracketMatches[0].result?.drinkA)
        assertNull(payload.bracketMatches[0].result?.drinkB)
    }

    @Test
    fun `preserves every other field unchanged`() {
        val payload = tournament.toUploadPayload(mapOf("m3" to MatchDrinks(teamA = "Water")))

        assertEquals(tournament.id, payload.id)
        assertEquals(tournament.name, payload.name)
        assertEquals(tournament.teams, payload.teams)
        assertEquals(tournament.phase, payload.phase)
        assertEquals("b", payload.bracketMatches[0].result?.winnerId)
        assertEquals(300, payload.bracketMatches[0].result?.winnerScore)
    }

    @Test
    fun `serializes to JSON shaped like the backend's TournamentIn schema`() {
        val payload = tournament.toUploadPayload(mapOf("m1" to MatchDrinks(teamA = "IPA", teamB = "Cider")))
        val json = Json.encodeToString(UploadTournament.serializer(), payload)

        assertEquals(true, json.contains("\"drinkA\":\"IPA\""))
        assertEquals(true, json.contains("\"drinkB\":\"Cider\""))
    }

    @Test
    fun `a singles result is uploaded with one loser entry derived from winnerScore`() {
        val payload = tournament.toUploadPayload(mapOf("m3" to MatchDrinks(teamA = "IPA", teamB = "Cider")))

        // m3: Beta ("b") beat Alpha ("a") with a 300 — Alpha is the lone loser, one refused drink.
        val result = payload.bracketMatches[0].result!!
        assertEquals(listOf(UploadPlayerResult("Alpha", 300, forfeitedDrinks = 1, drink = "IPA")), result.losers)
        assertEquals(mapOf("Beta" to "Cider"), result.winnerDrinks)
        assertEquals(1, payload.squadSize)
    }

    @Test
    fun `a squad result uploads every losing player's own counter and per-player drinks`() {
        val squads = Tournament(
            id = "t2",
            name = "Team Cup",
            squadSize = 2,
            teams = listOf(Team("x", "Anna & Ben", listOf("Anna", "Ben")), Team("y", "Cid & Dee", listOf("Cid", "Dee"))),
            bracketMatches = listOf(
                Match(
                    id = "f", teamAId = "x", teamBId = "y",
                    result = MatchResult.ofLosers("x", listOf(PlayerResult("Cid", 40), PlayerResult("Dee", 305, 1)))
                )
            ),
            phase = TournamentPhase.FINISHED
        )
        val drinks = MatchDrinks(byPlayer = mapOf("Anna" to "Helles", "Cid" to "Radler"))

        val result = squads.toUploadPayload(mapOf("f" to drinks)).bracketMatches[0].result!!

        assertEquals(345, result.winnerScore)
        assertEquals(
            listOf(UploadPlayerResult("Cid", 40, 0, "Radler"), UploadPlayerResult("Dee", 305, 1, null)),
            result.losers
        )
        assertEquals(mapOf("Anna" to "Helles"), result.winnerDrinks)  // Ben recorded nothing
    }

    @Test
    fun `no finish info leaves date, location, referees and comment null`() {
        val payload = tournament.toUploadPayload(emptyMap())

        assertNull(payload.date)
        assertNull(payload.location)
        assertNull(payload.referees)
        assertNull(payload.comment)
    }

    @Test
    fun `finish info is converted to an ISO-8601 date and blank fields become null`() {
        val finishInfo = TournamentFinishInfo(
            dateMillis = 1684281600000L, // 2023-05-17T00:00:00Z
            location = "  Joost's garage  ",
            referees = "",
            comment = "  "
        )

        val payload = tournament.toUploadPayload(emptyMap(), finishInfo)

        assertEquals("2023-05-17T00:00:00Z", payload.date)
        assertEquals("Joost's garage", payload.location)
        assertNull(payload.referees)
        assertNull(payload.comment)
    }

    @Test
    fun `carries play order, group format, final standings and penalty drinks`() {
        val squads = Tournament(
            id = "t2",
            name = "Squad Cup",
            teams = listOf(Team("x", "X", listOf("Anna", "Ben")), Team("y", "Y", listOf("Cid", "Dee"))),
            groups = listOf(
                Group(
                    id = "g", name = "G", teamIds = listOf("x", "y"), rounds = 2, knockoutStage = true,
                    matches = listOf(
                        Match(
                            id = "m1", teamAId = "x", teamBId = "y", sequence = 2,
                            result = MatchResult.ofLosers("x", listOf(PlayerResult("Cid", 40), PlayerResult("Dee", 305, 1)))
                        )
                    )
                )
            ),
            phase = TournamentPhase.FINISHED,
            squadSize = 2
        )
        val drinks = MatchDrinks(
            byPlayer = mapOf("Cid" to "Radler", "Anna" to "Helles"),
            penaltiesByPlayer = mapOf("Cid" to PenaltyDrinks(2, "Schnaps"), "Anna" to PenaltyDrinks(1))
        )
        val info = TournamentFinishInfo(0L, "", "", "", finalStandings = listOf("x", "y"))

        val payload = squads.toUploadPayload(mapOf("m1" to drinks), info)

        assertEquals(listOf("x", "y"), payload.finalStandings)
        assertEquals(2, payload.groups[0].rounds)
        assertEquals(true, payload.groups[0].knockoutStage)
        val match = payload.groups[0].matches[0]
        assertEquals(2, match.sequence)
        val result = match.result!!
        assertEquals(listOf(
            UploadPlayerResult("Cid", 40, 0, "Radler", penaltyDrinks = 2, penaltyDrink = "Schnaps"),
            UploadPlayerResult("Dee", 305, 1, null)
        ), result.losers)
        assertEquals(mapOf("Anna" to "Helles"), result.winnerDrinks)
        assertEquals(mapOf("Anna" to UploadPenalty(1, null)), result.winnerPenalties)
    }
}
