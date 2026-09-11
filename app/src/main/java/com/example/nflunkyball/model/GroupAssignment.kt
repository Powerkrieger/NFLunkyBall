package com.example.nflunkyball.model

import kotlin.random.Random

data class PlayerSeed(val teamId: String, val elo: Double)

/**
 * Tiered snake draft: sort players by Elo desc, chunk into tiers sized to the number of groups,
 * shuffle within each tier, then snake-draft one player per group per tier (1..N, N..1, 1..N, ...).
 * Keeps groups roughly balanced in average skill while still feeling random rather than rigidly
 * seeded — used by the organizer's "Auto-assign to groups" action in SetupScreen.
 */
fun assignGroupsBySeeding(
    players: List<PlayerSeed>,
    groupNames: List<String>,
    random: Random = Random.Default
): Map<String, List<String>> {
    require(groupNames.isNotEmpty()) { "Need at least one group to assign into" }

    val groups = groupNames.associateWith { mutableListOf<String>() }
    val tiers = players.sortedByDescending { it.elo }.chunked(groupNames.size)
    tiers.forEachIndexed { tierIndex, tier ->
        val order = if (tierIndex % 2 == 0) groupNames else groupNames.reversed()
        tier.shuffled(random).forEachIndexed { i, seed ->
            groups.getValue(order[i]).add(seed.teamId)
        }
    }
    return groups
}

/**
 * Fully random assignment, Elo ignored entirely: shuffle every team, then deal them out evenly
 * across the groups round-robin — used by the organizer's "Random groups" action in SetupScreen,
 * for when they'd rather not have skill balancing at all.
 */
fun assignGroupsRandomly(
    teamIds: List<String>,
    groupNames: List<String>,
    random: Random = Random.Default
): Map<String, List<String>> {
    require(groupNames.isNotEmpty()) { "Need at least one group to assign into" }

    val groups = groupNames.associateWith { mutableListOf<String>() }
    teamIds.shuffled(random).forEachIndexed { i, teamId ->
        groups.getValue(groupNames[i % groupNames.size]).add(teamId)
    }
    return groups
}
