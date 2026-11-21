package com.example.mt5

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong

data class WinlatorRuntimeTransferResult(
    val wineRuntime: File,
    val hostDll: File,
    val filesCopied: Int,
    val bytesCopied: Long
)

object WinlatorRuntimeTransferImporter {
    private const val MAX_BYTES = 24L * 1024L * 1024L * 1024L
    private const val MAX_ENTRIES = 1_000_000

    fun importFromTree(context: Context, treeUri: Uri): Result<WinlatorRuntimeTransferResult> = runCatching {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Unable to open the selected runtime transfer folder.")
        val imageFsDocument = root.findFile("imagefs")
            ?.takeIf { it.isDirectory }
            ?: error("The selected folder must contain an imagefs folder.")

        val stage = File(context.cacheDir, "winlator-runtime-transfer")
        stage.deleteRecursively()
        check(stage.mkdirs()) { "Unable to create the runtime import staging folder." }

        val copiedBytes = AtomicLong(0L)
        val entries = AtomicLong(0L)
        try {
            copyDocumentTree(context, imageFsDocument, stage, copiedBytes, entries)

            val proton = File(stage, "opt").listFiles()
                ?.filter { it.isDirectory }
                ?.sortedByDescending { it.name.contains("arm64ec", true) }
                ?.firstOrNull { directory ->
                    File(directory, "bin/wine").isFile || File(directory, "bin/wine64").isFile
                } ?: error("No Proton/Wine runtime was found under imagefs/opt.")

            val home = File(stage, "home")
            val hostDll = home.walkTopDown()
                .maxDepth(8)
                .firstOrNull {
                    it.isFile && (it.name.equals("wowbox64.dll", true) || it.name.equals("libwow64fex.dll", true)) &&
                        it.parentFile?.name.equals("system32", true)
                } ?: error("No wowbox64.dll or libwow64fex.dll was found under imagefs/home.")

            val relativePrefix = hostDll.parentFile.parentFile.parentFile.parentFile
            val relativeHome = relativePrefix.parentFile
            val prefixUser = relativeHome.name
            check(prefixUser == "xuser" || prefixUser.matches(Regex("xuser-\\d+"))) {
                "The host DLL is not inside a supported Winlator Wine prefix."
            }

            val imageFs = File(context.filesDir, "imagefs")
            val targetRuntime = File(imageFs, "opt/" + proton.name)
            replaceDirectory(proton, targetRuntime)

            val targetDll = File(
                imageFs,
                "home/" + prefixUser + "/.wine/drive_c/windows/system32/" + hostDll.name
            )
            targetDll.parentFile?.let {
                check(it.mkdirs() || it.isDirectory) { "Unable to create the Wine system32 directory." }
            }
            hostDll.copyTo(targetDll, overwrite = true)

            WinlatorRuntimeTransferResult(
                wineRuntime = targetRuntime,
                hostDll = targetDll,
                filesCopied = entries.get().toInt(),
                bytesCopied = copiedBytes.get()
            )
        } finally {
            stage.deleteRecursively()
        }
    }

    private fun copyDocumentTree(
        context: Context,
        source: DocumentFile,
        destination: File,
        bytes: AtomicLong,
        entries: AtomicLong
    ) {
        for (child in source.listFiles()) {
            val name = child.name ?: continue
            check(name != "." && name != ".." && !name.contains('/') && !name.contains('\\')) {
                "The selected folder contains an unsafe filename."
            }
            check(entries.incrementAndGet() <= MAX_ENTRIES) {
                "The selected runtime contains too many files."
            }
            val output = File(destination, name)
            if (child.isDirectory) {
                check(output.mkdirs() || output.isDirectory) { "Unable to create " + output.name + "." }
                copyDocumentTree(context, child, output, bytes, entries)
            } else if (child.isFile) {
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    FileOutputStream(output).use { sink ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            check(bytes.addAndGet(count.toLong()) <= MAX_BYTES) {
                                "Runtime transfer exceeds the 24 GiB safety limit."
                            }
                            sink.write(buffer, 0, count)
                        }
                        sink.fd.sync()
                    }
                } ?: error("Unable to read " + child.name + ".")
            }
        }
    }

    private fun replaceDirectory(source: File, destination: File) {
        val parent = destination.parentFile ?: error("Invalid runtime destination.")
        check(parent.mkdirs() || parent.isDirectory) { "Unable to create runtime destination." }
        val backup = File(parent, destination.name + ".previous")
        backup.deleteRecursively()
        if (destination.exists()) {
            check(destination.renameTo(backup)) { "Unable to preserve existing " + destination.name + "." }
        }
        if (!source.renameTo(destination)) {
            if (backup.exists()) backup.renameTo(destination)
            error("Unable to install " + destination.name + ".")
        }
        backup.deleteRecursively()
    }
}
