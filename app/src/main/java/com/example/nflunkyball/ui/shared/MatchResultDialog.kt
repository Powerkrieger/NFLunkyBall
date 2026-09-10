package com.example.nflunkyball.ui.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.nflunkyball.model.Match
import com.example.nflunkyball.model.MatchResult
import com.example.nflunkyball.ui.theme.Spacing

@Composable
fun MatchResultDialog(
    match: Match,
    teamNames: Map<String, String>,
    knownDrinks: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (MatchResult, drink: String) -> Unit
) {
    var winnerId by remember { mutableStateOf(match.result?.winnerId ?: match.teamAId) }
    var scoreText by remember { mutableStateOf(match.result?.winnerScore?.toString() ?: "") }
    var drink by remember { mutableStateOf("") }
    val score = scoreText.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (match.result == null) "Record result" else "Edit result") },
        text = {
            Column {
                Text("Winner", style = MaterialTheme.typography.labelMedium)
                listOf(match.teamAId, match.teamBId).forEach { teamId ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = winnerId == teamId, onClick = { winnerId = teamId })
                        Text(teamNames[teamId] ?: teamId)
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = scoreText,
                    onValueChange = { scoreText = it.filter(Char::isDigit) },
                    label = { Text("Winner's score (seconds, 300 = forfeit)") },
                    singleLine = true
                )
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = drink,
                    onValueChange = { drink = it },
                    // Deliberately not synced anywhere live — see MatchDrinkStore's doc for why
                    // this stays organizer-only until the tournament is finished and uploaded.
                    label = { Text("Drink (optional, organizer-only)") },
                    singleLine = true
                )
                if (knownDrinks.isNotEmpty()) {
                    LazyRow(Modifier.padding(top = Spacing.xs)) {
                        items(knownDrinks) { name ->
                            SuggestionChip(
                                onClick = { drink = name },
                                label = { Text(name) },
                                modifier = Modifier.padding(end = Spacing.sm)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(MatchResult(winnerId = winnerId, winnerScore = score!!), drink) },
                enabled = score != null
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
