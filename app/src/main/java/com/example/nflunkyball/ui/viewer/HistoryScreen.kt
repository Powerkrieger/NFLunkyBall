package com.example.nflunkyball.ui.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nflunkyball.server.CompetitorStats
import com.example.nflunkyball.server.TournamentSummary

@Composable
fun HistoryScreen(viewModel: ViewerViewModel, onOpenTournament: (Int) -> Unit) {
    LaunchedEffect(Unit) { viewModel.loadHistory() }
    var tabIndex by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tabIndex) {
            Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Tournaments") })
            Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Leaderboard") })
        }
        viewModel.historyStatus?.let { Text(it, modifier = Modifier.padding(16.dp)) }
        if (tabIndex == 0) {
            LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                items(viewModel.historyTournaments) { t ->
                    TournamentRow(t) { onOpenTournament(t.id) }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                items(viewModel.competitors.sortedByDescending { it.wins }) { c ->
                    CompetitorRow(c)
                }
            }
        }
    }
}

@Composable
private fun TournamentRow(t: TournamentSummary, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text(t.name, style = MaterialTheme.typography.titleMedium)
        Text(t.date, style = MaterialTheme.typography.bodySmall)
    }
    HorizontalDivider()
}

@Composable
private fun CompetitorRow(c: CompetitorStats) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(c.name)
        Text("${c.wins}W ${c.losses}L")
    }
    HorizontalDivider()
}
