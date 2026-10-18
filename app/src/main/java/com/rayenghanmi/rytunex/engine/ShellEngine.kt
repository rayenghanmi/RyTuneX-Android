package com.rayenghanmi.rytunex.engine

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

data class CommandResult(
    val command: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val isSuccess: Boolean
) {
    val output: String get() = when {
        stderr.isNotBlank() && stdout.isNotBlank() -> "$stdout\n[ERR]: $stderr"
        stderr.isNotBlank() -> "[ERR]: $stderr"
        stdout.isNotBlank() -> stdout
        else -> "Execution completed (Code $exitCode)"
    }
}

object ShellEngine {

    suspend fun exec(command: String, timeoutMs: Long = 15000L): CommandResult = withContext(Dispatchers.IO) {
        var process: Process? = null
        try {
            val result = withTimeoutOrNull(timeoutMs) {
                try {
                    if (!Shizuku.pingBinder()) {
                        return@withTimeoutOrNull CommandResult(
                            command = command,
                            stdout = "",
                            stderr = "Shizuku service is not active. Please start Shizuku via Wireless Debugging.",
                            exitCode = -1,
                            isSuccess = false
                        )
                    }

                    // Shizuku newProcess reflection
                    val method = Shizuku::class.java.getDeclaredMethod(
                        "newProcess",
                        Array<String>::class.java,
                        Array<String>::class.java,
                        String::class.java
                    )
                    method.isAccessible = true
                    val proc = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
                    process = proc

                    coroutineScope {
                        // Read stdout and stderr concurrently to prevent Linux pipe buffer deadlock
                        val stdoutDeferred = async(Dispatchers.IO) {
                            try {
                                BufferedReader(InputStreamReader(proc.inputStream)).use { it.readText() }.trim()
                            } catch (_: Throwable) {
                                ""
                            }
                        }
                        val stderrDeferred = async(Dispatchers.IO) {
                            try {
                                BufferedReader(InputStreamReader(proc.errorStream)).use { it.readText() }.trim()
                            } catch (_: Throwable) {
                                ""
                            }
                        }

                        val exitCode = proc.waitFor()
                        val stdout = stdoutDeferred.await()
                        val stderr = stderrDeferred.await()

                        CommandResult(
                            command = command,
                            stdout = stdout,
                            stderr = stderr,
                            exitCode = exitCode,
                            isSuccess = (exitCode == 0)
                        )
                    }
                } catch (e: Exception) {
                    CommandResult(
                        command = command,
                        stdout = "",
                        stderr = "Execution Exception: ${e.message}",
                        exitCode = -2,
                        isSuccess = false
                    )
                }
            }

            result ?: CommandResult(
                command = command,
                stdout = "",
                stderr = "Command timed out after ${timeoutMs}ms",
                exitCode = -3,
                isSuccess = false
            )
        } finally {
            process?.let { p ->
                try {
                    if (p.isAlive) {
                        p.destroyForcibly()
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    suspend fun checkPrivileges(): Pair<Int, String> = withContext(Dispatchers.IO) {
        if (!Shizuku.pingBinder()) return@withContext -1 to "Disconnected"
        val uidRes = exec("id -u")
        val uid = uidRes.stdout.trim().toIntOrNull() ?: -1
        val selinuxRes = exec("getenforce")
        val context = selinuxRes.stdout.ifBlank { "Unknown" }
        return@withContext uid to context
    }
}
