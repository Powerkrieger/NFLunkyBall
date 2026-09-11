package com.example.nflunkyball.model

/** Organizer-entered, free text only (no GPS), collected once when finishing a tournament and
 *  sent along with the final "upload to history" call only — same "never part of the synced
 *  Tournament model" treatment as [com.example.nflunkyball.persistence.MatchDrinkStore]. */
data class TournamentFinishInfo(
    val dateMillis: Long,
    val location: String,
    val referees: String,
    val comment: String
)
