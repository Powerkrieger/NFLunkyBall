package com.example.nflunkyball.ui.viewer

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import com.example.nflunkyball.ble.BleCapability
import com.example.nflunkyball.model.TournamentPhase
import com.example.nflunkyball.persistence.SavedTournament
import com.example.nflunkyball.server.CompetitorStats

@Composable
fun HistoryScreen(
    viewModel: ViewerViewModel,
    onOpenTournament: (String) -> Unit,
    onReconnected: () -> Unit
) {
    LaunchedEffect(Unit) { viewModel.loadHistory() }
    val saved by viewModel.savedTournaments.collectAsState()
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
        viewModel.historyStatus?.let { Text(it, modifier = Modifier.padding(16.dp)) }
        if (tabIndex == 0) {
            if (saved.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) {
                    Text(
                        "No tournaments yet — join one, or check back once the organizer finishes.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                    items(saved) { entry ->
                        SavedTournamentRow(
                            entry = entry,
                            onClick = {
                                if (entry.phase == TournamentPhase.FINISHED) {
                                    onOpenTournament(entry.id)
                                } else if (entry.joinPayload != null) {
                                    if (BleCapability.isBluetoothEnabled(context)) {
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
            LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                items(viewModel.competitors.sortedByDescending { it.wins }) { c ->
                    CompetitorRow(c)
                }
            }
        }
    }
}

@Composable
private fun SavedTournamentRow(entry: SavedTournament, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
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
    HorizontalDivider()
}

@Composable
private fun CompetitorRow(c: CompetitorStats) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(c.name)
        Text("${c.wins}W ${c.losses}L")
    }
    HorizontalDivider()
}
