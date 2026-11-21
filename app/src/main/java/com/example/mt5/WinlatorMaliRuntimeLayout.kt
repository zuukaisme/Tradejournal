package com.example.mt5

import java.io.File
import java.io.FileInputStream

data class WinlatorMaliRuntimeInspection(
    val imageFsVersion: String?,
    val box64: File?,
    val wine: File?,
    val winePrefix: File,
    val hostDll: File?,
    val terminal: File?,
    val missingComponents: List<String>,
    val launchBlockers: List<String>
) {
    val runtimeFilesReady: Boolean
        get() = missingComponents.isEmpty()

    val canLaunchEmbeddedTerminal: Boolean
        get() = runtimeFilesReady && launchBlockers.isEmpty()
}

object WinlatorMaliRuntimeLayout {
    private const val PROTON_RUNTIME = "proton-10-arm64ec"

    fun inspect(imageFs: File): WinlatorMaliRuntimeInspection {
        val versionFile = File(imageFs, ".winlator/.img_version")
        val version = versionFile.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
        val box64 = File(imageFs, "usr/bin/box64").takeIf { it.isFile && isElfMachine(it, 183) }
        val wine = File(imageFs, "opt/$PROTON_RUNTIME/bin/wine").takeIf { it.isFile }
        val prefix = File(imageFs, "home/xuser/.wine")
        val system32 = File(prefix, "drive_c/windows/system32")
        val hostDll = listOf("wowbox64.dll", "libwow64fex.dll")
            .map { File(system32, it) }
            .firstOrNull { it.isFile }
        val terminal = listOf(
            File(prefix, "drive_c/Program Files/MetaTrader 5/terminal64.exe"),
            File(prefix, "drive_c/Program Files (x86)/MetaTrader 5/terminal64.exe")
        ).firstOrNull { it.isFile && hasWindowsHeader(it) }

        val missing = buildList {
            if (version == null) add("Winlator Mali ImageFS version marker")
            if (box64 == null) add("Valid ARM64 Box64 ELF at usr/bin/box64")
            if (wine == null) add("Proton ARM64EC at opt/$PROTON_RUNTIME/bin/wine")
            if (!prefix.isDirectory) add("Wine prefix at home/xuser/.wine")
            if (hostDll == null) add("WOWBox64/FEX host DLL in the Wine system32 directory")
            if (terminal == null) add("Valid MetaTrader 5 terminal64.exe inside the Wine prefix")
        }

        return WinlatorMaliRuntimeInspection(
            imageFsVersion = version,
            box64 = box64,
            wine = wine,
            winePrefix = prefix,
            hostDll = hostDll,
            terminal = terminal,
            missingComponents = missing,
            launchBlockers = listOf("Headless Box64/Wine process supervision and virtual-display startup/shutdown are not implemented.")
        )
    }

    private fun isElfMachine(file: File, machine: Int): Boolean = runCatching {
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
                ((header[18].toInt() and 0xff) or ((header[19].toInt() and 0xff) shl 8)) == machine
        }
    }.getOrDefault(false)

    private fun hasWindowsHeader(file: File): Boolean = runCatching {
        FileInputStream(file).use { input ->
            input.read() == 'M'.code && input.read() == 'Z'.code
        }
    }.getOrDefault(false)
}
