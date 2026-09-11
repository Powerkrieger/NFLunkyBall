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

/** Same starting rating/K-factor as the backend's persisted Elo (`app/stats.py`'s
 *  `_elo_ratings`) — keep the two in sync if either changes. */
const val ELO_STARTING_RATING = 1000.0
const val ELO_K_FACTOR = 32.0

data class ProvisionalStanding(val winDelta: Int, val lossDelta: Int, val eloDelta: Double)

/**
 * A client-side-only preview of how this tournament's own matches would move each team's Elo,
 * starting everyone fresh at [ELO_STARTING_RATING] — never touches the server's persisted rating,
 * just produces a delta the caller can add on top of it (see HistoryScreen's Leaderboard tab).
 * Recomputed from this tournament's current match state, so it naturally goes stale/refreshes as
 * the organizer edits results.
 */
fun Tournament.provisionalStandings(): Map<String, ProvisionalStanding> {
    val ratings = teams.associate { it.id to ELO_STARTING_RATING }.toMutableMap()
    val wins = teams.associate { it.id to 0 }.toMutableMap()
    val losses = teams.associate { it.id to 0 }.toMutableMap()

    val allMatches = groups.flatMap { it.matches } + bracketMatches
    for (match in allMatches) {
        val result = match.result ?: continue
        val ratingA = ratings[match.teamAId] ?: ELO_STARTING_RATING
        val ratingB = ratings[match.teamBId] ?: ELO_STARTING_RATING
        val expectedA = 1.0 / (1.0 + Math.pow(10.0, (ratingB - ratingA) / 400.0))
        val actualA = if (result.winnerId == match.teamAId) 1.0 else 0.0
        val delta = ELO_K_FACTOR * (actualA - expectedA)
        ratings[match.teamAId] = ratingA + delta
        ratings[match.teamBId] = ratingB - delta

        val loserId = if (result.winnerId == match.teamAId) match.teamBId else match.teamAId
        wins[result.winnerId] = (wins[result.winnerId] ?: 0) + 1
        losses[loserId] = (losses[loserId] ?: 0) + 1
    }

    return teams.associate { team ->
        team.id to ProvisionalStanding(
            winDelta = wins[team.id] ?: 0,
            lossDelta = losses[team.id] ?: 0,
            eloDelta = (ratings[team.id] ?: ELO_STARTING_RATING) - ELO_STARTING_RATING
        )
    }
}
