package com.example.nflunkyball.ui.organizer

import androidx.compose.ui.res.stringResource
import com.example.nflunkyball.R
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.nflunkyball.server.InvitePayloadCodec
import com.example.nflunkyball.server.LinkOutcome
import com.example.nflunkyball.ui.theme.Spacing
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

/**
 * One code redemption flow for both roles — the server decides whether a code grants an
 * organizer identity or just standing viewer read access (see AccountManager.link),
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
    // Saveable: launching the QR scanner rotates the device on some phones, and losing a
    // half-typed 100-character code to that recreation is maddening.
    var inviteCode by rememberSaveable { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var linking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Mirrors the viewer's join-QR flow (JoinScreen) — the admin page can render an invite as a
    // QR code too, so scanning beats retyping a long code by hand.
    val badQrMessage = stringResource(R.string.login_bad_qr)
    val invalidCodeMessage = stringResource(R.string.login_invalid_code)
    val viewerMessage = stringResource(R.string.login_as_viewer)
    val linkedAsFormat = stringResource(R.string.login_linked_as)
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@rememberLauncherForActivityResult
        if (InvitePayloadCodec.decode(text) != null) {
            inviteCode = text
            status = null
        } else {
            status = badQrMessage
        }
    }

    Column(Modifier.fillMaxSize().padding(Spacing.lg)) {
        Text(stringResource(R.string.login_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.login_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.md)
        )
        OutlinedTextField(
            value = inviteCode,
            onValueChange = { inviteCode = it },
            label = { Text(stringResource(R.string.login_invite_code)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(
            onClick = { scanLauncher.launch(ScanOptions().setOrientationLocked(false).setBeepEnabled(false)) },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
        ) { Text(stringResource(R.string.login_scan)) }
        status?.let { Text(it, modifier = Modifier.padding(top = Spacing.sm)) }
        Button(
            onClick = {
                linking = true
                scope.launch {
                    val outcome = viewModel.linkAccount(inviteCode)
                    linking = false
                    status = when (outcome) {
                        is LinkOutcome.Organizer -> linkedAsFormat.format(outcome.displayName)
                        LinkOutcome.Viewer -> viewerMessage
                        LinkOutcome.InvalidCode -> invalidCodeMessage
                        is LinkOutcome.Rejected -> outcome.message
                    }
                    if (outcome is LinkOutcome.Organizer || outcome == LinkOutcome.Viewer) onDone()
                }
            },
            enabled = inviteCode.isNotBlank() && !linking,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)
        ) { Text(stringResource(R.string.login_title)) }
        onJoinTournament?.let {
            TextButton(
                onClick = it,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
            ) { Text(stringResource(R.string.login_just_watching)) }
        }
    }
}
