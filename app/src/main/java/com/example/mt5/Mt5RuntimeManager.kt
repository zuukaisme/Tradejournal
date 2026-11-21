package com.example.mt5

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object Mt5RuntimeManager {
    private const val MAX_UNPACKED_BYTES = 6L * 1024L * 1024L * 1024L

    fun runtimeDirectory(context: Context): File = File(context.filesDir, "mt5-runtime")
    fun logFile(context: Context): File = File(context.filesDir, "mt5-runtime.log")

    fun isInstalled(context: Context): Boolean =
        requiredFiles(runtimeDirectory(context)).all { it.isFile }

    fun missingFiles(context: Context): List<String> {
        val root = runtimeDirectory(context)
        return requiredFiles(root).filterNot { it.isFile }
            .map { it.relativeTo(root).invariantSeparatorsPath }
    }

    @Synchronized
    fun installBundle(context: Context, uri: Uri): Result<Unit> = runCatching {
        val staging = File(context.filesDir, "mt5-runtime-staging")
        staging.deleteRecursively()
        check(staging.mkdirs()) { "Unable to create runtime staging directory." }

        var extractedBytes = 0L
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Unable to open the selected runtime bundle.")

        input.use { source ->
            ZipInputStream(BufferedInputStream(source)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val relativePath = entry.name.replace('\\', '/').trimStart('/')
                    require(relativePath.isNotBlank()) { "The runtime bundle contains an empty path." }
                    require(relativePath.split('/').none { it == ".." || it == "." }) {
                        "The runtime bundle contains an unsafe file path."
                    }

                    val destination = File(staging, relativePath).canonicalFile
                    require(destination.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                        "The runtime bundle contains a path outside its installation directory."
                    }

                    if (entry.isDirectory) {
                        check(destination.mkdirs() || destination.isDirectory) {
                            "Unable to create ${entry.name}."
                        }
                    } else {
                        destination.parentFile?.let {
                            check(it.mkdirs() || it.isDirectory) { "Unable to create runtime folder." }
                        }
                        FileOutputStream(destination).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                extractedBytes += count
                                require(extractedBytes <= MAX_UNPACKED_BYTES) {
                                    "The runtime bundle exceeds the 6 GB extraction limit."
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
        }

        val runtimeRoot = if (File(staging, "runtime").isDirectory) File(staging, "runtime") else staging
        val missing = requiredFiles(runtimeRoot).filterNot { it.isFile }
            .map { it.relativeTo(runtimeRoot).invariantSeparatorsPath }
        require(missing.isEmpty()) {
            "This is not a supported TradePulse MT5 runtime bundle. Missing: ${missing.joinToString()}"
        }

        File(runtimeRoot, "launch-mt5.sh").setExecutable(true, false)
        File(runtimeRoot, "bin/proot").setExecutable(true, false)
        File(runtimeRoot, "rootfs/usr/bin/box64").setExecutable(true, false)
        File(runtimeRoot, "rootfs/usr/bin/wine").setExecutable(true, false)
        File(runtimeRoot, "rootfs/usr/bin/Xvfb").setExecutable(true, false)

        val installed = runtimeDirectory(context)
        val backup = File(context.filesDir, "mt5-runtime-backup")
        backup.deleteRecursively()
        if (installed.exists()) check(installed.renameTo(backup)) { "Unable to replace the existing runtime." }
        val source = if (runtimeRoot == staging) staging else runtimeRoot
        if (!source.renameTo(installed)) {
            if (backup.exists()) backup.renameTo(installed)
            error("Unable to finish installing the MT5 runtime.")
        }
        backup.deleteRecursively()
        staging.deleteRecursively()
    }

    private fun requiredFiles(root: File): List<File> = listOf(
        File(root, "launch-mt5.sh"),
        File(root, "bin/proot"),
        File(root, "rootfs/usr/bin/box64"),
        File(root, "rootfs/usr/bin/wine"),
        File(root, "rootfs/usr/bin/Xvfb"),
        File(root, "mt5/terminal64.exe")
    )
}