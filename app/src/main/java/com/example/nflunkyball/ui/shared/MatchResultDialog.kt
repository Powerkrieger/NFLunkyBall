package com.example.nflunkyball.ui.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nflunkyball.model.Match
import com.example.nflunkyball.model.MatchResult

@Composable
fun MatchResultDialog(
    match: Match,
    teamNames: Map<String, String>,
    onDismiss: () -> Unit,
    onConfirm: (MatchResult) -> Unit
) {
    var winnerId by remember { mutableStateOf(match.result?.winnerId ?: match.teamAId) }
    var scoreText by remember { mutableStateOf(match.result?.winnerScore?.toString() ?: "") }
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
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = scoreText,
                    onValueChange = { scoreText = it.filter(Char::isDigit) },
                    label = { Text("Winner's score (seconds, 300 = forfeit)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(MatchResult(winnerId = winnerId, winnerScore = score!!)) },
                enabled = score != null
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
