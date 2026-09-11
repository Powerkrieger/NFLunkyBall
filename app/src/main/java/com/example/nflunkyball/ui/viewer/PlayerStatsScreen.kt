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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nflunkyball.server.EloHistoryEntry
import com.example.nflunkyball.server.OpponentSummary
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
            Spacer2()
            stats.avgSecondsWhenLost?.let {
                Text("Avg. seconds to finish their beer when they lose: ${"%.1f".format(Locale.US, it)}")
            }
            stats.avgSecondsOpponentsWhenWon?.let {
                Text("Avg. seconds their opponents take to beat them: ${"%.1f".format(Locale.US, it)}")
            }
            Spacer2()
            OpponentLine("Best matchup", stats.bestOpponent)
            OpponentLine("Toughest matchup", stats.worstOpponent)

            if (stats.eloHistory.isNotEmpty()) {
                Spacer2()
                Text("Elo over time", style = MaterialTheme.typography.titleMedium)
                EloChart(stats.eloHistory, modifier = Modifier.padding(top = Spacing.sm))
                Column(Modifier.padding(top = Spacing.sm)) {
                    stats.eloHistory.forEach { entry ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${entry.tournamentName} (${entry.date.take(10)})")
                            Text("%.1f".format(Locale.US, entry.rating))
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
private fun EloChart(history: List<EloHistoryEntry>, modifier: Modifier = Modifier) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(history) {
        modelProducer.runTransaction {
            add(
                LineCartesianLayerModel.partial {
                    series(history.indices.map { it.toDouble() }, history.map { it.rating })
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
