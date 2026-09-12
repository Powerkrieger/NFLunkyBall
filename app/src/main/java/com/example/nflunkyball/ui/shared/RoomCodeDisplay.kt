package com.example.nflunkyball.ui.shared

import androidx.compose.ui.res.stringResource
import com.example.nflunkyball.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.example.nflunkyball.qr.JoinPayload
import com.example.nflunkyball.qr.JoinPayloadCodec
import com.example.nflunkyball.qr.QrCodeGenerator
import com.example.nflunkyball.ui.theme.Spacing

/** QR + plain-text rendering of a join code, shared by the organizer's hosting screen and a
 *  viewer's "invite others" screen — scanning either one lands in the same room. */
@Composable
fun RoomCodeDisplay(payload: JoinPayload, modifier: Modifier = Modifier) {
    val qrContent = remember(payload) { JoinPayloadCodec.encode(payload) }
    val bitmap = remember(qrContent) { QrCodeGenerator.generate(qrContent) }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            Modifier.padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = stringResource(R.string.room_qr_description))
            Text(
                payload.room,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.sm)
            )
        }
    }
}
