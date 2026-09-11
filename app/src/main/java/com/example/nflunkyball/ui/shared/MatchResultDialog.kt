package com.example.nflunkyball.ui.shared

import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.nflunkyball.model.Match
import com.example.nflunkyball.model.MatchResult
import com.example.nflunkyball.ui.theme.Spacing
import kotlinx.coroutines.delay

@Composable
fun MatchResultDialog(
    match: Match,
    teamNames: Map<String, String>,
    knownDrinks: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (MatchResult, drinkA: String, drinkB: String) -> Unit,
    onClear: (() -> Unit)? = null
) {
    var winnerId by remember { mutableStateOf(match.result?.winnerId ?: match.teamAId) }
    var scoreText by remember { mutableStateOf(match.result?.winnerScore?.toString() ?: "") }
    var drinkA by remember { mutableStateOf("") }
    var drinkB by remember { mutableStateOf("") }
    val score = scoreText.toIntOrNull()

    // Simple start/stop stopwatch: winnerScore is already defined as elapsed seconds (see
    // MatchResult's doc comment), so stopping it fills that field directly instead of the
    // organizer eyeballing a separate clock. Never persisted — resets whenever the dialog reopens.
    var timerStartMs by remember { mutableStateOf<Long?>(null) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(timerStartMs) {
        val startedAt = timerStartMs ?: return@LaunchedEffect
        while (true) {
            elapsedSeconds = (SystemClock.elapsedRealtime() - startedAt) / 1000
            delay(200)
        }
    }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        val running = timerStartMs != null
                        if (running) {
                            scoreText = elapsedSeconds.toString()
                            timerStartMs = null
                        } else {
                            elapsedSeconds = 0L
                            timerStartMs = SystemClock.elapsedRealtime()
                        }
                    }) { Text(if (timerStartMs != null) "Stop timer" else "Start timer") }
                    if (timerStartMs != null) {
                        Text("${elapsedSeconds}s", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = scoreText,
                    onValueChange = { scoreText = it.filter(Char::isDigit) },
                    label = { Text("Winner's score (seconds, 300 = forfeit)") },
                    singleLine = true
                )
                Spacer(Modifier.height(Spacing.sm))
                DrinkField(
                    label = "${teamNames[match.teamAId] ?: match.teamAId}'s drink",
                    value = drinkA,
                    onValueChange = { drinkA = it },
                    knownDrinks = knownDrinks
                )
                Spacer(Modifier.height(Spacing.sm))
                DrinkField(
                    label = "${teamNames[match.teamBId] ?: match.teamBId}'s drink",
                    value = drinkB,
                    onValueChange = { drinkB = it },
                    knownDrinks = knownDrinks
                )
                // A third dialog action doesn't fit AlertDialog's confirm/dismiss slots, so it
                // lives in the body instead — only offered once there's actually a result to undo.
                if (match.result != null && onClear != null) {
                    TextButton(
                        onClick = onClear,
                        modifier = Modifier.padding(top = Spacing.sm),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Clear result (undo)") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(MatchResult(winnerId = winnerId, winnerScore = score!!), drinkA, drinkB) },
                enabled = score != null
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DrinkField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    knownDrinks: List<String>
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            // Deliberately not synced anywhere live — see MatchDrinkStore's doc for why this
            // stays organizer-only until the tournament is finished and uploaded.
            label = { Text("$label (optional, organizer-only)") },
            singleLine = true
        )
        if (knownDrinks.isNotEmpty()) {
            LazyRow(Modifier.padding(top = Spacing.xs)) {
                items(knownDrinks) { name ->
                    SuggestionChip(
                        onClick = { onValueChange(name) },
                        label = { Text(name) },
                        modifier = Modifier.padding(end = Spacing.sm)
                    )
                }
            }
        }
    }
}
