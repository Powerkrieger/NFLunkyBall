package com.example.nflunkyball.ui.organizer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.example.nflunkyball.model.Match
import com.example.nflunkyball.model.Team
import com.example.nflunkyball.ui.shared.MatchList
import com.example.nflunkyball.ui.shared.MatchResultDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BracketScreen(viewModel: OrganizerViewModel, onFinish: () -> Unit, onAbandoned: () -> Unit) {
    val tournament by viewModel.tournament.collectAsState()
    val current = tournament ?: return
    val teamNames = current.teams.associate { it.id to it.name }
    var pendingMatch by remember { mutableStateOf<Match?>(null) }
    var showAddMatch by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { OrganizerTopBar("${current.name} — Bracket", current, viewModel, onAbandoned) }
    ) { padding ->
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        MatchList(
            matches = current.bracketMatches,
            teamNames = teamNames,
            onRecordResult = { match -> pendingMatch = match },
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(onClick = { showAddMatch = true }, modifier = Modifier.padding(top = 16.dp)) {
            Text("Add bracket match")
        }
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp)
        ) { Text("Finish Tournament") }
    }
    }

    pendingMatch?.let { match ->
        MatchResultDialog(
            match = match,
            teamNames = teamNames,
            onDismiss = { pendingMatch = null },
            onConfirm = { result ->
                viewModel.recordBracketMatchResult(match.id, result)
                pendingMatch = null
            }
        )
    }

    if (showAddMatch) {
        AddBracketMatchDialog(
            teams = current.teams,
            onDismiss = { showAddMatch = false },
            onConfirm = { teamAId, teamBId, roundLabel ->
                viewModel.addBracketMatch(teamAId, teamBId, roundLabel)
                showAddMatch = false
            }
        )
    }
}

@Composable
private fun AddBracketMatchDialog(
    teams: List<Team>,
    onDismiss: () -> Unit,
    onConfirm: (teamAId: String, teamBId: String, roundLabel: String) -> Unit
) {
    var teamAId by remember { mutableStateOf(teams.firstOrNull()?.id ?: "") }
    var teamBId by remember { mutableStateOf(teams.getOrNull(1)?.id ?: "") }
    var roundLabel by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add bracket match") },
        text = {
            Column {
                Text("Team A", style = MaterialTheme.typography.labelMedium)
                TeamPicker(teams, teamAId) { teamAId = it }
                Text(
                    "Team B",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                TeamPicker(teams, teamBId) { teamBId = it }
                OutlinedTextField(
                    value = roundLabel,
                    onValueChange = { roundLabel = it },
                    label = { Text("Round (e.g. Semifinal)") },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(teamAId, teamBId, roundLabel.ifBlank { "Bracket" }) },
                enabled = teamAId.isNotBlank() && teamBId.isNotBlank() && teamAId != teamBId
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun TeamPicker(teams: List<Team>, selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = teams.firstOrNull { it.id == selectedId }?.name ?: "Select team"
    TextButton(onClick = { expanded = true }) { Text(selectedName) }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        teams.forEach { team ->
            DropdownMenuItem(text = { Text(team.name) }, onClick = { onSelect(team.id); expanded = false })
        }
    }
}
