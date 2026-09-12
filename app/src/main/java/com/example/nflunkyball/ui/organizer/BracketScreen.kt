package com.example.nflunkyball.ui.organizer

import androidx.compose.ui.res.stringResource
import com.example.nflunkyball.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.nflunkyball.model.standings
import com.example.nflunkyball.model.suggestedFinalStandings
import com.example.nflunkyball.ui.shared.DropdownField
import com.example.nflunkyball.ui.shared.MatchList
import com.example.nflunkyball.ui.shared.MatchResultDialog
import com.example.nflunkyball.ui.shared.StandingsTable
import com.example.nflunkyball.ui.theme.Spacing

@Composable
fun BracketScreen(
    viewModel: OrganizerViewModel,
    onFinish: (TournamentFinishInfo) -> Unit,
    onOpenSettings: () -> Unit,
    onLinkAccount: () -> Unit,
    onManageGroups: () -> Unit
) {
    val tournament by viewModel.tournament.collectAsState()
    val current = tournament ?: return
    val account by viewModel.organizerAccount.collectAsState()
    val teamNames = current.teams.associate { it.id to it.name }
    var pendingMatch by remember { mutableStateOf<Match?>(null) }
    var pendingGroupMatch by remember { mutableStateOf<Pair<String, Match>?>(null) } // groupId to match
    var showAddMatch by remember { mutableStateOf(false) }
    var showUnlinkedWarning by remember { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { OrganizerTopBar(stringResource(R.string.bracket_title, current.name), viewModel, onOpenSettings) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(Spacing.md)
                    .verticalScroll(rememberScrollState())
            ) {
                // Groups played during the bracket (a consolation group, say) are scored here,
                // plus any group-stage group with matches still open when the bracket started.
                current.groups.filter { g -> g.knockoutStage || g.matches.any { it.result == null } }.forEach { group ->
                    Text(group.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.md))
                    StandingsTable(standings = group.standings(), teamNames = teamNames, modifier = Modifier.padding(top = Spacing.sm))
                    MatchList(
                        matches = group.matches,
                        teamNames = teamNames,
                        onRecordResult = { match -> pendingGroupMatch = group.id to match },
                        modifier = Modifier.padding(top = Spacing.sm)
                    )
                }
                if (current.bracketMatches.isNotEmpty() || current.groups.any { it.knockoutStage }) {
                    Text(stringResource(R.string.bracket_matches_heading), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.md))
                }
                MatchList(
                    matches = current.bracketMatches,
                    teamNames = teamNames,
                    onRecordResult = { match -> pendingMatch = match },
                    modifier = Modifier.padding(top = Spacing.sm)
                )
                Button(onClick = { showAddMatch = true }, modifier = Modifier.padding(top = Spacing.md)) {
                    Text(stringResource(R.string.bracket_add_match))
                }
                OutlinedButton(onClick = onManageGroups, modifier = Modifier.padding(top = Spacing.sm)) {
                    Text(stringResource(R.string.bracket_add_group))
                }
                Button(
                    onClick = {
                        // Finishing clears the local copy right after (see MainActivity's onFinish) —
                        // without a linked account nothing was ever uploaded, so that would silently
                        // lose the whole tournament unless the organizer explicitly says that's fine.
                        if (account == null) showUnlinkedWarning = true else showFinishDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.lg)
                ) { Text(stringResource(R.string.bracket_finish)) }
            }
            // Viewer emoji reactions (BLE mode only — server mode has no back-channel) float over
            // whatever the organizer is doing rather than needing their own screen.
            ReactionsOverlay(viewModel, modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.md))
        }
    }

    if (showUnlinkedWarning) {
        AlertDialog(
            onDismissRequest = { showUnlinkedWarning = false },
            title = { Text(stringResource(R.string.bracket_unlinked_title)) },
            text = {
                Text(stringResource(R.string.bracket_unlinked_body))
            },
            confirmButton = {
                TextButton(onClick = { showUnlinkedWarning = false; onLinkAccount() }) {
                    Text(stringResource(R.string.bracket_link_account))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnlinkedWarning = false
                    // Nothing will actually be uploaded (no linked account), so there's no point
                    // asking for referees/location/comment first — they'd just be discarded.
                    onFinish(TournamentFinishInfo(System.currentTimeMillis(), "", "", ""))
                }) {
                    Text(stringResource(R.string.bracket_finish_without_saving))
                }
            }
        )
    }

    if (showFinishDialog) {
        FinishTournamentDialog(
            teamNames = teamNames,
            suggestedStandings = current.suggestedFinalStandings(),
            onDismiss = { showFinishDialog = false },
            onConfirm = { info -> showFinishDialog = false; onFinish(info) }
        )
    }

    pendingGroupMatch?.let { (groupId, match) ->
        MatchResultDialog(
            match = match,
            teams = current.teams.associateBy { it.id },
            knownDrinks = viewModel.knownDrinks(),
            existingDrinks = viewModel.drinksFor(match.id),
            onDismiss = { pendingGroupMatch = null },
            onConfirm = { result, drinks ->
                viewModel.recordGroupMatchResult(groupId, match.id, result)
                viewModel.recordDrinks(match.id, drinks)
                pendingGroupMatch = null
            },
            onClear = {
                viewModel.recordGroupMatchResult(groupId, match.id, null)
                pendingGroupMatch = null
            }
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
        title = { Text(stringResource(R.string.bracket_add_match)) },
        text = {
            val options = teams.map { it.id to it.name }
            fun nameOf(id: String) = teams.firstOrNull { it.id == id }?.name ?: ""
            Column {
                DropdownField(label = stringResource(R.string.bracket_team_a), options = options, selectedLabel = nameOf(teamAId), onSelect = { teamAId = it })
                DropdownField(
                    label = stringResource(R.string.bracket_team_b),
                    options = options,
                    selectedLabel = nameOf(teamBId),
                    onSelect = { teamBId = it },
                    modifier = Modifier.padding(top = Spacing.sm)
                )
                OutlinedTextField(
                    value = roundLabel,
                    onValueChange = { roundLabel = it },
                    label = { Text(stringResource(R.string.bracket_round_label)) },
                    modifier = Modifier.padding(top = Spacing.sm)
                )
            }
        },
        confirmButton = {
            val defaultRound = stringResource(R.string.bracket_default_round)
            TextButton(
                onClick = { onConfirm(teamAId, teamBId, roundLabel.ifBlank { defaultRound }) },
                enabled = teamAId.isNotBlank() && teamBId.isNotBlank() && teamAId != teamBId
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
