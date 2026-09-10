package com.example.nflunkyball.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(onHost: () -> Unit, onJoin: () -> Unit, onMyTournaments: () -> Unit, onSettings: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        IconButton(onClick = onSettings, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("NFLunkyBall", style = MaterialTheme.typography.displaySmall)
            Button(onClick = onHost, modifier = Modifier.fillMaxWidth().padding(top = 32.dp)) {
                Text("Host a tournament")
            }
            Button(onClick = onJoin, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text("Join a tournament")
            }
            Button(onClick = onMyTournaments, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text("My tournaments")
            }
        }
    }
}
