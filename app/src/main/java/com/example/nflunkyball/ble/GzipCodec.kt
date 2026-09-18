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

    /**
     * Inflates [bytes], refusing to grow past [MAX_DECOMPRESSED_BYTES]. The input arrives over
     * BLE advertising from whoever is nearby, and gzip can expand ~1000x — without a cap a
     * deliberately crafted broadcast could make every viewer allocate tens of megabytes.
     * A real tournament is well under 100 KB uncompressed.
     */
    fun decompress(bytes: ByteArray): ByteArray =
        GZIPInputStream(bytes.inputStream()).use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                require(out.size() + read <= MAX_DECOMPRESSED_BYTES) { "Decompressed payload exceeds $MAX_DECOMPRESSED_BYTES bytes" }
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }

    const val MAX_DECOMPRESSED_BYTES = 4 * 1024 * 1024
}
