package com.claudecode.app.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.claudecode.app.ClaudeApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val oc = app as ClaudeApp

    data class Ui(
        val loading: Boolean = true,
        val hasKey: Boolean = false,
        val sessions: Int = 0,
        val llamaRunning: Boolean = false,
        val activeModel: String? = null,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui

    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            _ui.value = Ui(
                loading = false,
                hasKey = oc.prefs.claudeKey().isNotBlank(),
                sessions = oc.sessions.list().size,
                llamaRunning = oc.llamaServer.isRunning,
                activeModel = oc.prefs.activeModelId(),
            )
        }
    }
}
