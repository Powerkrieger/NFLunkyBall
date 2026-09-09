package com.example.nflunkyball.ui.organizer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LinkAccountScreen(
    viewModel: OrganizerViewModel,
    onDone: () -> Unit
) {
    var displayName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Link organizer account", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Ask whoever runs the group's history server for an invite code — it's a one-time " +
                "code that also tells the app where the server lives, so there's nothing else to enter.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Your name") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = inviteCode,
            onValueChange = { inviteCode = it },
            label = { Text("Invite code") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        status?.let { Text(it, modifier = Modifier.padding(top = 12.dp)) }
        Button(
            onClick = {
                viewModel.linkAccount(displayName, inviteCode) { success, message ->
                    status = message
                    if (success) onDone()
                }
            },
            enabled = displayName.isNotBlank() && inviteCode.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) { Text("Link account") }
    }
}
