package com.example.nflunkyball.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nflunkyball.ble.ChunkProgress

/** One segment per chunk of the in-flight version — green once that chunk has arrived, so it
 *  reads at a glance which specific packets are still missing rather than just a plain percent. */
@Composable
fun ChunkProgressBar(progress: ChunkProgress, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(10.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(progress.totalCount) { index ->
            val received = index in progress.receivedIndices
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        color = if (received) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}
