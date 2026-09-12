package com.example.nflunkyball.ui.organizer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.nflunkyball.model.Match
import com.example.nflunkyball.model.Team
import com.example.nflunkyball.model.TournamentFinishInfo
import com.example.nflunkyball.ui.shared.DropdownField
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
    val account by viewModel.organizerAccount.collectAsState()
    val teamNames = current.teams.associate { it.id to it.name }
    var pendingMatch by remember { mutableStateOf<Match?>(null) }
    var showAddMatch by remember { mutableStateOf(false) }
    var showUnlinkedWarning by remember { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { OrganizerTopBar("${current.name} — Bracket", viewModel, onOpenSettings) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier
                    .fillMaxSize()
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
                        if (account == null) showUnlinkedWarning = true else showFinishDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.lg)
                ) { Text("Finish Tournament") }
            }
            // Viewer emoji reactions (BLE mode only — server mode has no back-channel) float over
            // whatever the organizer is doing rather than needing their own screen.
            ReactionsOverlay(viewModel, modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.md))
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
            val options = teams.map { it.id to it.name }
            fun nameOf(id: String) = teams.firstOrNull { it.id == id }?.name ?: ""
            Column {
                DropdownField(label = "Team A", options = options, selectedLabel = nameOf(teamAId), onSelect = { teamAId = it })
                DropdownField(
                    label = "Team B",
                    options = options,
                    selectedLabel = nameOf(teamBId),
                    onSelect = { teamBId = it },
                    modifier = Modifier.padding(top = Spacing.sm)
                )
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
