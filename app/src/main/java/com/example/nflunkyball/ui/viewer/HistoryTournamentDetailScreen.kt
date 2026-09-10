package com.example.nflunkyball.ui.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.model.standings
import com.example.nflunkyball.server.ServerResult
import com.example.nflunkyball.ui.shared.MatchList
import com.example.nflunkyball.ui.shared.StandingsTable
import com.example.nflunkyball.ui.theme.Spacing

@Composable
fun HistoryTournamentDetailScreen(viewModel: ViewerViewModel, savedId: String) {
    val saved by viewModel.savedTournaments.collectAsState()
    val entry = saved.find { it.id == savedId }
    var tournament by remember { mutableStateOf<Tournament?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(savedId, entry?.cachedTournamentJson) {
        val cachedJson = entry?.cachedTournamentJson
        val serverId = entry?.serverId
        when {
            cachedJson != null -> {
                tournament = viewModel.decodeCachedTournament(cachedJson)
                if (tournament == null) error = "Couldn't read this tournament's saved data"
            }
            serverId != null -> when (val result = viewModel.fetchTournamentDetail(serverId)) {
                is ServerResult.Success -> tournament = result.value
                is ServerResult.Failure -> error = result.message
            }
            else -> error = "This tournament isn't available yet"
        }
    }

    val current = tournament
    when {
        error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error!!) }
        current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> {
            val teamNames = current.teams.associate { it.id to it.name }
            Column(
                Modifier.fillMaxSize().padding(Spacing.md).verticalScroll(rememberScrollState())
            ) {
                Text(
                    current.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                current.groups.forEach { group ->
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
                        modifier = Modifier.padding(top = Spacing.sm)
                    )
                }
                if (current.bracketMatches.isNotEmpty()) {
                    Text(
                        "Bracket",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = Spacing.lg)
                    )
                    MatchList(
                        matches = current.bracketMatches,
                        teamNames = teamNames,
                        onRecordResult = null,
                        modifier = Modifier.padding(top = Spacing.sm)
                    )
                }
            }
        }
    }
}
