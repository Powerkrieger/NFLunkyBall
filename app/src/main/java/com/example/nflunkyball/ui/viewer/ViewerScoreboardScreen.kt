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
import androidx.compose.material3.CardDefaults
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
import com.example.nflunkyball.ui.shared.BluetoothStatusRow
import com.example.nflunkyball.ui.shared.ChunkProgressBar
import com.example.nflunkyball.ui.shared.MatchList
import com.example.nflunkyball.ui.shared.RoomCodeDisplay
import com.example.nflunkyball.ui.shared.StandingsTable
import com.example.nflunkyball.ui.theme.Spacing

@Composable
fun ViewerScoreboardScreen(viewModel: ViewerViewModel, onOpenHistory: () -> Unit) {
    val tournament by viewModel.tournament.collectAsState()
    val receiveProgress by viewModel.receiveProgress.collectAsState()
    var showInvite by remember { mutableStateOf(false) }

    if (showInvite) {
        val payload = viewModel.joinPayload.collectAsState().value
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
                .padding(Spacing.md)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 72.dp)
        ) {
            // BLE status/progress only applies to BLE mode — server mode has no equivalent (a
            // single request either has the latest state or it doesn't, and needs no radio), so
            // this shows nothing extra there; "Waiting for the organizer's scores…" below covers
            // the empty case for both modes.
            if (viewModel.useBleSync()) {
                BluetoothStatusRow(modifier = Modifier.padding(bottom = Spacing.sm))
                val progress = receiveProgress
                if (progress != null) {
                    Text(
                        "Reading state of version ${progress.version}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    ChunkProgressBar(progress, modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.sm))
                }
            }

            val current = tournament
            if (current == null) {
                Text("Waiting for the organizer's scores…", style = MaterialTheme.typography.bodyLarge)
            } else {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        current.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
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
                        modifier = Modifier.padding(top = Spacing.lg)
                    )
                    StandingsTable(
                        standings = group.standings(),
                        teamNames = teamNames,
                        modifier = Modifier.padding(top = Spacing.sm)
                    )
                    MatchList(
                        matches = group.matches,
                        teamNames = teamNames,
                        onRecordResult = null,
                        modifier = Modifier.padding(top = Spacing.sm)
                    )
                }
                if (current.bracketMatches.isNotEmpty()) {
                    Text(
                        "Bracket",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = Spacing.lg)
                    )
                    MatchList(
                        matches = current.bracketMatches,
                        teamNames = teamNames,
                        onRecordResult = null,
                        modifier = Modifier.padding(top = Spacing.sm)
                    )
                }
            }
        }

        EmojiBar(
            onEmoji = { viewModel.sendEmoji(it) },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(Spacing.sm)
        )
    }
}

@Composable
private fun EmojiBar(onEmoji: (String) -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.sm),
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
