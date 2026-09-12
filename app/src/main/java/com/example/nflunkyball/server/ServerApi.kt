package com.example.nflunkyball.server

sealed interface ServerResult<out T> {
    data class Success<T>(val value: T) : ServerResult<T>
    data class Failure(val message: String) : ServerResult<Nothing>
}

/**
 * The NFLunkyBallServer backend as the app sees it. Read endpoints take the group's shared
 * [readPassword]; organizer writes carry an Ed25519 signature (see [UploadSigner]). Interface so
 * the ViewModel layer can be tested against a fake; [KtorServerApi] is the real client.
 */
interface ServerApi {
    suspend fun register(inviteToken: String, publicKeyBase64: String): ServerResult<RegisterResponse>
    suspend fun uploadTournament(accountId: Int, timestamp: Long, signatureBase64: String, bodyJson: String): ServerResult<UploadResponse>
    /** Organizer-side push for server-backed live sync (the alternative to BLE broadcasting) —
     *  called on every score change, unlike [uploadTournament] which is a one-shot archive
     *  upload. Upserts on the server, so no conflict handling here. */
    suspend fun pushLiveTournament(tournamentId: String, accountId: Int, timestamp: Long, signatureBase64: String, bodyJson: String): ServerResult<Unit>
    /** Viewer-side poll for server-backed live sync. Raw JSON text, like [getTournamentJson]. */
    suspend fun getLiveTournamentJson(tournamentId: String, readPassword: String): ServerResult<String>
    suspend fun listTournaments(readPassword: String): ServerResult<List<TournamentSummary>>
    /** Returns the raw JSON text (the app's own Tournament serializer decodes it from here). */
    suspend fun getTournamentJson(id: Int, readPassword: String): ServerResult<String>
    suspend fun getTournamentDetail(id: Int, readPassword: String): ServerResult<TournamentDetail>
    suspend fun getMatchDetail(id: Int, readPassword: String, mode: StatsMode = StatsMode.ALL): ServerResult<MatchDetail>
    suspend fun listCompetitors(readPassword: String, mode: StatsMode = StatsMode.ALL): ServerResult<List<CompetitorStats>>
    suspend fun getCompetitorStats(id: Int, readPassword: String, mode: StatsMode = StatsMode.ALL): ServerResult<CompetitorDetailStats>
    /** Lets a linked device check whether its own account still works (e.g. an admin revoked
     *  it) — see SettingsScreen's "Organizer account" section. */
    suspend fun getAccountStatus(accountId: Int, readPassword: String): ServerResult<AccountStatus>
}

/** Constructor reference shape for anything that needs a client per server URL. */
typealias ServerApiFactory = (baseUrl: String) -> ServerApi
