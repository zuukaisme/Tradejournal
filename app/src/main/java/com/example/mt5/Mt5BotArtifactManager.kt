package com.example.mt5

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object Mt5BotArtifactManager {
    private const val MAX_ARTIFACT_BYTES = 128L * 1024L * 1024L

    fun artifactFile(context: Context, botId: Long): File {
        require(botId > 0L) { "Save this bot before importing a compiled artifact." }
        return File(context.filesDir, "mt5-bots/$botId.ex5")
    }

    fun hasArtifact(context: Context, botId: Long): Boolean {
        val file = runCatching { artifactFile(context, botId) }.getOrNull() ?: return false
        return file.isFile && file.length() > 0L
    }

    @Synchronized
    fun importArtifact(context: Context, botId: Long, displayName: String, uri: Uri): Result<File> = runCatching {
        require(displayName.substringAfterLast('.', "").equals("ex5", ignoreCase = true)) {
            "Select a compiled MT5 .ex5 file."
        }
        val destination = artifactFile(context, botId)
        val directory = destination.parentFile ?: error("Unable to create the bot artifact folder.")
        check(directory.mkdirs() || directory.isDirectory) {
            "Unable to create the bot artifact folder."
        }
        val temporary = File(directory, "$botId.ex5.part")
        var total = 0L
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_ARTIFACT_BYTES) {
                        "The compiled bot is larger than the 128 MB import limit."
                    }
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        } ?: error("Unable to read the selected compiled bot.")
        require(total > 0L) { "The selected compiled bot is empty." }
        val backup = File(directory, "$botId.ex5.bak")
        if (backup.exists()) check(backup.delete()) { "Unable to prepare the artifact replacement." }
        if (destination.exists()) check(destination.renameTo(backup)) {
            "Unable to preserve the previously imported artifact."
        }
        if (!temporary.renameTo(destination)) {
            if (backup.exists()) backup.renameTo(destination)
            error("Unable to save the compiled bot.")
        }
        backup.delete()
        destination
    }.onFailure {
        runCatching { File(context.filesDir, "mt5-bots/$botId.ex5.part").delete() }
    }
}
