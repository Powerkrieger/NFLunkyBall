package com.example.nflunkyball.model

import java.util.UUID

/** Every team in the group plays every other team once. */
fun Group.generateRoundRobinMatches(): List<Match> {
    val matches = mutableListOf<Match>()
    for (i in teamIds.indices) {
        for (j in i + 1 until teamIds.size) {
            matches += Match(
                id = UUID.randomUUID().toString(),
                teamAId = teamIds[i],
                teamBId = teamIds[j]
            )
        }
    }
    return matches
}

data class TeamStanding(
    val teamId: String,
    val wins: Int,
    val losses: Int
)

/**
 * Sorted by wins desc. Ties between exactly two teams are broken by their head-to-head
 * result if they've played; anything beyond a pairwise tie falls back to the group's team
 * order (i.e. the organizer's own ordering) since a general N-way round-robin tiebreak isn't
 * well-defined.
 */
fun Group.standings(): List<TeamStanding> {
    val wins = teamIds.associateWith { 0 }.toMutableMap()
    val losses = teamIds.associateWith { 0 }.toMutableMap()
    for (match in matches) {
        val result = match.result ?: continue
        val loserId = if (result.winnerId == match.teamAId) match.teamBId else match.teamAId
        wins[result.winnerId] = (wins[result.winnerId] ?: 0) + 1
        losses[loserId] = (losses[loserId] ?: 0) + 1
    }

    fun headToHeadWinner(a: String, b: String): String? =
        matches.firstOrNull {
            (it.teamAId == a && it.teamBId == b) || (it.teamAId == b && it.teamBId == a)
        }?.result?.winnerId

    val standings = teamIds.map { TeamStanding(it, wins[it] ?: 0, losses[it] ?: 0) }
    return standings.sortedWith { a, b ->
        val winCompare = b.wins - a.wins
        if (winCompare != 0) return@sortedWith winCompare
        when (headToHeadWinner(a.teamId, b.teamId)) {
            a.teamId -> -1
            b.teamId -> 1
            else -> 0
        }
    }
}
