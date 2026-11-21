package com.example.mt5

import android.os.Build
import java.io.File
import java.io.IOException

class NativeProcessLauncher {
    private external fun nativeStart(
        executablePath: String,
        arguments: Array<String>,
        environment: Array<String>,
        workingDirectory: String,
        logPath: String,
        sdkInt: Int
    ): Int

    private external fun nativeWaitFor(pid: Int): Int
    private external fun nativeKill(pid: Int, signal: Int)
    private external fun nativeIsAlive(pid: Int): Boolean

    fun start(
        executable: File,
        arguments: List<String>,
        environment: Map<String, String>,
        workingDirectory: File,
        logFile: File
    ): Int {
        require(executable.isFile) { "Runtime executable not found: ${executable.absolutePath}" }
        require(arguments.isNotEmpty()) { "Runtime arguments cannot be empty." }
        val envp = environment.map { (key, value) -> "$key=$value" }.toTypedArray()
        return nativeStart(
            executable.absolutePath,
            arguments.toTypedArray(),
            envp,
            workingDirectory.absolutePath,
            logFile.absolutePath,
            Build.VERSION.SDK_INT
        ).also {
            if (it <= 0) throw IOException("The native runtime process could not be started.")
        }
    }

    fun waitFor(pid: Int): Int = nativeWaitFor(pid)
    fun stop(pid: Int) = nativeKill(pid, 15)
    fun forceStop(pid: Int) = nativeKill(pid, 9)
    fun isAlive(pid: Int): Boolean = nativeIsAlive(pid)

    companion object {
        init {
            System.loadLibrary("execshim")
        }
    }
}