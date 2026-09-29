package com.claudecode.app.feature.claude

import com.claudecode.app.core.net.LocalHttp
import com.claudecode.app.core.paths.AppPaths
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal Anthropic Messages API client (SSE streaming).
 * Bionic DNS + OkHttp: no resolv.conf problem, unlike glibc runtimes.
 */
class ClaudeClient(private val apiKey: String) {

    sealed interface Evt {
        data class Text(val delta: String) : Evt
        data class ToolStart(val id: String, val name: String) : Evt
        data class ToolJson(val id: String, val partial: String) : Evt
        data class Done(val stopReason: String) : Evt
        data class Error(val message: String) : Evt
    }

    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun stream(
        model: String,
        maxTokens: Int,
        system: String?,
        messages: JSONArray,
        tools: JSONArray,
    ): Flow<Evt> = callbackFlow {
        val payload = JSONObject()
            .put("model", model)
            .put("max_tokens", maxTokens)
            .put("stream", true)
            .put("messages", messages)
            .put("tools", tools)
        if (!system.isNullOrBlank()) payload.put("system", system)
        val req = Request.Builder()
            .url(AppPaths.ANTHROPIC_URL)
            .header("x-api-key", apiKey)
            .header("anthropic-version", AppPaths.ANTHROPIC_VERSION)
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        val call = LocalHttp.client.newCall(req)
        Thread({
            var curEvent = ""
            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val msg = try {
                            JSONObject(body).optJSONObject("error")?.optString("message")
                        } catch (_: Exception) {
                            null
                        } ?: "HTTP ${resp.code}"
                        close(RuntimeException(msg))
                        return@Thread
                    }
                    resp.body?.source()?.use { src ->
                        val toolJson = mutableMapOf<String, StringBuilder>()
                        val toolNames = mutableMapOf<Int, String>()
                        val toolIds = mutableMapOf<Int, String>()
                        while (!src.exhausted()) {
                            val line = src.readUtf8Line() ?: break
                            when {
                                line.startsWith("event:") -> curEvent = line.removePrefix("event:").trim()
                                line.startsWith("data:") -> {
                                    val data = line.removePrefix("data:").trim()
                                    if (data.isEmpty() || data == "[DONE]") continue
                                    try {
                                        val o = JSONObject(data)
                                        when (o.optString("type")) {
                                            "content_block_start" -> {
                                                val b = o.optJSONObject("content_block")
                                                if (b?.optString("type") == "tool_use") {
                                                    val idx = o.optInt("index")
                                                    val id = b.optString("id")
                                                    val name = b.optString("name")
                                                    toolIds[idx] = id
                                                    toolNames[idx] = name
                                                    toolJson[id] = StringBuilder()
                                                    trySend(Evt.ToolStart(id, name))
                                                }
                                            }
                                            "content_block_delta" -> {
                                                val idx = o.optInt("index")
                                                val d = o.optJSONObject("delta") ?: continue
                                                when (d.optString("type")) {
                                                    "text_delta" -> {
                                                        val t = d.optString("text")
                                                        if (t.isNotEmpty()) trySend(Evt.Text(t))
                                                    }
                                                    "input_json_delta" -> {
                                                        val id = toolIds[idx] ?: continue
                                                        val pj = d.optString("partial_json")
                                                        toolJson[id]?.append(pj)
                                                        trySend(Evt.ToolJson(id, pj))
                                                    }
                                                }
                                            }
                                            "message_delta" -> {
                                                val reason = o.optJSONObject("delta")?.optString("stop_reason").orEmpty()
                                                if (reason.isNotEmpty()) trySend(Evt.Done(reason))
                                            }
                                            "message_stop" -> { /* Done already sent */ }
                                            "error" -> {
                                                trySend(Evt.Error(o.optJSONObject("error")?.optString("message") ?: "stream error"))
                                            }
                                        }
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                close(e)
            } finally {
                close()
            }
        }, "claude-sse").apply { isDaemon = true }.start()
        awaitClose { call.cancel() }
    }
}
