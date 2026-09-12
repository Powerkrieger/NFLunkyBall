package com.example.nflunkyball.ui.shared

import androidx.compose.ui.res.stringResource
import com.example.nflunkyball.R
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
import com.example.nflunkyball.ble.BlePermissions
import com.example.nflunkyball.ui.theme.Spacing

/** Blocks [content] behind a permission-request screen until Bluetooth/camera perms are granted. */
@Composable
fun PermissionsGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(BlePermissions.hasAll(context)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        granted = BlePermissions.hasAll(context)
    }

    if (granted) {
        content()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.lg),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.permissions_body),
                style = MaterialTheme.typography.bodyLarge
            )
            Button(
                onClick = { launcher.launch(BlePermissions.required) },
                modifier = Modifier.padding(top = Spacing.md)
            ) {
                Text(stringResource(R.string.permissions_grant))
            }
        }
    }
}
