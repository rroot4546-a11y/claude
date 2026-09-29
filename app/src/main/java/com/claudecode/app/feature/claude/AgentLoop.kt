package com.claudecode.app.feature.claude

import android.content.Context
import android.util.Log
import com.claudecode.app.core.net.LocalHttp
import com.claudecode.app.core.paths.AppPaths
import com.claudecode.app.core.prefs.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Native agentic coding loop over the Anthropic Messages API.
 * No native binaries, no seccomp surface: pure Kotlin + HTTPS.
 * Tools are strictly scoped to the app workspace.
 */
class AgentLoop(
    private val context: Context,
    private val prefs: AppPrefs,
    private val sessions: SessionStore,
) {

    companion object {
        private const val TAG = "AgentLoop"
        private const val MAX_TURNS = 12
        private const val MAX_OUT = 20_000
    }

    data class ToolCall(val id: String, val name: String, val input: JSONObject)
    data class TurnResult(val text: String, val calls: List<Pair<ToolCall, String>>)

    private fun workspace(): File = AppPaths.workspaceDir(context)

    /** Resolve a user path safely inside the workspace. Null when escaping. */
    private fun safePath(p: String): File? {
        return try {
            val base = workspace().canonicalPath
            val f = if (File(p).isAbsolute) File(p) else File(workspace(), p)
            val c = f.canonicalPath
            if (c == base || c.startsWith("$base/")) File(c) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun toolsJson(): JSONArray {
        fun t(name: String, desc: String, props: JSONObject, required: List<String>): JSONObject {
            return JSONObject()
                .put("name", name)
                .put("description", desc)
                .put("input_schema", JSONObject().put("type", "object").put("properties", props).put("required", JSONArray(required)))
        }
        fun s(desc: String) = JSONObject().put("type", "string").put("description", desc)
        fun si(desc: String) = JSONObject().put("type", "integer").put("description", desc)
        return JSONArray()
            .put(t("read_file", "Read a text file (relative to workspace).", JSONObject().put("path", s("Relative file path")).put("offset", si("Start line (1-based, optional)")).put("limit", si("Max lines (optional)")), listOf("path")))
            .put(t("list_dir", "List a directory (relative to workspace).", JSONObject().put("path", s("Relative dir path, default .")), listOf()))
            .put(t("write_file", "Create or overwrite a text file.", JSONObject().put("path", s("Relative file path")).put("content", s("Full file content")), listOf("path", "content")))
            .put(t("edit_file", "Replace first occurrence of old_string with new_string.", JSONObject().put("path", s("Relative file path")).put("old_string", s("Exact text to replace")).put("new_string", s("Replacement text")), listOf("path", "old_string", "new_string")))
            .put(t("bash", "Run a shell command (toolbox sh, 30s timeout).", JSONObject().put("command", s("Shell command")).put("timeout_sec", si("Timeout seconds, default 30")), listOf("command")))
            .put(t("web_fetch", "Fetch a URL as text (truncated).", JSONObject().put("url", s("http(s) URL")), listOf("url")))
    }

    private suspend fun execTool(name: String, input: JSONObject): String = withContext(Dispatchers.IO) {
        try {
            when (name) {
                "read_file" -> {
                    val f = safePath(input.optString("path")) ?: return@withContext "error: path escapes workspace"
                    if (!f.isFile) return@withContext "error: not found: ${input.optString("path")}"
                    val lines = f.readLines()
                    val off = (input.optInt("offset", 1) - 1).coerceAtLeast(0)
                    val lim = input.optInt("limit", 200).coerceIn(1, 500)
                    lines.drop(off).take(lim).joinToString("\n").take(MAX_OUT)
                }
                "list_dir" -> {
                    val f = safePath(input.optString("path", ".")) ?: return@withContext "error: path escapes workspace"
                    if (!f.isDirectory) return@withContext "error: not a directory"
                    (f.listFiles() ?: emptyArray()).sortedBy { it.name }
                        .joinToString("\n") { (if (it.isDirectory) it.name + "/" else it.name) + " (${it.length()}b)" }
                        .ifBlank { "(empty)" }.take(MAX_OUT)
                }
                "write_file" -> {
                    val f = safePath(input.optString("path")) ?: return@withContext "error: path escapes workspace"
                    f.parentFile?.mkdirs()
                    f.writeText(input.optString("content"))
                    "wrote ${f.length()} bytes to ${input.optString("path")}"
                }
                "edit_file" -> {
                    val f = safePath(input.optString("path")) ?: return@withContext "error: path escapes workspace"
                    if (!f.isFile) return@withContext "error: not found"
                    val text = f.readText()
                    val old = input.optString("old_string")
                    if (!text.contains(old)) return@withContext "error: old_string not found"
                    f.writeText(text.replaceFirst(old, input.optString("new_string")))
                    "replaced first occurrence in ${input.optString("path")}"
                }
                "bash" -> {
                    val timeout = input.optInt("timeout_sec", 30).coerceIn(1, 120)
                    val proc = ProcessBuilder("/system/bin/sh", "-c", input.optString("command"))
                        .directory(workspace())
                        .redirectErrorStream(true)
                        .start()
                    val finished = proc.waitFor(timeout.toLong(), TimeUnit.SECONDS)
                    if (!finished) {
                        proc.destroyForcibly()
                        return@withContext "error: timed out after ${timeout}s"
                    }
                    val out = try {
                        proc.inputStream.bufferedReader().readText()
                    } catch (_: Exception) {
                        ""
                    }
                    "[exit ${proc.exitValue()}]\n" + out.take(MAX_OUT)
                }
                "web_fetch" -> {
                    LocalHttp.get(input.optString("url"), 20_000)?.take(30_000)
                        ?: "error: fetch failed"
                }
                else -> "error: unknown tool $name"
            }
        } catch (e: Exception) {
            "error: ${e.message}"
        }
    }

    private fun systemPrompt(): String = """
        You are Claude, an expert coding assistant running on Android.
        Workspace root: ${workspace().absolutePath} (all relative paths resolve here).
        Be concise. Use tools to read before editing. Prefer small, correct diffs.
        Never access paths outside the workspace. Reply in the user's language.
    """.trimIndent()

    data class TurnOutcome(val sessionId: String, val text: String)

    /**
     * Run one user turn (possibly multi-step with tool calls).
     * Streams text + tool events to callbacks. Returns when done.
     */
    suspend fun runTurn(
        sessionId: String?,
        userText: String,
        onToken: (String) -> Unit,
        onTool: (ToolCall) -> Unit,
        onToolResult: (ToolCall, String) -> Unit,
    ): TurnOutcome {
        val key = prefs.claudeKey()
        if (key.isBlank()) throw IllegalStateException("Add your Anthropic API key in Settings first.")
        val model = prefs.claudeModel().ifBlank { "claude-sonnet-4-5" }
        val maxTokens = prefs.claudeMaxTokens()
        val id = sessionId ?: sessions.newId()
        val messages = if (sessionId != null) sessions.loadMessages(sessionId) else JSONArray()
        messages.put(JSONObject().put("role", "user").put("content", userText))

        val client = ClaudeClient(key)
        val tools = toolsJson()
        var finalText = ""
        for (turn in 0 until MAX_TURNS) {
            val textAcc = StringBuilder()
            val ids = mutableListOf<String>()
            val names = mutableMapOf<String, String>()
            val inputs = mutableMapOf<String, StringBuilder>()
            var stopReason = ""
            client.stream(model, maxTokens, systemPrompt(), messages, tools)
                .catch { e -> throw e }
                .collect { ev ->
                    when (ev) {
                        is ClaudeClient.Evt.Text -> {
                            textAcc.append(ev.delta)
                            onToken(ev.delta)
                        }
                        is ClaudeClient.Evt.ToolStart -> {
                            ids.add(ev.id)
                            names[ev.id] = ev.name
                            inputs[ev.id] = StringBuilder()
                        }
                        is ClaudeClient.Evt.ToolJson -> {
                            inputs[ev.id]?.append(ev.partial)
                        }
                        is ClaudeClient.Evt.Done -> stopReason = ev.stopReason
                        is ClaudeClient.Evt.Error -> throw RuntimeException(ev.message)
                    }
                }
            finalText = textAcc.toString()
            if (stopReason != "tool_use" || ids.isEmpty()) {
                messages.put(JSONObject().put("role", "assistant").put("content", finalText.ifBlank { "…" }))
                break
            }
            // Build assistant tool_use block + execute each tool.
            val blocks = JSONArray()
            if (finalText.isNotBlank()) {
                blocks.put(JSONObject().put("type", "text").put("text", finalText))
            }
            val results = JSONArray()
            for (cid in ids) {
                val name = names[cid] ?: continue
                val input = try {
                    JSONObject(inputs[cid]?.toString() ?: "{}")
                } catch (_: Exception) {
                    JSONObject()
                }
                val call = ToolCall(cid, name, input)
                onTool(call)
                val out = execTool(name, input)
                onToolResult(call, out)
                blocks.put(JSONObject().put("type", "tool_use").put("id", cid).put("name", name).put("input", input))
                results.put(JSONObject().put("type", "tool_result").put("tool_use_id", cid).put("content", out))
            }
            messages.put(JSONObject().put("role", "assistant").put("content", blocks))
            messages.put(JSONObject().put("role", "user").put("content", results))
        }
        val first = try {
            messages.optJSONObject(0)?.optString("content") ?: "Chat"
        } catch (_: Exception) {
            "Chat"
        }
        sessions.save(id, first.take(60).ifBlank { "Chat" }, messages)
        return TurnOutcome(id, finalText)
    }
}
