package com.example.nflunkyball.ui.organizer

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/**
 * The organizer's top bar during scoring, shared by [GroupStageScreen] and [BracketScreen] —
 * shows the tournament name plus live sync status, and opens [TournamentSettingsScreen] (a full
 * page, not a popup — it has its own actions like "Abandon tournament" that deserve more room
 * and a confirm step than a dropdown gives them) via the three-dot icon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizerTopBar(
    title: String,
    viewModel: OrganizerViewModel,
    onOpenSettings: () -> Unit
) {
    val broadcastVersion by viewModel.broadcastVersion.collectAsState()
    val serverSyncStatus by viewModel.serverSyncStatus.collectAsState()

    TopAppBar(
        title = {
            Column {
                Text(title)
                if (viewModel.useBleSync()) {
                    if (broadcastVersion != null) {
                        Text("Broadcasting v$broadcastVersion", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    serverSyncStatus?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                }
            }
        },
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.MoreVert, contentDescription = "Tournament settings")
            }
        }
    )
}
