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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LinkAccountScreen(
    viewModel: OrganizerViewModel,
    onDone: () -> Unit
) {
    var displayName by remember { mutableStateOf("") }
    var inviteToken by remember { mutableStateOf("") }
    var groupReadPassword by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Link organizer account", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Ask whoever runs the group's history server for an invite token and the group's " +
                "read password.",
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
            value = inviteToken,
            onValueChange = { inviteToken = it },
            label = { Text("Invite token") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        OutlinedTextField(
            value = groupReadPassword,
            onValueChange = { groupReadPassword = it },
            label = { Text("Group read password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        status?.let { Text(it, modifier = Modifier.padding(top = 12.dp)) }
        Button(
            onClick = {
                viewModel.linkAccount(displayName, inviteToken, groupReadPassword) { success, message ->
                    status = message
                    if (success) onDone()
                }
            },
            enabled = displayName.isNotBlank() && inviteToken.isNotBlank() && groupReadPassword.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) { Text("Link account") }
    }
}
