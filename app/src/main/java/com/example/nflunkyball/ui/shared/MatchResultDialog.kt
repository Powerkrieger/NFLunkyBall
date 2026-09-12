package com.example.nflunkyball.ui.shared

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.nflunkyball.R
import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.nflunkyball.model.FORFEIT_FLAT_SECONDS
import com.example.nflunkyball.model.Match
import com.example.nflunkyball.model.MatchDrinks
import com.example.nflunkyball.model.MatchResult
import com.example.nflunkyball.model.PlayerResult
import com.example.nflunkyball.model.Team
import com.example.nflunkyball.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Records a result: pick the winning side, then every player on the losing side gets their own
 * independent timer and counter (drinking time plus a flat [FORFEIT_FLAT_SECONDS] per refused
 * drink — see [PlayerResult]). For a singles match that's one loser, exactly the old flow.
 * Drinks are per player and organizer-only (see [MatchDrinks]).
 */
@Composable
fun MatchResultDialog(
    match: Match,
    teams: Map<String, Team>,
    knownDrinks: List<String> = emptyList(),
    existingDrinks: MatchDrinks? = null,
    onDismiss: () -> Unit,
    onConfirm: (MatchResult, MatchDrinks) -> Unit,
    onClear: (() -> Unit)? = null
) {
    val teamA = teams[match.teamAId]
    val teamB = teams[match.teamBId]
    val nameA = teamA?.name ?: match.teamAId
    val nameB = teamB?.name ?: match.teamBId
    val membersA = teamA?.memberNames ?: listOf(nameA)
    val membersB = teamB?.memberNames ?: listOf(nameB)

    var winnerId by remember { mutableStateOf(match.result?.winnerId ?: match.teamAId) }
    val losers = if (winnerId == match.teamAId) membersB else membersA
    val winners = if (winnerId == match.teamAId) membersA else membersB

    // Per losing player: their counter (seconds, text so it's hand-editable) and refusal count.
    // Prefilled from an existing result when editing.
    val secondsText = remember {
        mutableStateMapOf<String, String>().also { map ->
            match.result?.let { result ->
                val losingName = if (result.winnerId == match.teamAId) nameB else nameA
                result.loserResults(losingName).forEach { map[it.player] = it.seconds.toString() }
            }
        }
    }
    val forfeits = remember {
        mutableStateMapOf<String, Int>().also { map ->
            match.result?.let { result ->
                val losingName = if (result.winnerId == match.teamAId) nameB else nameA
                result.loserResults(losingName).forEach { map[it.player] = it.forfeitedDrinks }
            }
        }
    }
    val drinks = remember {
        mutableStateMapOf<String, String>().also { map ->
            existingDrinks?.let { existing ->
                membersA.forEach { p -> (existing.byPlayer[p] ?: existing.teamA)?.let { map[p] = it } }
                membersB.forEach { p -> (existing.byPlayer[p] ?: existing.teamB)?.let { map[p] = it } }
            }
        }
    }

    val allLosersTimed = losers.all { secondsText[it]?.toIntOrNull() != null }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (match.result == null) R.string.result_title_record else R.string.result_title_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.result_winner), style = MaterialTheme.typography.labelMedium)
                listOf(match.teamAId to nameA, match.teamBId to nameB).forEach { (teamId, name) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = winnerId == teamId, onClick = { winnerId = teamId })
                        Text(name)
                    }
                }
                Spacer(Modifier.height(Spacing.sm))

                Text(
                    stringResource(if (losers.size == 1) R.string.result_loser_time_one else R.string.result_loser_time_many),
                    style = MaterialTheme.typography.labelMedium
                )
                losers.forEach { player ->
                    LoserCounter(
                        player = player,
                        showName = losers.size > 1,
                        secondsText = secondsText[player] ?: "",
                        onSecondsChange = { secondsText[player] = it },
                        forfeitedDrinks = forfeits[player] ?: 0,
                        onRefusedDrink = {
                            forfeits[player] = (forfeits[player] ?: 0) + 1
                            secondsText[player] = ((secondsText[player]?.toIntOrNull() ?: 0) + FORFEIT_FLAT_SECONDS).toString()
                        },
                        onResetRefusals = {
                            val count = forfeits[player] ?: 0
                            forfeits[player] = 0
                            secondsText[player] = ((secondsText[player]?.toIntOrNull() ?: 0) - count * FORFEIT_FLAT_SECONDS)
                                .coerceAtLeast(0).toString()
                        }
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }

                Text(stringResource(R.string.result_drinks), style = MaterialTheme.typography.labelMedium)
                (membersA + membersB).forEach { player ->
                    DrinkField(
                        label = stringResource(R.string.result_drink_label, player),
                        value = drinks[player] ?: "",
                        onValueChange = { drinks[player] = it },
                        knownDrinks = knownDrinks
                    )
                    Spacer(Modifier.height(Spacing.xs))
                }
                // A third dialog action doesn't fit AlertDialog's confirm/dismiss slots, so it
                // lives in the body instead — only offered once there's actually a result to undo.
                if (match.result != null && onClear != null) {
                    TextButton(
                        onClick = onClear,
                        modifier = Modifier.padding(top = Spacing.sm),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.result_clear)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val loserResults = losers.map { player ->
                        PlayerResult(player, secondsText[player]!!.toInt(), forfeits[player] ?: 0)
                    }
                    val result = MatchResult.ofLosers(winnerId, loserResults)
                    val byPlayer = drinks.filterValues { it.isNotBlank() }
                    val matchDrinks = MatchDrinks(
                        teamA = byPlayer[membersA.first()],
                        teamB = byPlayer[membersB.first()],
                        byPlayer = byPlayer
                    )
                    onConfirm(result, matchDrinks)
                },
                enabled = allLosersTimed && winners.isNotEmpty()
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/** One losing player's counter: an independent start/stop stopwatch that fills the seconds
 *  field, the hand-editable field itself, and a "refused a drink" action that adds the flat
 *  forfeit amount and bumps the refusal count. Never persisted — resets when the dialog reopens. */
@Composable
private fun LoserCounter(
    player: String,
    showName: Boolean,
    secondsText: String,
    onSecondsChange: (String) -> Unit,
    forfeitedDrinks: Int,
    onRefusedDrink: () -> Unit,
    onResetRefusals: () -> Unit
) {
    var timerStartMs by remember(player) { mutableStateOf<Long?>(null) }
    var elapsedSeconds by remember(player) { mutableLongStateOf(0L) }
    var baseSeconds by remember(player) { mutableIntStateOf(0) }
    LaunchedEffect(timerStartMs) {
        val startedAt = timerStartMs ?: return@LaunchedEffect
        while (true) {
            elapsedSeconds = (SystemClock.elapsedRealtime() - startedAt) / 1000
            delay(200)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        if (showName) Text(player, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                if (timerStartMs != null) {
                    // Stopping adds this run to whatever refusals already put on the counter.
                    onSecondsChange((baseSeconds + elapsedSeconds.toInt()).toString())
                    timerStartMs = null
                } else {
                    baseSeconds = forfeitedDrinks * FORFEIT_FLAT_SECONDS
                    elapsedSeconds = 0L
                    timerStartMs = SystemClock.elapsedRealtime()
                }
            }) { Text(stringResource(if (timerStartMs != null) R.string.result_timer_stop else R.string.result_timer_start)) }
            if (timerStartMs != null) {
                Text(stringResource(R.string.result_elapsed, elapsedSeconds), style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onRefusedDrink) { Text(stringResource(R.string.result_refused)) }
        }
        OutlinedTextField(
            value = secondsText,
            onValueChange = { onSecondsChange(it.filter(Char::isDigit)) },
            label = { Text(stringResource(R.string.result_seconds_label, FORFEIT_FLAT_SECONDS)) },
            singleLine = true
        )
        if (forfeitedDrinks > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(R.plurals.result_refused_count, forfeitedDrinks, forfeitedDrinks),
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = onResetRefusals) { Text(stringResource(R.string.result_reset)) }
            }
        }
    }
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
            label = { Text(stringResource(R.string.result_drink_field, label)) },
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
