package com.claudecode.app.feature.localai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.claudecode.app.core.paths.AppPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * On-device GGUF model library: import from storage (SAF), download from
 * URL / HuggingFace, validate magic bytes, track metadata.
 */
class GgufStore(private val context: Context) {

    companion object {
        private const val TAG = "GgufStore"
        private const val META = "models.json"
    }

    data class GgufModel(
        val id: String,
        val fileName: String,
        val sizeBytes: Long,
        val addedAt: Long,
        val source: String,
    )

    private val dir: File get() = AppPaths.modelsDir(context)
    private val metaFile: File get() = File(dir, META)

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    fun list(): List<GgufModel> {
        if (!metaFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(metaFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                GgufModel(
                    id = o.getString("id"),
                    fileName = o.getString("fileName"),
                    sizeBytes = o.optLong("sizeBytes"),
                    addedAt = o.optLong("addedAt"),
                    source = o.optString("source", "file"),
                )
            }.filter { File(dir, it.fileName).exists() }
        // NOTE: never filter via resolveFile() here — it calls list()
        // again and that mutual recursion overflows the stack as soon as
        // the library is non-empty (Library stayed 0 forever).
        } catch (e: Exception) {
            Log.w(TAG, "bad metadata: ${e.message}")
            emptyList()
        }
    }

    fun resolveFile(id: String): File? {
        val m = list().find { it.id == id } ?: return null
        return File(dir, m.fileName)
    }

    /** Model id = filename without extension (used as llama-server --alias). */
    fun modelIdFor(fileName: String): String =
        fileName.substringAfterLast("/").removeSuffix(".gguf").ifBlank { "local-model" }

    private fun saveMeta(models: List<GgufModel>) {
        val arr = JSONArray()
        models.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("fileName", it.fileName)
                    .put("sizeBytes", it.sizeBytes)
                    .put("addedAt", it.addedAt)
                    .put("source", it.source),
            )
        }
        metaFile.writeText(arr.toString())
    }

    private fun register(fileName: String, source: String): GgufModel {
        val models = list().toMutableList()
        models.removeAll { it.fileName == fileName }
        val m = GgufModel(
            id = modelIdFor(fileName),
            fileName = fileName,
            sizeBytes = File(dir, fileName).length(),
            addedAt = System.currentTimeMillis(),
            source = source,
        )
        models.add(m)
        saveMeta(models)
        return m
    }

    /** GGUF files start with the magic bytes "GGUF". */
    fun isValidGguf(file: File): Boolean {
        if (!file.exists() || file.length() < 8) return false
        return try {
            file.inputStream().use {
                val magic = ByteArray(4)
                it.read(magic) == 4 && magic.decodeToString() == "GGUF"
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun uniqueName(wanted: String): String {
        var name = wanted.substringAfterLast("/").takeIf { it.isNotBlank() } ?: "model.gguf"
        if (!name.endsWith(".gguf", ignoreCase = true)) name += ".gguf"
        var candidate = name
        var n = 1
        while (File(dir, candidate).exists()) {
            candidate = name.removeSuffix(".gguf") + "-$n.gguf"
            n++
        }
        return candidate
    }

    /** Copy a user-picked file (Storage Access Framework Uri) into the library. */
    suspend fun importFromUri(uri: Uri, onProgress: (Long, Long) -> Unit = { _, _ -> }): GgufModel =
        withContext(Dispatchers.IO) {
            val cr = context.contentResolver
            var displayName = "model.gguf"
            var total = -1L
            cr.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let {
                        displayName = c.getString(it) ?: displayName
                    }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let {
                        total = c.getLong(it)
                    }
                }
            }
            val target = File(dir, uniqueName(displayName))
            try {
                var done = 0L
                cr.openInputStream(uri)?.use { input ->
                    target.outputStream().use { out ->
                        done = copyStream(input, out, total, onProgress)
                    }
                } ?: throw RuntimeException("Cannot open picked file")
                if (total > 0 && done < total) {
                    throw RuntimeException("Copy stopped at ${done / 1048576}MB of ${total / 1048576}MB — retry")
                }
                if (!isValidGguf(target)) {
                    throw RuntimeException("Not a GGUF model (bad magic bytes)")
                }
                register(target.name, "file")
            } catch (e: Exception) {
                target.delete() // never leave a partial multi-hundred-MB file
                throw e
            }
        }

    /** Download a .gguf from a direct URL (incl. HuggingFace /resolve/ links). */
    suspend fun downloadFromUrl(url: String, onProgress: (Long, Long) -> Unit = { _, _ -> }): GgufModel =
        withContext(Dispatchers.IO) {
            val fileName = uniqueName(url.substringBefore("?").substringAfterLast("/"))
            val target = File(dir, fileName)
            val req = Request.Builder().url(url).header("User-Agent", "zclaw/2.0").get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
                val body = resp.body ?: throw RuntimeException("Empty response")
                val total = body.contentLength()
                try {
                    body.byteStream().use { input ->
                        target.outputStream().use { out ->
                            val done = copyStream(input, out, total, onProgress)
                            if (total > 0 && done < total) {
                                throw RuntimeException("Download stopped at ${done / 1048576}MB — retry")
                            }
                        }
                    }
                } catch (e: Exception) {
                    target.delete()
                    throw e
                }
            }
            if (!isValidGguf(target)) {
                target.delete()
                throw RuntimeException("Downloaded file is not a GGUF model")
            }
            register(target.name, "url")
        }

    /**
     * Robust stream copy. Two hang guards: (1) stop as soon as the advertised
     * byte count is reached instead of waiting for a late EOF from flaky
     * providers, (2) throttled progress so the UI thread is not flooded.
     */
    private fun copyStream(
        input: java.io.InputStream,
        out: java.io.OutputStream,
        total: Long,
        onProgress: (Long, Long) -> Unit,
    ): Long {
        val buf = ByteArray(512 * 1024)
        var done = 0L
        var lastPct = -1
        var lastEmit = 0L
        while (true) {
            val r = input.read(buf)
            if (r == -1) break
            out.write(buf, 0, r)
            done += r
            if (total > 0 && done >= total) {
                onProgress(done, total)
                break
            }
            val now = System.currentTimeMillis()
            if (total > 0) {
                val pct = ((done * 100) / total).toInt()
                if (pct != lastPct && now - lastEmit > 400) {
                    lastPct = pct
                    lastEmit = now
                    onProgress(done, total)
                }
            } else if (now - lastEmit > 500) {
                lastEmit = now
                onProgress(done, total)
            }
        }
        out.flush()
        return done
    }

    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        val m = list().find { it.id == id } ?: return@withContext false
        File(dir, m.fileName).delete()
        saveMeta(list().filter { it.id != id })
        true
    }
}
