package com.example.nflunkyball.ui.organizer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.nflunkyball.model.Match
import com.example.nflunkyball.model.standings
import com.example.nflunkyball.ui.shared.MatchList
import com.example.nflunkyball.ui.shared.MatchResultDialog
import com.example.nflunkyball.ui.shared.StandingsTable
import com.example.nflunkyball.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
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
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
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
        ) { Text("Advance to Bracket") }
    }
    }

    pendingMatch?.let { (groupId, match) ->
        MatchResultDialog(
            match = match,
            teamNames = teamNames,
            knownDrinks = viewModel.knownDrinks(),
            onDismiss = { pendingMatch = null },
            onConfirm = { result, drink ->
                viewModel.recordGroupMatchResult(groupId, match.id, result)
                viewModel.recordDrink(match.id, drink)
                pendingMatch = null
            }
        )
    }
}
