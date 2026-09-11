package com.example.nflunkyball.server

import com.example.nflunkyball.model.Group
import com.example.nflunkyball.model.Match
import com.example.nflunkyball.model.Team
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.model.TournamentFinishInfo
import com.example.nflunkyball.model.TournamentPhase
import com.example.nflunkyball.persistence.MatchDrinks
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Mirrors [Tournament] field-for-field, plus two additions: [UploadMatchResult.drinkA]/[UploadMatchResult.drinkB].
 * Kept as a genuinely separate type (not just extra nullable fields bolted onto the shared model)
 * so that BLE broadcasting and server-backed live sync — which both serialize the real
 * [Tournament] object directly — can never end up carrying drink choices, by construction rather
 * than by convention. Only [toUploadPayload] ever builds one of these, from the local-only
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
    val phase: TournamentPhase = TournamentPhase.SETUP,
    val squadSize: Int = 1,
    // Organizer-entered at finish time (see TournamentFinishInfo) — date is ISO-8601 (UTC), all
    // null if the tournament was finished without ever going through that dialog (e.g. finished
    // unlinked, with nothing to save anyway).
    val date: String? = null,
    val location: String? = null,
    val referees: String? = null,
    val comment: String? = null
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

/** One losing player's counter plus what they drank — the upload-only shape of [PlayerResult]. */
@Serializable
data class UploadPlayerResult(
    val player: String,
    val seconds: Int,
    val forfeitedDrinks: Int = 0,
    val drink: String? = null
)

@Serializable
data class UploadMatchResult(
    val winnerId: String,
    val winnerScore: Int,
    // Per-team drinks, the singles-era fields. Still sent (as the first member's drink on each
    // side) so an older backend keeps working; [losers]/[winnerDrinks] are the per-player record.
    val drinkA: String? = null,
    val drinkB: String? = null,
    val losers: List<UploadPlayerResult> = emptyList(),
    val winnerDrinks: Map<String, String> = emptyMap()
)

fun Tournament.toUploadPayload(
    drinksByMatchId: Map<String, MatchDrinks>,
    finishInfo: TournamentFinishInfo? = null
): UploadTournament {
    val teamsById = teams.associateBy { it.id }
    fun Match.toUpload() = UploadMatch(
        id = id,
        teamAId = teamAId,
        teamBId = teamBId,
        result = result?.let { result ->
            val drinks = drinksByMatchId[id]
            val winnerIsA = result.winnerId == teamAId
            val losingTeam = teamsById[if (winnerIsA) teamBId else teamAId]
            val winningTeam = teamsById[if (winnerIsA) teamAId else teamBId]
            val losingDrink = if (winnerIsA) drinks?.teamB else drinks?.teamA
            val winningDrink = if (winnerIsA) drinks?.teamA else drinks?.teamB
            val losers = result.loserResults(losingTeam?.name ?: "").map { entry ->
                UploadPlayerResult(entry.player, entry.seconds, entry.forfeitedDrinks, drinks?.byPlayer?.get(entry.player) ?: losingDrink)
            }
            val winnerDrinks = winningTeam?.memberNames.orEmpty()
                .mapNotNull { player -> (drinks?.byPlayer?.get(player) ?: winningDrink)?.let { player to it } }
                .toMap()
            UploadMatchResult(result.winnerId, result.winnerScore, drinks?.teamA, drinks?.teamB, losers, winnerDrinks)
        },
        roundLabel = roundLabel
    )
    return UploadTournament(
        id = id,
        name = name,
        teams = teams,
        groups = groups.map { UploadGroup(it.id, it.name, it.teamIds, it.matches.map { m -> m.toUpload() }) },
        bracketMatches = bracketMatches.map { it.toUpload() },
        phase = phase,
        squadSize = squadSize,
        date = finishInfo?.let { Instant.ofEpochMilli(it.dateMillis).toString() },
        location = finishInfo?.location?.trim()?.ifBlank { null },
        referees = finishInfo?.referees?.trim()?.ifBlank { null },
        comment = finishInfo?.comment?.trim()?.ifBlank { null }
    )
}
