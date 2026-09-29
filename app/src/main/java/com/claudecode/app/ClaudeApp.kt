package com.claudecode.app

import android.app.Application
import android.util.Log
import com.claudecode.app.core.prefs.AppPrefs
import com.claudecode.app.feature.claude.AgentLoop
import com.claudecode.app.feature.claude.SessionStore
import com.claudecode.app.feature.localai.GgufStore
import com.claudecode.app.feature.localai.LlamaServer

class ClaudeApp : Application() {

    val prefs by lazy { AppPrefs(this) }
    val ggufStore by lazy { GgufStore(this) }
    val llamaServer by lazy { LlamaServer(this, prefs, ggufStore) }
    val sessions by lazy { SessionStore(this) }
    val agent by lazy { AgentLoop(this, prefs, sessions) }

    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            Log.e("ClaudeApp", "Uncaught: ${e.message}", e)
        }
    }
}
