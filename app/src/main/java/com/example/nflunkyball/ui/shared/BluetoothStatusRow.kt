package com.example.nflunkyball.ui.shared

import androidx.compose.ui.res.stringResource
import com.example.nflunkyball.R
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.nflunkyball.ble.BleCapability

/**
 * Non-blocking status line for screens that need BLE but shouldn't hide their other content
 * behind it (unlike [BluetoothGate]) — e.g. a viewer's waiting screen, where "why is nothing
 * happening" needs an answer right there rather than forcing a trip to system Settings blind.
 * Renders nothing once Bluetooth is on.
 */
@Composable
fun BluetoothStatusRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(BleCapability.isBluetoothEnabled(context)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        enabled = BleCapability.isBluetoothEnabled(context)
    }

    if (enabled) return

    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.bluetooth_off_short), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { launcher.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) {
            Text(stringResource(R.string.bluetooth_turn_on))
        }
    }
}
