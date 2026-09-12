package com.example.nflunkyball.ui.organizer

import androidx.compose.ui.res.stringResource
import com.example.nflunkyball.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.DatePicker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.nflunkyball.model.TournamentFinishInfo
import com.example.nflunkyball.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun FinishTournamentDialog(
    teamNames: Map<String, String>,
    suggestedStandings: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (TournamentFinishInfo) -> Unit
) {
    // Final placement, best first — starts from the app's guess, the organizer moves rows.
    val standings = remember { suggestedStandings.toMutableStateList() }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    var showDatePicker by remember { mutableStateOf(false) }
    var location by remember { mutableStateOf("") }
    var referees by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_ok)) } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) } }
        ) { DatePicker(state = dateState) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.finish_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val dateMillis = dateState.selectedDateMillis ?: System.currentTimeMillis()
                val formatted = remember(dateMillis) {
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(dateMillis)
                }
                TextButton(onClick = { showDatePicker = true }) { Text(stringResource(R.string.finish_date, formatted)) }
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(stringResource(R.string.finish_location)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = referees,
                    onValueChange = { referees = it },
                    label = { Text(stringResource(R.string.finish_referees)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(stringResource(R.string.finish_comment)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Spacing.md))
                Text(stringResource(R.string.finish_placement), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.finish_placement_hint), style = MaterialTheme.typography.bodySmall)
                standings.forEachIndexed { index, teamId ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("${index + 1}.", modifier = Modifier.padding(end = Spacing.sm))
                        Text(teamNames[teamId] ?: teamId, modifier = Modifier.weight(1f))
                        IconButton(onClick = { standings.add(index - 1, standings.removeAt(index)) }, enabled = index > 0) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.finish_move_up))
                        }
                        IconButton(onClick = { standings.add(index + 1, standings.removeAt(index)) }, enabled = index < standings.lastIndex) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.finish_move_down))
                        }
                    }
                }
                Text(
                    stringResource(R.string.finish_note),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = Spacing.sm)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    TournamentFinishInfo(
                        dateMillis = dateState.selectedDateMillis ?: System.currentTimeMillis(),
                        location = location,
                        referees = referees,
                        comment = comment,
                        finalStandings = standings.toList()
                    )
                )
            }) { Text(stringResource(R.string.finish_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
