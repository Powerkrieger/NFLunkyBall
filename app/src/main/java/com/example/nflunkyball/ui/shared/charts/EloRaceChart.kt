package com.example.nflunkyball.ui.shared.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.nflunkyball.server.EloRace
import com.example.nflunkyball.ui.theme.Spacing

/**
 * The top players' Elo after every tournament, one line each on a shared tournament axis —
 * who overtook whom, and when. Series start at the first tournament a player appeared in.
 * Tap a name in the legend to open that player.
 */
@Composable
fun EloRaceChart(race: EloRace, onOpenPlayer: (Int) -> Unit, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val all = race.series.flatMap { it.ratings.filterNotNull() }
    val yMin = ((all.minOrNull() ?: 900.0) - 20).let { it - it % 50 }
    val yMax = ((all.maxOrNull() ?: 1100.0) + 20).let { it + (50 - it % 50) }
    val count = race.tournaments.size

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(220.dp)) {
            val left = 44.dp.toPx(); val bottom = size.height - 20.dp.toPx(); val top = 8.dp.toPx()
            val plotW = size.width - left - 8.dp.toPx(); val plotH = bottom - top
            fun xAt(i: Int) = left + if (count > 1) plotW * i / (count - 1) else plotW / 2
            fun yAt(v: Double) = (bottom - plotH * (v - yMin) / (yMax - yMin)).toFloat()
            var tick = yMin
            while (tick <= yMax) {
                drawLine(gridColor, Offset(left, yAt(tick)), Offset(size.width, yAt(tick)), strokeWidth = 1.dp.toPx())
                val l = textMeasurer.measure(tick.toInt().toString(), labelStyle)
                drawText(l, topLeft = Offset(left - l.size.width - 4.dp.toPx(), yAt(tick) - l.size.height / 2))
                tick += 50
            }
            for (i in 0 until count) {
                val l = textMeasurer.measure((i + 1).toString(), labelStyle)
                drawText(l, topLeft = Offset(xAt(i) - l.size.width / 2, bottom + 4.dp.toPx()))
            }
            race.series.forEachIndexed { index, s ->
                val color = seriesColor(index)
                val path = Path()
                var started = false
                s.ratings.forEachIndexed { i, r ->
                    if (r == null) return@forEachIndexed
                    val p = Offset(xAt(i), yAt(r))
                    if (!started) { path.moveTo(p.x, p.y); started = true } else path.lineTo(p.x, p.y)
                    drawCircle(color, 3.dp.toPx(), p)
                }
                drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        FlowRow(Modifier.padding(top = Spacing.xs)) {
            race.series.forEachIndexed { index, s ->
                Row(
                    Modifier.clickable { onOpenPlayer(s.id) }.padding(end = Spacing.md, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(10.dp).background(seriesColor(index), CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text(s.name, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text(
            race.tournaments.withIndex().joinToString("  ") { (i, t) -> "${i + 1}: ${t.name}" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs)
        )
    }
}
