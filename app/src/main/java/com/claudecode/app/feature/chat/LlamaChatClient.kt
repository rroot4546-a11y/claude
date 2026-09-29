package com.claudecode.app.feature.chat

import com.claudecode.app.core.net.LocalHttp
import com.claudecode.app.feature.localai.LlamaServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal OpenAI-compatible chat client against the on-device llama-server.
 * Powers the native LocalChat screen (streaming, no WebView, fully offline).
 */
class LlamaChatClient(private val baseUrl: String = com.claudecode.app.core.paths.AppPaths.LLAMA_BASE_URL) {

    data class Msg(val role: String, val content: String)

    private val JSON = "application/json; charset=utf-8".toMediaType()

    suspend fun models(): List<String> = withContext(Dispatchers.IO) {
        try {
            val body = LocalHttp.get("$baseUrl/models") ?: return@withContext emptyList()
            val data = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
            (0 until data.length()).mapNotNull {
                data.optJSONObject(it)?.optString("id")?.takeIf { s -> s.isNotBlank() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    data class GenOptions(
        val temperature: Float = 0.7f,
        val topP: Float = 0.95f,
        val topK: Float = 40f,
        val minP: Float = 0.05f,
        val repeatPenalty: Float = 1.1f,
        val maxTokens: Int = 1024,
    )

    /** Stream assistant tokens for [history] + [userText]. */
    fun stream(history: List<Msg>, userText: String, model: String, opts: GenOptions = GenOptions()): Flow<String> {
        val parts = JSONArray().put(JSONObject().put("type", "text").put("text", userText))
        return streamParts(history, parts, model, opts)
    }

    /**
     * Stream with full OpenAI content parts (text + image_url data URIs).
     * Vision works only with multimodal GGUF models; otherwise the server
     * answers with an error which surfaces to the caller.
     */
    fun streamParts(
        history: List<Msg>,
        userParts: JSONArray,
        model: String,
        opts: GenOptions = GenOptions(),
    ): Flow<String> = callbackFlow {
        val arr = JSONArray()
        history.forEach { arr.put(JSONObject().put("role", it.role).put("content", it.content)) }
        arr.put(JSONObject().put("role", "user").put("content", userParts))
        val payload = JSONObject()
            .put("model", model)
            .put("messages", arr)
            .put("stream", true)
            .put("temperature", opts.temperature.toDouble())
            .put("top_p", opts.topP.toDouble())
            .put("top_k", opts.topK.toInt())
            .put("min_p", opts.minP.toDouble())
            .put("repeat_penalty", opts.repeatPenalty.toDouble())
            .put("max_tokens", opts.maxTokens)
        val req = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer ${LlamaServer.API_KEY}")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        val call = LocalHttp.client.newCall(req)
        Thread({
            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) {
                        close(RuntimeException("llama HTTP ${resp.code}"))
                        return@Thread
                    }
                    resp.body?.source()?.use { src ->
                        while (!src.exhausted()) {
                            val line = src.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue
                            val data = line.removePrefix("data:").trim()
                            if (data.isEmpty() || data == "[DONE]") break
                            try {
                                val delta = JSONObject(data)
                                    .optJSONArray("choices")
                                    ?.optJSONObject(0)
                                    ?.optJSONObject("delta")
                                    ?.optString("content").orEmpty()
                                if (delta.isNotEmpty()) trySend(delta)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Stopping the chat cancels the call: its IOException lands
                // here after cancellation — close() is a harmless no-op then.
                close(e)
            } finally {
                close()
            }
        }, "llama-chat").apply { isDaemon = true }.start()
        // Mandatory terminal statement: suspends until the stream is closed
        // (done/error) or the collector goes away (stop button) — and only
        // then cancels the HTTP call. Ending the block any other way is the
        // 'awaitClose' crash from the bug report.
        awaitClose { call.cancel() }
    }
}
