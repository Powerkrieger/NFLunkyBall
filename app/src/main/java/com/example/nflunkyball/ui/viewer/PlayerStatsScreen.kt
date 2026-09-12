package com.example.nflunkyball.ui.viewer

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nflunkyball.server.OpponentSummary
import com.example.nflunkyball.server.SimilarPlayer
import com.example.nflunkyball.ui.shared.BackTopBar
import com.example.nflunkyball.ui.shared.format1
import com.example.nflunkyball.ui.LoadState
import com.example.nflunkyball.ui.valueOrNull
import androidx.compose.runtime.collectAsState
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

/** One point on the Elo chart, whichever granularity it came from. [onOpen] navigates to the
 *  match or tournament the point came from. */
private data class EloPoint(val label: String, val rating: Double, val onOpen: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerStatsScreen(
    viewModel: ViewerViewModel,
    competitorId: Int,
    onBack: () -> Unit,
    onOpenPlayer: (Int) -> Unit,
    onOpenMatch: (Int) -> Unit,
    onOpenTournament: (Int) -> Unit
) {
    LaunchedEffect(competitorId) { viewModel.loadPlayerStats(competitorId) }
    val state by viewModel.playerStats.collectAsState()
    val stats = state.valueOrNull
    val statsMode by viewModel.statsMode.collectAsState()

    Scaffold(
        topBar = {
            BackTopBar(title = stats?.name ?: "Player", onBack = onBack)
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(Spacing.md).verticalScroll(rememberScrollState())
        ) {
            StatsModeSelector(
                selected = statsMode,
                onSelect = viewModel::selectStatsMode,
                modifier = Modifier.padding(bottom = Spacing.sm)
            )
            if (stats == null) {
                Text((state as? LoadState.Failed)?.message ?: "Loading…", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }

            Text(
                "${stats.wins}W ${stats.losses}L · Elo ${stats.elo.format1()}",
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
                Text("When they lose, they average ${it.format1()}s to finish their drink")
            }
            stats.avgSecondsOpponentsWhenWon?.let {
                Text("When they win, their opponent averages ${it.format1()}s to finish theirs")
            }
            stats.forfeitRate?.let {
                Text("Forfeit rate (when losing): ${(it * 100).toInt()}%")
            }
            if (stats.forfeitedDrinks > 0) {
                Text("Drinks refused: ${stats.forfeitedDrinks}")
            }
            stats.favoriteDrink?.let {
                Text("Favorite drink: $it")
            }
            Spacer2()
            OpponentLine("Best matchup", stats.bestOpponent, onOpenPlayer)
            OpponentLine("Toughest matchup", stats.worstOpponent, onOpenPlayer)
            OpponentLine("Nemesis (most played)", stats.nemesis, onOpenPlayer)
            SimilarPlayerLine(stats.mostSimilarPlayer, onOpenPlayer)

            val byTournament = remember(stats) {
                stats.eloHistory.map {
                    EloPoint("${it.tournamentName} (${it.date.take(10)})", it.rating) { onOpenTournament(it.tournamentId) }
                }
            }
            val byMatch = remember(stats) {
                stats.eloMatchHistory.map {
                    val with = if (it.teammates.isEmpty()) "" else " with ${it.teammates.joinToString(" & ") { t -> t.name }}"
                    EloPoint("${if (it.won) "W" else "L"} · vs ${it.opponentName}$with (${it.date.take(10)})", it.rating) {
                        onOpenMatch(it.matchId)
                    }
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
                            Modifier.fillMaxWidth().clickable(onClick = point.onOpen).padding(vertical = Spacing.xs),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(point.label, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            Text(point.rating.format1())
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
private fun OpponentLine(label: String, opponent: OpponentSummary?, onOpenPlayer: (Int) -> Unit) {
    if (opponent != null) {
        LinkLine(
            "$label: ${opponent.name} (${(opponent.winRate * 100).toInt()}% over ${opponent.matches} matches)"
        ) { onOpenPlayer(opponent.id) }
    } else {
        Text("$label: not enough data yet")
    }
}

@Composable
private fun SimilarPlayerLine(similar: SimilarPlayer?, onOpenPlayer: (Int) -> Unit) {
    if (similar != null) {
        LinkLine("Most similar player: ${similar.name}") { onOpenPlayer(similar.id) }
    } else {
        Text("Most similar player: not enough data yet")
    }
}

@Composable
private fun LinkLine(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.xs)
    )
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
