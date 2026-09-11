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
    /** Format marker: 1 is the classic singles format, anything above means a team tournament.
     *  Squads are NOT required to match it or each other — sides can be uneven (2 v 3) — so
     *  this is the largest squad at setup, not a constraint. */
    val squadSize: Int = 1
)
