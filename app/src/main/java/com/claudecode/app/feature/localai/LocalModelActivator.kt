package com.claudecode.app.feature.localai

import com.claudecode.app.core.prefs.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One shared flow everywhere: pick GGUF → serve it locally.
 * No engine linking in this app (the Claude agent uses the cloud API;
 * local chat talks to llama-server directly).
 */
object LocalModelActivator {

    data class Result(val ok: Boolean, val message: String)

    suspend fun activate(
        prefs: AppPrefs,
        server: LlamaServer,
        modelId: String,
    ): Result = withContext(Dispatchers.IO) {
        try {
            prefs.setActiveModelId(modelId)
            server.stop()
            if (!server.start(modelId)) {
                return@withContext Result(
                    false,
                    "Server failed to start:\n${server.lastError().take(500)}",
                )
            }
            Result(true, "Model $modelId is live ✓")
        } catch (e: Exception) {
            Result(false, "Error: ${e.message}")
        }
    }
}
