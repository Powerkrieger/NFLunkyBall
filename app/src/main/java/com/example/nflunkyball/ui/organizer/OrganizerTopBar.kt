package com.example.nflunkyball.ui.organizer

import androidx.compose.ui.res.stringResource
import com.example.nflunkyball.R
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
import com.example.nflunkyball.sync.SyncState

/**
 * The organizer's top bar during scoring, shared by [GroupStageScreen] and [BracketScreen] —
 * shows the tournament name plus live sync status, and opens [TournamentSettingsScreen] (a full
 * page, not a popup — it has its own actions like "Abandon tournament" that deserve more room
 * and a confirm step than a dropdown gives them) via the three-dot icon.
 */
/** The one-line status text for server-mode sync, shared by the top bar and HostingScreen;
 *  null when there's nothing to say (BLE mode / not hosting). */
@Composable
fun SyncState.label(): String? = when (this) {
    SyncState.Off -> null
    SyncState.NotLinked -> stringResource(R.string.sync_not_linked)
    SyncState.Starting -> stringResource(R.string.sync_starting)
    SyncState.Syncing -> stringResource(R.string.sync_syncing)
    SyncState.Synced -> stringResource(R.string.sync_synced)
    is SyncState.Failed -> stringResource(R.string.sync_failed, message)
}

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
                    broadcastVersion?.let { version ->
                        Text(stringResource(R.string.topbar_broadcasting, version), style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    serverSyncStatus.label()?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                }
            }
        },
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.topbar_settings))
            }
        }
    )
}
