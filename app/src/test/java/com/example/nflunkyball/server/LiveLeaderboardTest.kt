package com.example.nflunkyball.server

import com.example.nflunkyball.model.ELO_STARTING_RATING
import com.example.nflunkyball.model.Group
import com.example.nflunkyball.model.Match
import com.example.nflunkyball.model.MatchResult
import com.example.nflunkyball.model.Team
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.model.TournamentPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveLeaderboardTest {

    private fun tournament(phase: TournamentPhase) = Tournament(
        id = "t",
        name = "Live",
        teams = listOf(Team("a", "Anna"), Team("b", "Ben")),
        groups = listOf(
            Group(
                id = "g", name = "G", teamIds = listOf("a", "b"),
                matches = listOf(Match("m", "a", "b", result = MatchResult("a", 120)))
            )
        ),
        phase = phase
    )

    @Test
    fun `live results are added onto persisted stats, matched case-insensitively`() {
        val server = listOf(CompetitorStats(id = 7, name = "anna", wins = 3, losses = 1, elo = 1050.0))

        val merged = server.withLiveStandings(listOf(tournament(TournamentPhase.GROUP_STAGE), null))
        val anna = merged.single { it.name == "anna" }
        val ben = merged.single { it.name == "Ben" }

        assertEquals(7, anna.id)
        assertEquals(4, anna.wins)
        assertEquals(1, anna.losses)
        assertTrue(anna.elo > 1050.0)
        // Ben isn't on the server yet: synthetic entry starting from the default rating.
        assertEquals(-1, ben.id)
        assertEquals(1, ben.losses)
        assertTrue(ben.elo < ELO_STARTING_RATING)
    }

    @Test
    fun `finished tournaments are ignored since the backend already has them`() {
        val server = listOf(CompetitorStats(id = 7, name = "Anna", wins = 3, losses = 1, elo = 1050.0))
        assertEquals(server, server.withLiveStandings(listOf(tournament(TournamentPhase.FINISHED))))
    }
}
