package com.example.nflunkyball.ui.viewer

import com.example.nflunkyball.ui.text
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.nflunkyball.R
import androidx.compose.runtime.getValue
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.example.nflunkyball.server.MatchPlayer
import com.example.nflunkyball.ui.shared.BackTopBar
import com.example.nflunkyball.ui.shared.format1
import com.example.nflunkyball.ui.shared.formatSigned1
import com.example.nflunkyball.ui.LoadState
import com.example.nflunkyball.ui.valueOrNull
import androidx.compose.runtime.collectAsState
import com.example.nflunkyball.ui.theme.Spacing

/** One archived match: both sides player by player, with every player and the tournament as links. */
@Composable
fun MatchDetailScreen(
    viewModel: ViewerViewModel,
    matchId: Int,
    onBack: () -> Unit,
    onOpenPlayer: (Int) -> Unit,
    onOpenTournament: (Int) -> Unit
) {
    // The stats-mode lens is re-applied by the ViewModel itself (selectStatsMode reloads the
    // last match), so only the id triggers a load here.
    LaunchedEffect(matchId) { viewModel.loadMatchDetail(matchId) }
    val state by viewModel.matchDetail.collectAsState()
    val detail = state.valueOrNull

    Scaffold(
        topBar = {
            BackTopBar(title = detail?.let { stringResource(R.string.match_versus, it.teamAName, it.teamBName) } ?: stringResource(R.string.match_title_fallback), onBack = onBack)
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(Spacing.md).verticalScroll(rememberScrollState())
        ) {
            val match = detail
            if (match == null) {
                Text((state as? LoadState.Failed)?.text() ?: stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium)
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

            SideCard(name = match.teamAName, players = match.sideA, won = match.winnerIsA, onOpenPlayer = onOpenPlayer)
            Spacer(Modifier.height(Spacing.sm))
            SideCard(name = match.teamBName, players = match.sideB, won = !match.winnerIsA, onOpenPlayer = onOpenPlayer)
            Spacer(Modifier.height(Spacing.md))

            Text(stringResource(R.string.match_result), style = MaterialTheme.typography.titleMedium)
            val winningName = if (match.winnerIsA) match.teamAName else match.teamBName
            val losers = if (match.winnerIsA) match.sideB else match.sideA
            Text(stringResource(R.string.match_wins, winningName))
            losers.forEach { loser ->
                Text(loserLine(loser, showName = losers.size > 1))
            }
            Spacer(Modifier.height(Spacing.md))

            Text(stringResource(R.string.match_head_to_head), style = MaterialTheme.typography.titleMedium)
            val h2h = match.headToHead
            Text(
                pluralStringResource(
                    R.plurals.match_h2h_line, h2h.matches,
                    match.competitorA.name, h2h.winsA, h2h.winsB, match.competitorB.name, h2h.matches
                )
            )
        }
    }
}

@Composable
private fun loserLine(loser: MatchPlayer, showName: Boolean): String {
    val seconds = loser.seconds
        ?: return if (showName) stringResource(R.string.match_no_time_named, loser.name) else stringResource(R.string.match_no_time)
    val base = if (showName) {
        stringResource(R.string.match_seconds_on_counter_named, loser.name, seconds)
    } else {
        stringResource(R.string.match_seconds_on_counter, seconds)
    }
    val refusals = if (loser.forfeitedDrinks == 0) "" else {
        pluralStringResource(R.plurals.match_refusals_suffix, loser.forfeitedDrinks, loser.forfeitedDrinks)
    }
    return base + refusals
}

/** One side of the match: the squad name (or the player, for singles) and a tappable row per
 *  member with their Elo movement and drink. */
@Composable
private fun SideCard(name: String, players: List<MatchPlayer>, won: Boolean, onOpenPlayer: (Int) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (won) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Row(Modifier.fillMaxWidth()) {
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(stringResource(if (won) R.string.match_winner else R.string.match_loser), style = MaterialTheme.typography.labelMedium)
            }
            players.forEach { player ->
                Column(
                    Modifier.fillMaxWidth().clickable { onOpenPlayer(player.id) }.padding(vertical = Spacing.xs)
                ) {
                    if (players.size > 1 || player.name != name) {
                        Text(player.name, color = MaterialTheme.colorScheme.primary)
                    }
                    val before = player.eloBefore
                    val after = player.eloAfter
                    if (before != null && after != null) {
                        Text(
                            stringResource(R.string.match_elo_line, before.format1(), after.format1(), (after - before).formatSigned1()),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    player.drink?.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(R.string.match_drink, it), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
