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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import com.example.nflunkyball.model.ProvisionalStanding
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.model.TournamentPhase
import com.example.nflunkyball.model.provisionalLeaderboard
import com.example.nflunkyball.persistence.SavedTournament
import com.example.nflunkyball.server.CompetitorStats
import com.example.nflunkyball.server.StatsMode
import com.example.nflunkyball.server.withLiveStandings
import com.example.nflunkyball.ui.theme.Spacing
import java.util.Locale

/** [hostedTournament] is the organizer side's own tournament, if this device is hosting one.
 *  TournamentRepository (behind OrganizerViewModel) is a separate single-slot store from the
 *  viewer's saved-tournaments list, so it wouldn't otherwise show up here at all — the caller
 *  passes it in explicitly rather than this screen reaching into the organizer ViewModel.
 *
 *  A hosted tournament in the FINISHED phase is one whose archive upload hasn't succeeded yet
 *  (see OrganizerViewModel.finishAndUpload): [hostedUploadStatus] is the last attempt's outcome,
 *  and the row offers [onRetryUpload] / [onDiscardHosted] instead of resuming hosting. */
@Composable
fun HistoryScreen(
    viewModel: ViewerViewModel,
    hostedTournament: Tournament?,
    hostedUploadStatus: String?,
    onRetryUpload: () -> Unit,
    onDiscardHosted: () -> Unit,
    onOpenTournament: (String) -> Unit,
    onReconnected: () -> Unit,
    onResumeHosting: () -> Unit,
    onOpenPlayer: (Int) -> Unit
) {
    LaunchedEffect(Unit) { viewModel.loadHistory() }
    val saved by viewModel.savedTournaments.collectAsState()
    // The viewer's currently-watched live tournament (BLE or server sync) — folded into the
    // Leaderboard tab below so it reflects games in progress, not just archived results.
    val watchedTournament by viewModel.tournament.collectAsState()
    var tabIndex by remember { mutableIntStateOf(0) }
    var showBluetoothOff by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Discard finished tournament?") },
            text = { Text("It was never uploaded, so all its results will be lost. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { showDiscardConfirm = false; onDiscardHosted() }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { showDiscardConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showBluetoothOff) {
        AlertDialog(
            onDismissRequest = { showBluetoothOff = false },
            title = { Text("Bluetooth is off") },
            text = { Text("Turn on Bluetooth to reconnect and view live scores.") },
            confirmButton = { TextButton(onClick = { showBluetoothOff = false }) { Text("OK") } }
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
                            if (hosted.phase == TournamentPhase.FINISHED) {
                                PendingUploadRow(
                                    name = hosted.name,
                                    status = hostedUploadStatus,
                                    onRetry = onRetryUpload,
                                    onDiscard = { showDiscardConfirm = true }
                                )
                            } else {
                                HostedTournamentRow(name = hosted.name, phase = hosted.phase, onClick = onResumeHosting)
                            }
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
                            }
                        )
                    }
                }
            }
        } else {
            // Hosted preferred over watched if somehow both are active — this device's own
            // tournament is more clearly "the one" than one it merely happens to be viewing.
            val currentTournament = (hostedTournament ?: watchedTournament)
                ?.takeIf { it.phase != TournamentPhase.FINISHED }
            var leaderboardTab by remember { mutableIntStateOf(if (currentTournament != null) 0 else 1) }

            TabRow(selectedTabIndex = leaderboardTab) {
                Tab(
                    selected = leaderboardTab == 0,
                    onClick = { leaderboardTab = 0 },
                    text = { Text("This tournament") }
                )
                Tab(selected = leaderboardTab == 1, onClick = { leaderboardTab = 1 }, text = { Text("Global") })
            }
            if (leaderboardTab == 0) {
                if (currentTournament == null) {
                    Box(Modifier.fillMaxSize().padding(Spacing.md), contentAlignment = Alignment.TopCenter) {
                        Text("No tournament in progress right now.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    val teamNames = currentTournament.teams.associate { it.id to it.name }
                    val rows = remember(currentTournament) { currentTournament.provisionalLeaderboard() }
                    LazyColumn(
                        Modifier.fillMaxSize().padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        items(rows) { (teamId, standing) ->
                            TournamentStandingRow(teamNames[teamId] ?: teamId, standing)
                        }
                    }
                }
            } else {
                val leaderboard = remember(viewModel.competitors, hostedTournament, watchedTournament) {
                    viewModel.competitors
                        .withLiveStandings(listOf(hostedTournament, watchedTournament))
                        .sortedByDescending { it.elo }
                }
                LazyColumn(
                    Modifier.fillMaxSize().padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    item {
                        StatsModeSelector(
                            selected = viewModel.statsMode,
                            onSelect = viewModel::selectStatsMode,
                            modifier = Modifier.padding(bottom = Spacing.sm)
                        )
                    }
                    items(leaderboard) { c ->
                        // c.id is -1 for a live-only player not yet in the backend's Competitor
                        // table (see withLiveStandings) — nothing to open a stats page for yet.
                        CompetitorRow(c, onClick = { if (c.id >= 0) onOpenPlayer(c.id) })
                    }
                }
            }
        }
    }
}

/** All / Singles / Teams lens for every server-computed stat — see [ViewerViewModel.statsMode]. */
@Composable
fun StatsModeSelector(selected: StatsMode, onSelect: (StatsMode) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StatsMode.entries.forEach { mode ->
            FilterChip(selected = selected == mode, onClick = { onSelect(mode) }, label = { Text(mode.label) })
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

/** A finished tournament still on this device because its upload hasn't gone through. */
@Composable
private fun PendingUploadRow(name: String, status: String?, onRetry: () -> Unit, onDiscard: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(
                "Finished · not uploaded yet" + (status?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall
            )
            Row(Modifier.padding(top = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = onRetry) { Text("Retry upload") }
                TextButton(onClick = onDiscard) { Text("Discard") }
            }
        }
    }
}

@Composable
private fun SavedTournamentRow(entry: SavedTournament, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Text(entry.name, style = MaterialTheme.typography.titleMedium)
            Text(
                if (entry.phase == TournamentPhase.FINISHED) "Finished" else "In progress · tap to reconnect",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun TournamentStandingRow(teamName: String, standing: ProvisionalStanding) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(teamName)
        val sign = if (standing.eloDelta >= 0) "+" else ""
        Text("${standing.winDelta}W ${standing.lossDelta}L · $sign${String.format(Locale.US, "%.1f", standing.eloDelta)} elo")
    }
    HorizontalDivider()
}

@Composable
private fun CompetitorRow(c: CompetitorStats, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = c.id >= 0, onClick = onClick).padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(c.name)
        Text("${String.format(Locale.US, "%.1f", c.elo)} · ${c.wins}W ${c.losses}L")
    }
    HorizontalDivider()
}
