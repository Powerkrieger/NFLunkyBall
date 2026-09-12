package com.example.nflunkyball.ui.shared.charts

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.example.nflunkyball.R
import com.example.nflunkyball.server.MapAxis

/** Series colours shared by every chart, chosen to stay distinct in both themes. */
val ChartPalette: List<Color> = listOf(
    Color(0xFF4CAF50), Color(0xFFFF9800), Color(0xFF2196F3), Color(0xFFE91E63),
    Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFFFEB3B), Color(0xFF795548)
)

fun seriesColor(index: Int): Color = ChartPalette[index % ChartPalette.size]

/** Human label for one of the backend's similarity dimensions (see `_FEATURE_DIMENSIONS`). */
@Composable
fun dimensionLabel(dimension: String): String = when (dimension) {
    "win_rate" -> stringResource(R.string.dim_win_rate)
    "elo" -> stringResource(R.string.dim_elo)
    "avg_seconds_when_lost" -> stringResource(R.string.dim_own_drink_time)
    "avg_seconds_opponents_when_won" -> stringResource(R.string.dim_opponent_drink_time)
    "forfeit_rate" -> stringResource(R.string.dim_forfeits)
    "matches_played" -> stringResource(R.string.dim_matches)
    else -> dimension
}

/** "Win rate ↑ · Elo ↑ · Forfeits ↓" — the two or three stats that dominate a principal axis,
 *  with the direction they pull in, so the map's axes mean something to a reader. */
@Composable
fun axisCaption(axis: MapAxis, take: Int = 3): String {
    val parts = axis.loadings.entries
        .sortedByDescending { kotlin.math.abs(it.value) }
        .take(take)
        .filter { kotlin.math.abs(it.value) >= 0.25 }
        .map { (dim, loading) -> dimensionLabel(dim) + if (loading >= 0) " ↑" else " ↓" }
    return parts.joinToString(" · ")
}
