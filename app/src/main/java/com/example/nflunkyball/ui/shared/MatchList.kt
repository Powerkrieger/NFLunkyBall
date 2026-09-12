package com.example.nflunkyball.ui.shared

import androidx.compose.ui.res.stringResource
import com.example.nflunkyball.R
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
 *
 * [onOpen] makes the whole row tappable (an archived tournament's rows link to the match's
 * detail page); null everywhere the match has no server-side record to open.
 */
@Composable
fun MatchRow(
    match: Match,
    teamNames: Map<String, String>,
    onRecordResult: ((Match) -> Unit)?,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null
) {
    val teamAName = teamNames[match.teamAId] ?: match.teamAId
    val teamBName = teamNames[match.teamBId] ?: match.teamBId
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)

    if (onOpen != null) {
        Card(onClick = onOpen, modifier = modifier.fillMaxWidth(), colors = colors) {
            MatchRowContent(match, teamAName, teamBName, teamNames, onRecordResult)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = colors) {
            MatchRowContent(match, teamAName, teamBName, teamNames, onRecordResult)
        }
    }
}

@Composable
private fun MatchRowContent(
    match: Match,
    teamAName: String,
    teamBName: String,
    teamNames: Map<String, String>,
    onRecordResult: ((Match) -> Unit)?
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            match.roundLabel?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            Text(stringResource(R.string.match_versus, teamAName, teamBName))
            match.result?.let { result ->
                val winnerName = teamNames[result.winnerId] ?: result.winnerId
                Text(
                    stringResource(R.string.match_winner_line, winnerName, result.winnerScore),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (onRecordResult != null) {
            TextButton(onClick = { onRecordResult(match) }) {
                Text(stringResource(if (match.result == null) R.string.match_record else R.string.match_edit))
            }
        }
    }
}

/** [onOpenMatch] returns null for a match that has nothing to open (see [MatchRow.onOpen]). */
@Composable
fun MatchList(
    matches: List<Match>,
    teamNames: Map<String, String>,
    onRecordResult: ((Match) -> Unit)?,
    modifier: Modifier = Modifier,
    onOpenMatch: ((Match) -> (() -> Unit)?)? = null
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        matches.forEach { match ->
            MatchRow(
                match = match,
                teamNames = teamNames,
                onRecordResult = onRecordResult,
                onOpen = onOpenMatch?.invoke(match)
            )
        }
    }
}
