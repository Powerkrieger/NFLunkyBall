package com.example.nflunkyball.model

import kotlinx.serialization.Serializable

/** Organizer-entered, free text only (no GPS), collected once when finishing a tournament and
 *  sent along with the final "upload to history" call only — same "never part of the synced
 *  Tournament model" treatment as [com.example.nflunkyball.persistence.MatchDrinkStore]. Kept
 *  locally (see [com.example.nflunkyball.persistence.FinishInfoStore]) until that upload
 *  succeeds, so a failed upload can be retried without re-entering it. */
@Serializable
data class TournamentFinishInfo(
    val dateMillis: Long,
    val location: String,
    val referees: String,
    val comment: String
)
