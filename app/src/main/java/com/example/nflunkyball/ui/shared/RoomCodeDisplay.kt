package com.example.nflunkyball.ui.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.example.nflunkyball.qr.JoinPayload
import com.example.nflunkyball.qr.JoinPayloadCodec
import com.example.nflunkyball.qr.QrCodeGenerator

/** QR + plain-text rendering of a join code, shared by the organizer's hosting screen and a
 *  viewer's "invite others" screen — scanning either one lands in the same room. */
@Composable
fun RoomCodeDisplay(payload: JoinPayload, modifier: Modifier = Modifier) {
    val qrContent = remember(payload) { JoinPayloadCodec.encode(payload) }
    val bitmap = remember(qrContent) { QrCodeGenerator.generate(qrContent) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Join QR code",
            modifier = Modifier.padding(24.dp)
        )
        Text(payload.room, style = MaterialTheme.typography.displaySmall)
    }
}
