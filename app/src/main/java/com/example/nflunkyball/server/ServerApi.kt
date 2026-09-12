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
import kotlinx.serialization.json.Json

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

    private val client get() = sharedClient

    private companion object {
        /** One engine for the whole process: callers construct a fresh [ServerApi] per call
         *  site (the server URL can differ between the organizer account and a scanned join
         *  code), and a new CIO client per instance would leak its thread pool each time. */
        val sharedClient: HttpClient by lazy {
            HttpClient(CIO) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        }
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
