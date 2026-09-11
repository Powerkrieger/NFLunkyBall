package com.example.nflunkyball.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RegisterRequest(
    @SerialName("display_name") val displayName: String,
    @SerialName("invite_token") val inviteToken: String,
    @SerialName("public_key") val publicKey: String
)

@Serializable
data class RegisterResponse(
    @SerialName("account_id") val accountId: Int,
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
        displayName: String,
        inviteToken: String,
        publicKeyBase64: String
    ): ServerResult<RegisterResponse> = serverCall {
        client.post("$baseUrl/accounts/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(displayName, inviteToken, publicKeyBase64))
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

    suspend fun listCompetitors(readPassword: String): ServerResult<List<CompetitorStats>> = serverCall {
        client.get("$baseUrl/competitors") {
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
