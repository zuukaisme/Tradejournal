package com.example.mt5

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import com.github.luben.zstd.ZstdInputStream

data class WinlatorImageFsImportResult(
    val destination: File,
    val filesWritten: Int,
    val bytesWritten: Long,
    val source: String
)

object WinlatorImageFsImporter {
    const val WINLATOR_PACKAGE = "com.winlator.mali"
    const val IMAGE_FS_ASSET = "imagefs.tzst"
    private const val MAX_EXPANDED_BYTES = 24L * 1024L * 1024L * 1024L
    private const val MAX_ENTRIES = 1_000_000

    fun bundledAssetAvailable(context: Context): Boolean = runCatching {
        context.createPackageContext(WINLATOR_PACKAGE, 0)
            .assets.open(IMAGE_FS_ASSET).use { true }
    }.getOrDefault(false)

    fun importBundledImageFs(context: Context): Result<WinlatorImageFsImportResult> = runCatching {
        val packageContext = context.createPackageContext(WINLATOR_PACKAGE, 0)
        val stage = File(context.filesDir, "imagefs-importing")
        val destination = File(context.filesDir, "imagefs")
        val backup = File(context.filesDir, "imagefs-previous")

        stage.deleteRecursively()
        check(stage.mkdirs()) { "Unable to create the ImageFS staging directory." }

        var entriesWritten = 0
        val expandedBytes = AtomicLong(0L)

        try {
            packageContext.assets.open(IMAGE_FS_ASSET).use { asset ->
                ZstdInputStream(BufferedInputStream(asset)).use { zstd ->
                    TarArchiveInputStream(zstd).use { tar ->
                        while (true) {
                            val entry = tar.nextTarEntry ?: break
                            entriesWritten++
                            check(entriesWritten <= MAX_ENTRIES) { "ImageFS archive contains too many entries." }

                            val relativePath = safeRelativePath(entry.name) ?: continue
                            if (relativePath.isEmpty()) continue
                            val output = File(stage, relativePath)
                            val canonicalStage = stage.canonicalFile.path
                            val canonicalOutput = output.canonicalFile.path
                            check(canonicalOutput.startsWith(canonicalStage + File.separator)) {
                                "ImageFS archive contains an unsafe path."
                            }

                            if (entry.isDirectory) {
                                check(output.mkdirs() || output.isDirectory) {
                                    "Unable to create ImageFS directory: $relativePath"
                                }
                                continue
                            }

                            if (!entry.isFile) continue
                            output.parentFile?.let {
                                check(it.mkdirs() || it.isDirectory) {
                                    "Unable to create ImageFS directory."
                                }
                            }

                            FileOutputStream(output).use { sink ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    val count = tar.read(buffer)
                                    if (count < 0) break
                                    val total = expandedBytes.addAndGet(count.toLong())
                                    check(total <= MAX_EXPANDED_BYTES) {
                                        "ImageFS archive expands beyond the 24 GiB safety limit."
                                    }
                                    sink.write(buffer, 0, count)
                                }
                                sink.fd.sync()
                            }
                            if ((entry.mode and 0b001001001) != 0) output.setExecutable(true, false)
                        }
                    }
                }
            }

            val version = File(stage, ".winlator/.img_version")
            check(version.isFile && version.readText().isNotBlank()) {
                "The imported archive is not a valid Winlator Mali ImageFS."
            }
            check(File(stage, "usr/bin/box64").isFile) {
                "The ImageFS archive does not contain the expected Box64 runtime."
            }

            backup.deleteRecursively()
            if (destination.exists()) {
                check(destination.renameTo(backup)) { "Unable to preserve the existing runtime before import." }
            }
            if (!stage.renameTo(destination)) {
                if (backup.exists()) backup.renameTo(destination)
                error("Unable to activate the imported Winlator ImageFS.")
            }
            backup.deleteRecursively()

            WinlatorImageFsImportResult(
                destination = destination,
                filesWritten = entriesWritten,
                bytesWritten = expandedBytes.get(),
                source = "Installed Winlator Mali app"
            )
        } catch (error: Throwable) {
            stage.deleteRecursively()
            throw error
        }
    }

    internal fun safeRelativePath(rawPath: String): String? {
        if (rawPath.isBlank() || rawPath.startsWith("/") || rawPath.startsWith("\\")) return null
        val segments = rawPath.replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() && it != "." }
        if (segments.isEmpty() || segments.any { it == ".." || it.contains(':') }) return null
        return segments.joinToString("/")
    }
}
