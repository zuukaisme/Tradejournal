package com.example.mt5

import java.io.File
import java.io.FileInputStream

data class WinlatorMaliLaunchConfiguration(
    val executable: File?,
    val workingDirectory: File,
    val arguments: List<String>,
    val environment: Map<String, String>,
    val blockers: List<String>
) {
    val ready: Boolean
        get() = blockers.isEmpty() && executable != null
}

object WinlatorMaliLaunchConfigurationBuilder {
    fun build(
        imageFs: File,
        inspection: WinlatorMaliRuntimeInspection,
        displaySocket: File = File(imageFs, "usr/tmp/.X11-unix/X0"),
        displayAddress: String = ":0",
        xAuthorityFile: File? = null,
        displayAvailable: Boolean = displaySocket.exists(),
        wineProbeOnly: Boolean = false
    ): WinlatorMaliLaunchConfiguration {
        val prefix = inspection.winePrefix
        val wine = inspection.wine
        val terminal = inspection.terminal
        val home = prefix?.parentFile
        val root = imageFs.absoluteFile
        val wineBin = wine?.parentFile
        val hostDll = inspection.hostDll

        val blockers = buildList {
            if (wineProbeOnly) {
                if (wine == null || !isArm64Elf(wine)) {
                    add("Proton Wine launcher must be an executable Android ARM64 ELF")
                }
            } else {
                addAll(inspection.missingComponents)
                if (wine == null || !isArm64Elf(wine)) {
                    add("Proton Wine launcher must be an executable Android ARM64 ELF")
                }
                if (home == null || !home.isDirectory) {
                    add("The selected Wine home directory is missing")
                }
                if (prefix == null || !prefix.isDirectory) {
                    add("The selected Wine prefix is missing")
                }
                if (terminal == null || !terminal.isFile) {
                    add("MetaTrader 5 terminal executable is missing")
                }
                if (hostDll == null) {
                    add("The ARM64EC host DLL is missing")
                }
            }
            if (!displayAvailable) {
                add("A live headless X11 server is required at " + displaySocket.absolutePath)
            }
            if (!File(root, "usr/lib").isDirectory) {
                add("Winlator ImageFS usr/lib directory is missing")
            }
        }.distinct()

        val environment = buildMap {
            if (home != null) {
                put("HOME", home.absolutePath)
                put("USER", home.name)
            }
            if (prefix != null) put("WINEPREFIX", prefix.absolutePath)
            if (wineBin != null) {
                put("PATH", "${wineBin.absolutePath}:${File(root, "usr/bin").absolutePath}")
            }
            put("TMPDIR", File(root, "usr/tmp").absolutePath)
            put("XDG_DATA_DIRS", File(root, "usr/share").absolutePath)
            put("XDG_CONFIG_DIRS", File(root, "usr/etc/xdg").absolutePath)
            put("FONTCONFIG_PATH", File(root, "usr/etc/fonts").absolutePath)
            put("PREFIX", File(root, "usr").absolutePath)
            put("LD_LIBRARY_PATH", "${File(root, "usr/lib").absolutePath}:/system/lib64")
            put("DISPLAY", displayAddress)
            if (xAuthorityFile?.isFile == true) put("XAUTHORITY", xAuthorityFile.absolutePath)
            put("WINE_NO_DUPLICATE_EXPLORER", "1")
            put("WINE_DISABLE_FULLSCREEN_HACK", "1")
            put("WINE_GST_NO_GL", "1")
            put("GST_PLUGIN_FEATURE_RANK", "ximagesink:3000")
            put("WINE_NEW_NDIS", "1")
            put("BOX64_MMAP32", "0")
            if (hostDll != null) {
                put("HODLL", if (hostDll.name == "libwow64fex.dll") "libwow64fex.dll" else "wowbox64.dll")
            }
            put("OPENSSL_CONF", File(root, "usr/etc/tls/openssl.cnf").absolutePath)
            put("SSL_CERT_FILE", File(root, "usr/etc/tls/cert.pem").absolutePath)
            put("SSL_CERT_DIR", File(root, "usr/etc/tls/certs").absolutePath)
        }

        return WinlatorMaliLaunchConfiguration(
            executable = wine,
            workingDirectory = root,
            arguments = if (wineProbeOnly) listOf("--version") else terminal?.let { listOf(it.absolutePath, "/portable") }.orEmpty(),
            environment = environment,
            blockers = blockers
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
}
