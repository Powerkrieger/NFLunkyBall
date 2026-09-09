package com.example.nflunkyball.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TournamentLogicTest {

    private fun team(id: String) = Team(id, id)

    @Test
    fun `round robin generates every pair exactly once`() {
        val group = Group(id = "g1", name = "Group A", teamIds = listOf("A", "B", "C", "D"))
        val matches = group.generateRoundRobinMatches()

        assertEquals(6, matches.size) // C(4,2)
        val pairs = matches.map { setOf(it.teamAId, it.teamBId) }.toSet()
        assertEquals(6, pairs.size) // all unique
        assertEquals(
            setOf(
                setOf("A", "B"), setOf("A", "C"), setOf("A", "D"),
                setOf("B", "C"), setOf("B", "D"), setOf("C", "D")
            ),
            pairs
        )
    }

    @Test
    fun `standings sort by wins descending`() {
        val group = Group(
            id = "g1",
            name = "Group A",
            teamIds = listOf("A", "B", "C"),
            matches = listOf(
                Match("m1", "A", "B", MatchResult(winnerId = "A", winnerScore = 12)),
                Match("m2", "A", "C", MatchResult(winnerId = "A", winnerScore = 8)),
                Match("m3", "B", "C", MatchResult(winnerId = "B", winnerScore = 20))
            )
        )

        val standings = group.standings()

        assertEquals(listOf("A", "B", "C"), standings.map { it.teamId })
        assertEquals(2, standings[0].wins)
        assertEquals(1, standings[1].wins)
        assertEquals(0, standings[2].wins)
    }

    @Test
    fun `tied teams broken by head-to-head result`() {
        // A and B both have 1 win, 1 loss overall, but A beat B directly.
        val group = Group(
            id = "g1",
            name = "Group A",
            teamIds = listOf("B", "A"), // deliberately out of "expected" order
            matches = listOf(
                Match("m1", "A", "B", MatchResult(winnerId = "A", winnerScore = 5)),
                Match("m2", "A", "C", MatchResult(winnerId = "C", winnerScore = 5)),
                Match("m3", "B", "C", MatchResult(winnerId = "B", winnerScore = 5))
            )
        )

        val standings = group.standings()

        // A and C both have 1-1, B has 1-1 too actually all tied at 1 win 1 loss (3-team full tie);
        // only A vs B head-to-head is directly resolvable, A must precede B.
        val aIndex = standings.indexOfFirst { it.teamId == "A" }
        val bIndex = standings.indexOfFirst { it.teamId == "B" }
        assert(aIndex < bIndex) { "A beat B head-to-head, so A should rank above B" }
    }

    @Test
    fun `standings with no results yet has zero wins and losses for everyone`() {
        val group = Group(id = "g1", name = "Group A", teamIds = listOf("A", "B"))
        val standings = group.standings()
        assertEquals(2, standings.size)
        standings.forEach {
            assertEquals(0, it.wins)
            assertEquals(0, it.losses)
        }
    }
}
