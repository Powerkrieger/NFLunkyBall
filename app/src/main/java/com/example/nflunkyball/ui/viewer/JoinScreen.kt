package com.example.nflunkyball.ui.viewer

import androidx.compose.ui.res.stringResource
import com.example.nflunkyball.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.nflunkyball.qr.JoinPayloadCodec
import com.example.nflunkyball.ui.shared.BluetoothGate
import com.example.nflunkyball.ui.theme.Spacing
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun JoinScreen(viewModel: ViewerViewModel, onJoined: () -> Unit) {
    var manualCode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val badQrMessage = stringResource(R.string.join_bad_qr)
    val invalidCodeMessage = stringResource(R.string.join_invalid)
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@rememberLauncherForActivityResult
        val payload = JoinPayloadCodec.decode(text)
        if (payload != null) {
            viewModel.join(payload)
            onJoined()
        } else {
            error = badQrMessage
        }
    }

    val content: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.join_title), style = MaterialTheme.typography.headlineSmall)

            Button(
                onClick = { scanLauncher.launch(ScanOptions().setOrientationLocked(false).setBeepEnabled(false)) },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg)
            ) { Text(stringResource(R.string.join_scan)) }

            Text(stringResource(R.string.join_or), modifier = Modifier.padding(vertical = Spacing.md))

            OutlinedTextField(
                value = manualCode,
                onValueChange = { manualCode = it },
                label = { Text(stringResource(R.string.join_enter_code)) },
                modifier = Modifier.fillMaxWidth()
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = Spacing.sm))
            }
            Button(
                onClick = {
                    val payload = JoinPayloadCodec.decode(manualCode)
                    if (payload != null) {
                        viewModel.join(payload)
                        onJoined()
                    } else {
                        error = invalidCodeMessage
                    }
                },
                enabled = manualCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)
            ) { Text(stringResource(R.string.join_action)) }
        }
    }

    // Bluetooth is only actually needed here when BLE sync is the active mode — gating the
    // whole screen behind "Bluetooth must be on" otherwise would block entering a join code for
    // no reason (server mode needs no Bluetooth at all).
    if (viewModel.useBleSync()) {
        BluetoothGate { content() }
    } else {
        content()
    }
}
