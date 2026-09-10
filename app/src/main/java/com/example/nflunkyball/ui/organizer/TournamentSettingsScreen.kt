package com.example.nflunkyball.ui.organizer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.nflunkyball.ble.RoomCode
import com.example.nflunkyball.model.Group
import com.example.nflunkyball.qr.JoinPayload
import com.example.nflunkyball.ui.shared.RoomCodeDisplay

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
    onLinkAccount: () -> Unit
) {
    val tournament by viewModel.tournament.collectAsState()
    val current = tournament ?: return

    var showAddPlayer by remember { mutableStateOf(false) }
    var showAddGroup by remember { mutableStateOf(false) }
    var showRoomCode by remember { mutableStateOf(false) }
    var showAbandonConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tournament settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SettingsRow("Add player", enabled = current.groups.isNotEmpty()) { showAddPlayer = true }
            SettingsRow("Add group") { showAddGroup = true }
            SettingsRow("Show viewer code") { showRoomCode = true }
            // Always available, not just while unlinked — an existing link can stop working
            // (account revoked, credentials lost) with no local sign other than sync quietly
            // failing, so re-entering a fresh invite token needs to work as a recovery path too.
            SettingsRow(if (viewModel.organizerAccount == null) "Link organizer account" else "Add invite token") {
                onLinkAccount()
            }
            SettingsRow("Abandon tournament", destructive = true) { showAbandonConfirm = true }
        }
    }

    if (showAddPlayer) {
        AddPlayerDialog(
            groups = current.groups,
            onDismiss = { showAddPlayer = false },
            onConfirm = { groupId, name ->
                viewModel.addPlayer(groupId, name)
                showAddPlayer = false
            }
        )
    }

    if (showAddGroup) {
        AddGroupDialog(
            onDismiss = { showAddGroup = false },
            onConfirm = { name ->
                viewModel.addGroup(name)
                showAddGroup = false
            }
        )
    }

    if (showRoomCode) {
        val roomId = RoomCode.forTournament(current.id)
        val account = viewModel.organizerAccount
        val payload = JoinPayload(room = RoomCode.encode(roomId), server = account?.serverUrl, pw = viewModel.readPassword, tid = current.id)
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
    Text(
        text,
        color = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            destructive -> MaterialTheme.colorScheme.error
            else -> Color.Unspecified
        },
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    )
    HorizontalDivider()
}

@Composable
private fun AddPlayerDialog(
    groups: List<Group>,
    onDismiss: () -> Unit,
    onConfirm: (groupId: String, name: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var groupId by remember { mutableStateOf(groups.firstOrNull()?.id ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add player") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Player name") },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (groups.size > 1) {
                    Text("Group", style = MaterialTheme.typography.labelMedium)
                    GroupPicker(groups, groupId) { groupId = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(groupId, name) },
                enabled = name.isNotBlank() && groupId.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddGroupDialog(onDismiss: () -> Unit, onConfirm: (name: String) -> Unit) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add group") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Group name") })
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun GroupPicker(groups: List<Group>, selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = groups.firstOrNull { it.id == selectedId }?.name ?: "Select group"
    TextButton(onClick = { expanded = true }) { Text(selectedName) }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        groups.forEach { group ->
            DropdownMenuItem(text = { Text(group.name) }, onClick = { onSelect(group.id); expanded = false })
        }
    }
}
