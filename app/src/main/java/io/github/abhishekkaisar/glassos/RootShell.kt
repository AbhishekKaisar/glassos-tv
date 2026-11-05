package io.github.abhishekkaisar.glassos

import java.io.EOFException
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A root shell on rooted TVs, so setup can run the grants without ADB debugging. One `su` process runs every
 * command, so the root manager asks once. Each command's output is read up to a marker line it echoes after it.
 */
class RootShell private constructor(private val p: Process) : AutoCloseable {
    private val input = p.outputStream.bufferedWriter()
    private val output = p.inputStream.bufferedReader()

    /** Runs [command] as root and returns its output (stdout and stderr). Call off the UI thread. */
    fun shell(command: String): String {
        val end = "__glass_done_${System.nanoTime()}__"
        // The bare echo ends output that has no newline of its own, so the marker is always a line by itself.
        input.write("$command 2>&1; echo; echo $end\n")
        input.flush()
        val out = StringBuilder()
        while (true) {
            val line = output.readLine() ?: throw EOFException("su closed")
            if (line == end) break
            out.append(line).append('\n')
        }
        return out.toString().trim()
    }

    override fun close() {
        try { input.write("exit\n"); input.flush() } catch (e: Throwable) { }
        try { p.destroy() } catch (e: Throwable) { }
    }

    companion object {
        private val DIRS = listOf("/system/xbin", "/system/bin", "/sbin", "/su/bin", "/system/sbin", "/vendor/bin")

        /** True when this TV has an `su` (it is rooted). Only looks for the file, so no root prompt shows. */
        fun available(): Boolean = try {
            ((System.getenv("PATH") ?: "").split(':') + DIRS).any { it.isNotEmpty() && File(it, "su").exists() }
        } catch (e: Throwable) { false }

        /**
         * Starts the root shell once the root manager allows it. Throws when it refuses, or gives no answer within
         * [timeoutMs] (the shell is then killed). Call off the UI thread.
         */
        fun open(timeoutMs: Long = 30_000): RootShell {
            val p = ProcessBuilder("su").redirectErrorStream(true).start()
            val s = RootShell(p)
            val answered = AtomicBoolean(false)
            Thread {
                try { Thread.sleep(timeoutMs) } catch (e: InterruptedException) { }
                if (!answered.get()) p.destroy()
            }.apply { isDaemon = true }.start()
            val id = try { s.shell("id") } catch (e: Throwable) { s.close(); throw e } finally { answered.set(true) }
            if (!id.contains("uid=0")) { s.close(); throw SecurityException("su is not root: ${id.take(80)}") }
            return s
        }
    }
}
