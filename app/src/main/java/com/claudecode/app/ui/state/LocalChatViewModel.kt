package com.claudecode.app.ui.state

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.claudecode.app.ClaudeApp
import com.claudecode.app.feature.chat.Attachments
import com.claudecode.app.feature.chat.LlamaChatClient
import com.claudecode.app.feature.localai.LocalModelActivator
import com.claudecode.app.ui.screens.ModelOption
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class LocalChatViewModel(app: Application) : AndroidViewModel(app) {

    private val oc = app as ClaudeApp
    private val client = LlamaChatClient()
    private var streamJob: Job? = null

    data class Msg(val role: String, val text: String)
    data class Ui(
        val messages: List<Msg> = emptyList(),
        val streaming: Boolean = false,
        val busy: Boolean = false,
        val modelId: String? = null,
        val serverUp: Boolean = false,
        val options: List<ModelOption> = emptyList(),
        val attachments: List<Attachments.Item> = emptyList(),
        val notice: String? = null,
        val showThinking: Boolean = true,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui

    fun refresh() {
        viewModelScope.launch {
            val up = oc.llamaServer.isRunning && oc.llamaServer.health()
            val models = oc.ggufStore.list()
            _ui.value = _ui.value.copy(
                serverUp = up,
                showThinking = oc.prefs.showThinking(),
                modelId = oc.prefs.activeModelId(),
                options = models.map {
                    ModelOption(
                        id = it.id,
                        label = it.id,
                        subtitle = "${it.sizeBytes / 1048576}MB · GGUF",
                        isLocal = true,
                    )
                },
            )
        }
    }

    fun dismissNotice() {
        _ui.value = _ui.value.copy(notice = null)
    }

    fun pickModel(o: ModelOption) {
        if (o.id == _ui.value.modelId && _ui.value.serverUp) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true)
            val r = LocalModelActivator.activate(oc.prefs, oc.llamaServer, o.id)
            _ui.value = _ui.value.copy(
                busy = false,
                notice = if (r.ok) r.message else r.message,
            )
            refresh()
        }
    }

    fun addAttachment(uri: Uri) {
        viewModelScope.launch {
            try {
                val item = Attachments.stage(oc, uri)
                _ui.value = _ui.value.copy(attachments = _ui.value.attachments + item)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(notice = "Attach failed: ${e.message}")
            }
        }
    }

    fun removeAttachment(i: Int) {
        _ui.value = _ui.value.copy(attachments = _ui.value.attachments.filterIndexed { idx, _ -> idx != i })
    }

    fun stop() {
        streamJob?.cancel()
        streamJob = null
        _ui.value = _ui.value.copy(streaming = false)
    }

    fun toggleThinking() {
        viewModelScope.launch {
            val v = !_ui.value.showThinking
            oc.prefs.setShowThinking(v)
            _ui.value = _ui.value.copy(showThinking = v)
        }
    }

    private fun visible(text: String): String =
        if (_ui.value.showThinking) text
        else com.claudecode.app.feature.localai.GgufMeta.stripThinking(text)

    fun send(text: String) {
        val q = text.trim()
        val atts = _ui.value.attachments
        if ((q.isEmpty() && atts.isEmpty()) || _ui.value.streaming) return
        streamJob = viewModelScope.launch {
            val model = _ui.value.modelId ?: oc.prefs.activeModelId()
            if (model == null) {
                _ui.value = _ui.value.copy(notice = "Activate a model first (top picker or Models tab)")
                return@launch
            }
            val parts = JSONArray()
            val inline = StringBuilder()
            atts.filter { !it.isImage }.forEach { a ->
                val snippet = Attachments.readTextSnippet(a.file)
                if (snippet != null) {
                    inline.append("\n\n[attached file: ${a.name}]\n```\n")
                        .append(snippet.take(Attachments.MAX_TEXT_BYTES))
                        .append("\n```")
                } else {
                    inline.append("\n\n[attached binary file: ${a.name} (${a.size} bytes) — content not readable as text]")
                }
            }
            val fullText = (q + inline.toString()).ifBlank { "Describe the attached files." }
            parts.put(JSONObject().put("type", "text").put("text", fullText))
            atts.filter { it.isImage }.forEach { a ->
                Attachments.imageToDataUri(a.file, a.mime)?.let { uri ->
                    parts.put(
                        JSONObject().put("type", "image_url")
                            .put("image_url", JSONObject().put("url", uri)),
                    )
                }
            }
            val history = _ui.value.messages
                .takeLast(20)
                .map { LlamaChatClient.Msg(it.role, it.text) }
            val shownText = if (q.isNotEmpty()) q else "📎 ${atts.joinToString { it.name }}"
            _ui.value = _ui.value.copy(
                messages = history.map { Msg(it.role, it.content) } + Msg("user", shownText) + Msg("assistant", ""),
                streaming = true,
                attachments = emptyList(),
                notice = null,
            )
            val acc = StringBuilder()
            val opts = LlamaChatClient.GenOptions(
                temperature = oc.prefs.temperature(),
                topP = oc.prefs.topP(),
                topK = oc.prefs.topK(),
                minP = oc.prefs.minP(),
                repeatPenalty = oc.prefs.repeatPenalty(),
                maxTokens = oc.prefs.maxTokens(),
            )
            client.streamParts(history, parts, model, opts)
                .catch { e ->
                    _ui.value = _ui.value.copy(streaming = false, notice = "Model error: ${e.message}")
                }
                .collect { token ->
                    acc.append(token)
                    val msgs = _ui.value.messages.toMutableList()
                    if (msgs.isNotEmpty()) msgs[msgs.lastIndex] = Msg("assistant", visible(acc.toString()))
                    _ui.value = _ui.value.copy(messages = msgs)
                }
            _ui.value = _ui.value.copy(streaming = false)
        }
    }

    fun clear() {
        stop()
        _ui.value = _ui.value.copy(messages = emptyList(), notice = null, attachments = emptyList())
    }
}
