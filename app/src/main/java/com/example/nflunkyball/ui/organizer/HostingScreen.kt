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

/** Reached only once the organizer has a linked account (see MainActivity's routing), so
 *  [account] here is always non-null in practice. */
@Composable
fun HostingScreen(
    viewModel: OrganizerViewModel,
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
            val payload = JoinPayload(room = code, server = account?.serverUrl, pw = readPassword)
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

        account?.let { Text("Hosting as ${it.displayName}", style = MaterialTheme.typography.bodyMedium) }

        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            Text("Continue to scoring")
        }
    }
}
