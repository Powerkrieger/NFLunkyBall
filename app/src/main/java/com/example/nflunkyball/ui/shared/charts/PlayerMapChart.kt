package com.example.nflunkyball.ui.shared.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.nflunkyball.R
import com.example.nflunkyball.server.PlayerMap
import com.example.nflunkyball.ui.theme.Spacing
import kotlin.math.hypot

/**
 * The group as a scatter of the backend's 2D PCA (see [PlayerMap]): every player is a dot with
 * a name, [highlightId] is drawn bigger in the primary colour, and [linkToId] (their most
 * similar player) gets a connecting line — the distance you can see is the similarity the
 * player page reports. Tapping near a dot opens that player.
 */
@Composable
fun PlayerMapChart(
    map: PlayerMap,
    highlightId: Int?,
    linkToId: Int?,
    onOpenPlayer: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface)
    val dotColor = MaterialTheme.colorScheme.secondary
    val highlightColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val points = map.points
    val xCaption = map.axes.getOrNull(0)?.let { axisCaption(it) } ?: ""
    val yCaption = map.axes.getOrNull(1)?.let { axisCaption(it) } ?: ""

    Column(modifier) {
        // Same scale on both axes so distances aren't distorted, with padding for labels.
        val xs = points.map { it.x }
        val ys = points.map { it.y }
        val xMin = (xs.minOrNull() ?: -1.0); val xMax = (xs.maxOrNull() ?: 1.0)
        val yMin = (ys.minOrNull() ?: -1.0); val yMax = (ys.maxOrNull() ?: 1.0)
        val span = maxOf(xMax - xMin, yMax - yMin, 1e-6)
        val cx = (xMin + xMax) / 2; val cy = (yMin + yMax) / 2

        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(points) {
                    detectTapGestures { tap ->
                        val inset = 36.dp.toPx()
                        val scale = (minOf(size.width, size.height) - 2 * inset) / span
                        fun project(x: Double, y: Double) = Offset(
                            (size.width / 2 + (x - cx) * scale).toFloat(),
                            (size.height / 2 - (y - cy) * scale).toFloat()
                        )
                        points.minByOrNull { p -> project(p.x, p.y).let { hypot(it.x - tap.x, it.y - tap.y) } }
                            ?.takeIf { p -> project(p.x, p.y).let { hypot(it.x - tap.x, it.y - tap.y) } < 28.dp.toPx() }
                            ?.let { onOpenPlayer(it.id) }
                    }
                }
        ) {
            val inset = 36.dp.toPx()
            val scale = (size.minDimension - 2 * inset) / span
            fun project(x: Double, y: Double) = Offset(
                (size.width / 2 + (x - cx) * scale).toFloat(),
                (size.height / 2 - (y - cy) * scale).toFloat()
            )
            // Axes through the group's centre (the PCA origin is the average player).
            val origin = project(0.0, 0.0)
            drawLine(gridColor, Offset(0f, origin.y), Offset(size.width, origin.y), strokeWidth = 1.dp.toPx())
            drawLine(gridColor, Offset(origin.x, 0f), Offset(origin.x, size.height), strokeWidth = 1.dp.toPx())

            val highlighted = points.firstOrNull { it.id == highlightId }
            val linked = points.firstOrNull { it.id == linkToId }
            if (highlighted != null && linked != null) {
                drawLine(
                    highlightColor.copy(alpha = 0.6f),
                    project(highlighted.x, highlighted.y), project(linked.x, linked.y),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                )
            }
            for (p in points) {
                val at = project(p.x, p.y)
                val isHighlight = p.id == highlightId
                val isLinked = p.id == linkToId
                val radius = if (isHighlight) 8.dp.toPx() else 5.dp.toPx()
                drawCircle(if (isHighlight) highlightColor else dotColor, radius, at)
                if (isLinked) drawCircle(highlightColor, radius + 3.dp.toPx(), at, style = Stroke(2.dp.toPx()))
                val layout = textMeasurer.measure(p.name, labelStyle)
                drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y + radius + 2.dp.toPx()))
            }
        }
        Text(
            stringResource(R.string.map_axis_x, xCaption),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = Spacing.xs)
        )
        Text(stringResource(R.string.map_axis_y, yCaption), style = MaterialTheme.typography.labelSmall)
    }
}

