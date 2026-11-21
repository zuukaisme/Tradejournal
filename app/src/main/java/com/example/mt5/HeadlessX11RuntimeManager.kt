package com.example.mt5

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object HeadlessX11RuntimeManager {
    private const val MAX_UNPACKED_BYTES = 1024L * 1024L * 1024L

    fun runtimeDirectory(context: Context): File = File(context.filesDir, "headless-x11")

    fun isInstalled(context: Context): Boolean {
        val root = runtimeDirectory(context)
        return isArm64Elf(File(root, "bin/Xvfb")) &&
            File(root, "lib").isDirectory &&
            File(root, "share/X11/xkb").isDirectory &&
            File(root, "share/fonts/misc").isDirectory
    }

    @Synchronized
    fun install(context: Context, uri: Uri): Result<Unit> = runCatching {
        val staging = File(context.filesDir, "headless-x11-staging")
        staging.deleteRecursively()
        check(staging.mkdirs()) { "Unable to create the headless X11 staging directory." }

        var extractedBytes = 0L
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Unable to open the selected Xvfb runtime bundle.")

        input.use { source ->
            ZipInputStream(BufferedInputStream(source)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val relativePath = entry.name.replace(92.toChar(), '/').trimStart('/')
                    require(relativePath.isNotBlank()) { "The Xvfb bundle contains an empty path." }
                    require(relativePath.split('/').none { it == ".." || it == "." }) {
                        "The Xvfb bundle contains an unsafe file path."
                    }

                    val destination = File(staging, relativePath).canonicalFile
                    require(destination.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                        "The Xvfb bundle contains a path outside its installation directory."
                    }

                    if (entry.isDirectory) {
                        check(destination.mkdirs() || destination.isDirectory) {
                            "Unable to create ${entry.name}."
                        }
                    } else {
                        destination.parentFile?.let {
                            check(it.mkdirs() || it.isDirectory) { "Unable to create an Xvfb runtime folder." }
                        }
                        FileOutputStream(destination).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                extractedBytes += count
                                require(extractedBytes <= MAX_UNPACKED_BYTES) {
                                    "The Xvfb bundle exceeds the 1 GB extraction limit."
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
        }

        require(isArm64Elf(File(staging, "bin/Xvfb"))) {
            "bin/Xvfb must be an Android ARM64 executable."
        }
        require(File(staging, "lib").isDirectory) { "The Xvfb bundle is missing its lib directory." }
        require(File(staging, "share/X11/xkb").isDirectory) {
            "The Xvfb bundle is missing share/X11/xkb."
        }
        require(File(staging, "share/fonts/misc").isDirectory) {
            "The Xvfb bundle is missing the Xorg misc fonts."
        }

        File(staging, "bin/Xvfb").setExecutable(true, false)
        val installed = runtimeDirectory(context)
        val backup = File(context.filesDir, "headless-x11-backup")
        backup.deleteRecursively()
        if (installed.exists()) check(installed.renameTo(backup)) {
            "Unable to replace the existing headless X11 runtime."
        }
        if (!staging.renameTo(installed)) {
            if (backup.exists()) backup.renameTo(installed)
            error("Unable to finish installing the headless X11 runtime.")
        }
        backup.deleteRecursively()
    }.onFailure {
        File(context.filesDir, "headless-x11-staging").deleteRecursively()
    }

    private fun isArm64Elf(file: File): Boolean = runCatching {
        FileInputStream(file).use { input ->
            val header = ByteArray(20)
            var offset = 0
            while (offset < header.size) {
                val count = input.read(header, offset, header.size - offset)
                if (count < 0) return@use false
                offset += count
            }
            header[0] == 0x7f.toByte() &&
                header[1] == 'E'.code.toByte() &&
                header[2] == 'L'.code.toByte() &&
                header[3] == 'F'.code.toByte() &&
                header[4] == 2.toByte() &&
                header[5] == 1.toByte() &&
                ((header[18].toInt() and 0xff) or ((header[19].toInt() and 0xff) shl 8)) == 183
        }
    }.getOrDefault(false)
}
