package com.example.nflunkyball.model

import java.util.UUID
import kotlin.math.pow

/** Every team in the group plays every other team once per round ([Group.rounds]); the
 *  home/away order flips between rounds. */
fun Group.generateRoundRobinMatches(): List<Match> = pairingsFor(teamIds)

/** The matches [newTeamId] adds to this group: one against every team already in it, per round. */
fun Group.pairingsFor(newTeamId: String): List<Match> =
    (1..rounds).flatMap { round ->
        teamIds.map { existing ->
            val (a, b) = if (round % 2 == 1) existing to newTeamId else newTeamId to existing
            Match(id = UUID.randomUUID().toString(), teamAId = a, teamBId = b)
        }
    }

private fun Group.pairingsFor(ids: List<String>): List<Match> =
    (1..rounds).flatMap { round ->
        ids.indices.flatMap { i ->
            (i + 1 until ids.size).map { j ->
                val (a, b) = if (round % 2 == 1) ids[i] to ids[j] else ids[j] to ids[i]
                Match(id = UUID.randomUUID().toString(), teamAId = a, teamBId = b)
            }
        }
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

/**
 * A starting point for the organizer's final placement at finish time (see
 * [TournamentFinishInfo.finalStandings]): every team, best first — anyone who reached the
 * bracket above anyone who didn't, then by bracket record (wins desc, losses asc), then by
 * knockout-group record, then by group-stage standing and record, then team order. The
 * organizer reorders the rest; the app doesn't try to read placement matches by name.
 */
fun Tournament.suggestedFinalStandings(): List<String> {
    fun record(matches: List<Match>, teamId: String): Pair<Int, Int> {
        val mine = matches.filter { it.result != null && (it.teamAId == teamId || it.teamBId == teamId) }
        val wins = mine.count { it.result?.winnerId == teamId }
        return wins to (mine.size - wins)
    }
    val bracket = bracketMatches
    val knockoutGroups = groups.filter { it.knockoutStage }.flatMap { it.matches }
    val groupRank = groups.filterNot { it.knockoutStage }
        .flatMap { g -> g.standings().mapIndexed { i, s -> s.teamId to i } }
        .toMap()
    val stage = groups.filterNot { it.knockoutStage }.flatMap { it.matches }
    return teams.map { it.id }.sortedWith(
        compareByDescending<String> { record(bracket, it) != 0 to 0 }
            .thenByDescending { record(bracket, it).first }
            .thenBy { record(bracket, it).second }
            .thenByDescending { record(knockoutGroups, it).first }
            .thenBy { record(knockoutGroups, it).second }
            .thenBy { groupRank[it] ?: Int.MAX_VALUE }
            .thenByDescending { record(stage, it).first }
            .thenBy { record(stage, it).second }
    )
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
