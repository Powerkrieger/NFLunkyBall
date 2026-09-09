package com.example.nflunkyball.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ed25519Test {

    @Test
    fun `generated keys match RFC 8032 wire format sizes`() {
        val keyPair = Ed25519.generateKeyPair()
        assertEquals(32, keyPair.privateKeySeed.size)
        assertEquals(32, keyPair.publicKeyBytes.size)
    }

    @Test
    fun `signature is 64 bytes and verifies against the matching public key`() {
        val keyPair = Ed25519.generateKeyPair()
        val message = "tournament123|1234567890|abcdef".toByteArray()

        val signature = Ed25519.sign(keyPair.privateKeySeed, message)

        assertEquals(64, signature.size)
        assertTrue(Ed25519.verify(keyPair.publicKeyBytes, message, signature))
    }

    @Test
    fun `signature does not verify against a tampered message`() {
        val keyPair = Ed25519.generateKeyPair()
        val message = "tournament123|1234567890|abcdef".toByteArray()
        val signature = Ed25519.sign(keyPair.privateKeySeed, message)

        val tampered = "tournament123|1234567890|ffffff".toByteArray()

        assertFalse(Ed25519.verify(keyPair.publicKeyBytes, tampered, signature))
    }

    @Test
    fun `signature does not verify against a different key pair`() {
        val keyPair = Ed25519.generateKeyPair()
        val otherKeyPair = Ed25519.generateKeyPair()
        val message = "tournament123|1234567890|abcdef".toByteArray()
        val signature = Ed25519.sign(keyPair.privateKeySeed, message)

        assertFalse(Ed25519.verify(otherKeyPair.publicKeyBytes, message, signature))
    }
}
