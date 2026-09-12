package com.example.nflunkyball.ui

import androidx.compose.ui.res.stringResource
import com.example.nflunkyball.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.nflunkyball.server.AccountSyncStatus
import com.example.nflunkyball.ui.shared.BackTopBar
import com.example.nflunkyball.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onLinkAccount: () -> Unit
) {
    var useBleSync by remember { mutableStateOf(viewModel.useBleSync()) }
    var showUnlinkConfirm by remember { mutableStateOf(false) }
    val account = viewModel.account.collectAsState().value
    val syncStatus by viewModel.syncStatus.collectAsState()

    // Keyed on the account id (not just "is one linked") so unlinking and linking a different
    // one re-checks, rather than showing stale status for whichever account was checked first.
    LaunchedEffect(account?.accountId) {
        if (account != null) viewModel.checkSyncStatus()
    }

    Scaffold(
        topBar = {
            BackTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(Spacing.md)) {
                    Text(stringResource(R.string.settings_account_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        // Deliberately independent of any tournament: an account only controls
                        // whether this device can sync/save to a server and pull known-player
                        // suggestions — hosting and scoring a tournament (over Bluetooth at
                        // least) works without one.
                        if (account != null) {
                            stringResource(R.string.settings_account_linked, account.displayName, account.serverUrl)
                        } else {
                            stringResource(R.string.settings_account_not_linked)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = Spacing.xs)
                    )
                    if (account != null) {
                        val (statusText, statusColor) = when (syncStatus) {
                            null, AccountSyncStatus.CHECKING -> stringResource(R.string.settings_sync_checking) to Color.Unspecified
                            AccountSyncStatus.CAN_SYNC -> stringResource(R.string.settings_sync_ok) to Color.Unspecified
                            AccountSyncStatus.REVOKED -> stringResource(R.string.settings_sync_revoked) to MaterialTheme.colorScheme.error
                            AccountSyncStatus.UNKNOWN -> stringResource(R.string.settings_sync_unknown) to Color.Unspecified
                        }
                        Text(
                            statusText,
                            color = statusColor,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = Spacing.xs)
                        )
                        OutlinedButton(
                            onClick = { showUnlinkConfirm = true },
                            modifier = Modifier.padding(top = Spacing.sm)
                        ) { Text(stringResource(R.string.settings_unlink)) }
                    } else {
                        Button(onClick = onLinkAccount, modifier = Modifier.padding(top = Spacing.sm)) {
                            Text(stringResource(R.string.settings_log_in))
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_ble_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.settings_ble_body),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = Spacing.xs)
                        )
                    }
                    Switch(
                        checked = useBleSync,
                        onCheckedChange = {
                            useBleSync = it
                            viewModel.setUseBleSync(it)
                        }
                    )
                }
            }
        }
    }

    if (showUnlinkConfirm) {
        AlertDialog(
            onDismissRequest = { showUnlinkConfirm = false },
            title = { Text(stringResource(R.string.settings_unlink_confirm_title)) },
            text = {
                Text(stringResource(R.string.settings_unlink_confirm_body))
            },
            confirmButton = {
                TextButton(onClick = { showUnlinkConfirm = false; viewModel.unlinkAccount() }) {
                    Text(stringResource(R.string.settings_unlink_confirm_action))
                }
            },
            dismissButton = { TextButton(onClick = { showUnlinkConfirm = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}
