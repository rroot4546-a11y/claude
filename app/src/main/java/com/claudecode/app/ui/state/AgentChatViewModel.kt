package com.claudecode.app.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.claudecode.app.ClaudeApp
import com.claudecode.app.feature.claude.AgentLoop
import com.claudecode.app.feature.claude.SessionStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class AgentChatViewModel(app: Application) : AndroidViewModel(app) {

    private val oc = app as ClaudeApp
    private var job: Job? = null

    sealed interface Row {
        data class User(val text: String) : Row
        data class Assistant(var text: String) : Row
        data class Tool(val name: String, val summary: String) : Row
        data class ToolOut(val name: String, val text: String) : Row
    }

    data class Ui(
        val rows: List<Row> = emptyList(),
        val busy: Boolean = false,
        val status: String = "",
        val sessions: List<SessionStore.Meta> = emptyList(),
        val sessionId: String? = null,
        val hasKey: Boolean = false,
        val notice: String? = null,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui

    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(
                hasKey = oc.prefs.claudeKey().isNotBlank(),
                sessions = oc.sessions.list(),
            )
        }
    }

    fun dismissNotice() {
        _ui.value = _ui.value.copy(notice = null)
    }

    fun soon() {
        _ui.value = _ui.value.copy(notice = "Attachments are coming soon to the agent. Use Local chat for uploads today.")
    }

    fun newSession() {
        job?.cancel()
        _ui.value = _ui.value.copy(sessionId = null, rows = emptyList(), status = "", busy = false)
    }

    fun openSession(id: String) {
        viewModelScope.launch {
            job?.cancel()
            val obj = oc.sessions.load(id)
            val rows = mutableListOf<Row>()
            val msgs = obj?.optJSONArray("messages") ?: JSONArray()
            for (i in 0 until msgs.length()) {
                val m = msgs.optJSONObject(i) ?: continue
                rows.addAll(renderStored(m))
            }
            _ui.value = _ui.value.copy(sessionId = id, rows = rows, status = "", busy = false)
        }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            oc.sessions.delete(id)
            if (_ui.value.sessionId == id) newSession()
            refresh()
        }
    }

    private fun renderStored(m: JSONObject): List<Row> {
        val out = mutableListOf<Row>()
        val role = m.optString("role")
        val c = m.opt("content")
        if (c is String) {
            if (c.isNotBlank()) out.add(if (role == "user") Row.User(c) else Row.Assistant(c))
            return out
        }
        if (c is JSONArray) {
            for (i in 0 until c.length()) {
                val b = c.optJSONObject(i) ?: continue
                when (b.optString("type")) {
                    "text" -> {
                        val t = b.optString("text")
                        if (t.isNotBlank()) out.add(if (role == "user") Row.User(t) else Row.Assistant(t))
                    }
                    "tool_use" -> out.add(Row.Tool(b.optString("name"), summarizeInput(b.optJSONObject("input"))))
                    "tool_result" -> {
                        val t = b.optString("content").take(400)
                        if (t.isNotBlank()) out.add(Row.ToolOut("", t))
                    }
                }
            }
        }
        return out
    }

    private fun summarizeInput(input: JSONObject?): String {
        if (input == null) return ""
        for (k in listOf("path", "command", "url")) {
            if (input.has(k)) return "${k}: ${input.optString(k).take(80)}"
        }
        return input.toString().take(100)
    }

    fun stop() {
        job?.cancel()
        job = null
        _ui.value = _ui.value.copy(busy = false, status = "")
    }

    fun send(text: String) {
        val q = text.trim()
        if (q.isEmpty() || _ui.value.busy) return
        job = viewModelScope.launch {
            if (!oc.prefs.claudeKey().isNotBlank()) {
                _ui.value = _ui.value.copy(notice = "Add your Anthropic API key in Settings first.")
                return@launch
            }
            val rows = _ui.value.rows.toMutableList()
            rows.add(Row.User(q))
            _ui.value = _ui.value.copy(rows = rows, busy = true, status = "Thinking…", notice = null)
            try {
                val outcome = oc.agent.runTurn(
                    sessionId = _ui.value.sessionId,
                    userText = q,
                    onToken = { tok -> appendToken(tok) },
                    onTool = { call ->
                        val r = _ui.value.rows.toMutableList()
                        r.add(Row.Tool(call.name, summarizeInput(call.input)))
                        _ui.value = _ui.value.copy(rows = r, status = "Running ${call.name}…")
                    },
                    onToolResult = { call, out ->
                        val r = _ui.value.rows.toMutableList()
                        r.add(Row.ToolOut(call.name, out.take(600)))
                        _ui.value = _ui.value.copy(rows = r, status = "Thinking…")
                    },
                )
                _ui.value = _ui.value.copy(sessionId = outcome.sessionId, busy = false, status = "")
                refresh()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    _ui.value = _ui.value.copy(busy = false, status = "")
                } else {
                    _ui.value = _ui.value.copy(busy = false, status = "", notice = "Agent error: ${e.message}")
                }
                refresh()
            }
        }
    }

    private fun appendToken(tok: String) {
        val r = _ui.value.rows.toMutableList()
        val last = r.lastOrNull()
        if (last is Row.Assistant) {
            last.text += tok
        } else {
            r.add(Row.Assistant(tok))
        }
        _ui.value = _ui.value.copy(rows = r)
    }
}
