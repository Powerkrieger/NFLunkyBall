package com.example.nflunkyball.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The format freedoms added for uneven tournaments: multi-round groups, groups played during
 *  the bracket, play-order stamping and the suggested final placement. */
class TournamentFormatTest {

    private val teams = listOf(Team("a", "Anna"), Team("b", "Ben"), Team("c", "Cara"), Team("d", "Dan"))

    private fun base() = newTournament(
        name = "T",
        teams = teams,
        groupAssignments = mapOf("A" to listOf("a", "b", "c"), "B" to listOf("d"))
    )

    private fun win(winnerId: String) = MatchResult(winnerId, 10)

    @Test
    fun `a double round robin plays every pairing twice with sides swapped`() {
        val t0 = base()
        val t = t0.withGroupRounds(t0.groups[0].id, 2)
        val matches = t.groups[0].matches
        assertEquals(6, matches.size)
        assertEquals(2, t.groups[0].rounds)
        val pairs = matches.map { it.teamAId to it.teamBId }
        assertTrue(("a" to "b") in pairs && ("b" to "a") in pairs)
    }

    @Test
    fun `rounds are locked once the group has a result`() {
        val t = base()
        val matchId = t.groups[0].matches[0].id
        val played = t.withGroupMatchResult(t.groups[0].id, matchId, win("a"))
        assertSame(played, played.withGroupRounds(t.groups[0].id, 2))
        assertEquals(3, played.groups[0].matches.size)
    }

    @Test
    fun `an existing team added to a group plays everyone already there, once per round`() {
        val t0 = base()
        val t = t0.withGroupRounds(t0.groups[0].id, 2).withTeamAddedToGroup(t0.groups[0].id, "d")
        val group = t.groups[0]
        assertEquals(listOf("a", "b", "c", "d"), group.teamIds)
        assertEquals(12, group.matches.size)
        assertEquals(4, t.teams.size) // no new team created
        assertSame(t, t.withTeamAddedToGroup(group.id, "d")) // already in
        assertSame(t, t.withTeamAddedToGroup(group.id, "nobody"))
    }

    @Test
    fun `a team can leave a group only while it has no result there`() {
        val t0 = base()
        val t = t0.withTeamAddedToGroup(t0.groups[1].id, "a")
        val groupB = t.groups[1]
        assertEquals(listOf("d", "a"), groupB.teamIds)
        val left = t.withTeamRemovedFromGroup(groupB.id, "a")
        assertEquals(listOf("d"), left.groups[1].teamIds)
        assertTrue(left.groups[1].matches.isEmpty())
        assertTrue(left.teams.any { it.id == "a" }) // still in the tournament

        val played = t.withGroupMatchResult(groupB.id, groupB.matches[0].id, win("a"))
        assertFalse(played.canRemoveTeamFromGroup(groupB.id, "a"))
        assertSame(played, played.withTeamRemovedFromGroup(groupB.id, "a"))
    }

    @Test
    fun `a group added during the bracket is a knockout-stage group`() {
        val t = base().withPhase(TournamentPhase.BRACKET).withGroupAdded("Consolation", knockoutStage = true)
        assertTrue(t.groups.last().knockoutStage)
        assertFalse(t.groups.first().knockoutStage)
    }

    @Test
    fun `recording results stamps play order across groups and bracket, clearing drops it`() {
        var t = base().withBracketMatchAdded("a", "d", "Final")
        val groupId = t.groups[0].id
        val (m1, m2) = t.groups[0].matches
        val final = t.bracketMatches[0]

        t = t.withGroupMatchResult(groupId, m1.id, win("a"))
        t = t.withBracketMatchResult(final.id, win("d"))
        t = t.withGroupMatchResult(groupId, m2.id, win("b"))
        assertEquals(1, t.groups[0].matches[0].sequence)
        assertEquals(3, t.groups[0].matches[1].sequence)
        assertEquals(2, t.bracketMatches[0].sequence)

        // Editing keeps the stamp; clearing drops it; re-recording gets a fresh one.
        t = t.withGroupMatchResult(groupId, m1.id, win("b"))
        assertEquals(1, t.groups[0].matches[0].sequence)
        t = t.withGroupMatchResult(groupId, m1.id, null)
        assertNull(t.groups[0].matches[0].sequence)
        t = t.withGroupMatchResult(groupId, m1.id, win("a"))
        assertEquals(4, t.groups[0].matches[0].sequence)
    }

    @Test
    fun `suggested standings rank by bracket record, then knockout group, then group stage`() {
        var t = base()
        val groupId = t.groups[0].id
        val (ab, ac, bc) = t.groups[0].matches // (a,b) (a,c) (b,c)
        t = t.withGroupMatchResult(groupId, ab.id, win("a"))
            .withGroupMatchResult(groupId, ac.id, win("a"))
            .withGroupMatchResult(groupId, bc.id, win("b"))
        // Bracket: d beats a in the final; c beats b in a consolation group.
        t = t.withPhase(TournamentPhase.BRACKET)
            .withBracketMatchAdded("a", "d", "Final")
            .withGroupAdded("C", knockoutStage = true)
        val consolation = t.groups.last().id
        t = t.withTeamAddedToGroup(consolation, "b").withTeamAddedToGroup(consolation, "c")
        t = t.withBracketMatchResult(t.bracketMatches[0].id, win("d"))
            .withGroupMatchResult(consolation, t.groups.last().matches[0].id, win("c"))

        assertEquals(listOf("d", "a", "c", "b"), t.suggestedFinalStandings())
    }
}
