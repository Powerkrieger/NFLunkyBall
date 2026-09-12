package com.example.nflunkyball.server


data class OrganizerAccount(
    val accountId: Int,
    val displayName: String,
    val serverUrl: String,
    val privateKeySeed: ByteArray,
    val publicKeyBytes: ByteArray
)

/**
 * On-device storage for server credentials: the organizer's account/keypair (if this device has
 * linked one — the private key never leaves it) and the shared group read password (used by
 * both the organizer, to embed in the QR it shows viewers, and viewers themselves, to unlock the
 * history screens). Interface so JVM tests can substitute an in-memory one.
 */
interface CredentialsStore {
    fun loadAccount(): OrganizerAccount?
    fun saveAccount(account: OrganizerAccount)
    fun loadReadPassword(): String?
    fun saveReadPassword(password: String)
    /** Last known server address for a pure viewer (never linked an organizer account) —
     *  so returning to History after reopening the app works without rescanning a QR. */
    fun loadViewerServerUrl(): String?
    fun saveViewerServerUrl(serverUrl: String)
    /** Clears only the organizer identity (which account, and its keypair) — not the shared
     *  read password or last-known viewer server URL, since neither is specific to *being* an
     *  organizer (the read password unlocks history for viewers too; see [OrganizerViewModel.unlinkAccount]
     *  for why unlinking shouldn't reach further than the account itself). */
    fun clearAccount()
}
