package com.claudecode.app.feature.claude

import android.content.Context
import com.claudecode.app.core.paths.AppPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persisted agent sessions: one JSON file each
 * {id, title, updatedAt, messages:[Anthropic message objects]}.
 */
class SessionStore(private val context: Context) {

    data class Meta(val id: String, val title: String, val updatedAt: Long)

    private fun dir(): File = AppPaths.sessionsDir(context)
    private fun file(id: String): File = File(dir(), "$id.json")

    suspend fun list(): List<Meta> = withContext(Dispatchers.IO) {
        (dir().listFiles() ?: emptyArray())
            .filter { it.extension == "json" }
            .mapNotNull { f ->
                try {
                    val o = JSONObject(f.readText())
                    Meta(f.nameWithoutExtension, o.optString("title", "Untitled"), o.optLong("updatedAt"))
                } catch (_: Exception) {
                    null
                }
            }
            .sortedByDescending { it.updatedAt }
    }

    suspend fun load(id: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            JSONObject(file(id).readText())
        } catch (_: Exception) {
            null
        }
    }

    suspend fun loadMessages(id: String): JSONArray = withContext(Dispatchers.IO) {
        load(id)?.optJSONArray("messages") ?: JSONArray()
    }

    suspend fun save(id: String, title: String, messages: JSONArray) = withContext(Dispatchers.IO) {
        val o = JSONObject()
            .put("id", id)
            .put("title", title.take(80))
            .put("updatedAt", System.currentTimeMillis())
            .put("messages", messages)
        file(id).writeText(o.toString())
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        try {
            file(id).delete()
        } catch (_: Exception) {
        }
    }

    fun newId(): String = "s${System.currentTimeMillis()}"
}
