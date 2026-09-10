package com.example.nflunkyball.ble

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Tournament JSON is heavily repetitive (field names, UUID-shaped ids) and compresses very
 *  well — this is what keeps a real multi-group tournament's chunk count within
 *  [BleConstants.MAX_CHUNK_COUNT] on the legacy stream instead of blowing past it (see the
 *  broadcaster/receiver docs for why that used to crash the app outright). */
object GzipCodec {

    fun compress(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    fun decompress(bytes: ByteArray): ByteArray =
        GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
}
