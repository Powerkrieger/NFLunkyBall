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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.nflunkyball.model.Group
import com.example.nflunkyball.ui.shared.RenameDialog
import com.example.nflunkyball.ui.shared.BackTopBar
import com.example.nflunkyball.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageGroupsScreen(viewModel: OrganizerViewModel, onBack: () -> Unit) {
    val tournament by viewModel.tournament.collectAsState()
    val current = tournament ?: return

    var newGroupName by remember { mutableStateOf("") }
    var renamingGroup by remember { mutableStateOf<Group?>(null) }

    Scaffold(
        topBar = {
            BackTopBar(title = "Manage groups", onBack = onBack)
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text("Group name") },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { viewModel.addGroup(newGroupName); newGroupName = "" },
                    enabled = newGroupName.isNotBlank(),
                    modifier = Modifier.padding(start = Spacing.sm)
                ) { Text("Add") }
            }

            HorizontalDivider(Modifier.padding(vertical = Spacing.md))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(current.groups, key = { it.id }) { group ->
                    val removable = viewModel.canRemoveGroup(group.id)
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
                                Text(group.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${group.teamIds.size} player" + if (group.teamIds.size == 1) "" else "s",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (!removable) {
                                    Text(
                                        "Has players — remove them first",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            IconButton(onClick = { renamingGroup = group }) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename ${group.name}")
                            }
                            IconButton(onClick = { viewModel.removeGroup(group.id) }, enabled = removable) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove ${group.name}")
                            }
                        }
                    }
                }
            }
        }
    }

    renamingGroup?.let { group ->
        RenameDialog(
            title = "Rename group",
            initialValue = group.name,
            onDismiss = { renamingGroup = null },
            onConfirm = { newName ->
                viewModel.renameGroup(group.id, newName)
                renamingGroup = null
            }
        )
    }
}
