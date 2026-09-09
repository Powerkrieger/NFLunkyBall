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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.model.standings
import com.example.nflunkyball.server.ServerResult
import com.example.nflunkyball.ui.shared.MatchList
import com.example.nflunkyball.ui.shared.StandingsTable

@Composable
fun HistoryTournamentDetailScreen(viewModel: ViewerViewModel, tournamentId: Int) {
    var tournament by remember { mutableStateOf<Tournament?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tournamentId) {
        when (val result = viewModel.fetchTournamentDetail(tournamentId)) {
            is ServerResult.Success -> tournament = result.value
            is ServerResult.Failure -> error = result.message
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
                Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
            ) {
                Text(current.name, style = MaterialTheme.typography.headlineSmall)
                current.groups.forEach { group ->
                    Text(
                        group.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 20.dp)
                    )
                    StandingsTable(
                        standings = group.standings(),
                        teamNames = teamNames,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    MatchList(
                        matches = group.matches,
                        teamNames = teamNames,
                        onRecordResult = null,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (current.bracketMatches.isNotEmpty()) {
                    Text(
                        "Bracket",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 20.dp)
                    )
                    MatchList(
                        matches = current.bracketMatches,
                        teamNames = teamNames,
                        onRecordResult = null,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}
