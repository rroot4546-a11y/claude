package com.claudecode.app.core.process

import android.util.Log
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

/**
 * Spawns and supervises native child processes (llama-server, node, proot…).
 * Every process gets: an `on('error')`-style guard (no unhandled crashes),
 * bounded log capture and a best-effort kill tree on stop.
 */
class ProcessManager(private val tag: String) {

    data class Handle(
        val process: Process,
        val pidFile: File?,
        val logLines: ArrayDeque<String> = ArrayDeque(),
    )

    private val lock = Any()
    private var handle: Handle? = null

    val isRunning: Boolean
        get() = synchronized(lock) {
            val h = handle ?: return false
            return try {
                h.process.exitValue()
                false
            } catch (_: IllegalThreadStateException) {
                true
            }
        }

    fun lastError(tail: Int = 25): String = synchronized(lock) {
        val h = handle ?: return "process never started"
        if (h.logLines.isEmpty()) return "no output captured"
        return h.logLines.toList().takeLast(tail).joinToString("\n")
    }

    /**
     * Start [command] with [env]. Previous instance (if any) is stopped first.
     * Reader threads swallow stream errors (EPIPE-safe) so a dying child can
     * never crash the host app.
     */
    fun start(
        command: List<String>,
        env: Map<String, String>,
        workDir: File,
        pidFile: File? = null,
        logCap: Int = 400,
    ): Boolean {
        stop()
        return try {
            val pb = ProcessBuilder(command)
            pb.directory(workDir)
            pb.environment().clear()
            pb.environment().putAll(env)
            pb.redirectErrorStream(true)
            // Daemons must never inherit an open stdin pipe (silent hang).
            pb.redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
            val proc = pb.start()
            val h = Handle(proc, pidFile)
            // Note: Process.pid() is unavailable on this compile target;
            // the pid file is best-effort only (stop() uses destroy()).
            try {
                pidFile?.writeText(proc.hashCode().toString())
            } catch (_: Exception) {
            }
            Thread({
                try {
                    proc.inputStream.bufferedReader().forEachLine { line ->
                        synchronized(lock) {
                            h.logLines.addLast(line)
                            while (h.logLines.size > logCap) h.logLines.removeFirst()
                        }
                        Log.d(tag, line)
                    }
                } catch (_: Exception) {
                    // Stream closed (child died / EPIPE) — swallow, never crash.
                }
            }, "$tag-log").apply { isDaemon = true }.start()
            synchronized(lock) { handle = h }
            Log.i(tag, "started: ${command.joinToString(" ")}")
            true
        } catch (e: Exception) {
            Log.e(tag, "spawn failed: ${e.message}", e)
            false
        }
    }

    fun stop() {
        val h = synchronized(lock) { handle.also { handle = null } } ?: return
        try {
            h.pidFile?.delete()
            h.process.destroy()
            if (!h.process.waitFor(3, TimeUnit.SECONDS)) {
                h.process.destroyForcibly()
            }
        } catch (e: Exception) {
            Log.w(tag, "stop: ${e.message}")
        }
    }
}
