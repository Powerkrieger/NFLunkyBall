package com.example.nflunkyball.ui.shared.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.nflunkyball.ui.theme.Spacing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** One polygon on the radar: values in 0..1 in the same order as the chart's axes. */
data class RadarSeries(val name: String, val values: List<Double>, val color: Color)

/**
 * A spider chart of a player's profile (each stat scaled 0..1 across the group), with an
 * optional second player overlaid — the quickest way to see *why* two players are "similar".
 */
@Composable
fun RadarChart(axisLabels: List<String>, series: List<RadarSeries>, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val n = axisLabels.size

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.15f)) {
            val centre = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2 - 40.dp.toPx()
            fun point(index: Int, value: Double): Offset {
                val angle = -PI / 2 + 2 * PI * index / n
                return Offset(centre.x + (radius * value * cos(angle)).toFloat(), centre.y + (radius * value * sin(angle)).toFloat())
            }
            // Rings at 25/50/75/100 % and one spoke per axis.
            for (ring in 1..4) {
                val path = Path()
                for (i in 0 until n) { val p = point(i, ring / 4.0); if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                path.close()
                drawPath(path, gridColor, style = Stroke(1.dp.toPx()))
            }
            for (i in 0 until n) {
                drawLine(gridColor, centre, point(i, 1.0), strokeWidth = 1.dp.toPx())
                val layout = textMeasurer.measure(axisLabels[i], labelStyle)
                val anchor = point(i, 1.0)
                val dx = (anchor.x - centre.x) / radius
                val dy = (anchor.y - centre.y) / radius
                drawText(
                    layout,
                    topLeft = Offset(
                        anchor.x + dx * 6.dp.toPx() - layout.size.width * (1 - dx) / 2f,
                        anchor.y + dy * 6.dp.toPx() - layout.size.height * (1 - dy) / 2f
                    )
                )
            }
            for (s in series) {
                val path = Path()
                s.values.forEachIndexed { i, v -> val p = point(i, v.coerceIn(0.0, 1.0)); if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                path.close()
                drawPath(path, s.color.copy(alpha = 0.25f))
                drawPath(path, s.color, style = Stroke(2.dp.toPx()))
            }
        }
        Row(Modifier.padding(top = Spacing.xs)) {
            series.forEach { s ->
                Box(Modifier.size(10.dp).background(s.color, CircleShape).align(Alignment.CenterVertically))
                Spacer(Modifier.width(4.dp))
                Text(s.name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = Spacing.md))
            }
        }
    }
}
