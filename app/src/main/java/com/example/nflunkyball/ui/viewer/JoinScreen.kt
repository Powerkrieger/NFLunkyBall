package com.example.nflunkyball.ui.viewer

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
import androidx.compose.ui.unit.dp
import com.example.nflunkyball.qr.JoinPayloadCodec
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun JoinScreen(viewModel: ViewerViewModel, onJoined: () -> Unit) {
    var manualCode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@rememberLauncherForActivityResult
        val payload = JoinPayloadCodec.decode(text)
        if (payload != null) {
            viewModel.join(payload)
            onJoined()
        } else {
            error = "That QR code doesn't look like an NFLunkyBall join code."
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Join a tournament", style = MaterialTheme.typography.headlineSmall)

        Button(
            onClick = { scanLauncher.launch(ScanOptions().setOrientationLocked(false).setBeepEnabled(false)) },
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
        ) { Text("Scan QR code") }

        Text("or", modifier = Modifier.padding(vertical = 16.dp))

        OutlinedTextField(
            value = manualCode,
            onValueChange = { manualCode = it },
            label = { Text("Enter code") },
            modifier = Modifier.fillMaxWidth()
        )
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
        Button(
            onClick = {
                val payload = JoinPayloadCodec.decode(manualCode)
                if (payload != null) {
                    viewModel.join(payload)
                    onJoined()
                } else {
                    error = "Enter a valid code"
                }
            },
            enabled = manualCode.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) { Text("Join") }
    }
}
