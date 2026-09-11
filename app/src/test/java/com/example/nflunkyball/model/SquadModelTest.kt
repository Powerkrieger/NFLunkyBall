package com.example.nflunkyball.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** The squad additions must leave every pre-squad tournament decodable and meaning the same. */
class SquadModelTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a pre-squad tournament JSON still decodes, as singles`() {
        val legacy = """
            {"id":"t1","name":"Cup","teams":[{"id":"a","name":"Alpha"},{"id":"b","name":"Beta"}],
             "groups":[{"id":"g","name":"G","teamIds":["a","b"],
               "matches":[{"id":"m1","teamAId":"a","teamBId":"b","result":{"winnerId":"a","winnerScore":305}}]}],
             "bracketMatches":[],"phase":"FINISHED"}
        """.trimIndent()

        val tournament = json.decodeFromString(Tournament.serializer(), legacy)

        assertEquals(1, tournament.squadSize)
        assertEquals(listOf("Alpha"), tournament.teams[0].memberNames)
        val result = tournament.groups[0].matches[0].result!!
        assertEquals(emptyList<PlayerResult>(), result.losers)
        assertEquals(listOf(PlayerResult("Beta", 305, forfeitedDrinks = 1)), result.loserResults("Beta"))
    }

    @Test
    fun `defaults are omitted on encode so singles broadcasts stay byte-compatible`() {
        val tournament = Tournament(
            id = "t1",
            name = "Cup",
            teams = listOf(Team("a", "Alpha")),
            groups = listOf(Group("g", "G", listOf("a"), listOf(Match("m1", "a", "a", MatchResult("a", 12)))))
        )

        val encoded = Json.encodeToString(Tournament.serializer(), tournament)

        assertEquals(false, encoded.contains("members"))
        assertEquals(false, encoded.contains("losers"))
        assertEquals(false, encoded.contains("squadSize"))
    }

    @Test
    fun `squad result sums the losers' counters into winnerScore`() {
        val result = MatchResult.ofLosers(
            winnerId = "a",
            losers = listOf(PlayerResult("Cid", 45), PlayerResult("Dee", 320, forfeitedDrinks = 1))
        )

        assertEquals(365, result.winnerScore)
        assertEquals(result.losers, result.loserResults("ignored"))
    }

    @Test
    fun `provisional squad Elo uses side means and gives every member the full delta`() {
        // Mirrors the backend's test_squad_elo_uses_side_means_and_gives_every_member_the_full_delta.
        val tournament = Tournament(
            id = "t", name = "Squad Cup", squadSize = 2,
            teams = listOf(Team("x", "Anna & Ben", listOf("Anna", "Ben")), Team("y", "Cid & Dee", listOf("Cid", "Dee"))),
            groups = listOf(
                Group(
                    "g", "G", listOf("x", "y"),
                    listOf(
                        Match("m1", "x", "y", MatchResult.ofLosers("x", listOf(PlayerResult("Cid", 40), PlayerResult("Dee", 305, 1)))),
                        Match("m2", "x", "y", MatchResult.ofLosers("y", listOf(PlayerResult("Anna", 20), PlayerResult("Ben", 30))))
                    )
                )
            )
        )

        val byPlayer = tournament.provisionalPlayerStandings()
        val byTeam = tournament.provisionalStandings()

        val expectedX = 1.0 / (1.0 + Math.pow(10.0, (984.0 - 1016.0) / 400.0))
        val delta2 = ELO_K_FACTOR * (0.0 - expectedX)
        assertEquals(16.0 + delta2, byPlayer.getValue("Anna").eloDelta, 1e-9)
        assertEquals(byPlayer.getValue("Anna").eloDelta, byPlayer.getValue("Ben").eloDelta, 1e-9)
        assertEquals(-16.0 - delta2, byPlayer.getValue("Cid").eloDelta, 1e-9)
        assertEquals(1, byPlayer.getValue("Dee").winDelta)
        assertEquals(1, byPlayer.getValue("Dee").lossDelta)
        assertEquals(byPlayer.getValue("Anna").eloDelta, byTeam.getValue("x").eloDelta, 1e-9)
        assertEquals(1, byTeam.getValue("x").winDelta)
    }

    @Test
    fun `squad auto name joins members in order`() {
        assertEquals("Anna & Ben", Team.autoName(listOf("Anna", "Ben")))
        assertEquals(listOf("Anna", "Ben"), Team("x", "Anna & Ben", listOf("Anna", "Ben")).memberNames)
    }
}
