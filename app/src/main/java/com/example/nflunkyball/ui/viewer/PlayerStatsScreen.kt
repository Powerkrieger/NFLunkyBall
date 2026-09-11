package com.example.nflunkyball.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nflunkyball.server.OpponentSummary
import com.example.nflunkyball.server.SimilarPlayer
import com.example.nflunkyball.ui.theme.Spacing
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.LineCartesianLayerModel
import java.util.Locale

/** One point on the Elo chart, whichever granularity it came from. */
private data class EloPoint(val label: String, val rating: Double)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerStatsScreen(viewModel: ViewerViewModel, competitorId: Int, onBack: () -> Unit) {
    LaunchedEffect(competitorId) { viewModel.loadPlayerStats(competitorId) }
    val stats = viewModel.playerStats
    val status = viewModel.playerStatsStatus

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stats?.name ?: "Player") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(Spacing.md).verticalScroll(rememberScrollState())
        ) {
            if (stats == null) {
                Text(status ?: "Loading…", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }

            Text(
                "${stats.wins}W ${stats.losses}L · Elo ${"%.1f".format(Locale.US, stats.elo)}",
                style = MaterialTheme.typography.titleMedium
            )
            if (stats.wins + stats.losses == 0) {
                Text("No matches played yet", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    if (stats.currentStreak > 0) "Current streak: ${stats.currentStreak}W"
                    else if (stats.currentStreak < 0) "Current streak: ${-stats.currentStreak}L"
                    else "Current streak: none",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("Longest win streak: ${stats.longestWinStreak}", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer2()
            // Both are the same underlying measurement (winnerScore = the loser's own
            // beer-finishing time, credited as the winner's score — see the backend's
            // player_detail_stats doc) viewed from either side of this player's matches.
            stats.avgSecondsWhenLost?.let {
                Text("When they lose, they average ${"%.1f".format(Locale.US, it)}s to finish their drink")
            }
            stats.avgSecondsOpponentsWhenWon?.let {
                Text("When they win, their opponent averages ${"%.1f".format(Locale.US, it)}s to finish theirs")
            }
            stats.forfeitRate?.let {
                Text("Forfeit rate (when losing): ${(it * 100).toInt()}%")
            }
            stats.favoriteDrink?.let {
                Text("Favorite drink: $it")
            }
            Spacer2()
            OpponentLine("Best matchup", stats.bestOpponent)
            OpponentLine("Toughest matchup", stats.worstOpponent)
            OpponentLine("Nemesis (most played)", stats.nemesis)
            SimilarPlayerLine(stats.mostSimilarPlayer)

            val byTournament = remember(stats) {
                stats.eloHistory.map { EloPoint("${it.tournamentName} (${it.date.take(10)})", it.rating) }
            }
            val byMatch = remember(stats) {
                stats.eloMatchHistory.map {
                    EloPoint("${if (it.won) "W" else "L"} · vs ${it.opponentName} (${it.date.take(10)})", it.rating)
                }
            }
            if (byTournament.isNotEmpty() || byMatch.isNotEmpty()) {
                Spacer2()
                Text("Elo over time", style = MaterialTheme.typography.titleMedium)
                var granularity by remember { mutableIntStateOf(0) }
                TabRow(selectedTabIndex = granularity) {
                    Tab(selected = granularity == 0, onClick = { granularity = 0 }, text = { Text("By tournament") })
                    Tab(selected = granularity == 1, onClick = { granularity = 1 }, text = { Text("By match") })
                }
                val points = if (granularity == 0) byTournament else byMatch
                EloChart(points, modifier = Modifier.padding(top = Spacing.sm))
                Column(Modifier.padding(top = Spacing.sm)) {
                    points.forEach { point ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(point.label)
                            Text("%.1f".format(Locale.US, point.rating))
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun Spacer2() {
    Spacer(Modifier.height(Spacing.md))
}

@Composable
private fun OpponentLine(label: String, opponent: OpponentSummary?) {
    Text(
        if (opponent != null) {
            "$label: ${opponent.name} (${(opponent.winRate * 100).toInt()}% over ${opponent.matches} matches)"
        } else {
            "$label: not enough data yet"
        }
    )
}

@Composable
private fun SimilarPlayerLine(similar: SimilarPlayer?) {
    Text(if (similar != null) "Most similar player: ${similar.name}" else "Most similar player: not enough data yet")
}

@Composable
private fun EloChart(points: List<EloPoint>, modifier: Modifier = Modifier) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points) {
        // Vico rejects an empty series outright; an empty list just leaves the chart blank.
        if (points.isEmpty()) return@LaunchedEffect
        modelProducer.runTransaction {
            add(
                LineCartesianLayerModel.partial {
                    series(points.indices.map { it.toDouble() }, points.map { it.rating })
                }
            )
        }
    }
    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom()
            ),
            modelProducer = modelProducer,
            modifier = modifier.fillMaxWidth().height(200.dp)
        )
    }
}
