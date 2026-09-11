package com.example.nflunkyball.ui.organizer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.nflunkyball.server.InvitePayloadCodec
import com.example.nflunkyball.ui.theme.Spacing
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * One code redemption flow for both roles — the server decides whether a code grants an
 * organizer identity or just standing viewer read access (see OrganizerViewModel.linkAccount),
 * so this screen never needs to know which kind of code it's handling ahead of time. Reused both
 * as the app's required first screen (no group access on this device yet) and from Settings for
 * an explicit re-link/switch-role later.
 */
@Composable
fun LoginScreen(
    viewModel: OrganizerViewModel,
    onDone: () -> Unit,
    // Null when reached mid-flow (e.g. "you need to link to save this tournament") — the escape
    // hatch to just watch a different tournament doesn't make sense there, only on first run.
    onJoinTournament: (() -> Unit)? = null
) {
    var inviteCode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    // Mirrors the viewer's join-QR flow (JoinScreen) — the admin page can render an invite as a
    // QR code too, so scanning beats retyping a long code by hand.
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@rememberLauncherForActivityResult
        if (InvitePayloadCodec.decode(text) != null) {
            inviteCode = text
            status = null
        } else {
            status = "That QR code doesn't look like an NFLunkyBall invite code."
        }
    }

    Column(Modifier.fillMaxSize().padding(Spacing.lg)) {
        Text("Log in", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Ask whoever runs the group's history server for an invite code — it's a one-time " +
                "code that also tells the app where the server lives. Whether it logs you in as " +
                "an organizer or just a viewer depends on which kind of code it is.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.md)
        )
        OutlinedTextField(
            value = inviteCode,
            onValueChange = { inviteCode = it },
            label = { Text("Invite code") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(
            onClick = { scanLauncher.launch(ScanOptions().setOrientationLocked(false).setBeepEnabled(false)) },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
        ) { Text("Scan QR code instead") }
        status?.let { Text(it, modifier = Modifier.padding(top = Spacing.sm)) }
        Button(
            onClick = {
                viewModel.linkAccount(inviteCode) { success, message ->
                    status = message
                    if (success) onDone()
                }
            },
            enabled = inviteCode.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)
        ) { Text("Log in") }
        onJoinTournament?.let {
            TextButton(
                onClick = it,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
            ) { Text("Just watching one tournament? Scan its code instead") }
        }
    }
}
