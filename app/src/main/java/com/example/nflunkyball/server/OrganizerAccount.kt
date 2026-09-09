package com.example.nflunkyball.server

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class OrganizerAccount(
    val accountId: Int,
    val displayName: String,
    val privateKeySeed: ByteArray,
    val publicKeyBytes: ByteArray
)

/**
 * On-device storage for server credentials: the organizer's account/keypair (if this device has
 * linked one — the private key never leaves it) and the shared group read password (used by
 * both the organizer, to embed in the QR it shows viewers, and viewers themselves, to unlock the
 * history screens).
 */
class ServerCredentialsStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "server_credentials",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun loadAccount(): OrganizerAccount? {
        val accountId = prefs.getInt(KEY_ACCOUNT_ID, -1)
        if (accountId == -1) return null
        val displayName = prefs.getString(KEY_DISPLAY_NAME, null) ?: return null
        val privateKeySeed = prefs.getString(KEY_PRIVATE_KEY, null)
            ?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        val publicKeyBytes = prefs.getString(KEY_PUBLIC_KEY, null)
            ?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        return OrganizerAccount(accountId, displayName, privateKeySeed, publicKeyBytes)
    }

    fun saveAccount(account: OrganizerAccount) {
        prefs.edit()
            .putInt(KEY_ACCOUNT_ID, account.accountId)
            .putString(KEY_DISPLAY_NAME, account.displayName)
            .putString(KEY_PRIVATE_KEY, Base64.encodeToString(account.privateKeySeed, Base64.NO_WRAP))
            .putString(KEY_PUBLIC_KEY, Base64.encodeToString(account.publicKeyBytes, Base64.NO_WRAP))
            .apply()
    }

    fun loadReadPassword(): String? = prefs.getString(KEY_READ_PASSWORD, null)

    fun saveReadPassword(password: String) {
        prefs.edit().putString(KEY_READ_PASSWORD, password).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_ACCOUNT_ID = "account_id"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_PRIVATE_KEY = "private_key_seed"
        const val KEY_PUBLIC_KEY = "public_key"
        const val KEY_READ_PASSWORD = "read_password"
    }
}
