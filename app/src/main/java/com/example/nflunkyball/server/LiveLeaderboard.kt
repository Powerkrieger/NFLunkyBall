package com.example.nflunkyball.server

import com.example.nflunkyball.model.ELO_STARTING_RATING
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.model.TournamentPhase
import com.example.nflunkyball.model.provisionalPlayerStandings

/**
 * Overlays each still-in-progress (non-FINISHED) tournament's own provisional standings on top
 * of the backend's persisted, archived-only stats, matched by player name — so a
 * currently-watched or currently-hosted tournament's results show up on the Global leaderboard
 * immediately instead of only after the organizer uploads it to history. Purely a display-time
 * approximation: it's fine for this to shift as the organizer edits live results (see
 * [provisionalPlayerStandings]).
 *
 * Players not yet known to the backend get a synthetic entry with `id = -1` — there's no stats
 * page to open for them yet, and callers should treat that id as "no link".
 */
fun List<CompetitorStats>.withLiveStandings(liveTournaments: List<Tournament?>): List<CompetitorStats> {
    val byNameLower = associateBy { it.name.lowercase() }.toMutableMap()
    liveTournaments.filterNotNull()
        .filter { it.phase != TournamentPhase.FINISHED }
        .forEach { tournament ->
            // Per player, not per team: a squad's members each carry the result on their own
            // persisted rating, exactly as the backend will once the tournament is uploaded.
            tournament.provisionalPlayerStandings().forEach { (player, delta) ->
                val key = player.lowercase()
                val current = byNameLower[key]
                    ?: CompetitorStats(id = -1, name = player, wins = 0, losses = 0, elo = ELO_STARTING_RATING)
                byNameLower[key] = current.copy(
                    wins = current.wins + delta.winDelta,
                    losses = current.losses + delta.lossDelta,
                    elo = current.elo + delta.eloDelta
                )
            }
        }
    return byNameLower.values.toList()
}
