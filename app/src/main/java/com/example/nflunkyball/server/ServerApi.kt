package com.example.nflunkyball.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import com.example.nflunkyball.model.Tournament
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
enum class StatsMode(val query: String, val label: String) {
    ALL("all", "All"),
    SINGLES("singles", "Singles"),
    TEAMS("teams", "Teams")
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

sealed interface ServerResult<out T> {
    data class Success<T>(val value: T) : ServerResult<T>
    data class Failure(val message: String) : ServerResult<Nothing>
}

private suspend fun <T> serverCall(block: suspend () -> T): ServerResult<T> =
    runCatching { block() }.fold(
        onSuccess = { ServerResult.Success(it) },
        onFailure = { ServerResult.Failure(it.message ?: "Request failed") }
    )

/** [baseUrl] always comes from a decoded [InvitePayload] or [com.example.nflunkyball.qr.JoinPayload] —
 *  never hardcoded, see those types' docs for why. */
class ServerApi(private val baseUrl: String) {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    suspend fun register(
        inviteToken: String,
        publicKeyBase64: String
    ): ServerResult<RegisterResponse> = serverCall {
        client.post("$baseUrl/accounts/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(inviteToken, publicKeyBase64))
        }.body()
    }

    suspend fun uploadTournament(
        accountId: Int,
        timestamp: Long,
        signatureBase64: String,
        bodyJson: String
    ): ServerResult<UploadResponse> = serverCall {
        client.post("$baseUrl/tournaments") {
            contentType(ContentType.Application.Json)
            setBody(UploadRequest(accountId, timestamp, signatureBase64, bodyJson))
        }.body()
    }

    /** Organizer-side push for server-backed live sync (the alternative to BLE broadcasting,
     *  see AppSettingsStore) — called on every score change, unlike [uploadTournament] which is
     *  a manual one-shot archive upload. Upserts on the server, so no conflict handling here. */
    suspend fun pushLiveTournament(
        tournamentId: String,
        accountId: Int,
        timestamp: Long,
        signatureBase64: String,
        bodyJson: String
    ): ServerResult<Unit> = serverCall {
        val response = client.put("$baseUrl/tournaments/live/$tournamentId") {
            contentType(ContentType.Application.Json)
            setBody(UploadRequest(accountId, timestamp, signatureBase64, bodyJson))
        }
        check(response.status.isSuccess()) { "Live push failed: ${response.status}" }
    }

    /** Viewer-side poll for server-backed live sync. Returns the raw JSON text, same as
     *  [getTournamentJson] — the app's own Tournament serializer decodes it from here. */
    suspend fun getLiveTournamentJson(tournamentId: String, readPassword: String): ServerResult<String> = serverCall {
        client.get("$baseUrl/tournaments/live/$tournamentId") {
            header("X-Read-Password", readPassword)
        }.body()
    }

    suspend fun listTournaments(readPassword: String): ServerResult<List<TournamentSummary>> = serverCall {
        client.get("$baseUrl/tournaments") {
            header("X-Read-Password", readPassword)
        }.body()
    }

    /** Returns the raw JSON text (the app's own Tournament serializer decodes it from here). */
    suspend fun getTournamentJson(id: Int, readPassword: String): ServerResult<String> = serverCall {
        client.get("$baseUrl/tournaments/$id") {
            header("X-Read-Password", readPassword)
        }.body()
    }

    suspend fun getTournamentDetail(id: Int, readPassword: String): ServerResult<TournamentDetail> = serverCall {
        client.get("$baseUrl/tournaments/$id/detail") {
            header("X-Read-Password", readPassword)
        }.body()
    }

    suspend fun getMatchDetail(id: Int, readPassword: String, mode: StatsMode = StatsMode.ALL): ServerResult<MatchDetail> =
        serverCall {
            client.get("$baseUrl/matches/$id") {
                parameter("mode", mode.query)
                header("X-Read-Password", readPassword)
            }.body()
        }

    suspend fun listCompetitors(readPassword: String, mode: StatsMode = StatsMode.ALL): ServerResult<List<CompetitorStats>> =
        serverCall {
            client.get("$baseUrl/competitors") {
                parameter("mode", mode.query)
                header("X-Read-Password", readPassword)
            }.body()
        }

    suspend fun getCompetitorStats(
        id: Int,
        readPassword: String,
        mode: StatsMode = StatsMode.ALL
    ): ServerResult<CompetitorDetailStats> = serverCall {
        client.get("$baseUrl/competitors/$id") {
            parameter("mode", mode.query)
            header("X-Read-Password", readPassword)
        }.body()
    }

    /** Lets a linked device check whether its own account still works (e.g. an admin revoked
     *  it) — see SettingsScreen's "Organizer account" section, which shows this rather than
     *  just whether credentials exist locally. */
    suspend fun getAccountStatus(accountId: Int, readPassword: String): ServerResult<AccountStatus> = serverCall {
        client.get("$baseUrl/accounts/$accountId") {
            header("X-Read-Password", readPassword)
        }.body()
    }
}
