package com.example.nflunkyball.ui.organizer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nflunkyball.ble.RoomCode
import com.example.nflunkyball.qr.JoinPayload
import com.example.nflunkyball.ui.shared.BluetoothGate
import com.example.nflunkyball.ui.shared.RoomCodeDisplay

/** An account is optional, not a prerequisite for hosting (see SettingsScreen's "Organizer
 *  account" section) — [account] can legitimately be null here, in which case this just shows
 *  a "Link organizer account" affordance instead of a sync status, and BLE mode (unaffected
 *  either way) keeps working normally. */
@Composable
fun HostingScreen(
    viewModel: OrganizerViewModel,
    onContinue: () -> Unit,
    onLinkAccount: () -> Unit
) {
    // BLE is an opt-in fallback (see SettingsScreen) — only gate this screen behind the
    // Bluetooth radio being on when we're actually going to use it. Server mode needs no gate.
    if (viewModel.useBleSync()) {
        BluetoothGate {
            // Inside the gate so it only starts once Bluetooth is confirmed on, rather than
            // silently no-op-ing if it was off when this screen first appeared.
            LaunchedEffect(Unit) { viewModel.startHosting() }
            HostingScreenContent(viewModel, useBleSync = true, onContinue, onLinkAccount)
        }
    } else {
        LaunchedEffect(Unit) { viewModel.startHosting() }
        HostingScreenContent(viewModel, useBleSync = false, onContinue, onLinkAccount)
    }
}

@Composable
private fun HostingScreenContent(
    viewModel: OrganizerViewModel,
    useBleSync: Boolean,
    onContinue: () -> Unit,
    onLinkAccount: () -> Unit
) {
    val tournament by viewModel.tournament.collectAsState()
    // Derived from the tournament's own stable id rather than stored separately, so the
    // same QR/code stays valid for viewers even if the organizer's app restarts mid-event.
    val roomId = tournament?.id?.let { RoomCode.forTournament(it) }
    val account = viewModel.organizerAccount
    val readPassword = viewModel.readPassword
    val broadcastVersion by viewModel.broadcastVersion.collectAsState()
    val serverSyncStatus by viewModel.serverSyncStatus.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Share this to let people watch", style = MaterialTheme.typography.titleMedium)

        if (roomId != null && tournament != null) {
            val code = RoomCode.encode(roomId)
            val payload = JoinPayload(room = code, server = account?.serverUrl, pw = readPassword, tid = tournament?.id)
            RoomCodeDisplay(payload)
        } else {
            CircularProgressIndicator(Modifier.padding(24.dp))
        }

        Spacer(Modifier.height(24.dp))

        if (useBleSync) {
            val versionText = broadcastVersion?.let { "Broadcasting version $it" } ?: "Starting broadcast…"
            Text(versionText, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(serverSyncStatus ?: "Starting sync…", style = MaterialTheme.typography.bodyMedium)
        }

        if (account != null) {
            Text("Hosting as ${account.displayName}", style = MaterialTheme.typography.bodyMedium)
        } else {
            Button(onClick = onLinkAccount, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Link organizer account")
            }
        }

        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            Text("Continue to scoring")
        }
    }
}
