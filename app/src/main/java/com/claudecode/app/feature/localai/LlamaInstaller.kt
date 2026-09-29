package com.claudecode.app.feature.localai

import android.content.Context
import android.util.Log
import com.claudecode.app.core.paths.AppPaths
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches the official llama.cpp Android (arm64) runtime on demand and
 * extracts `llama-server` + its shared libraries into [AppPaths.llamaDir].
 * Nothing is bundled in the APK — the runtime (~78MB) downloads once.
 */
object LlamaInstaller {

    private const val TAG = "LlamaInstaller"
    const val LLAMA_TAG = "b10549"
    const val DOWNLOAD_URL =
        "https://github.com/ggml-org/llama.cpp/releases/download/b10549/llama-b10549-bin-android-arm64.tar.gz"

    fun isInstalled(context: Context): Boolean {
        val bin = File(AppPaths.llamaDir(context), "llama-server")
        return bin.exists() && bin.canExecute()
    }

    fun installedVersion(context: Context): String {
        val vf = File(AppPaths.llamaDir(context), "VERSION")
        return if (vf.exists()) vf.readText().trim() else ""
    }

    suspend fun install(context: Context, onProgress: (Int) -> Unit) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val dir = AppPaths.llamaDir(context)
        dir.mkdirs()
        val tmp = File(context.cacheDir, "llama-android.tar.gz")
        try {
            download(DOWNLOAD_URL, tmp, onProgress)
            extract(tmp, dir)
            // Executable bit on binaries and shared objects.
            dir.listFiles()?.forEach { f ->
                if (f.isFile && (f.name.startsWith("llama") || f.name.endsWith(".so"))) {
                    f.setExecutable(true, true)
                }
            }
            val server = File(dir, "llama-server")
            if (!server.exists()) throw RuntimeException("llama-server missing after extract")
            server.setExecutable(true, true)
            File(dir, "VERSION").writeText(LLAMA_TAG)
            onProgress(100)
            Log.i(TAG, "llama.cpp $LLAMA_TAG runtime ready at $dir")
        } finally {
            tmp.delete()
        }
    }

    private fun download(url: String, target: File, onProgress: (Int) -> Unit) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        // 78MB runtime on slow mobile data needs patience.
        conn.readTimeout = 300_000
        conn.instanceFollowRedirects = true
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            FileOutputStream(target).use { out ->
                val buf = ByteArray(256 * 1024)
                var r: Int
                var done = 0L
                var last = -1
                while (input.read(buf).also { r = it } != -1) {
                    out.write(buf, 0, r)
                    done += r
                    if (total > 0) {
                        val pct = ((done * 90) / total).toInt() // 0-90 for download
                        if (pct != last) {
                            last = pct
                            onProgress(pct)
                        }
                    }
                }
            }
        }
        if (target.length() < 10L * 1024 * 1024) {
            throw RuntimeException("Runtime download incomplete (${target.length()} bytes)")
        }
    }

    /** Zip-slip-safe extraction, flattening the single top-level directory. */
    private fun extract(archive: File, dest: File) {
        TarArchiveInputStream(
            GzipCompressorInputStream(BufferedInputStream(archive.inputStream())),
        ).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                // Strip the "llama-bXXXX/" prefix.
                val name = entry.name.substringAfter("/", "")
                if (name.isNotEmpty()) {
                    val out = File(dest, name)
                    // Zip-slip guard.
                    if (!out.canonicalPath.startsWith(dest.canonicalPath)) {
                        throw RuntimeException("Unsafe entry: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { o -> tar.copyTo(o) }
                    }
                }
                entry = tar.nextEntry
            }
        }
    }
}
