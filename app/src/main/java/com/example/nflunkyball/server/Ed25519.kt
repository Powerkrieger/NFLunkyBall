package com.example.nflunkyball.server

import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.KeyPairGenerator
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import java.security.MessageDigest
import java.security.SecureRandom

private val ED25519_SPEC = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)

data class Ed25519KeyPair(val privateKeySeed: ByteArray, val publicKeyBytes: ByteArray)

/**
 * Thin wrapper around net.i2p.crypto:eddsa producing the standard RFC 8032 wire format (32-byte
 * public key, 64-byte signature) the backend's Python `cryptography` library expects — see
 * NFLunkyBallServer's app/crypto.py. Pure JVM code, no Android dependency, so it's covered by
 * plain unit tests (Ed25519Test) rather than needing an actual device.
 */
object Ed25519 {

    fun generateKeyPair(): Ed25519KeyPair {
        val generator = KeyPairGenerator()
        generator.initialize(ED25519_SPEC, SecureRandom())
        val keyPair = generator.generateKeyPair()
        val privateKey = keyPair.private as EdDSAPrivateKey
        val publicKey = keyPair.public as EdDSAPublicKey
        return Ed25519KeyPair(privateKey.seed, publicKey.abyte)
    }

    fun sign(privateKeySeed: ByteArray, message: ByteArray): ByteArray {
        val privateKey = EdDSAPrivateKey(EdDSAPrivateKeySpec(privateKeySeed, ED25519_SPEC))
        val engine = EdDSAEngine(MessageDigest.getInstance(ED25519_SPEC.hashAlgorithm))
        engine.initSign(privateKey)
        engine.update(message)
        return engine.sign()
    }

    fun verify(publicKeyBytes: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        val publicKey = EdDSAPublicKey(EdDSAPublicKeySpec(publicKeyBytes, ED25519_SPEC))
        val engine = EdDSAEngine(MessageDigest.getInstance(ED25519_SPEC.hashAlgorithm))
        engine.initVerify(publicKey)
        engine.update(message)
        return engine.verify(signature)
    }
}
