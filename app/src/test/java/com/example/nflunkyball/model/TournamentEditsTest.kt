package com.example.nflunkyball.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TournamentEditsTest {

    private val anna = Team("a", "Anna")
    private val ben = Team("b", "Ben")
    private val cara = Team("c", "Cara")

    private fun base() = newTournament(
        name = "T",
        teams = listOf(anna, ben, cara),
        groupAssignments = mapOf("Group A" to listOf("a", "b"), "Group B" to listOf("c"))
    )

    @Test
    fun `newTournament starts in group stage with round-robin schedules`() {
        val t = base()
        assertEquals(TournamentPhase.GROUP_STAGE, t.phase)
        assertEquals(listOf("Group A", "Group B"), t.groups.map { it.name })
        assertEquals(1, t.groups[0].matches.size)
        assertEquals(0, t.groups[1].matches.size)
    }

    @Test
    fun `parseMemberNames accepts commas and ampersands and drops blanks`() {
        assertEquals(listOf("Anna", "Ben"), parseMemberNames(" Anna , Ben "))
        assertEquals(listOf("Anna", "Ben"), parseMemberNames("Anna & Ben"))
        assertEquals(listOf("Anna"), parseMemberNames("Anna,,"))
        assertTrue(parseMemberNames("  ").isEmpty())
    }

    @Test
    fun `adding a team generates matches only against that group's existing teams`() {
        val t = base()
        val groupA = t.groups[0].id
        val edited = t.withTeamAdded(groupA, "Dora")
        val dora = edited.teams.single { it.name == "Dora" }

        assertEquals(4, edited.teams.size)
        assertEquals(listOf("a", "b", dora.id), edited.groups[0].teamIds)
        // 1 existing + 2 new (a-dora, b-dora); the other group is untouched.
        assertEquals(3, edited.groups[0].matches.size)
        assertEquals(setOf("a", "b"), edited.groups[0].matches.filter { it.teamBId == dora.id }.map { it.teamAId }.toSet())
        assertEquals(t.groups[1], edited.groups[1])
    }

    @Test
    fun `adding to a squad tournament auto-names the team from its members`() {
        val t = base().copy(squadSize = 2)
        val squad = t.withTeamAdded(t.groups[0].id, "Dora & Emil").teams.last()
        assertEquals("Dora & Emil", squad.name)
        assertEquals(listOf("Dora", "Emil"), squad.members)
    }

    @Test
    fun `blank names are no-ops`() {
        val t = base()
        assertSame(t, t.withTeamAdded(t.groups[0].id, " "))
        assertSame(t, t.withGroupAdded(""))
        assertSame(t, t.withTeamRenamed("a", "   "))
        assertSame(t, t.withGroupRenamed(t.groups[0].id, ""))
    }

    @Test
    fun `a team with a recorded result can't be removed`() {
        val t = base()
        val match = t.groups[0].matches.single()
        val played = t.withGroupMatchResult(t.groups[0].id, match.id, MatchResult("a", 90))

        assertTrue(t.canRemoveTeam("a"))
        assertFalse(played.canRemoveTeam("a"))
        assertFalse(played.canRemoveTeam("b"))
        assertTrue(played.canRemoveTeam("c"))
        assertSame(played, played.withTeamRemoved("a"))
    }

    @Test
    fun `removing a team drops its membership and unplayed group and bracket matches`() {
        val t = base().withBracketMatchAdded("a", "c", "Final")
        val edited = t.withTeamRemoved("a")

        assertEquals(listOf("b", "c"), edited.teams.map { it.id })
        assertEquals(listOf("b"), edited.groups[0].teamIds)
        assertTrue(edited.groups[0].matches.isEmpty())
        assertTrue(edited.bracketMatches.isEmpty())
    }

    @Test
    fun `only empty groups can be removed`() {
        val t = base().withGroupAdded("Group C")
        val groupC = t.groups.last().id
        assertFalse(t.canRemoveGroup(t.groups[0].id))
        assertTrue(t.canRemoveGroup(groupC))
        assertSame(t, t.withGroupRemoved(t.groups[0].id))
        assertEquals(2, t.withGroupRemoved(groupC).groups.size)
    }

    @Test
    fun `results can be recorded and cleared in groups and bracket`() {
        val t = base().withBracketMatchAdded("a", "c", "Final")
        val group = t.groups[0]
        val gm = group.matches.single()
        val bm = t.bracketMatches.single()

        val recorded = t.withGroupMatchResult(group.id, gm.id, MatchResult("b", 45))
            .withBracketMatchResult(bm.id, MatchResult("c", 300))
        assertEquals("b", recorded.groups[0].matches.single().result?.winnerId)
        assertEquals("c", recorded.bracketMatches.single().result?.winnerId)

        val cleared = recorded.withGroupMatchResult(group.id, gm.id, null).withBracketMatchResult(bm.id, null)
        assertNull(cleared.groups[0].matches.single().result)
        assertNull(cleared.bracketMatches.single().result)
    }
}
