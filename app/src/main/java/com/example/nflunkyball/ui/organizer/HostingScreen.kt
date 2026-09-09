package com.example.nflunkyball.ui.organizer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.example.nflunkyball.ble.RoomCode
import com.example.nflunkyball.qr.JoinPayload
import com.example.nflunkyball.qr.JoinPayloadCodec
import com.example.nflunkyball.qr.QrCodeGenerator
import com.example.nflunkyball.server.DEFAULT_SERVER_URL

@Composable
fun HostingScreen(
    viewModel: OrganizerViewModel,
    onLinkAccount: () -> Unit,
    onContinue: () -> Unit
) {
    LaunchedEffect(Unit) { viewModel.startHosting() }

    val roomId = viewModel.roomId
    val account = viewModel.organizerAccount
    val readPassword = viewModel.readPassword

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Share this to let people watch", style = MaterialTheme.typography.titleMedium)

        if (roomId != null) {
            val code = RoomCode.encode(roomId)
            val payload = JoinPayload(
                room = code,
                server = if (account != null) DEFAULT_SERVER_URL else null,
                pw = if (account != null) readPassword else null
            )
            val qrContent = remember(payload) { JoinPayloadCodec.encode(payload) }
            val bitmap = remember(qrContent) { QrCodeGenerator.generate(qrContent) }
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Join QR code",
                modifier = Modifier.padding(24.dp)
            )
            Text(code, style = MaterialTheme.typography.displaySmall)
        } else {
            CircularProgressIndicator(Modifier.padding(24.dp))
        }

        Spacer(Modifier.height(24.dp))

        if (account == null) {
            OutlinedButton(onClick = onLinkAccount) {
                Text("Link organizer account (optional, for history)")
            }
        } else {
            Text("Linked as ${account.displayName}", style = MaterialTheme.typography.bodyMedium)
        }

        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            Text("Continue to scoring")
        }
    }
}
