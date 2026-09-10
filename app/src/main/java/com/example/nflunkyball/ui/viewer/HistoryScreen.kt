package com.example.nflunkyball.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.nflunkyball.ble.BleCapability
import com.example.nflunkyball.model.TournamentPhase
import com.example.nflunkyball.persistence.SavedTournament
import com.example.nflunkyball.server.CompetitorStats
import com.example.nflunkyball.ui.organizer.OrganizerViewModel
import com.example.nflunkyball.ui.theme.Spacing

@Composable
fun HistoryScreen(
    viewModel: ViewerViewModel,
    organizerViewModel: OrganizerViewModel,
    onOpenTournament: (String) -> Unit,
    onReconnected: () -> Unit,
    onResumeHosting: () -> Unit
) {
    LaunchedEffect(Unit) { viewModel.loadHistory() }
    val saved by viewModel.savedTournaments.collectAsState()
    // TournamentRepository (behind OrganizerViewModel) is a separate single-slot store from the
    // viewer's saved-tournaments list above, so a tournament this device is hosting wouldn't
    // otherwise show up here at all — surfaced explicitly instead.
    val hostedTournament by organizerViewModel.tournament.collectAsState()
    var tabIndex by remember { mutableIntStateOf(0) }
    var showBluetoothOff by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<SavedTournament?>(null) }
    val context = LocalContext.current

    if (showBluetoothOff) {
        AlertDialog(
            onDismissRequest = { showBluetoothOff = false },
            title = { Text("Bluetooth is off") },
            text = { Text("Turn on Bluetooth to reconnect and view live scores.") },
            confirmButton = { TextButton(onClick = { showBluetoothOff = false }) { Text("OK") } }
        )
    }

    pendingRemoval?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove \"${entry.name}\"?") },
            text = { Text("This only removes it from this list — it doesn't affect the backend or anyone else's device.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeSavedTournament(entry.id)
                    pendingRemoval = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") } }
        )
    }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tabIndex) {
            Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Tournaments") })
            Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Leaderboard") })
        }
        viewModel.historyStatus?.let { Text(it, modifier = Modifier.padding(Spacing.md)) }
        if (tabIndex == 0) {
            if (saved.isEmpty() && hostedTournament == null) {
                Box(Modifier.fillMaxSize().padding(Spacing.md), contentAlignment = Alignment.TopCenter) {
                    Text(
                        "No tournaments yet — join one, or check back once the organizer finishes.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    hostedTournament?.let { hosted ->
                        item {
                            HostedTournamentRow(name = hosted.name, phase = hosted.phase, onClick = onResumeHosting)
                        }
                    }
                    items(saved) { entry ->
                        SavedTournamentRow(
                            entry = entry,
                            onClick = {
                                if (entry.phase == TournamentPhase.FINISHED) {
                                    onOpenTournament(entry.id)
                                } else if (entry.joinPayload != null) {
                                    // Bluetooth is only actually needed to reconnect in BLE
                                    // mode — server mode's reconnect never touches it.
                                    if (!viewModel.useBleSync() || BleCapability.isBluetoothEnabled(context)) {
                                        viewModel.reconnect(entry)
                                        onReconnected()
                                    } else {
                                        showBluetoothOff = true
                                    }
                                }
                            },
                            onRemove = { pendingRemoval = entry }
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                items(viewModel.competitors.sortedByDescending { it.wins }) { c ->
                    CompetitorRow(c)
                }
            }
        }
    }
}

@Composable
private fun HostedTournamentRow(name: String, phase: TournamentPhase, onClick: () -> Unit) {
    // Tinted with the primary container (rather than the neutral surfaceVariant every other row
    // uses) so the organizer's own active tournament reads as visually distinct from anything
    // just being watched or reconnected to.
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(
                "Hosting · ${phase.name.lowercase().replace('_', ' ')} · tap to continue",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SavedTournamentRow(entry: SavedTournament, onClick: () -> Unit, onRemove: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (entry.phase == TournamentPhase.FINISHED) "Finished" else "In progress · tap to reconnect",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove ${entry.name} from this list")
            }
        }
    }
}

@Composable
private fun CompetitorRow(c: CompetitorStats) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(c.name)
        Text("${c.wins}W ${c.losses}L")
    }
    HorizontalDivider()
}
