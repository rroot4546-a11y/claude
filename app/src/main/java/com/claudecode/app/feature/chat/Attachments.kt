package com.claudecode.app.feature.chat

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import com.claudecode.app.core.paths.AppPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * User attachments (photos / files) staged in app-private storage so the
 * local llama-server can consume them (base64 data URIs for vision,
 * inlined text for documents).
 */
object Attachments {

    private const val TAG = "Attachments"
    const val MAX_TEXT_BYTES = 50 * 1024
    const val MAX_IMAGE_DIM = 1568

    data class Item(
        val name: String,
        val mime: String,
        val size: Long,
        val isImage: Boolean,
        val file: File,
    )

    fun dir(context: Context): File = File(context.filesDir, "attachments").apply { mkdirs() }

    suspend fun stage(context: Context, uri: Uri): Item = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        var name = "file"
        var size = -1L
        var mime = cr.getType(uri) ?: "application/octet-stream"
        cr.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let {
                    name = c.getString(it) ?: name
                }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let {
                    size = c.getLong(it)
                }
            }
        }
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeIf { it.isNotBlank() } ?: "file"
        var target = File(dir(context), safe)
        var n = 1
        while (target.exists()) {
            target = File(dir(context), "${safe.substringBeforeLast('.', safe)}-$n.${safe.substringAfterLast('.', "bin")}")
            n++
        }
        cr.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: throw RuntimeException("Cannot read picked file")
        if (size < 0) size = target.length()
        val isImage = mime.startsWith("image/") ||
            target.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp", "gif")
        if (isImage && mime == "application/octet-stream") mime = "image/jpeg"
        Item(name, mime, size, isImage, target)
    }

    /** First [MAX_TEXT_BYTES] of a text/code file, or null if binary. */
    fun readTextSnippet(file: File): String? {
        return try {
            val bytes = file.inputStream().use {
                val buf = ByteArray(MAX_TEXT_BYTES + 1)
                val r = it.read(buf)
                if (r < 0) ByteArray(0) else buf.copyOf(r)
            }
            if (bytes.isEmpty()) return ""
            // Binary sniff: NUL byte → not text.
            if (bytes.contains(0)) return null
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "readText: ${e.message}")
            null
        }
    }

    /** Downscale + JPEG + base64 data URI for OpenAI-style image_url parts. */
    fun imageToDataUri(file: File, mime: String): String? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > MAX_IMAGE_DIM || bounds.outHeight / sample > MAX_IMAGE_DIM) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
            val out = ByteArrayOutputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
            bmp.recycle()
            "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w(TAG, "imageToDataUri: ${e.message}")
            null
        }
    }

    fun cleanup(context: Context, keepNewerThanMs: Long = 24 * 3600 * 1000L) {
        try {
            val cutoff = System.currentTimeMillis() - keepNewerThanMs
            dir(context).listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        } catch (_: Exception) {
        }
    }
}
