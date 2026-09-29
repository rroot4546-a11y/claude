package com.claudecode.app.core.paths

import android.content.Context
import java.io.File

/**
 * On-device layout. Zero native binaries except the llama.cpp runtime
 * (direct exec, proven). The Claude agent is pure Kotlin + HTTPS —
 * nothing to exec, nothing to seccomp.
 */
object AppPaths {

    const val LLAMA_PORT = 18925
    const val LLAMA_BASE_URL = "http://127.0.0.1:$LLAMA_PORT/v1"

    const val ANTHROPIC_URL = "https://api.anthropic.com/v1/messages"
    const val ANTHROPIC_VERSION = "2023-06-01"

    /** Agent workspace (scoped file tools live here). */
    fun workspaceDir(context: Context): File =
        File(context.filesDir, "workspace").apply { mkdirs() }

    /** Persisted agent sessions (JSON). */
    fun sessionsDir(context: Context): File =
        File(context.filesDir, "sessions").apply { mkdirs() }

    /** llama.cpp runtime (llama-server + libs). */
    fun llamaDir(context: Context): File = File(context.filesDir, "llama").apply { mkdirs() }

    /** User GGUF models. */
    fun modelsDir(context: Context): File = File(context.filesDir, "models").apply { mkdirs() }

    /** User attachments staged for vision/file prompts. */
    fun attachmentsDir(context: Context): File = File(context.filesDir, "attachments").apply { mkdirs() }
}
