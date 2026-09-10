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

/** Reached only once the organizer has a linked account (see MainActivity's routing), so
 *  [account] here is always non-null in practice. */
@Composable
fun HostingScreen(
    viewModel: OrganizerViewModel,
    onContinue: () -> Unit
) {
    // BLE live sync is opt-in (see SettingsScreen) — only gate this screen behind the Bluetooth
    // radio being on when we're actually going to use it.
    if (viewModel.bleEnabled()) {
        BluetoothGate {
            // Inside the gate so it only starts once Bluetooth is confirmed on, rather than
            // silently no-op-ing if it was off when this screen first appeared.
            LaunchedEffect(Unit) { viewModel.startHosting() }
            HostingScreenContent(viewModel, bleEnabled = true, onContinue)
        }
    } else {
        HostingScreenContent(viewModel, bleEnabled = false, onContinue)
    }
}

@Composable
private fun HostingScreenContent(
    viewModel: OrganizerViewModel,
    bleEnabled: Boolean,
    onContinue: () -> Unit
) {
    val tournament by viewModel.tournament.collectAsState()
    // Derived from the tournament's own stable id rather than stored separately, so the
    // same QR/code stays valid for viewers even if the organizer's app restarts mid-event.
    val roomId = tournament?.id?.let { RoomCode.forTournament(it) }
    val account = viewModel.organizerAccount
    val readPassword = viewModel.readPassword
    val broadcastVersion by viewModel.broadcastVersion.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Share this to let people watch", style = MaterialTheme.typography.titleMedium)

        if (roomId != null) {
            val code = RoomCode.encode(roomId)
            val payload = JoinPayload(room = code, server = account?.serverUrl, pw = readPassword)
            RoomCodeDisplay(payload)
        } else {
            CircularProgressIndicator(Modifier.padding(24.dp))
        }

        Spacer(Modifier.height(24.dp))

        if (bleEnabled) {
            val versionText = broadcastVersion?.let { "Broadcasting version $it" } ?: "Starting broadcast…"
            Text(versionText, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                "Live sync over Bluetooth is off. Turn it on in Settings if you want viewers " +
                    "to see live scores.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        account?.let { Text("Hosting as ${it.displayName}", style = MaterialTheme.typography.bodyMedium) }

        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            Text("Continue to scoring")
        }
    }
}
