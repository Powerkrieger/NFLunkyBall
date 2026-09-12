package com.example.nflunkyball.server

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Whether the currently linked account can actually reach the server and sync, as opposed to
 *  merely having credentials stored locally — see [AccountManager.checkSyncStatus] and
 *  SettingsScreen's "Organizer account" section, which surfaces this. */
enum class AccountSyncStatus { CHECKING, CAN_SYNC, REVOKED, UNKNOWN }

/** What redeeming an invite code led to — see [AccountManager.link]. */
sealed interface LinkOutcome {
    data class Organizer(val displayName: String) : LinkOutcome
    data object Viewer : LinkOutcome
    /** The code didn't decode at all; nothing was sent to any server. */
    data object InvalidCode : LinkOutcome
    /** The server refused it (used up, unknown, unreachable…) with its own message. */
    data class Rejected(val message: String) : LinkOutcome
}

/**
 * This device's relationship to the group's history server: the organizer account (if one is
 * linked — private key never leaves the device) and the shared read password. Owns linking via
 * invite code, unlinking, and the "is my account still valid" check. Pure JVM apart from
 * [CredentialsStore], so it's covered by AccountManagerTest.
 */
class AccountManager(
    private val credentials: CredentialsStore,
    private val serverApi: ServerApiFactory,
    private val scope: CoroutineScope
) {
    private val _account = MutableStateFlow(credentials.loadAccount())
    val account: StateFlow<OrganizerAccount?> = _account

    private val _readPassword = MutableStateFlow(credentials.loadReadPassword())
    val readPassword: StateFlow<String?> = _readPassword

    /** Null until [checkSyncStatus] has been called (or when there's no linked account to check). */
    private val _syncStatus = MutableStateFlow<AccountSyncStatus?>(null)
    val syncStatus: StateFlow<AccountSyncStatus?> = _syncStatus

    /** Actually asks the server whether the linked account can sync (not revoked), rather than
     *  just trusting that credentials exist locally — an admin revoking it from the other end
     *  leaves no local trace otherwise. */
    fun checkSyncStatus() {
        val account = _account.value
        val password = _readPassword.value
        if (account == null || password == null) {
            _syncStatus.value = null
            return
        }
        _syncStatus.value = AccountSyncStatus.CHECKING
        scope.launch {
            _syncStatus.value = when (val result = serverApi(account.serverUrl).getAccountStatus(account.accountId, password)) {
                is ServerResult.Success -> if (result.value.revoked) AccountSyncStatus.REVOKED else AccountSyncStatus.CAN_SYNC
                is ServerResult.Failure -> AccountSyncStatus.UNKNOWN
            }
        }
    }

    /**
     * Redeems an invite code — the whole code an admin generated, which bundles the server URL
     * and token (see [InvitePayload] for why the app never hardcodes a server address). The server
     * decides what it grants: an organizer invite yields an account + fresh keypair, a viewer
     * invite just standing read access.
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun link(inviteCode: String): LinkOutcome {
        val invite = InvitePayloadCodec.decode(inviteCode) ?: return LinkOutcome.InvalidCode
        val keyPair = Ed25519.generateKeyPair()
        val publicKeyB64 = Base64.encode(keyPair.publicKeyBytes)
        return when (val result = serverApi(invite.server).register(invite.token, publicKeyB64)) {
            is ServerResult.Success -> {
                val accountId = result.value.accountId
                val displayName = result.value.displayName
                credentials.saveReadPassword(result.value.readPassword)
                _readPassword.value = result.value.readPassword
                if (accountId != null && displayName != null) {
                    val account = OrganizerAccount(
                        accountId = accountId,
                        displayName = displayName,
                        serverUrl = invite.server,
                        privateKeySeed = keyPair.privateKeySeed,
                        publicKeyBytes = keyPair.publicKeyBytes
                    )
                    credentials.saveAccount(account)
                    _account.value = account
                    LinkOutcome.Organizer(displayName)
                } else {
                    // A viewer invite: no Account/keypair, just standing read access — same
                    // two values a scanned tournament QR provides.
                    credentials.saveViewerServerUrl(invite.server)
                    LinkOutcome.Viewer
                }
            }
            is ServerResult.Failure -> LinkOutcome.Rejected(result.message)
        }
    }

    /** Forgets this device's organizer identity — local credentials only, nothing server-side
     *  (an admin revoking/deleting the account is a separate, deliberate action). The read
     *  password deliberately stays: it unlocks history/leaderboard viewing, which isn't specific
     *  to being an organizer (see [CredentialsStore.clearAccount]). */
    fun unlink() {
        credentials.clearAccount()
        _account.value = null
        _syncStatus.value = null
    }
}
