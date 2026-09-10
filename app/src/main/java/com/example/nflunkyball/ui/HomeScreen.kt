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
import com.example.nflunkyball.ui.theme.Spacing

@Composable
fun HomeScreen(onHost: () -> Unit, onJoin: () -> Unit, onMyTournaments: () -> Unit, onSettings: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
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
            // Filled for the primary action, outlined for the rest — one clear default choice
            // ("start something new") instead of three visually equal buttons.
            Button(onClick = onHost, modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl)) {
                Text("Host a tournament")
            }
            OutlinedButton(onClick = onJoin, modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)) {
                Text("Join a tournament")
            }
            OutlinedButton(onClick = onMyTournaments, modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)) {
                Text("My tournaments")
            }
        }
    }
}
