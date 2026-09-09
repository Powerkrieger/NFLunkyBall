package com.example.nflunkyball.ui.organizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nflunkyball.model.Team
import java.util.UUID

@Composable
fun SetupScreen(
    viewModel: OrganizerViewModel,
    onStart: (name: String, teams: List<Team>, groupAssignments: Map<String, List<String>>) -> Unit
) {
    LaunchedEffect(Unit) { viewModel.loadKnownCompetitors() }

    var tournamentName by remember { mutableStateOf("") }
    var teamNameInput by remember { mutableStateOf("") }
    var groupNameInput by remember { mutableStateOf("") }
    val teams = remember { mutableStateListOf<Team>() }
    val groupNames = remember { mutableStateListOf<String>() }
    val assignments = remember { mutableStateMapOf<String, String>() } // teamId -> groupName

    fun addTeam(name: String) {
        if (name.isNotBlank() && teams.none { it.name.equals(name, ignoreCase = true) }) {
            teams.add(Team(id = UUID.randomUUID().toString(), name = name.trim()))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("New tournament", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = tournamentName,
            onValueChange = { tournamentName = it },
            label = { Text("Tournament name") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )

        Text("Teams", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = teamNameInput,
                onValueChange = { teamNameInput = it },
                label = { Text("Team name") },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { addTeam(teamNameInput); teamNameInput = "" },
                modifier = Modifier.padding(start = 8.dp)
            ) { Text("Add") }
        }

        val addedNames = teams.map { it.name.lowercase() }.toSet()
        val suggestions = viewModel.knownCompetitorNames.filter { it.lowercase() !in addedNames }
        if (suggestions.isNotEmpty()) {
            Text(
                "Known players",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            LazyRow(Modifier.padding(top = 4.dp)) {
                items(suggestions) { name ->
                    SuggestionChip(
                        onClick = { addTeam(name) },
                        label = { Text(name) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        }

        teams.forEach { team ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(team.name, Modifier.weight(1f))
                var expanded by remember { mutableStateOf(false) }
                TextButton(onClick = { expanded = true }) {
                    Text(assignments[team.id] ?: "Assign group")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    groupNames.forEach { groupName ->
                        DropdownMenuItem(
                            text = { Text(groupName) },
                            onClick = {
                                assignments[team.id] = groupName
                                expanded = false
                            }
                        )
                    }
                }
                TextButton(onClick = { teams.remove(team); assignments.remove(team.id) }) {
                    Text("✕")
                }
            }
        }

        Text("Groups", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = groupNameInput,
                onValueChange = { groupNameInput = it },
                label = { Text("Group name") },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val trimmed = groupNameInput.trim()
                    if (trimmed.isNotBlank() && trimmed !in groupNames) {
                        groupNames.add(trimmed)
                        groupNameInput = ""
                    }
                },
                modifier = Modifier.padding(start = 8.dp)
            ) { Text("Add") }
        }
        groupNames.forEach { name -> Text("• $name", Modifier.padding(vertical = 2.dp)) }

        val allAssigned = teams.isNotEmpty() && teams.all { assignments.containsKey(it.id) }
        Button(
            onClick = {
                val grouped = groupNames.associateWith { groupName ->
                    teams.filter { assignments[it.id] == groupName }.map { it.id }
                }
                onStart(tournamentName.ifBlank { "Flunkyball Tournament" }, teams.toList(), grouped)
            },
            enabled = tournamentName.isNotBlank() && allAssigned,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp)
        ) { Text("Start Group Stage") }
    }
}
