package com.example.mt5

import android.content.Context
import java.io.File
import java.io.FileInputStream

data class ProotRuntimeInspection(
    val proot: File?,
    val x11Server: File?,
    val box64: File?,
    val wine: File?,
    val winePrefix: File,
    val terminal: File?,
    val missingComponents: List<String>,
    val launchBlockers: List<String>
) {
    val runtimeFilesReady: Boolean
        get() = missingComponents.isEmpty()

    val canLaunchEmbeddedTerminal: Boolean
        get() = runtimeFilesReady && launchBlockers.isEmpty()
}

object ProotRuntimeLayout {
    fun inspect(context: Context): ProotRuntimeInspection = inspect(
        rootfs = File(context.filesDir, "rootfs"),
        prefix = File(context.filesDir, "wineprefix"),
        nativeLibraryDir = File(context.applicationInfo.nativeLibraryDir)
    )

    internal fun inspect(rootfs: File, prefix: File, nativeLibraryDir: File): ProotRuntimeInspection {
        val proot = File(nativeLibraryDir, "libproot.so").takeIf { it.isFile && isArm64Elf(it) }
        val x11Server = File(nativeLibraryDir, "libXlorie.so").takeIf { it.isFile && isArm64Elf(it) }
        val box64 = listOf(File(rootfs, "usr/bin/box64"), File(rootfs, "usr/local/bin/box64"))
            .firstOrNull { it.isFile && isArm64Elf(it) }
        val wine = listOf(
            File(rootfs, "opt/wine/bin/wine"),
            File(rootfs, "usr/bin/wine"),
            File(rootfs, "usr/local/bin/wine")
        ).firstOrNull { it.isFile }
        val terminal = listOf(
            File(prefix, "drive_c/Program Files/MetaTrader 5/terminal64.exe"),
            File(prefix, "drive_c/Program Files (x86)/MetaTrader 5/terminal64.exe")
        ).firstOrNull { it.isFile && hasWindowsHeader(it) }

        val missing = buildList {
            if (proot == null) add("Valid ARM64 PRoot native library (libproot.so)")
            if (x11Server == null) add("Valid ARM64 embedded X11 server (libXlorie.so)")
            if (box64 == null) add("Valid ARM64 Box64 executable inside the Linux rootfs")
            if (wine == null) add("Wine inside the Linux rootfs")
            if (!File(prefix, "system.reg").isFile) add("Initialized Wine prefix")
            if (terminal == null) add("MetaTrader 5 terminal64.exe")
        }

        return ProotRuntimeInspection(
            proot = proot,
            x11Server = x11Server,
            box64 = box64,
            wine = wine,
            winePrefix = prefix,
            terminal = terminal,
            missingComponents = missing,
            launchBlockers = listOf(
                "The Xlorie native Surface attachment and frame-presentation path is not implemented in TradePulse.",
                "The X11 input socket protocol has not been connected to a verified server instance."
            )
        )
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

    private fun hasWindowsHeader(file: File): Boolean = runCatching {
        FileInputStream(file).use { input ->
            input.read() == 'M'.code && input.read() == 'Z'.code
        }
    }.getOrDefault(false)
}
