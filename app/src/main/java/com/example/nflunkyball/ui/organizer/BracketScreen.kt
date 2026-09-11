package com.example.nflunkyball.ui.organizer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import com.example.nflunkyball.model.Match
import com.example.nflunkyball.model.Team
import com.example.nflunkyball.model.TournamentFinishInfo
import com.example.nflunkyball.ui.shared.MatchList
import com.example.nflunkyball.ui.shared.MatchResultDialog
import com.example.nflunkyball.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BracketScreen(
    viewModel: OrganizerViewModel,
    onFinish: (TournamentFinishInfo) -> Unit,
    onOpenSettings: () -> Unit,
    onLinkAccount: () -> Unit
) {
    val tournament by viewModel.tournament.collectAsState()
    val current = tournament ?: return
    val teamNames = current.teams.associate { it.id to it.name }
    var pendingMatch by remember { mutableStateOf<Match?>(null) }
    var showAddMatch by remember { mutableStateOf(false) }
    var showUnlinkedWarning by remember { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { OrganizerTopBar("${current.name} — Bracket", viewModel, onOpenSettings) }
    ) { padding ->
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(Spacing.md)
            .verticalScroll(rememberScrollState())
    ) {
        MatchList(
            matches = current.bracketMatches,
            teamNames = teamNames,
            onRecordResult = { match -> pendingMatch = match },
            modifier = Modifier.padding(top = Spacing.sm)
        )
        Button(onClick = { showAddMatch = true }, modifier = Modifier.padding(top = Spacing.md)) {
            Text("Add bracket match")
        }
        Button(
            onClick = {
                // Finishing clears the local copy right after (see MainActivity's onFinish) —
                // without a linked account nothing was ever uploaded, so that would silently
                // lose the whole tournament unless the organizer explicitly says that's fine.
                if (viewModel.organizerAccount == null) showUnlinkedWarning = true else showFinishDialog = true
            },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.lg)
        ) { Text("Finish Tournament") }
    }
    }

    if (showUnlinkedWarning) {
        AlertDialog(
            onDismissRequest = { showUnlinkedWarning = false },
            title = { Text("Not linked to a server") },
            text = {
                Text(
                    "This tournament isn't linked to an organizer account, so it can't be saved " +
                        "to history. Link an account now to save it, or finish without saving."
                )
            },
            confirmButton = {
                TextButton(onClick = { showUnlinkedWarning = false; onLinkAccount() }) {
                    Text("Link account")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnlinkedWarning = false
                    // Nothing will actually be uploaded (no linked account), so there's no point
                    // asking for referees/location/comment first — they'd just be discarded.
                    onFinish(TournamentFinishInfo(System.currentTimeMillis(), "", "", ""))
                }) {
                    Text("Finish without saving")
                }
            }
        )
    }

    if (showFinishDialog) {
        FinishTournamentDialog(
            onDismiss = { showFinishDialog = false },
            onConfirm = { info -> showFinishDialog = false; onFinish(info) }
        )
    }

    pendingMatch?.let { match ->
        MatchResultDialog(
            match = match,
            teams = current.teams.associateBy { it.id },
            knownDrinks = viewModel.knownDrinks(),
            existingDrinks = viewModel.drinksFor(match.id),
            onDismiss = { pendingMatch = null },
            onConfirm = { result, drinks ->
                viewModel.recordBracketMatchResult(match.id, result)
                viewModel.recordDrinks(match.id, drinks)
                pendingMatch = null
            },
            onClear = {
                viewModel.recordBracketMatchResult(match.id, null)
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
                TeamPicker(teams, teamAId, label = "Team A") { teamAId = it }
                TeamPicker(
                    teams,
                    teamBId,
                    label = "Team B",
                    modifier = Modifier.padding(top = Spacing.sm)
                ) { teamBId = it }
                OutlinedTextField(
                    value = roundLabel,
                    onValueChange = { roundLabel = it },
                    label = { Text("Round (e.g. Semifinal)") },
                    modifier = Modifier.padding(top = Spacing.sm)
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

/** Exposed dropdown (real trailing-arrow field, not a plain unstyled button) — see
 *  [ManagePlayersScreen]'s equivalent group picker for why the previous TextButton-triggered
 *  menu here gave no visual hint it was tappable at all. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamPicker(
    teams: List<Team>,
    selectedId: String,
    label: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = teams.firstOrNull { it.id == selectedId }?.name ?: ""

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            teams.forEach { team ->
                DropdownMenuItem(text = { Text(team.name) }, onClick = { onSelect(team.id); expanded = false })
            }
        }
    }
}
