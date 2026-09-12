package com.example.nflunkyball.server

import com.example.nflunkyball.model.Tournament
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire types for the NFLunkyBallServer backend — request/response bodies only, no behaviour.
// Field names follow the backend's JSON (snake_case where it uses it), so keep @SerialName in
// sync with the Python side when either changes.

@Serializable
data class RegisterRequest(
    // No display name here — it's assigned by whoever mints the invite, not chosen by whoever
    // redeems it (see the backend's admin panel).
    @SerialName("invite_token") val inviteToken: String,
    @SerialName("public_key") val publicKey: String
)

@Serializable
data class RegisterResponse(
    // Both null for a viewer invite — no Account is created for those.
    @SerialName("account_id") val accountId: Int?,
    @SerialName("display_name") val displayName: String?,
    @SerialName("read_password") val readPassword: String
)

@Serializable
data class UploadRequest(
    @SerialName("account_id") val accountId: Int,
    val timestamp: Long,
    val signature: String,
    val body: String
)

@Serializable
data class UploadResponse(val id: Int)

@Serializable
data class TournamentSummary(val id: Int, val name: String, val date: String, val phase: String)

@Serializable
data class CompetitorStats(val id: Int, val name: String, val wins: Int, val losses: Int, val elo: Double)

@Serializable
data class OpponentSummary(val id: Int, val name: String, val matches: Int, val winRate: Double)

@Serializable
data class EloHistoryEntry(
    @SerialName("tournament_id") val tournamentId: Int,
    @SerialName("tournament_name") val tournamentName: String,
    val date: String,
    val rating: Double
)

@Serializable
data class EloMatchHistoryEntry(
    @SerialName("match_id") val matchId: Int,
    @SerialName("tournament_id") val tournamentId: Int,
    @SerialName("tournament_name") val tournamentName: String,
    val date: String,
    // Singles-era pair: the first opposing player, and the opposing side's names joined.
    @SerialName("opponent_id") val opponentId: Int,
    @SerialName("opponent_name") val opponentName: String,
    val opponents: List<CompetitorRef> = emptyList(),
    val teammates: List<CompetitorRef> = emptyList(),
    val won: Boolean,
    val rating: Double
)

/** Which matches a stats view counts — mirrors the backend's `mode` query parameter. */
enum class StatsMode(val query: String) {
    ALL("all"),
    SINGLES("singles"),
    TEAMS("teams")
}

@Serializable
data class SimilarPlayer(val id: Int, val name: String, val distance: Double)

@Serializable
data class CompetitorDetailStats(
    val id: Int,
    val name: String,
    val wins: Int,
    val losses: Int,
    val elo: Double,
    val avgSecondsWhenLost: Double?,
    val avgSecondsOpponentsWhenWon: Double?,
    val forfeitRate: Double?,
    val forfeitedDrinks: Int = 0,
    val currentStreak: Int,
    val longestWinStreak: Int,
    val favoriteDrink: String?,
    val bestOpponent: OpponentSummary?,
    val worstOpponent: OpponentSummary?,
    val nemesis: OpponentSummary?,
    val mostSimilarPlayer: SimilarPlayer?,
    val eloHistory: List<EloHistoryEntry>,
    val eloMatchHistory: List<EloMatchHistoryEntry>
)

@Serializable
data class CompetitorRef(val id: Int, val name: String)

@Serializable
data class TournamentRef(val id: Int, val name: String, val date: String, val location: String?)

@Serializable
data class HeadToHead(val matches: Int, val winsA: Int, val winsB: Int)

/** One player's part in a match. [seconds]/[forfeitedDrinks] are their own counter and only
 *  meaningful on the losing side. */
@Serializable
data class MatchPlayer(
    val id: Int,
    val name: String,
    val won: Boolean,
    val seconds: Int?,
    val forfeitedDrinks: Int,
    val drink: String?,
    val eloBefore: Double?,
    val eloAfter: Double?
)

/** Everything the archive knows about one match — see the backend's `match_detail`. Elo
 *  before/after is what the full replay assigned at this point in play order. [sideA]/[sideB]
 *  are the squad-aware view; the remaining per-side fields are the singles-era shape (first
 *  player per side, losers' summed counter). */
@Serializable
data class MatchDetail(
    val id: Int,
    val tournament: TournamentRef,
    val roundLabel: String?,
    val teamAName: String = "",
    val teamBName: String = "",
    val sideA: List<MatchPlayer> = emptyList(),
    val sideB: List<MatchPlayer> = emptyList(),
    val winnerIsA: Boolean = true,
    val competitorA: CompetitorRef,
    val competitorB: CompetitorRef,
    val winner: CompetitorRef?,
    val winnerScore: Int?,
    val forfeit: Boolean,
    val drinkA: String?,
    val drinkB: String?,
    val eloBeforeA: Double?,
    val eloAfterA: Double?,
    val eloBeforeB: Double?,
    val eloAfterB: Double?,
    val headToHead: HeadToHead
)

/** The archived tournament body plus organizer-entered metadata and the client-id → server-id
 *  maps that turn its match rows and team names into links ([matchIds] only covers matches
 *  that have a result — those are the only ones the backend keeps as rows). */
@Serializable
data class TournamentDetail(
    val id: Int,
    val name: String,
    val date: String,
    val phase: String,
    val squadSize: Int = 1,
    val location: String?,
    val referees: String?,
    val comment: String?,
    val tournament: Tournament,
    val matchIds: Map<String, Int>,
    /** Client team id → every member's competitor id, in order. */
    val memberIds: Map<String, List<Int>> = emptyMap(),
    /** Client team id → first member — the singles-era shape. */
    val competitorIds: Map<String, Int>
)

@Serializable
data class AccountStatus(
    val id: Int,
    @SerialName("display_name") val displayName: String,
    val revoked: Boolean
)
