package com.example.nflunkyball.ui.shared

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.nflunkyball.ble.BleCapability
import com.example.nflunkyball.ui.theme.Spacing

/**
 * Blocks [content] behind a clear "Bluetooth is off" message until it's turned on, instead of
 * silently doing nothing — advertising/scanning calls just no-op when Bluetooth is disabled,
 * which is otherwise indistinguishable from a real bug (this is exactly what made a real BLE
 * sync failure hard to diagnose during testing).
 */
@Composable
fun BluetoothGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(BleCapability.isBluetoothEnabled(context)) }
    val hasAdapter = remember { BleCapability.bluetoothAdapter(context) != null }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        enabled = BleCapability.isBluetoothEnabled(context)
    }

    if (enabled) {
        content()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.lg),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (hasAdapter) {
                Text(
                    "Bluetooth is off. NFLunkyBall needs it turned on to broadcast or view live scores.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(
                    onClick = { launcher.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                    modifier = Modifier.padding(top = Spacing.md)
                ) {
                    Text("Open Bluetooth settings")
                }
            } else {
                Text(
                    "This device doesn't have Bluetooth, so live scores can't work here.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
