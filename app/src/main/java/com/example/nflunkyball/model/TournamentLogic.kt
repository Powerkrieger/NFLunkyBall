package com.example.nflunkyball.model

import java.util.UUID
import kotlin.math.pow

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
 * A client-side-only preview of how this tournament's own matches would move each *player's*
 * Elo, keyed by player name, starting everyone fresh at [ELO_STARTING_RATING] — never touches
 * the server's persisted rating, just produces a delta the caller can add on top of it (see
 * HistoryScreen's Leaderboard tab). Recomputed from this tournament's current match state, so it
 * naturally goes stale/refreshes as the organizer edits results.
 *
 * Squads use the backend's rule (`_elo_steps` in `app/stats.py`): a side's rating is the mean
 * of its members', the expected score comes from the two means, and every member receives the
 * full delta. For singles that is exactly the classic two-player formula.
 */
fun Tournament.provisionalPlayerStandings(): Map<String, ProvisionalStanding> {
    val players = teams.flatMap { it.memberNames }.distinct()
    val ratings = players.associateWith { ELO_STARTING_RATING }.toMutableMap()
    val wins = players.associateWith { 0 }.toMutableMap()
    val losses = players.associateWith { 0 }.toMutableMap()
    val membersByTeamId = teams.associate { it.id to it.memberNames }

    val allMatches = groups.flatMap { it.matches } + bracketMatches
    for (match in allMatches) {
        val result = match.result ?: continue
        val sideA = membersByTeamId[match.teamAId] ?: continue
        val sideB = membersByTeamId[match.teamBId] ?: continue
        val ratingA = sideA.map { ratings[it] ?: ELO_STARTING_RATING }.average()
        val ratingB = sideB.map { ratings[it] ?: ELO_STARTING_RATING }.average()
        val expectedA = 1.0 / (1.0 + 10.0.pow((ratingB - ratingA) / 400.0))
        val winnerIsA = result.winnerId == match.teamAId
        val delta = ELO_K_FACTOR * ((if (winnerIsA) 1.0 else 0.0) - expectedA)
        sideA.forEach { ratings[it] = (ratings[it] ?: ELO_STARTING_RATING) + delta }
        sideB.forEach { ratings[it] = (ratings[it] ?: ELO_STARTING_RATING) - delta }

        (if (winnerIsA) sideA else sideB).forEach { wins[it] = (wins[it] ?: 0) + 1 }
        (if (winnerIsA) sideB else sideA).forEach { losses[it] = (losses[it] ?: 0) + 1 }
    }

    return players.associateWith { player ->
        ProvisionalStanding(
            winDelta = wins[player] ?: 0,
            lossDelta = losses[player] ?: 0,
            eloDelta = (ratings[player] ?: ELO_STARTING_RATING) - ELO_STARTING_RATING
        )
    }
}

/**
 * [provisionalPlayerStandings] rolled up per team (keyed by team id) for the "This tournament"
 * leaderboard. Wins/losses are the squad's; its Elo delta is its members' mean delta — which,
 * since every member receives the same delta per match, is each member's delta for a squad
 * whose members only ever played together.
 */
fun Tournament.provisionalStandings(): Map<String, ProvisionalStanding> {
    val byPlayer = provisionalPlayerStandings()
    val allMatches = groups.flatMap { it.matches } + bracketMatches
    return teams.associate { team ->
        val played = allMatches.filter { it.result != null && (it.teamAId == team.id || it.teamBId == team.id) }
        val members = team.memberNames.mapNotNull { byPlayer[it] }
        team.id to ProvisionalStanding(
            winDelta = played.count { it.result?.winnerId == team.id },
            lossDelta = played.count { it.result?.winnerId != team.id },
            eloDelta = if (members.isEmpty()) 0.0 else members.map { it.eloDelta }.average()
        )
    }
}

/**
 * [provisionalStandings] sorted the way a "how's this tournament going" leaderboard should read:
 * most wins first, ties broken by Elo gained/lost this tournament (not the persisted, global
 * Elo — see HistoryScreen's "This tournament" vs "Global" leaderboard tabs).
 */
fun Tournament.provisionalLeaderboard(): List<Pair<String, ProvisionalStanding>> =
    provisionalStandings().toList().sortedWith(
        compareByDescending<Pair<String, ProvisionalStanding>> { it.second.winDelta }
            .thenByDescending { it.second.eloDelta }
    )
