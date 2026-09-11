package com.example.nflunkyball.ui.organizer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishTournamentDialog(
    onDismiss: () -> Unit,
    onConfirm: (TournamentFinishInfo) -> Unit
) {
    val dateState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    var showDatePicker by remember { mutableStateOf(false) }
    var location by remember { mutableStateOf("") }
    var referees by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { showDatePicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = dateState) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finish tournament") },
        text = {
            Column {
                val dateMillis = dateState.selectedDateMillis ?: System.currentTimeMillis()
                val formatted = remember(dateMillis) {
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(dateMillis)
                }
                TextButton(onClick = { showDatePicker = true }) { Text("Date: $formatted") }
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = referees,
                    onValueChange = { referees = it },
                    label = { Text("Referees (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Comment (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "All optional — none of this is synced live, it's only saved with the final upload.",
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
                        comment = comment
                    )
                )
            }) { Text("Finish") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
