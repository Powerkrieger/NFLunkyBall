package com.example.nflunkyball.ui.organizer

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.nflunkyball.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.AssistChip
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.CardDefaults
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
import com.example.nflunkyball.ui.shared.DropdownField
import com.example.nflunkyball.ui.shared.RenameDialog
import com.example.nflunkyball.ui.shared.BackTopBar
import com.example.nflunkyball.ui.theme.Spacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManageGroupsScreen(viewModel: OrganizerViewModel, onBack: () -> Unit) {
    val tournament by viewModel.tournament.collectAsState()
    val current = tournament ?: return
    val teamNames = current.teams.associate { it.id to it.name }

    var newGroupName by remember { mutableStateOf("") }
    var renamingGroup by remember { mutableStateOf<Group?>(null) }
    var addingTeamTo by remember { mutableStateOf<Group?>(null) }

    Scaffold(
        topBar = {
            BackTopBar(title = stringResource(R.string.groups_title), onBack = onBack)
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text(stringResource(R.string.group_name)) },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { viewModel.addGroup(newGroupName); newGroupName = "" },
                    enabled = newGroupName.isNotBlank(),
                    modifier = Modifier.padding(start = Spacing.sm)
                ) { Text(stringResource(R.string.action_add)) }
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
                                    pluralStringResource(R.plurals.group_player_count, group.teamIds.size, group.teamIds.size) +
                                        if (group.knockoutStage) " · " + stringResource(R.string.group_knockout_stage) else "",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (!removable) {
                                    Text(
                                        stringResource(R.string.group_has_players),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            IconButton(onClick = { renamingGroup = group }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rename_named, group.name))
                            }
                            IconButton(onClick = { viewModel.removeGroup(group.id) }, enabled = removable) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove_named, group.name))
                            }
                        }
                        // Rounds: how often every pairing is played. Locked once anything in the
                        // group has a result, since changing it regenerates the schedule.
                        Row(
                            Modifier.padding(horizontal = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.group_rounds, group.rounds), style = MaterialTheme.typography.bodyMedium)
                            TextButton(
                                onClick = { viewModel.setGroupRounds(group.id, group.rounds - 1) },
                                enabled = !group.hasResults && group.rounds > 1
                            ) { Text("−") }
                            TextButton(
                                onClick = { viewModel.setGroupRounds(group.id, group.rounds + 1) },
                                enabled = !group.hasResults
                            ) { Text("+") }
                            if (group.hasResults) {
                                Text(stringResource(R.string.group_rounds_locked), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        // Members: existing teams can be put into (or taken out of) any group — how a
                        // consolation group is filled from the teams knocked out of the group stage.
                        FlowRow(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
                            group.teamIds.forEach { teamId ->
                                val name = teamNames[teamId] ?: teamId
                                val canRemove = viewModel.canRemoveTeamFromGroup(group.id, teamId)
                                AssistChip(
                                    onClick = { if (canRemove) viewModel.removeTeamFromGroup(group.id, teamId) },
                                    label = { Text(name) },
                                    enabled = canRemove,
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove_named, name)) },
                                    modifier = Modifier.padding(end = Spacing.xs)
                                )
                            }
                            if (current.teams.any { it.id !in group.teamIds }) {
                                AssistChip(
                                    onClick = { addingTeamTo = group },
                                    label = { Text(stringResource(R.string.group_add_existing_team)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    addingTeamTo?.let { group ->
        val candidates = current.teams.filter { it.id !in group.teamIds }
        var teamId by remember(group.id) { mutableStateOf(candidates.firstOrNull()?.id ?: "") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { addingTeamTo = null },
            title = { Text(stringResource(R.string.group_add_existing_team_title, group.name)) },
            text = {
                DropdownField(
                    label = stringResource(R.string.players_group_label),
                    options = candidates.map { it.id to it.name },
                    selectedLabel = candidates.firstOrNull { it.id == teamId }?.name ?: "",
                    onSelect = { teamId = it }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.addTeamToGroup(group.id, teamId); addingTeamTo = null },
                    enabled = teamId.isNotBlank()
                ) { Text(stringResource(R.string.action_add)) }
            },
            dismissButton = { TextButton(onClick = { addingTeamTo = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    renamingGroup?.let { group ->
        RenameDialog(
            title = stringResource(R.string.group_rename_title),
            initialValue = group.name,
            onDismiss = { renamingGroup = null },
            onConfirm = { newName ->
                viewModel.renameGroup(group.id, newName)
                renamingGroup = null
            }
        )
    }
}
