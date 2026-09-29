package com.claudecode.app.feature.localai

/**
 * GGUF presentation metadata. Ported from pocketbrain-ai
 * (lib/model-metadata.ts): filename-quant inference + size formatting.
 * Pure filename/size logic — no native engine needed.
 */
object GgufMeta {

    private val QUANT_RE = Regex("""(?:^|[-_.])(q[2-8](?:[_-]k(?:[_-][msl])?|[_-][0-2]))(?:[-_.]|$)""", RegexOption.IGNORE_CASE)

    fun isGgufFile(name: String): Boolean =
        name.trim().lowercase().endsWith(".gguf")

    fun inferQuantization(name: String): String {
        val m = QUANT_RE.find(name.lowercase()) ?: return "GGUF"
        return m.groupValues[1].uppercase().replace("-", "_")
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "size unknown"
        if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
        if (bytes < 1024L * 1024 * 1024) return "${bytes / 1048576} MB"
        return String.format("%.1f GB", bytes / 1073741824.0)
    }

    /**
     * Strip <think>/<thinking> reasoning blocks (complete or partial,
     * head or tail) — ported from pocketbrain-ai lib/thinking.ts.
     */
    fun stripThinking(text: String): String {
        var s = text
        s = Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE).replace(s, "")
        s = Regex("<thinking>[\\s\\S]*?</thinking>", RegexOption.IGNORE_CASE).replace(s, "")
        s = Regex("<think>[\\s\\S]*$", RegexOption.IGNORE_CASE).replace(s, "")
        s = Regex("<thinking>[\\s\\S]*$", RegexOption.IGNORE_CASE).replace(s, "")
        s = Regex("^[\\s\\S]*?</think>", RegexOption.IGNORE_CASE).replace(s, "")
        s = Regex("^[\\s\\S]*?</thinking>", RegexOption.IGNORE_CASE).replace(s, "")
        return s.trimStart()
    }
}
