package com.example.nflunkyball.model

import kotlinx.serialization.Serializable

/**
 * loserId = 0 by definition; winnerScore = seconds/points credited to the winner,
 * taken from the loser's remaining beer. A forfeit is recorded as a flat 300.
 */
@Serializable
data class MatchResult(
    val winnerId: String,
    val winnerScore: Int
)

@Serializable
data class Match(
    val id: String,
    val teamAId: String,
    val teamBId: String,
    val result: MatchResult? = null,
    val roundLabel: String? = null
)
