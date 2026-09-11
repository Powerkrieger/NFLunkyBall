package com.example.nflunkyball.model

import kotlinx.serialization.Serializable

/** A refused drink adds this flat amount to the losing player's counter. */
const val FORFEIT_FLAT_SECONDS = 300

/**
 * One losing player's own counter. [seconds] is the rule-based total — drinking time across
 * every drink they did take, plus [FORFEIT_FLAT_SECONDS] per drink they refused — which is
 * exactly the number a singles loss has always recorded as [MatchResult.winnerScore].
 * [forfeitedDrinks] counts the refusals folded into [seconds]; it's recorded separately
 * because a multi-drink total alone can't tell 2×200s apart from 300+100s.
 */
@Serializable
data class PlayerResult(
    val player: String,
    val seconds: Int,
    val forfeitedDrinks: Int = 0
)

/**
 * loserId = 0 by definition; winnerScore = seconds/points credited to the winner,
 * taken from the loser's remaining beer. A forfeit is recorded as a flat 300.
 *
 * With squads every losing player is timed on their own counter ([losers]); [winnerScore] is
 * then the sum of those counters, kept so anything reading a single number (standings
 * displays, older viewers) still sees something sensible. [losers] is empty for a singles
 * result recorded before squads existed — [loserResults] gives the per-player view either way.
 */
@Serializable
data class MatchResult(
    val winnerId: String,
    val winnerScore: Int,
    val losers: List<PlayerResult> = emptyList()
) {
    /** Per-player losing records; for a pre-squad singles result, derived from [winnerScore]
     *  for the one losing player named by [loserName]. */
    fun loserResults(loserName: String): List<PlayerResult> =
        losers.ifEmpty {
            listOf(PlayerResult(loserName, winnerScore, forfeitedDrinks = winnerScore / FORFEIT_FLAT_SECONDS))
        }

    companion object {
        /** Builds a squad result: [winnerScore] is the sum of the losers' counters. */
        fun ofLosers(winnerId: String, losers: List<PlayerResult>): MatchResult =
            MatchResult(winnerId, losers.sumOf { it.seconds }, losers)
    }
}

@Serializable
data class Match(
    val id: String,
    val teamAId: String,
    val teamBId: String,
    val result: MatchResult? = null,
    val roundLabel: String? = null
)
