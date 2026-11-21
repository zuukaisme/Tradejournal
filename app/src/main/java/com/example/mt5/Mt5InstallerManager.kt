package com.example.mt5

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object Mt5InstallerManager {
    private const val MAX_INSTALLER_BYTES = 200L * 1024L * 1024L

    fun installerFile(context: Context): File =
        File(context.filesDir, "mt5-installers/mt5setup.exe")

    fun isInstallerStaged(context: Context): Boolean {
        val file = installerFile(context)
        return file.isFile && file.length() >= 2 && hasWindowsExecutableHeader(file)
    }

    @Synchronized
    fun importInstaller(context: Context, uri: Uri): Result<File> = runCatching {
        val directory = File(context.filesDir, "mt5-installers")
        check(directory.mkdirs() || directory.isDirectory) {
            "Unable to create the MT5 installer folder."
        }

        val temporary = File(directory, "mt5setup.exe.part")
        val destination = installerFile(context)
        var total = 0L

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_INSTALLER_BYTES) {
                        "The MT5 installer is larger than the 200 MB import limit."
                    }
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        } ?: error("Unable to read the selected installer.")

        require(total >= 2 && hasWindowsExecutableHeader(temporary)) {
            "The selected file is not a valid Windows .exe installer."
        }

        if (destination.exists()) check(destination.delete()) {
            "Unable to replace the previously imported installer."
        }
        check(temporary.renameTo(destination)) {
            "Unable to save the MT5 installer."
        }
        destination
    }.onFailure {
        File(context.filesDir, "mt5-installers/mt5setup.exe.part").delete()
    }

    private fun hasWindowsExecutableHeader(file: File): Boolean = runCatching {
        FileInputStream(file).use { input ->
            input.read() == 'M'.code && input.read() == 'Z'.code
        }
    }.getOrDefault(false)
}
