package com.example.nflunkyball.ui.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.nflunkyball.model.Match
import com.example.nflunkyball.ui.theme.Spacing

/**
 * [onRecordResult] is null for viewers (read-only) and a callback for the organizer, which is
 * what hides/shows the record/edit action — same composable serves both roles. Recording is
 * never final: a match with a result already gets an "Edit" button instead of "Record", so
 * typos can be corrected rather than being stuck.
 */
@Composable
fun MatchRow(
    match: Match,
    teamNames: Map<String, String>,
    onRecordResult: ((Match) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val teamAName = teamNames[match.teamAId] ?: match.teamAId
    val teamBName = teamNames[match.teamBId] ?: match.teamBId

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                match.roundLabel?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                Text("$teamAName vs $teamBName")
                match.result?.let { result ->
                    val winnerName = teamNames[result.winnerId] ?: result.winnerId
                    Text(
                        "$winnerName wins (${result.winnerScore})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (onRecordResult != null) {
                TextButton(onClick = { onRecordResult(match) }) {
                    Text(if (match.result == null) "Record" else "Edit")
                }
            }
        }
    }
}

@Composable
fun MatchList(
    matches: List<Match>,
    teamNames: Map<String, String>,
    onRecordResult: ((Match) -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        matches.forEach { match ->
            MatchRow(match = match, teamNames = teamNames, onRecordResult = onRecordResult)
        }
    }
}
