package com.toimg.app.core.util

import android.content.Context
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Per-session/per-source durable scan journal.
 * A URI is appended only after its image + source link + scan counters have committed in Room.
 * A crash between Room commit and journal append causes a harmless idempotent re-read; the reverse
 * ordering is deliberately impossible, so a journal entry can never hide an uncommitted image.
 */
object ScanCheckpointStore {
    private const val DIRECTORY = "scan-checkpoints"

    fun load(context: Context, sessionId: String?, sourceId: String?): MutableSet<String> {
        val file = fileFor(context, sessionId, sourceId) ?: return mutableSetOf()
        if (!file.exists()) return mutableSetOf()
        return runCatching {
            file.readLines(Charsets.UTF_8).mapNotNullTo(mutableSetOf()) { line ->
                runCatching { String(Base64.decode(line, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8) }.getOrNull()
            }
        }.getOrDefault(mutableSetOf())
    }

    /**
     * Returns false when the journal cannot be durably appended (for example, low storage).
     * The caller may continue because the corresponding Room batch has already committed;
     * the next process run will safely replay uncheckpointed rows (at-least-once semantics).
     */
    @Synchronized
    fun append(context: Context, sessionId: String?, sourceId: String?, itemUris: Collection<String>): Boolean {
        if (itemUris.isEmpty()) return true
        val file = fileFor(context, sessionId, sourceId) ?: return true
        return try {
            val parent = file.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) return false
            // The caller owns a per-source in-memory set and appends each URI once. Avoid re-reading
            // the growing journal on every batch (quadratic behavior for large folders).
            FileOutputStream(file, true).use { stream ->
                val writer = stream.bufferedWriter(Charsets.UTF_8)
                itemUris.filter { it.isNotBlank() }.forEach { uri ->
                    writer.append(Base64.encodeToString(uri.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
                    writer.newLine()
                }
                writer.flush()
                stream.fd.sync()
            }
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    /** Metadata token invalidates a checkpoint if the same provider URI changes before resume. */
    fun token(uri: String, size: Long, modifiedAt: Long): String = "$uri\u0000$size\u0000$modifiedAt"

    /** Providers that omit size or modification time cannot safely support metadata-only resume. */
    fun hasReliableMetadata(size: Long, modifiedAt: Long): Boolean = size > 0L && modifiedAt > 0L

    fun clearSession(context: Context, sessionId: String?) {
        if (sessionId.isNullOrBlank()) return
        val prefix = digest(sessionId) + "_"
        File(context.filesDir, DIRECTORY).listFiles()?.filter { it.name.startsWith(prefix) }?.forEach { it.delete() }
    }

    private fun fileFor(context: Context, sessionId: String?, sourceId: String?): File? {
        if (sessionId.isNullOrBlank() || sourceId.isNullOrBlank()) return null
        val dir = File(context.filesDir, DIRECTORY)
        return File(dir, "${digest(sessionId)}_${digest(sourceId)}.journal")
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).take(12).joinToString("") { "%02x".format(it) }
}
