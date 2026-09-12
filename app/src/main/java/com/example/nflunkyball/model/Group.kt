package com.example.nflunkyball.model

import kotlinx.serialization.Serializable

@Serializable
data class Group(
    val id: String,
    val name: String,
    val teamIds: List<String>,
    val matches: List<Match> = emptyList(),
    /** How many times every pairing is played — 2 for a double round robin (a small group
     *  wanting as many matches as a bigger one). See [generateRoundRobinMatches]. */
    val rounds: Int = 1,
    /** A group played during the knockout phase rather than the group stage — a consolation
     *  group for the teams knocked out early, say. Scored from the bracket screen; its members
     *  are usually already in a group-stage group too. */
    val knockoutStage: Boolean = false
) {
    val hasResults: Boolean get() = matches.any { it.result != null }
}
