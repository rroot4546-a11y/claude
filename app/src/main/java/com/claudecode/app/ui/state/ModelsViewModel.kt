package com.claudecode.app.ui.state

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.claudecode.app.ClaudeApp
import com.claudecode.app.feature.localai.GgufStore
import com.claudecode.app.feature.localai.LlamaInstaller
import com.claudecode.app.feature.localai.LocalModelActivator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ModelsViewModel(app: Application) : AndroidViewModel(app) {

    private val oc = app as ClaudeApp

    companion object {
        const val STARTER_URL =
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"
    }

    data class Ui(
        val loading: Boolean = true,
        val models: List<GgufStore.GgufModel> = emptyList(),
        val activeId: String? = null,
        val runtimeInstalled: Boolean = false,
        val runtimeVersion: String = "",
        val serverRunning: Boolean = false,
        val busy: String? = null,
        val progress: Int = -1,
        val progressText: String = "",
        val notice: String? = null,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui

    fun refresh() {
        viewModelScope.launch {
            _ui.value = Ui(
                loading = false,
                models = oc.ggufStore.list(),
                activeId = oc.prefs.activeModelId(),
                runtimeInstalled = LlamaInstaller.isInstalled(oc),
                runtimeVersion = LlamaInstaller.installedVersion(oc),
                serverRunning = oc.llamaServer.isRunning,
                busy = _ui.value.busy,
                progress = _ui.value.progress,
                progressText = _ui.value.progressText,
                notice = _ui.value.notice,
            )
        }
    }

    private fun setBusy(label: String?, pct: Int = -1, text: String = "") {
        _ui.value = _ui.value.copy(busy = label, progress = pct, progressText = text)
    }

    fun dismissNotice() {
        _ui.value = _ui.value.copy(notice = null)
    }

    fun installRuntime() {
        viewModelScope.launch {
            setBusy("Downloading llama.cpp runtime…", 0)
            try {
                LlamaInstaller.install(oc) { pct ->
                    _ui.value = _ui.value.copy(progress = pct.coerceIn(0, 100))
                }
                setBusy(null)
                _ui.value = _ui.value.copy(notice = "Runtime installed ✓")
            } catch (e: Exception) {
                setBusy(null)
                _ui.value = _ui.value.copy(notice = "Install failed: ${e.message ?: e.javaClass.simpleName}")
            }
            refresh()
        }
    }

    fun importUri(uri: Uri) {
        viewModelScope.launch {
            setBusy("Importing model…", -1)
            try {
                val m = oc.ggufStore.importFromUri(uri) { done, total ->
                    val pct = if (total > 0) ((done * 100) / total).toInt() else -1
                    _ui.value = _ui.value.copy(progress = pct, progressText = "${done / 1048576}MB")
                }
                setBusy(null)
                _ui.value = _ui.value.copy(notice = "Added ${m.id} ✓")
            } catch (e: Exception) {
                setBusy(null)
                _ui.value = _ui.value.copy(notice = "Import failed: ${e.message ?: e.javaClass.simpleName}")
            }
            refresh()
        }
    }

    fun downloadUrl(url: String) {
        viewModelScope.launch {
            setBusy("Downloading model…", 0)
            try {
                val m = oc.ggufStore.downloadFromUrl(url.trim()) { done, total ->
                    val pct = if (total > 0) ((done * 100) / total).toInt() else -1
                    _ui.value = _ui.value.copy(
                        progress = pct,
                        progressText = "${done / 1048576}MB" + if (total > 0) " / ${total / 1048576}MB" else "",
                    )
                }
                setBusy(null)
                _ui.value = _ui.value.copy(notice = "Downloaded ${m.id} ✓")
            } catch (e: Exception) {
                setBusy(null)
                _ui.value = _ui.value.copy(notice = "Download failed: ${e.message ?: e.javaClass.simpleName}")
            }
            refresh()
        }
    }

    fun downloadStarter() = downloadUrl(STARTER_URL)

    fun testServer() {
        viewModelScope.launch {
            setBusy("Testing local server…", -1)
            val id = oc.llamaServer.servedModelId()
            setBusy(null)
            _ui.value = _ui.value.copy(
                notice = if (id != null) "Server is serving: $id ✓"
                else "No response — start a model first. Log:\n${oc.llamaServer.lastError().take(400)}",
            )
            refresh()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            if (oc.prefs.activeModelId() == id) {
                oc.prefs.setActiveModelId(null)
                oc.llamaServer.stop()
                
            }
            oc.ggufStore.delete(id)
            _ui.value = _ui.value.copy(notice = "Deleted $id")
            refresh()
        }
    }

    fun activate(id: String) {
        viewModelScope.launch {
            setBusy("Activating $id…", -1, "Starting server…")
            val r = LocalModelActivator.activate(oc.prefs, oc.llamaServer, id)
            setBusy(null)
            _ui.value = _ui.value.copy(
                notice = if (r.ok) r.message else r.message,
            )
            refresh()
        }
    }

    fun stopServer() {
        viewModelScope.launch {
            oc.llamaServer.stop()
            
            _ui.value = _ui.value.copy(notice = "Local server stopped & unlinked")
            refresh()
        }
    }
}
