package com.example.nflunkyball.ui.organizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.nflunkyball.model.Group
import com.example.nflunkyball.model.Team
import com.example.nflunkyball.ui.shared.RenameDialog
import com.example.nflunkyball.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePlayersScreen(viewModel: OrganizerViewModel, onBack: () -> Unit) {
    val tournament by viewModel.tournament.collectAsState()
    val current = tournament ?: return

    var newPlayerName by remember { mutableStateOf("") }
    var newPlayerGroupId by remember { mutableStateOf(current.groups.firstOrNull()?.id ?: "") }
    var renamingTeam by remember { mutableStateOf<Team?>(null) }

    fun groupNameFor(teamId: String): String? = current.groups.firstOrNull { teamId in it.teamIds }?.name

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage players") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(Spacing.md)) {
            if (current.groups.isEmpty()) {
                Text(
                    "Add a group first (see Manage groups) before adding players.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                OutlinedTextField(
                    value = newPlayerName,
                    onValueChange = { newPlayerName = it },
                    label = {
                        Text(
                            if (current.squadSize > 1) "Team members (${current.squadSize}, comma-separated)"
                            else "Player name"
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (current.groups.size > 1) {
                    GroupDropdownField(
                        groups = current.groups,
                        selectedId = newPlayerGroupId,
                        onSelect = { newPlayerGroupId = it },
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
                    )
                }
                Button(
                    onClick = {
                        val groupId = newPlayerGroupId.ifBlank { current.groups.first().id }
                        viewModel.addPlayer(groupId, newPlayerName)
                        newPlayerName = ""
                    },
                    enabled = newPlayerName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
                ) { Text("Add") }
            }

            HorizontalDivider(Modifier.padding(vertical = Spacing.md))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(current.teams, key = { it.id }) { team ->
                    val removable = viewModel.canRemovePlayer(team.id)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(team.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    groupNameFor(team.id) ?: "No group",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (!removable) {
                                    Text(
                                        "Already has recorded results — can't remove",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            IconButton(onClick = { renamingTeam = team }) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename ${team.name}")
                            }
                            IconButton(onClick = { viewModel.removePlayer(team.id) }, enabled = removable) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove ${team.name}")
                            }
                        }
                    }
                }
            }
        }
    }

    renamingTeam?.let { team ->
        RenameDialog(
            title = "Rename player",
            initialValue = team.name,
            onDismiss = { renamingTeam = null },
            onConfirm = { newName ->
                viewModel.renamePlayer(team.id, newName)
                renamingTeam = null
            }
        )
    }
}

/** Exposed dropdown (a real dropdown-styled field with a trailing arrow) rather than a plain
 *  text-button-triggered menu — the latter (still used in [BracketScreen]'s team picker) gave no
 *  visual hint it was tappable at all. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupDropdownField(
    groups: List<Group>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = groups.firstOrNull { it.id == selectedId }?.name ?: groups.first().name

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Group") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            groups.forEach { group ->
                DropdownMenuItem(text = { Text(group.name) }, onClick = { onSelect(group.id); expanded = false })
            }
        }
    }
}
