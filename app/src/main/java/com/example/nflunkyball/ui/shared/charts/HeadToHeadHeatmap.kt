package com.example.nflunkyball.ui.shared.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.nflunkyball.R
import com.example.nflunkyball.server.HeadToHeadGrid
import com.example.nflunkyball.ui.theme.Spacing

/**
 * Players × players, each cell coloured by the row player's win rate against the column player
 * (green = wins, red = loses, neutral = even), stronger the more matches behind it. Empty cells
 * are pairs that have never played. Rows/columns are ordered by Elo. Tap a name to open that
 * player. Scrolls sideways once the group outgrows the screen.
 */
@Composable
fun HeadToHeadHeatmap(grid: HeadToHeadGrid, onOpenPlayer: (Int) -> Unit, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface)
    val cellTextStyle = MaterialTheme.typography.labelSmall.copy(color = Color.Black)
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val win = Color(0xFF66BB6A)
    val loss = Color(0xFFEF5350)
    val even = Color(0xFFBDBDBD)
    val players = grid.players
    val n = players.size
    val byPair = grid.cells.associateBy { it.a to it.b }
    val cell = 34.dp
    val labelW = 72.dp
    val labelH = 64.dp

    Column(modifier) {
        Canvas(
            Modifier
                .horizontalScroll(rememberScrollState())
                .width(labelW + cell * n)
                .height(labelH + cell * n)
                .pointerInput(players) {
                    detectTapGestures { tap ->
                        val row = ((tap.y - labelH.toPx()) / cell.toPx()).toInt()
                        val col = ((tap.x - labelW.toPx()) / cell.toPx()).toInt()
                        when {
                            tap.x < labelW.toPx() && row in 0 until n -> onOpenPlayer(players[row].id)
                            tap.y < labelH.toPx() && col in 0 until n -> onOpenPlayer(players[col].id)
                        }
                    }
                }
        ) {
            val c = cell.toPx()
            val x0 = labelW.toPx()
            val y0 = labelH.toPx()
            for ((i, p) in players.withIndex()) {
                val rowLabel = textMeasurer.measure(p.name.take(9), labelStyle)
                drawText(rowLabel, topLeft = Offset(x0 - rowLabel.size.width - 6.dp.toPx(), y0 + i * c + (c - rowLabel.size.height) / 2))
                val colLabel = textMeasurer.measure(p.name.take(9), labelStyle)
                rotate(-90f, pivot = Offset(x0 + i * c + c / 2, y0 - 4.dp.toPx())) {
                    drawText(colLabel, topLeft = Offset(x0 + i * c + c / 2, y0 - 4.dp.toPx() - colLabel.size.height / 2))
                }
            }
            for (i in 0 until n) for (j in 0 until n) {
                val topLeft = Offset(x0 + j * c, y0 + i * c)
                if (i == j) {
                    drawRect(empty.copy(alpha = 0.4f), topLeft, Size(c - 2, c - 2))
                    continue
                }
                val a = minOf(players[i].id, players[j].id)
                val b = maxOf(players[i].id, players[j].id)
                val data = byPair[a to b]
                if (data == null || data.matches == 0) {
                    drawRect(empty, topLeft, Size(c - 2, c - 2))
                    continue
                }
                val rowWins = if (players[i].id == a) data.winsA else data.matches - data.winsA
                val rate = rowWins.toFloat() / data.matches
                val base = if (rate >= 0.5f) lerp(even, win, (rate - 0.5f) * 2) else lerp(even, loss, (0.5f - rate) * 2)
                val confidence = (data.matches / 4f).coerceIn(0.35f, 1f)
                drawRect(lerp(empty, base, confidence), topLeft, Size(c - 2, c - 2))
                val label = textMeasurer.measure("$rowWins/${data.matches}", cellTextStyle)
                drawText(label, topLeft = Offset(topLeft.x + (c - label.size.width) / 2, topLeft.y + (c - label.size.height) / 2))
            }
        }
        Text(stringResource(R.string.h2h_legend), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = Spacing.xs))
    }
}
