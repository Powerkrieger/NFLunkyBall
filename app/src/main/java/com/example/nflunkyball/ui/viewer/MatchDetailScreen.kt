package com.example.nflunkyball.ui.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.nflunkyball.server.CompetitorRef
import com.example.nflunkyball.server.MatchDetail
import com.example.nflunkyball.server.ServerResult
import com.example.nflunkyball.ui.theme.Spacing
import java.util.Locale

/** One archived match, with both players and the tournament as links. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailScreen(
    viewModel: ViewerViewModel,
    matchId: Int,
    onBack: () -> Unit,
    onOpenPlayer: (Int) -> Unit,
    onOpenTournament: (Int) -> Unit
) {
    var detail by remember { mutableStateOf<MatchDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(matchId) {
        detail = null
        error = null
        when (val result = viewModel.fetchMatchDetail(matchId)) {
            is ServerResult.Success -> detail = result.value
            is ServerResult.Failure -> error = result.message
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.let { "${it.competitorA.name} vs ${it.competitorB.name}" } ?: "Match") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(Spacing.md).verticalScroll(rememberScrollState())
        ) {
            val match = detail
            if (match == null) {
                Text(error ?: "Loading…", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }

            Text(
                buildString {
                    append(match.tournament.name)
                    match.roundLabel?.let { append(" · ").append(it) }
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onOpenTournament(match.tournament.id) }
            )
            Text(
                listOfNotNull(match.tournament.date.take(10), match.tournament.location?.takeIf { it.isNotBlank() })
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(Spacing.md))

            PlayerCard(
                player = match.competitorA,
                won = match.winner?.id == match.competitorA.id,
                drink = match.drinkA,
                eloBefore = match.eloBeforeA,
                eloAfter = match.eloAfterA,
                onOpen = { onOpenPlayer(match.competitorA.id) }
            )
            Spacer(Modifier.height(Spacing.sm))
            PlayerCard(
                player = match.competitorB,
                won = match.winner?.id == match.competitorB.id,
                drink = match.drinkB,
                eloBefore = match.eloBeforeB,
                eloAfter = match.eloAfterB,
                onOpen = { onOpenPlayer(match.competitorB.id) }
            )
            Spacer(Modifier.height(Spacing.md))

            Text("Result", style = MaterialTheme.typography.titleMedium)
            val winner = match.winner
            if (winner != null) {
                val loser = if (winner.id == match.competitorA.id) match.competitorB else match.competitorA
                Text("${winner.name} wins")
                match.winnerScore?.let { score ->
                    Text(
                        if (match.forfeit) "$score · forfeit by ${loser.name}"
                        else "$score seconds for ${loser.name} to finish their drink"
                    )
                }
            } else {
                Text("No result recorded")
            }
            Spacer(Modifier.height(Spacing.md))

            Text("Head to head", style = MaterialTheme.typography.titleMedium)
            val h2h = match.headToHead
            Text(
                "${match.competitorA.name} ${h2h.winsA} – ${h2h.winsB} ${match.competitorB.name} " +
                    "over ${h2h.matches} ${if (h2h.matches == 1) "match" else "matches"}"
            )
        }
    }
}

@Composable
private fun PlayerCard(
    player: CompetitorRef,
    won: Boolean,
    drink: String?,
    eloBefore: Double?,
    eloAfter: Double?,
    onOpen: () -> Unit
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (won) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Row(Modifier.fillMaxWidth()) {
                Text(player.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(if (won) "Winner" else "Loser", style = MaterialTheme.typography.labelMedium)
            }
            if (eloBefore != null && eloAfter != null) {
                val delta = eloAfter - eloBefore
                val sign = if (delta >= 0) "+" else ""
                Text(
                    "Elo ${"%.1f".format(Locale.US, eloBefore)} → ${"%.1f".format(Locale.US, eloAfter)} " +
                        "($sign${"%.1f".format(Locale.US, delta)})",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            drink?.takeIf { it.isNotBlank() }?.let { Text("Drink: $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
