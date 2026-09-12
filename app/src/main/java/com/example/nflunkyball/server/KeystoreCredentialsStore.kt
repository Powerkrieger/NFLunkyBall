package com.example.nflunkyball.server

import android.content.Context
import android.content.SharedPreferences
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * [CredentialsStore] on plain SharedPreferences with every value encrypted by a [SecretCipher]
 * — on device, AES-GCM under an Android Keystore key (see [AesGcmCipher.androidKeystoreKey]).
 * Replaces the deprecated Jetpack `EncryptedSharedPreferences`, which did the same thing via
 * Tink; existing installs are carried over once by [LegacyCredentialsMigration]. Only the fixed
 * key names are plaintext; they carry no information.
 *
 * Any value that fails to decrypt makes the whole account read as absent: a half-usable
 * account (id but no key) would only produce confusing sync failures.
 */
@OptIn(ExperimentalEncodingApi::class)
class KeystoreCredentialsStore(private val prefs: SharedPreferences, private val cipher: SecretCipher) : CredentialsStore {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        AesGcmCipher(AesGcmCipher.androidKeystoreKey(KEY_ALIAS))
    )

    override fun loadAccount(): OrganizerAccount? {
        val accountId = read(KEY_ACCOUNT_ID)?.toIntOrNull() ?: return null
        val displayName = read(KEY_DISPLAY_NAME) ?: return null
        val serverUrl = read(KEY_SERVER_URL) ?: return null
        val privateKeySeed = read(KEY_PRIVATE_KEY)?.let { Base64.decode(it) } ?: return null
        val publicKeyBytes = read(KEY_PUBLIC_KEY)?.let { Base64.decode(it) } ?: return null
        return OrganizerAccount(accountId, displayName, serverUrl, privateKeySeed, publicKeyBytes)
    }

    override fun saveAccount(account: OrganizerAccount) {
        prefs.edit()
            .putString(KEY_ACCOUNT_ID, cipher.encrypt(account.accountId.toString()))
            .putString(KEY_DISPLAY_NAME, cipher.encrypt(account.displayName))
            .putString(KEY_SERVER_URL, cipher.encrypt(account.serverUrl))
            .putString(KEY_PRIVATE_KEY, cipher.encrypt(Base64.encode(account.privateKeySeed)))
            .putString(KEY_PUBLIC_KEY, cipher.encrypt(Base64.encode(account.publicKeyBytes)))
            .apply()
    }

    override fun loadReadPassword(): String? = read(KEY_READ_PASSWORD)

    override fun saveReadPassword(password: String) {
        prefs.edit().putString(KEY_READ_PASSWORD, cipher.encrypt(password)).apply()
    }

    override fun loadViewerServerUrl(): String? = read(KEY_VIEWER_SERVER_URL)

    override fun saveViewerServerUrl(serverUrl: String) {
        prefs.edit().putString(KEY_VIEWER_SERVER_URL, cipher.encrypt(serverUrl)).apply()
    }

    override fun clearAccount() {
        prefs.edit()
            .remove(KEY_ACCOUNT_ID)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_SERVER_URL)
            .remove(KEY_PRIVATE_KEY)
            .remove(KEY_PUBLIC_KEY)
            .apply()
    }

    private fun read(key: String): String? = prefs.getString(key, null)?.let(cipher::decrypt)

    companion object {
        const val PREFS_NAME = "credentials"
        private const val KEY_ALIAS = "nflunkyball_credentials"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_PRIVATE_KEY = "private_key_seed"
        private const val KEY_PUBLIC_KEY = "public_key"
        private const val KEY_READ_PASSWORD = "read_password"
        private const val KEY_VIEWER_SERVER_URL = "viewer_server_url"
    }
}
