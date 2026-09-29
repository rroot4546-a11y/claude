package com.claudecode.app.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.claudecode.app.ClaudeApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val oc = app as ClaudeApp

    val models = listOf(
        "claude-sonnet-4-5",
        "claude-opus-4-1",
        "claude-haiku-4-5",
    )

    data class Ui(
        val ctx: Int = 4096,
        val threads: Int = 4,
        val maxThreads: Int = 8,
        val thinking: Boolean = true,
        val maxTokens: Int = 1024,
        val temperature: Float = 0.7f,
        val topP: Float = 0.95f,
        val topK: Float = 40f,
        val minP: Float = 0.05f,
        val repeatPenalty: Float = 1.1f,
        val claudeKey: String = "",
        val claudeModel: String = "claude-sonnet-4-5",
        val claudeMax: Int = 4096,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui

    fun refresh() {
        viewModelScope.launch {
            val max = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
            _ui.value = Ui(
                ctx = oc.prefs.ctxSize(),
                threads = oc.prefs.threads().coerceAtMost(max),
                maxThreads = max,
                thinking = oc.prefs.showThinking(),
                maxTokens = oc.prefs.maxTokens(),
                temperature = oc.prefs.temperature(),
                topP = oc.prefs.topP(),
                topK = oc.prefs.topK(),
                minP = oc.prefs.minP(),
                repeatPenalty = oc.prefs.repeatPenalty(),
                claudeKey = oc.prefs.claudeKey(),
                claudeModel = oc.prefs.claudeModel(),
                claudeMax = oc.prefs.claudeMaxTokens(),
            )
        }
    }

    fun setCtx(v: Int) {
        viewModelScope.launch {
            oc.prefs.setCtxSize(v)
            _ui.value = _ui.value.copy(ctx = v)
        }
    }

    fun setThreads(v: Int) {
        viewModelScope.launch {
            oc.prefs.setThreads(v)
            _ui.value = _ui.value.copy(threads = v)
        }
    }

    fun setThinking(v: Boolean) {
        viewModelScope.launch {
            oc.prefs.setShowThinking(v)
            _ui.value = _ui.value.copy(thinking = v)
        }
    }

    fun setMaxTokens(v: Int) {
        viewModelScope.launch {
            oc.prefs.setMaxTokens(v)
            _ui.value = _ui.value.copy(maxTokens = v)
        }
    }

    fun setTemperature(v: Float) {
        viewModelScope.launch {
            oc.prefs.setTemperature(v)
            _ui.value = _ui.value.copy(temperature = v)
        }
    }

    fun setTopP(v: Float) {
        viewModelScope.launch {
            oc.prefs.setTopP(v)
            _ui.value = _ui.value.copy(topP = v)
        }
    }

    fun setTopK(v: Float) {
        viewModelScope.launch {
            oc.prefs.setTopK(v)
            _ui.value = _ui.value.copy(topK = v)
        }
    }

    fun setMinP(v: Float) {
        viewModelScope.launch {
            oc.prefs.setMinP(v)
            _ui.value = _ui.value.copy(minP = v)
        }
    }

    fun setRepeatPenalty(v: Float) {
        viewModelScope.launch {
            oc.prefs.setRepeatPenalty(v)
            _ui.value = _ui.value.copy(repeatPenalty = v)
        }
    }

    fun setClaudeKey(v: String) {
        viewModelScope.launch {
            oc.prefs.setClaudeKey(v)
            _ui.value = _ui.value.copy(claudeKey = v.trim())
        }
    }

    fun setClaudeModel(v: String) {
        viewModelScope.launch {
            oc.prefs.setClaudeModel(v)
            _ui.value = _ui.value.copy(claudeModel = v)
        }
    }

    fun setClaudeMax(v: Int) {
        viewModelScope.launch {
            oc.prefs.setClaudeMaxTokens(v)
            _ui.value = _ui.value.copy(claudeMax = v)
        }
    }
}
