package com.claudecode.app.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Tiny HTTP helper for talking to on-device servers (llama-server,
 * opencode serve…). Single shared OkHttp client.
 */
object LocalHttp {

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    suspend fun get(url: String, timeoutMs: Long = 5_000): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun postJson(url: String, payload: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(JSON))
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return@withContext null
                if (!resp.isSuccessful) return@withContext JSONObject().put("httpError", resp.code)
                JSONObject(body)
            }
        } catch (e: Exception) {
            JSONObject().put("transportError", e.message.toString())
        }
    }

    suspend fun probe(url: String, timeoutMs: Long = 3_000): Boolean = withContext(Dispatchers.IO) {
        try {
            val c = client.newBuilder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()
            val req = Request.Builder().url(url).head().build()
            c.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            // HEAD may be unsupported — fall back to GET.
            get(url, timeoutMs) != null
        }
    }
}
