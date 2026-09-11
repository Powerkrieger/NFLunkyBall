package com.example.nflunkyball.ui.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.nflunkyball.model.Match
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.model.standings
import com.example.nflunkyball.server.ServerResult
import com.example.nflunkyball.server.TournamentDetail
import com.example.nflunkyball.ui.shared.MatchList
import com.example.nflunkyball.ui.shared.StandingsTable
import com.example.nflunkyball.ui.theme.Spacing

/**
 * One archived tournament, reached either from the viewer's saved list ([savedId]) or by its
 * backend id ([serverId], e.g. from a player's Elo history). With a server id on hand the
 * backend's detail endpoint is preferred — it carries the finish metadata and the id maps that
 * make match rows and player names tappable; the locally cached body is the offline fallback
 * and renders read-only.
 */
@Composable
fun HistoryTournamentDetailScreen(
    viewModel: ViewerViewModel,
    savedId: String?,
    serverId: Int?,
    onOpenMatch: (Int) -> Unit,
    onOpenPlayer: (Int) -> Unit
) {
    val saved by viewModel.savedTournaments.collectAsState()
    val entry = saved.find { savedId != null && it.id == savedId } ?: saved.find { serverId != null && it.serverId == serverId }
    val resolvedServerId = serverId ?: entry?.serverId
    var detail by remember { mutableStateOf<TournamentDetail?>(null) }
    var cached by remember { mutableStateOf<Tournament?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(savedId, resolvedServerId, entry?.cachedTournamentJson) {
        detail = null
        cached = null
        error = null
        if (resolvedServerId != null) {
            when (val result = viewModel.fetchTournamentDetail(resolvedServerId)) {
                is ServerResult.Success -> { detail = result.value; return@LaunchedEffect }
                is ServerResult.Failure -> error = result.message
            }
        }
        val cachedJson = entry?.cachedTournamentJson
        when {
            cachedJson != null -> {
                cached = viewModel.decodeCachedTournament(cachedJson)
                if (cached != null) error = null else error = "Couldn't read this tournament's saved data"
            }
            error == null -> error = "This tournament isn't available yet"
        }
    }

    val current = detail?.tournament ?: cached
    when {
        current == null && error != null ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error!!) }
        current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> TournamentBody(current, detail, onOpenMatch, onOpenPlayer)
    }
}

@Composable
private fun TournamentBody(
    tournament: Tournament,
    detail: TournamentDetail?,
    onOpenMatch: (Int) -> Unit,
    onOpenPlayer: (Int) -> Unit
) {
    val teamNames = tournament.teams.associate { it.id to it.name }
    val openMatch: ((Match) -> (() -> Unit)?)? = detail?.let { d ->
        { match -> d.matchIds[match.id]?.let { id -> { onOpenMatch(id) } } }
    }

    Column(Modifier.fillMaxSize().padding(Spacing.md).verticalScroll(rememberScrollState())) {
        Text(
            tournament.name,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        detail?.let { d ->
            Text(d.date.take(10), style = MaterialTheme.typography.bodyMedium)
            d.location?.takeIf { it.isNotBlank() }?.let { Text("Location: $it", style = MaterialTheme.typography.bodyMedium) }
            d.referees?.takeIf { it.isNotBlank() }?.let { Text("Referees: $it", style = MaterialTheme.typography.bodyMedium) }
            d.comment?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.xs))
            }
        }

        val squads = tournament.squadSize > 1 || tournament.teams.any { it.members.size > 1 }
        Text(
            if (squads) "Teams" else "Players",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = Spacing.lg)
        )
        tournament.teams.forEach { team ->
            val memberIds = detail?.memberIds?.get(team.id)
                ?: detail?.competitorIds?.get(team.id)?.let { listOf(it) }
                .orEmpty()
            if (squads) {
                Text(team.name, modifier = Modifier.padding(top = Spacing.xs))
            }
            team.memberNames.forEachIndexed { index, member ->
                val competitorId = memberIds.getOrNull(index)
                Text(
                    if (squads) "    $member" else member,
                    color = if (competitorId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = competitorId != null) { competitorId?.let(onOpenPlayer) }
                        .padding(vertical = Spacing.xs)
                )
            }
            HorizontalDivider()
        }

        tournament.groups.forEach { group ->
            Text(
                group.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = Spacing.lg)
            )
            StandingsTable(
                standings = group.standings(),
                teamNames = teamNames,
                modifier = Modifier.padding(top = Spacing.sm)
            )
            MatchList(
                matches = group.matches,
                teamNames = teamNames,
                onRecordResult = null,
                modifier = Modifier.padding(top = Spacing.sm),
                onOpenMatch = openMatch
            )
        }
        if (tournament.bracketMatches.isNotEmpty()) {
            Text(
                "Bracket",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = Spacing.lg)
            )
            MatchList(
                matches = tournament.bracketMatches,
                teamNames = teamNames,
                onRecordResult = null,
                modifier = Modifier.padding(top = Spacing.sm),
                onOpenMatch = openMatch
            )
        }
    }
}
