package com.example.nflunkyball.server

import com.example.nflunkyball.fakes.FakeSharedPreferences
import com.example.nflunkyball.fakes.testAccount
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The real store and the real AES-GCM cipher, with an in-memory key standing in for the
 *  Android Keystore one — the only Android-specific part. */
class KeystoreCredentialsStoreTest {

    private fun aesKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val key = aesKey()
    private val prefs = FakeSharedPreferences()
    private val store = KeystoreCredentialsStore(prefs, AesGcmCipher { key })

    @Test
    fun `account round-trips and nothing is stored in the clear`() {
        val account = testAccount("https://srv")
        store.saveAccount(account)

        val loaded = store.loadAccount()!!
        assertEquals(account.accountId, loaded.accountId)
        assertEquals(account.displayName, loaded.displayName)
        assertEquals(account.serverUrl, loaded.serverUrl)
        assertArrayEquals(account.privateKeySeed, loaded.privateKeySeed)
        assertArrayEquals(account.publicKeyBytes, loaded.publicKeyBytes)

        // Every stored value is ciphertext: none of the plaintexts appear anywhere in the file.
        val stored = prefs.values.values.joinToString("\n") { it.toString() }
        for (plain in listOf("Orga", "https://srv", kotlin.io.encoding.Base64.encode(account.privateKeySeed), kotlin.io.encoding.Base64.encode(account.publicKeyBytes))) {
            assertFalse("found '$plain' in the clear", stored.contains(plain))
        }
        assertNotEquals("7", prefs.values["account_id"])
        assertTrue(prefs.values.values.all { it is String })
    }

    @Test
    fun `read password and viewer url round-trip, password encrypted`() {
        store.saveReadPassword("hunter2")
        store.saveViewerServerUrl("https://srv")
        assertEquals("hunter2", store.loadReadPassword())
        assertEquals("https://srv", store.loadViewerServerUrl())
        assertNotEquals("hunter2", prefs.values["read_password"])
        assertNotEquals("https://srv", prefs.values["viewer_server_url"])
    }

    @Test
    fun `each encryption uses a fresh IV`() {
        val cipher = AesGcmCipher { key }
        assertNotEquals(cipher.encrypt("same"), cipher.encrypt("same"))
        assertEquals("same", cipher.decrypt(cipher.encrypt("same")))
    }

    @Test
    fun `a different key (device wipe or restore) reads as not linked instead of crashing`() {
        store.saveAccount(testAccount())
        store.saveReadPassword("pw")

        val afterRestore = KeystoreCredentialsStore(prefs, AesGcmCipher { aesKey() })
        assertNull(afterRestore.loadAccount())
        assertNull(afterRestore.loadReadPassword())
        assertNull(afterRestore.loadViewerServerUrl())
    }

    @Test
    fun `garbage ciphertext reads as absent`() {
        prefs.values["read_password"] = "not base64!!"
        assertNull(store.loadReadPassword())
        prefs.values["read_password"] = "AAAA"
        assertNull(store.loadReadPassword())
    }

    @Test
    fun `clearAccount keeps the read password and viewer url`() {
        store.saveAccount(testAccount())
        store.saveReadPassword("pw")
        store.saveViewerServerUrl("https://srv")
        store.clearAccount()
        assertNull(store.loadAccount())
        assertEquals("pw", store.loadReadPassword())
        assertEquals("https://srv", store.loadViewerServerUrl())
        assertTrue(prefs.values.keys.none { it.startsWith("private") || it == "account_id" })
    }
}
