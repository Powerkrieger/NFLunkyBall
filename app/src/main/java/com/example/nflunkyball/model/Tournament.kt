package com.example.nflunkyball.model

import kotlinx.serialization.Serializable

@Serializable
enum class TournamentPhase { SETUP, GROUP_STAGE, BRACKET, FINISHED }

@Serializable
data class Tournament(
    val id: String,
    val name: String,
    val teams: List<Team> = emptyList(),
    val groups: List<Group> = emptyList(),
    val bracketMatches: List<Match> = emptyList(),
    val phase: TournamentPhase = TournamentPhase.SETUP,
    /** Players per [Team], chosen by the organizer at setup. 1 is the classic singles format. */
    val squadSize: Int = 1
)
