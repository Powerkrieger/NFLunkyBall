package com.example.nflunkyball.persistence

import java.io.File
import java.util.concurrent.Executors

/**
 * One small JSON file, read synchronously once at construction and written asynchronously from
 * then on. Every store in this package updates a [kotlinx.coroutines.flow.StateFlow] first (so
 * the UI sees the change immediately) and then persists — writing on the caller's thread meant
 * a file write per recorded score / settings toggle on the main thread.
 *
 * All stores share one single-thread executor, so writes and deletes are applied strictly in
 * the order they were requested. Writes go through a temp file + rename so a kill mid-write
 * leaves the previous version intact rather than a truncated file.
 */
internal class JsonFile(private val file: File) {

    /** Null if the file doesn't exist yet. Synchronous — only called from a store's constructor. */
    fun readOrNull(): String? = if (file.exists()) file.readText() else null

    fun write(text: String) {
        writer.execute {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                // Rename can fail on some filesystems if the target exists; fall back to a plain
                // overwrite rather than silently dropping the write.
                file.writeText(text)
                tmp.delete()
            }
        }
    }

    fun delete() {
        writer.execute { file.delete() }
    }

    private companion object {
        val writer = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "json-file-writer").apply { isDaemon = true }
        }
    }
}
