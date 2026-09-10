package com.example.nflunkyball.server

import com.example.nflunkyball.model.Group
import com.example.nflunkyball.model.Match
import com.example.nflunkyball.model.Team
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.model.TournamentPhase
import kotlinx.serialization.Serializable

/**
 * Mirrors [Tournament] field-for-field, plus one addition: [UploadMatchResult.drink]. Kept as a
 * genuinely separate type (not just an extra nullable field bolted onto the shared model) so
 * that BLE broadcasting and server-backed live sync — which both serialize the real [Tournament]
 * object directly — can never end up carrying drink choices, by construction rather than by
 * convention. Only [toUploadPayload] ever builds one of these, from the local-only
 * [com.example.nflunkyball.persistence.MatchDrinkStore], and only for the final "upload to
 * history" call.
 */
@Serializable
data class UploadTournament(
    val id: String,
    val name: String,
    val teams: List<Team> = emptyList(),
    val groups: List<UploadGroup> = emptyList(),
    val bracketMatches: List<UploadMatch> = emptyList(),
    val phase: TournamentPhase = TournamentPhase.SETUP
)

@Serializable
data class UploadGroup(
    val id: String,
    val name: String,
    val teamIds: List<String>,
    val matches: List<UploadMatch> = emptyList()
)

@Serializable
data class UploadMatch(
    val id: String,
    val teamAId: String,
    val teamBId: String,
    val result: UploadMatchResult? = null,
    val roundLabel: String? = null
)

@Serializable
data class UploadMatchResult(
    val winnerId: String,
    val winnerScore: Int,
    val drink: String? = null
)

fun Tournament.toUploadPayload(drinksByMatchId: Map<String, String>): UploadTournament {
    fun Match.toUpload() = UploadMatch(
        id = id,
        teamAId = teamAId,
        teamBId = teamBId,
        result = result?.let { UploadMatchResult(it.winnerId, it.winnerScore, drinksByMatchId[id]) },
        roundLabel = roundLabel
    )
    return UploadTournament(
        id = id,
        name = name,
        teams = teams,
        groups = groups.map { UploadGroup(it.id, it.name, it.teamIds, it.matches.map { m -> m.toUpload() }) },
        bracketMatches = bracketMatches.map { it.toUpload() },
        phase = phase
    )
}
