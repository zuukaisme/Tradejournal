package com.example.mt5

import java.io.File
import java.io.FileInputStream

data class WinlatorMaliRuntimeInspection(
    val imageFsVersion: String?,
    val box64: File?,
    val wine: File?,
    val winePrefix: File?,
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
        val box64 = listOf(
            File(imageFs, "usr/bin/box64"),
            File(imageFs, "usr/local/bin/box64")
        ).firstOrNull { it.isFile && isElfMachine(it, 183) }
        val proton = File(imageFs, "opt/$PROTON_RUNTIME")
        val wine = listOf(
            File(proton, "bin/wine"),
            File(proton, "bin/wine64")
        ).firstOrNull { it.isFile }

        val prefixes = findWinePrefixes(imageFs)
        val prefixCandidates = prefixes.map { prefix ->
            val system32 = File(prefix, "drive_c/windows/system32")
            val hostDll = listOf("wowbox64.dll", "libwow64fex.dll")
                .map { File(system32, it) }
                .firstOrNull { it.isFile }
            Triple(prefix, hostDll, findTerminal(prefix))
        }
        val selected = prefixCandidates.firstOrNull { it.second != null && it.third != null }
            ?: prefixCandidates.firstOrNull { it.second != null }
            ?: prefixCandidates.firstOrNull { it.third != null }
            ?: prefixCandidates.firstOrNull()

        val selectedPrefix = selected?.first
        val hostDll = selected?.second
        val terminal = selected?.third

        val missing = buildList {
            if (version == null) add("Winlator Mali ImageFS version marker")
            if (box64 == null) add("Valid ARM64 Box64 ELF at usr/bin/box64")
            if (wine == null) add("Proton ARM64EC Wine executable at opt/$PROTON_RUNTIME/bin/wine")
            if (selectedPrefix == null) add("Wine prefix in home/xuser/.wine or home/xuser-<id>/.wine")
            if (hostDll == null) add("WOWBox64/FEX host DLL in the selected Wine prefix system32 directory")
            if (terminal == null) add("Valid MetaTrader 5 terminal64.exe inside a Wine prefix")
        }

        return WinlatorMaliRuntimeInspection(
            imageFsVersion = version,
            box64 = box64,
            wine = wine,
            winePrefix = selectedPrefix,
            hostDll = hostDll,
            terminal = terminal,
            missingComponents = missing,
            launchBlockers = listOf(
                "The Winlator Mali XServer and guest-process environment are not integrated into TradePulse. A headless display backend and lifecycle supervision are still required."
            )
        )
    }

    private fun findWinePrefixes(imageFs: File): List<File> {
        val home = File(imageFs, "home")
        val prefixes = mutableListOf<File>()
        val defaultPrefix = File(home, "xuser/.wine")
        if (defaultPrefix.isDirectory) prefixes += defaultPrefix

        home.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && it.name.matches(Regex("xuser-\\d+")) }
            ?.map { File(it, ".wine") }
            ?.filter { it.isDirectory }
            ?.sortedBy { it.parentFile?.name?.removePrefix("xuser-")?.toIntOrNull() ?: Int.MAX_VALUE }
            ?.forEach(prefixes::add)

        return prefixes.distinctBy { it.absolutePath }
    }

    private fun findTerminal(prefix: File): File? {
        val candidates = listOf(
            File(prefix, "drive_c/Program Files/MetaTrader 5/terminal64.exe"),
            File(prefix, "drive_c/Program Files (x86)/MetaTrader 5/terminal64.exe")
        )
        return candidates.firstOrNull { it.isFile && hasWindowsHeader(it) }
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
