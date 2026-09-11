package com.example.nflunkyball.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import com.example.nflunkyball.persistence.AppSettingsStore
import com.example.nflunkyball.ui.organizer.AccountSyncStatus
import com.example.nflunkyball.ui.organizer.OrganizerViewModel
import com.example.nflunkyball.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    organizerViewModel: OrganizerViewModel,
    onBack: () -> Unit,
    onLinkAccount: () -> Unit
) {
    val context = LocalContext.current
    val settingsStore = remember { AppSettingsStore(context) }
    var useBleSync by remember { mutableStateOf(settingsStore.useBleSync()) }
    var showUnlinkConfirm by remember { mutableStateOf(false) }
    val account = organizerViewModel.organizerAccount
    val syncStatus by organizerViewModel.accountSyncStatus.collectAsState()

    // Keyed on the account id (not just "is one linked") so unlinking and linking a different
    // one re-checks, rather than showing stale status for whichever account was checked first.
    LaunchedEffect(account?.accountId) {
        if (account != null) organizerViewModel.checkAccountSyncStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
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
                    Text("Organizer account", style = MaterialTheme.typography.titleMedium)
                    Text(
                        // Deliberately independent of any tournament: an account only controls
                        // whether this device can sync/save to a server and pull known-player
                        // suggestions — hosting and scoring a tournament (over Bluetooth at
                        // least) works without one.
                        if (account != null) {
                            "Linked as ${account.displayName} (${account.serverUrl})"
                        } else {
                            "Not linked. Without an account, tournaments can still be hosted and " +
                                "scored over Bluetooth, but can't be synced to a server, saved to " +
                                "history, or use known-player suggestions."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = Spacing.xs)
                    )
                    if (account != null) {
                        val (statusText, statusColor) = when (syncStatus) {
                            null, AccountSyncStatus.CHECKING -> "Checking sync status…" to Color.Unspecified
                            AccountSyncStatus.CAN_SYNC -> "Can sync" to Color.Unspecified
                            AccountSyncStatus.REVOKED ->
                                "This account has been revoked and can't sync — add a new invite " +
                                    "token to restore access." to MaterialTheme.colorScheme.error
                            AccountSyncStatus.UNKNOWN -> "Couldn't check sync status (offline?)" to Color.Unspecified
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
                        ) { Text("Unlink account") }
                    } else {
                        Button(onClick = onLinkAccount, modifier = Modifier.padding(top = Spacing.sm)) {
                            Text("Log in")
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
                        Text("Use Bluetooth instead of server", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Live scores sync through the server by default. Turn this on to sync " +
                                "directly between phones over Bluetooth instead — useful with no " +
                                "internet, but only works at close range.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = Spacing.xs)
                        )
                    }
                    Switch(
                        checked = useBleSync,
                        onCheckedChange = {
                            useBleSync = it
                            settingsStore.setUseBleSync(it)
                        }
                    )
                }
            }
        }
    }

    if (showUnlinkConfirm) {
        AlertDialog(
            onDismissRequest = { showUnlinkConfirm = false },
            title = { Text("Unlink organizer account?") },
            text = {
                Text(
                    "This only forgets the account on this device — it doesn't delete anything " +
                        "on the server, and doesn't touch any tournament you're currently hosting " +
                        "(it just loses server sync until you link again)."
                )
            },
            confirmButton = {
                TextButton(onClick = { showUnlinkConfirm = false; organizerViewModel.unlinkAccount() }) {
                    Text("Unlink")
                }
            },
            dismissButton = { TextButton(onClick = { showUnlinkConfirm = false }) { Text("Cancel") } }
        )
    }
}
