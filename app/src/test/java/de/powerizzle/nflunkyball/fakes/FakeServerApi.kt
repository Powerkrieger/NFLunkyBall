package de.powerizzle.nflunkyball.fakes

import de.powerizzle.nflunkyball.server.AccountStatus
import de.powerizzle.nflunkyball.server.CompetitorDetailStats
import de.powerizzle.nflunkyball.server.CompetitorStats
import de.powerizzle.nflunkyball.server.EloRace
import de.powerizzle.nflunkyball.server.HeadToHeadGrid
import de.powerizzle.nflunkyball.server.PlayerMap
import de.powerizzle.nflunkyball.server.MatchDetail
import de.powerizzle.nflunkyball.server.RegisterResponse
import de.powerizzle.nflunkyball.server.ServerApi
import de.powerizzle.nflunkyball.server.ServerResult
import de.powerizzle.nflunkyball.server.StatsMode
import de.powerizzle.nflunkyball.server.TournamentDetail
import de.powerizzle.nflunkyball.server.TournamentSummary
import de.powerizzle.nflunkyball.server.UploadResponse

/** Records every call and answers from the canned results set by the test. Unset endpoints
 *  fail with "not stubbed" so a test can't accidentally pass on a call it never expected. */
class FakeServerApi(val baseUrl: String = "https://example.test") : ServerApi {

    data class Upload(val accountId: Int, val timestamp: Long, val signatureBase64: String, val bodyJson: String)
    data class LivePush(val tournamentId: String, val accountId: Int, val timestamp: Long, val signatureBase64: String, val bodyJson: String)

    val uploads = mutableListOf<Upload>()
    val livePushes = mutableListOf<LivePush>()
    val registrations = mutableListOf<Pair<String, String>>()
    var liveJsonRequests = 0

    var registerResult: ServerResult<RegisterResponse> = notStubbed()
    var uploadResult: ServerResult<UploadResponse> = notStubbed()
    var livePushResult: ServerResult<Unit> = ServerResult.Success(Unit)
    var liveJsonResult: ServerResult<String> = notStubbed()
    var accountStatusResult: ServerResult<AccountStatus> = notStubbed()
    var competitorsResult: ServerResult<List<CompetitorStats>> = ServerResult.Success(emptyList())
    var tournamentsResult: ServerResult<List<TournamentSummary>> = ServerResult.Success(emptyList())
    var tournamentJsonResult: ServerResult<String> = notStubbed()
    var tournamentDetailResult: ServerResult<TournamentDetail> = notStubbed()
    var matchDetailResult: ServerResult<MatchDetail> = notStubbed()
    var competitorStatsResult: ServerResult<CompetitorDetailStats> = notStubbed()
    var playerMapResult: ServerResult<PlayerMap> = notStubbed()
    var headToHeadResult: ServerResult<HeadToHeadGrid> = notStubbed()
    var eloRaceResult: ServerResult<EloRace> = notStubbed()

    override suspend fun register(inviteToken: String, publicKeyBase64: String): ServerResult<RegisterResponse> {
        registrations += inviteToken to publicKeyBase64
        return registerResult
    }

    override suspend fun uploadTournament(accountId: Int, timestamp: Long, signatureBase64: String, bodyJson: String): ServerResult<UploadResponse> {
        uploads += Upload(accountId, timestamp, signatureBase64, bodyJson)
        return uploadResult
    }

    override suspend fun pushLiveTournament(tournamentId: String, accountId: Int, timestamp: Long, signatureBase64: String, bodyJson: String): ServerResult<Unit> {
        livePushes += LivePush(tournamentId, accountId, timestamp, signatureBase64, bodyJson)
        return livePushResult
    }

    override suspend fun getLiveTournamentJson(tournamentId: String, readPassword: String): ServerResult<String> {
        liveJsonRequests++
        return liveJsonResult
    }

    override suspend fun listTournaments(readPassword: String) = tournamentsResult
    override suspend fun getTournamentJson(id: Int, readPassword: String) = tournamentJsonResult
    override suspend fun getTournamentDetail(id: Int, readPassword: String) = tournamentDetailResult
    override suspend fun getMatchDetail(id: Int, readPassword: String, mode: StatsMode) = matchDetailResult
    override suspend fun listCompetitors(readPassword: String, mode: StatsMode) = competitorsResult
    override suspend fun getCompetitorStats(id: Int, readPassword: String, mode: StatsMode) = competitorStatsResult
    override suspend fun getPlayerMap(readPassword: String, mode: StatsMode) = playerMapResult
    override suspend fun getHeadToHead(readPassword: String, mode: StatsMode) = headToHeadResult
    override suspend fun getEloRace(readPassword: String, mode: StatsMode, limit: Int) = eloRaceResult
    override suspend fun getAccountStatus(accountId: Int, readPassword: String) = accountStatusResult

    private fun notStubbed() = ServerResult.Failure("not stubbed")
}
