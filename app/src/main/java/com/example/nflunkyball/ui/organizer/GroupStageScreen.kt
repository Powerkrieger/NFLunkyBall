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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import com.example.nflunkyball.model.Match
import com.example.nflunkyball.model.standings
import com.example.nflunkyball.ui.shared.MatchList
import com.example.nflunkyball.ui.shared.MatchResultDialog
import com.example.nflunkyball.ui.shared.StandingsTable
import com.example.nflunkyball.ui.theme.Spacing

@Composable
fun GroupStageScreen(
    viewModel: OrganizerViewModel,
    onAdvanceToBracket: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val tournament by viewModel.tournament.collectAsState()
    val current = tournament ?: return
    val teamNames = current.teams.associate { it.id to it.name }
    var pendingMatch by remember { mutableStateOf<Pair<String, Match>?>(null) } // groupId to match

    Scaffold(
        topBar = { OrganizerTopBar(current.name, viewModel, onOpenSettings) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(Spacing.md)
                    .verticalScroll(rememberScrollState())
            ) {
                current.groups.forEach { group ->
                    Text(
                        group.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = Spacing.lg)
                    )
                    StandingsTable(
                        standings = group.standings(),
                        teamNames = teamNames,
                        modifier = Modifier.padding(top = Spacing.sm)
                    )
                    MatchList(
                        matches = group.matches,
                        teamNames = teamNames,
                        onRecordResult = { match -> pendingMatch = group.id to match },
                        modifier = Modifier.padding(top = Spacing.sm)
                    )
                }
                Button(
                    onClick = onAdvanceToBracket,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.lg)
                ) { Text(stringResource(R.string.group_stage_advance)) }
            }
            // Viewer emoji reactions (BLE mode only — server mode has no back-channel) float over
            // whatever the organizer is doing rather than needing their own screen.
            ReactionsOverlay(viewModel, modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.md))
        }
    }

    pendingMatch?.let { (groupId, match) ->
        MatchResultDialog(
            match = match,
            teams = current.teams.associateBy { it.id },
            knownDrinks = viewModel.knownDrinks(),
            existingDrinks = viewModel.drinksFor(match.id),
            onDismiss = { pendingMatch = null },
            onConfirm = { result, drinks ->
                viewModel.recordGroupMatchResult(groupId, match.id, result)
                viewModel.recordDrinks(match.id, drinks)
                pendingMatch = null
            },
            onClear = {
                viewModel.recordGroupMatchResult(groupId, match.id, null)
                pendingMatch = null
            }
        )
    }
}
