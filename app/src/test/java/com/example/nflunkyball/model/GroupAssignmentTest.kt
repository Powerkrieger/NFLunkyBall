package com.example.nflunkyball.model

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupAssignmentTest {

    @Test
    fun `every player is assigned to exactly one group`() {
        val players = (1..9).map { PlayerSeed(teamId = "p$it", elo = it * 100.0) }
        val groups = assignGroupsBySeeding(players, listOf("A", "B", "C"), Random(42))

        assertEquals(setOf("A", "B", "C"), groups.keys)
        assertEquals(9, groups.values.sumOf { it.size })
        assertEquals(players.map { it.teamId }.toSet(), groups.values.flatten().toSet())
        groups.values.forEach { assertEquals(3, it.size) }
    }

    @Test
    fun `an uneven remainder tier still assigns everyone exactly once`() {
        val players = (1..7).map { PlayerSeed(teamId = "p$it", elo = it * 100.0) }
        val groups = assignGroupsBySeeding(players, listOf("A", "B", "C"), Random(1))

        assertEquals(7, groups.values.sumOf { it.size })
        assertEquals(players.map { it.teamId }.toSet(), groups.values.flatten().toSet())
    }

    @Test
    fun `random assignment places every team exactly once, evenly across groups`() {
        val teamIds = (1..9).map { "p$it" }
        val groups = assignGroupsRandomly(teamIds, listOf("A", "B", "C"), Random(7))

        assertEquals(setOf("A", "B", "C"), groups.keys)
        assertEquals(teamIds.toSet(), groups.values.flatten().toSet())
        groups.values.forEach { assertEquals(3, it.size) }
    }

    @Test
    fun `top tier player always lands in some group, never dropped, regardless of seed`() {
        val players = listOf(
            PlayerSeed("best", 2000.0),
            PlayerSeed("mid", 1000.0),
            PlayerSeed("worst", 100.0)
        )
        repeat(20) { seed ->
            val groups = assignGroupsBySeeding(players, listOf("A", "B", "C"), Random(seed.toLong()))
            assertEquals(setOf("best", "mid", "worst"), groups.values.flatten().toSet())
        }
    }
}
