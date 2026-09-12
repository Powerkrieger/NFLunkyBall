package com.example.nflunkyball.server

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Encrypts short strings for at-rest storage. [decrypt] returns null for anything it can't
 *  open (wrong key after a device wipe/restore, corrupted value) — callers treat that as
 *  "not stored". */
interface SecretCipher {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String?
}

/**
 * AES-256-GCM with a key supplied by [keyProvider]; each value is stored as
 * base64(iv ‖ ciphertext ‖ tag) with a fresh random IV. The Android build uses
 * [androidKeystoreKey]; tests supply an in-memory key so the exact same code path is
 * exercised on the JVM.
 */
@OptIn(ExperimentalEncodingApi::class)
class AesGcmCipher(private val keyProvider: () -> SecretKey) : SecretCipher {

    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.encodeToByteArray())
        return Base64.encode(byteArrayOf(iv.size.toByte()) + iv + encrypted)
    }

    override fun decrypt(ciphertext: String): String? = runCatching {
        val bytes = Base64.decode(ciphertext)
        val ivLength = bytes[0].toInt()
        val iv = bytes.copyOfRange(1, 1 + ivLength)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_BITS, iv))
        cipher.doFinal(bytes, 1 + ivLength, bytes.size - 1 - ivLength).decodeToString()
    }.getOrNull()

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val KEYSTORE = "AndroidKeyStore"

        /**
         * The app's one credentials key, generated in the hardware-backed Android Keystore on
         * first use and never exportable. Deliberately not bound to user authentication: BLE
         * hosting keeps running with the screen locked, and lock-screen changes must not
         * invalidate the organizer's identity. Keystore keys don't survive a device wipe or a
         * backup restore — [decrypt] then simply fails and the user relinks.
         */
        fun androidKeystoreKey(alias: String): () -> SecretKey = {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (keyStore.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
                init(
                    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
                generateKey()
            }
        }
    }
}
