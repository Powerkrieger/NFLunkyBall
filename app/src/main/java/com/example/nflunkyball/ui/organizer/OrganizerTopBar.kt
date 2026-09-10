package com.example.nflunkyball.ui.organizer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.example.nflunkyball.ble.RoomCode
import com.example.nflunkyball.model.Group
import com.example.nflunkyball.model.Tournament
import com.example.nflunkyball.qr.JoinPayload
import com.example.nflunkyball.ui.shared.RoomCodeDisplay

/**
 * The organizer's tournament-settings menu (three-dot overflow), shared by [GroupStageScreen]
 * and [BracketScreen] — adding players/groups, re-showing the viewer join code, and abandoning
 * the tournament are all useful at any point after hosting has started, not just at setup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizerTopBar(
    title: String,
    tournament: Tournament,
    viewModel: OrganizerViewModel,
    onAbandoned: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showAddPlayer by remember { mutableStateOf(false) }
    var showAddGroup by remember { mutableStateOf(false) }
    var showRoomCode by remember { mutableStateOf(false) }
    var showAbandonConfirm by remember { mutableStateOf(false) }
    val broadcastVersion by viewModel.broadcastVersion.collectAsState()

    TopAppBar(
        title = {
            Column {
                Text(title)
                if (viewModel.bleEnabled() && broadcastVersion != null) {
                    Text("Broadcasting v$broadcastVersion", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        actions = {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Tournament settings")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Add player") },
                    onClick = { menuExpanded = false; showAddPlayer = true },
                    enabled = tournament.groups.isNotEmpty()
                )
                DropdownMenuItem(
                    text = { Text("Add group") },
                    onClick = { menuExpanded = false; showAddGroup = true }
                )
                DropdownMenuItem(
                    text = { Text("Show viewer code") },
                    onClick = { menuExpanded = false; showRoomCode = true }
                )
                DropdownMenuItem(
                    text = { Text("Abandon tournament") },
                    onClick = { menuExpanded = false; showAbandonConfirm = true }
                )
            }
        }
    )

    if (showAddPlayer) {
        AddPlayerDialog(
            groups = tournament.groups,
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
        val roomId = RoomCode.forTournament(tournament.id)
        val account = viewModel.organizerAccount
        val payload = JoinPayload(room = RoomCode.encode(roomId), server = account?.serverUrl, pw = viewModel.readPassword)
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
            text = { Text("This deletes \"${tournament.name}\" from this device, including all recorded scores. This can't be undone.") },
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
