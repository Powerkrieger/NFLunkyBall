package com.example.nflunkyball.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nflunkyball.ble.EmojiPalette
import com.example.nflunkyball.model.standings
import com.example.nflunkyball.ui.shared.MatchList
import com.example.nflunkyball.ui.shared.RoomCodeDisplay
import com.example.nflunkyball.ui.shared.StandingsTable

@Composable
fun ViewerScoreboardScreen(viewModel: ViewerViewModel, onOpenHistory: () -> Unit) {
    val tournament by viewModel.tournament.collectAsState()
    var showInvite by remember { mutableStateOf(false) }

    if (showInvite) {
        val payload = viewModel.joinPayload
        AlertDialog(
            onDismissRequest = { showInvite = false },
            title = { Text("Invite others") },
            text = {
                if (payload != null) {
                    RoomCodeDisplay(payload)
                } else {
                    Text("No join code available.")
                }
            },
            confirmButton = { TextButton(onClick = { showInvite = false }) { Text("Close") } }
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 72.dp)
        ) {
            val current = tournament
            if (current == null) {
                Text("Waiting for the organizer's scores…", style = MaterialTheme.typography.bodyLarge)
            } else {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        current.name,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showInvite = true }) { Text("Invite") }
                    if (viewModel.historyAvailable()) {
                        TextButton(onClick = onOpenHistory) { Text("History") }
                    }
                }
                val teamNames = current.teams.associate { it.id to it.name }
                current.groups.forEach { group ->
                    Text(
                        group.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 20.dp)
                    )
                    StandingsTable(
                        standings = group.standings(),
                        teamNames = teamNames,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    MatchList(
                        matches = group.matches,
                        teamNames = teamNames,
                        onRecordResult = null,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (current.bracketMatches.isNotEmpty()) {
                    Text(
                        "Bracket",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 20.dp)
                    )
                    MatchList(
                        matches = current.bracketMatches,
                        teamNames = teamNames,
                        onRecordResult = null,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        EmojiBar(
            onEmoji = { viewModel.sendEmoji(it) },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)
        )
    }
}

@Composable
private fun EmojiBar(onEmoji: (String) -> Unit, modifier: Modifier = Modifier) {
    Card(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            EmojiPalette.emojis.forEach { emoji ->
                TextButton(onClick = { onEmoji(emoji) }) {
                    Text(emoji, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}
