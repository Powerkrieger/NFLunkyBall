package com.example.nflunkyball.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nflunkyball.BuildConfig
import com.example.nflunkyball.ui.theme.Spacing

@Composable
fun HomeScreen(
    hasActiveOrLinkedSession: Boolean,
    onHost: () -> Unit,
    onJoin: () -> Unit,
    onMyTournaments: () -> Unit,
    onRulebook: () -> Unit,
    onSettings: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        IconButton(
            onClick = onRulebook,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(Spacing.sm)
                .size(44.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = "Rulebook",
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        IconButton(
            onClick = onSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Spacing.sm)
                .size(44.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Column(
            Modifier.fillMaxSize().padding(Spacing.lg),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "NFLunkyBall",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
            // "My tournaments" is the one worth highlighting once there's something there to go
            // back to (a tournament in progress, or a linked/joined account); otherwise Host/Join
            // are the more useful default actions on an otherwise-empty home screen. Filled =
            // the highlighted action, outlined = the rest, same visual language either way.
            HomeActionButton(
                text = "My tournaments",
                highlighted = hasActiveOrLinkedSession,
                onClick = onMyTournaments,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl)
            )
            HomeActionButton(
                text = "Host a tournament",
                highlighted = !hasActiveOrLinkedSession,
                onClick = onHost,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)
            )
            HomeActionButton(
                text = "Join a tournament",
                highlighted = !hasActiveOrLinkedSession,
                onClick = onJoin,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)
            )
        }
        Text(
            "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.sm)
        )
    }
}

@Composable
private fun HomeActionButton(text: String, highlighted: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (highlighted) {
        Button(onClick = onClick, modifier = modifier) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
    }
}
