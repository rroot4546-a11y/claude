package com.claudecode.app.feature.localai

import android.content.Context
import android.util.Log
import com.claudecode.app.core.net.LocalHttp
import com.claudecode.app.core.paths.AppPaths
import com.claudecode.app.core.prefs.AppPrefs
import com.claudecode.app.core.process.ProcessManager
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File

/**
 * Lifecycle of the on-device OpenAI-compatible server (`llama-server`).
 * Serves the active GGUF model on 127.0.0.1:18925 for both the native
 * LocalChat UI and the linked OpenCode provider.
 */
class LlamaServer(
    private val context: Context,
    private val prefs: AppPrefs,
    private val store: GgufStore,
) {

    companion object {
        private const val TAG = "LlamaServer"
        const val API_KEY = "zclaw-local"
    }

    private val pm = ProcessManager(TAG)

    val baseUrl: String get() = AppPaths.LLAMA_BASE_URL
    val isRunning: Boolean get() = pm.isRunning
    fun lastError(): String = pm.lastError()

    suspend fun health(): Boolean {
        if (LocalHttp.get("$baseUrl/models") != null) return true
        return LocalHttp.probe("$baseUrl/models")
    }

    /** /v1/models → first model id (we force it via --alias, see [start]). */
    suspend fun servedModelId(): String? {
        return try {
            val body = LocalHttp.get("$baseUrl/models") ?: return null
            val data = JSONObject(body).optJSONArray("data") ?: return null
            if (data.length() == 0) return null
            data.getJSONObject(0).optString("id", null)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Start serving [modelId] (defaults to the user's active model).
     * Returns true once /v1/models answers.
     */
    suspend fun start(modelId: String? = null): Boolean =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val id = modelId ?: prefs.activeModelId()
                ?: return@withContext false.also { Log.w(TAG, "no active model") }
            val modelFile = store.resolveFile(id)
                ?: return@withContext false.also { Log.w(TAG, "model file missing: $id") }
            if (!LlamaInstaller.isInstalled(context)) {
                Log.w(TAG, "llama runtime not installed")
                return@withContext false
            }
            val llamaDir = AppPaths.llamaDir(context)
            val server = File(llamaDir, "llama-server")
            // NOTE: no --api-key on purpose. The server is bound to loopback
            // only, and our own health/models probes send no auth header —
            // enforcing a key made every probe 401 and start() could never
            // succeed ("local model not working"). Clients may still send
            // an Authorization header; it is simply ignored.
            val cmd = listOf(
                server.absolutePath,
                "--host", "127.0.0.1",
                "--port", AppPaths.LLAMA_PORT.toString(),
                "-m", modelFile.absolutePath,
                "--alias", id,
                "-c", prefs.ctxSize().toString(),
                "-t", prefs.threads().toString(),
            )
            val env = mapOf(
                "PATH" to "/system/bin",
                "LD_LIBRARY_PATH" to llamaDir.absolutePath,
                "HOME" to context.cacheDir.absolutePath,
                "TMPDIR" to context.cacheDir.absolutePath,
            )
            if (!pm.start(command = cmd, env = env, workDir = llamaDir, pidFile = File(llamaDir, "llama-server.pid"))) {
                return@withContext false
            }
            // Model load on phones can take a while — poll up to ~3 minutes.
            repeat(90) {
                delay(2_000)
                if (!pm.isRunning) {
                    Log.e(TAG, "llama-server died during load:\n${pm.lastError()}")
                    return@withContext false
                }
                if (health()) {
                    Log.i(TAG, "serving '$id' at $baseUrl")
                    return@withContext true
                }
            }
            Log.e(TAG, "llama-server never became ready")
            false
        }

    fun stop() = pm.stop()
}
