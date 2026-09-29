package com.claudecode.app.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("opencode_app")

class AppPrefs(private val context: Context) {

    companion object {
        private val ACTIVE_MODEL = stringPreferencesKey("active_model_id")
        private val AUTO_LINK = booleanPreferencesKey("auto_link_agents")
        private val CTX_SIZE = intPreferencesKey("llama_ctx")
        private val N_THREADS = intPreferencesKey("llama_threads")
        private val LLAMA_VERSION = stringPreferencesKey("llama_version")
        private val CLAUDE_KEY = stringPreferencesKey("claude_key")
        private val CLAUDE_MODEL = stringPreferencesKey("claude_model")
        private val CLAUDE_MAX = intPreferencesKey("claude_max")
        // PocketBrain-merged generation controls.
        private val THINKING = booleanPreferencesKey("show_thinking")
        private val MAX_TOKENS = intPreferencesKey("max_tokens")
        private val TEMPERATURE = stringPreferencesKey("temperature")
        private val TOP_P = stringPreferencesKey("top_p")
        private val TOP_K = stringPreferencesKey("top_k")
        private val MIN_P = stringPreferencesKey("min_p")
        private val REPEAT_PENALTY = stringPreferencesKey("repeat_penalty")
    }

    suspend fun activeModelId(): String? =
        context.dataStore.data.map { it[ACTIVE_MODEL] }.first()

    suspend fun setActiveModelId(id: String?) = context.dataStore.edit {
        if (id == null) it.remove(ACTIVE_MODEL) else it[ACTIVE_MODEL] = id
    }

    suspend fun autoLink(): Boolean =
        context.dataStore.data.map { it[AUTO_LINK] ?: true }.first()

    suspend fun setAutoLink(v: Boolean) = context.dataStore.edit { it[AUTO_LINK] = v }

    suspend fun ctxSize(): Int =
        context.dataStore.data.map { it[CTX_SIZE] ?: 4096 }.first()

    suspend fun setCtxSize(v: Int) = context.dataStore.edit { it[CTX_SIZE] = v }

    suspend fun threads(): Int =
        context.dataStore.data.map { it[N_THREADS] ?: maxOf(2, Runtime.getRuntime().availableProcessors() - 1) }.first()

    suspend fun setThreads(v: Int) = context.dataStore.edit { it[N_THREADS] = v }

    suspend fun llamaVersion(): String? =
        context.dataStore.data.map { it[LLAMA_VERSION] }.first()

    suspend fun setLlamaVersion(v: String) = context.dataStore.edit { it[LLAMA_VERSION] = v }

    // ── generation controls (pocketbrain merge) ──────────────────────────
    suspend fun showThinking(): Boolean =
        context.dataStore.data.map { it[THINKING] ?: true }.first()

    suspend fun setShowThinking(v: Boolean) = context.dataStore.edit { it[THINKING] = v }

    suspend fun maxTokens(): Int =
        context.dataStore.data.map { it[MAX_TOKENS] ?: 1024 }.first()

    suspend fun setMaxTokens(v: Int) = context.dataStore.edit { it[MAX_TOKENS] = v }

    private suspend fun float(key: androidx.datastore.preferences.core.Preferences.Key<String>, def: Float): Float =
        context.dataStore.data.map { it[key]?.toFloatOrNull() ?: def }.first()

    private suspend fun setFloat(key: androidx.datastore.preferences.core.Preferences.Key<String>, v: Float) =
        context.dataStore.edit { it[key] = v.toString() }

    suspend fun temperature(): Float = float(TEMPERATURE, 0.7f)
    suspend fun setTemperature(v: Float) = setFloat(TEMPERATURE, v)
    suspend fun topP(): Float = float(TOP_P, 0.95f)
    suspend fun setTopP(v: Float) = setFloat(TOP_P, v)
    suspend fun topK(): Float = float(TOP_K, 40f)
    suspend fun setTopK(v: Float) = setFloat(TOP_K, v)
    suspend fun minP(): Float = float(MIN_P, 0.05f)
    suspend fun setMinP(v: Float) = setFloat(MIN_P, v)
    suspend fun repeatPenalty(): Float = float(REPEAT_PENALTY, 1.1f)
    suspend fun setRepeatPenalty(v: Float) = setFloat(REPEAT_PENALTY, v)

    // ── Claude agent ─────────────────────────────────────────────────────
    suspend fun claudeKey(): String =
        context.dataStore.data.map { it[CLAUDE_KEY].orEmpty() }.first()

    suspend fun setClaudeKey(v: String) = context.dataStore.edit { it[CLAUDE_KEY] = v.trim() }

    suspend fun claudeModel(): String =
        context.dataStore.data.map { it[CLAUDE_MODEL] ?: "claude-sonnet-4-5" }.first()

    suspend fun setClaudeModel(v: String) = context.dataStore.edit { it[CLAUDE_MODEL] = v.trim() }

    suspend fun claudeMaxTokens(): Int =
        context.dataStore.data.map { it[CLAUDE_MAX] ?: 4096 }.first()

    suspend fun setClaudeMaxTokens(v: Int) = context.dataStore.edit { it[CLAUDE_MAX] = v }
}
