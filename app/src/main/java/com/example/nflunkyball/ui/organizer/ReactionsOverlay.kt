package com.example.nflunkyball.ui.organizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.example.nflunkyball.ui.theme.Spacing
import kotlinx.coroutines.delay

/** Small floating display of incoming viewer emoji reactions — meant to sit atop other organizer screens. */
@Composable
fun ReactionsOverlay(viewModel: OrganizerViewModel, modifier: Modifier = Modifier) {
    var latest by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.emojiEvents.collect { emoji -> latest = emoji }
    }

    LaunchedEffect(latest) {
        if (latest != null) {
            delay(1500)
            latest = null
        }
    }

    Box(modifier) {
        AnimatedVisibility(visible = latest != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(text = latest ?: "", fontSize = 48.sp, modifier = Modifier.padding(Spacing.md))
            }
        }
    }
}
