package com.example.nflunkyball.server

import android.content.Context
import android.util.Log

/**
 * One-shot move of an existing install's credentials from the deprecated
 * `EncryptedSharedPreferences` file to [KeystoreCredentialsStore]. Runs on every app start
 * but is a no-op once the old file is gone. The account's keypair is copied verbatim, so the
 * server never notices — no re-invite needed.
 *
 * Reading the old store can throw (Tink keyset corrupted, Keystore master key gone after a
 * restore): that's caught and the old file is dropped anyway, since it was unreadable for the
 * app too — the user relinks, exactly as they would have had to before.
 */
object LegacyCredentialsMigration {
    private const val TAG = "CredentialsMigration"

    fun migrateIfNeeded(context: Context, target: CredentialsStore) {
        val legacyFile = context.getSharedPreferencesFile(LegacyServerCredentialsStore.PREFS_NAME)
        if (!legacyFile.exists()) return
        runCatching {
            val legacy = LegacyServerCredentialsStore(context)
            legacy.loadAccount()?.let { target.saveAccount(it) }
            legacy.loadReadPassword()?.let { target.saveReadPassword(it) }
            legacy.loadViewerServerUrl()?.let { target.saveViewerServerUrl(it) }
            Log.i(TAG, "Migrated credentials to the Keystore-backed store")
        }.onFailure { Log.w(TAG, "Legacy credentials unreadable — dropping them; the user will need to relink", it) }
        context.deleteSharedPreferences(LegacyServerCredentialsStore.PREFS_NAME)
    }

    private fun Context.getSharedPreferencesFile(name: String) =
        java.io.File(applicationInfo.dataDir, "shared_prefs/$name.xml")
}
