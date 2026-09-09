package com.example.nflunkyball.ui.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nflunkyball.model.Match

/**
 * [onRecordResult] is null for viewers (read-only) and a callback for the organizer, which is
 * what hides/shows the "Record" action — same composable serves both roles.
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

    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                match.roundLabel?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                Text("$teamAName vs $teamBName")
                match.result?.let { result ->
                    val winnerName = teamNames[result.winnerId] ?: result.winnerId
                    Text(
                        "$winnerName wins (${result.winnerScore})",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (match.result == null && onRecordResult != null) {
                TextButton(onClick = { onRecordResult(match) }) { Text("Record") }
            }
        }
        HorizontalDivider(Modifier.padding(top = 6.dp))
    }
}

@Composable
fun MatchList(
    matches: List<Match>,
    teamNames: Map<String, String>,
    onRecordResult: ((Match) -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        matches.forEach { match ->
            MatchRow(match = match, teamNames = teamNames, onRecordResult = onRecordResult)
        }
    }
}
