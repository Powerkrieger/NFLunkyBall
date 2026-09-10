package com.example.nflunkyball.ui.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nflunkyball.model.TeamStanding
import com.example.nflunkyball.ui.theme.Spacing

@Composable
fun StandingsTable(
    standings: List<TeamStanding>,
    teamNames: Map<String, String>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Row(Modifier.padding(vertical = Spacing.xs)) {
                Text("Team", Modifier.weight(2f), style = MaterialTheme.typography.labelMedium)
                Text("W", Modifier.width(32.dp), style = MaterialTheme.typography.labelMedium)
                Text("L", Modifier.width(32.dp), style = MaterialTheme.typography.labelMedium)
            }
            HorizontalDivider()
            standings.forEach { standing ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    Text(teamNames[standing.teamId] ?: standing.teamId, Modifier.weight(2f))
                    Text(standing.wins.toString(), Modifier.width(32.dp))
                    Text(standing.losses.toString(), Modifier.width(32.dp))
                }
            }
        }
    }
}
