package com.example.nflunkyball.model

import kotlinx.serialization.Serializable

/** winnerId = 0 seconds by definition; loserSeconds = time the losing team took to finish their drink. */
@Serializable
data class MatchResult(
    val winnerId: String,
    val loserSeconds: Int
)

@Serializable
data class Match(
    val id: String,
    val teamAId: String,
    val teamBId: String,
    val result: MatchResult? = null,
    val roundLabel: String? = null
)
