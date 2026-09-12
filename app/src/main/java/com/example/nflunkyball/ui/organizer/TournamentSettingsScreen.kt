package com.example.nflunkyball.ui.organizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.nflunkyball.ble.RoomCode
import com.example.nflunkyball.qr.JoinPayload
import com.example.nflunkyball.ui.shared.RoomCodeDisplay
import com.example.nflunkyball.ui.shared.BackTopBar
import com.example.nflunkyball.ui.theme.Spacing

/**
 * Full page (not a popup) reached from [OrganizerTopBar]'s three-dot icon — a real back button
 * and enough room for a destructive action ("Abandon tournament") to not feel like an
 * accidental-tap trap the way a cramped dropdown item would.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentSettingsScreen(
    viewModel: OrganizerViewModel,
    onBack: () -> Unit,
    onAbandoned: () -> Unit,
    onLinkAccount: () -> Unit,
    onManagePlayers: () -> Unit,
    onManageGroups: () -> Unit
) {
    val tournament by viewModel.tournament.collectAsState()
    val current = tournament ?: return
    val account by viewModel.organizerAccount.collectAsState()
    val readPassword by viewModel.readPassword.collectAsState()

    var showRoomCode by remember { mutableStateOf(false) }
    var showAbandonConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BackTopBar(title = "Tournament settings", onBack = onBack)
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            SettingsRow("Manage players", onClick = onManagePlayers)
            SettingsRow("Manage groups", onClick = onManageGroups)
            SettingsRow("Show viewer code") { showRoomCode = true }
            // Always available, not just while unlinked — an existing link can stop working
            // (account revoked, credentials lost) with no local sign other than sync quietly
            // failing, so re-entering a fresh invite token needs to work as a recovery path too.
            SettingsRow(if (account == null) "Link organizer account" else "Add invite token") {
                onLinkAccount()
            }
            SettingsRow("Abandon tournament", destructive = true) { showAbandonConfirm = true }
        }
    }

    if (showRoomCode) {
        val roomId = RoomCode.forTournament(current.id)
        val payload = JoinPayload(room = RoomCode.encode(roomId), server = account?.serverUrl, pw = readPassword, tid = current.id)
        AlertDialog(
            onDismissRequest = { showRoomCode = false },
            title = { Text("Share this to let people watch") },
            text = { RoomCodeDisplay(payload) },
            confirmButton = { TextButton(onClick = { showRoomCode = false }) { Text("Close") } }
        )
    }

    if (showAbandonConfirm) {
        AlertDialog(
            onDismissRequest = { showAbandonConfirm = false },
            title = { Text("Abandon tournament?") },
            text = { Text("This deletes \"${current.name}\" from this device, including all recorded scores. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showAbandonConfirm = false
                    viewModel.clearTournament()
                    onAbandoned()
                }) { Text("Abandon") }
            },
            dismissButton = { TextButton(onClick = { showAbandonConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsRow(
    text: String,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(
            text,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                destructive -> MaterialTheme.colorScheme.error
                else -> Color.Unspecified
            },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.md)
        )
    }
}
