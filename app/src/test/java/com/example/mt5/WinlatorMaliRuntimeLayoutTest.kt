package com.example.mt5

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WinlatorMaliRuntimeLayoutTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun reportsMissingWinlatorMaliComponents() {
        val inspection = WinlatorMaliRuntimeLayout.inspect(temporaryFolder.root)

        assertFalse(inspection.runtimeFilesReady)
        assertFalse(inspection.canLaunchEmbeddedTerminal)
        assertTrue(inspection.missingComponents.contains("Winlator Mali ImageFS version marker"))
        assertTrue(inspection.missingComponents.contains("Valid ARM64 Box64 ELF at usr/bin/box64"))
    }

    @Test
    fun recognizesArm64EcRuntimeAndInstalledMt5Terminal() {
        val root = temporaryFolder.root
        createFile(root, ".winlator/.img_version", "21")
        createArm64Elf(root, "usr/bin/box64")
        createFile(root, "opt/proton-10-arm64ec/bin/wine")
        createFile(root, "home/xuser/.wine/drive_c/windows/system32/wowbox64.dll")
        createFile(root, "home/xuser/.wine/drive_c/Program Files/MetaTrader 5/terminal64.exe", "MZ")

        val inspection = WinlatorMaliRuntimeLayout.inspect(root)

        assertTrue(inspection.runtimeFilesReady)
        assertFalse(inspection.canLaunchEmbeddedTerminal)
        assertEquals("21", inspection.imageFsVersion)
        assertEquals("wowbox64.dll", inspection.hostDll?.name)
        assertEquals("terminal64.exe", inspection.terminal?.name)
        assertEquals(1, inspection.launchBlockers.size)
    }

    @Test
    fun acceptsFexHostDllWhenWowBox64IsNotInstalled() {
        val root = temporaryFolder.root
        createFile(root, ".winlator/.img_version", "21")
        createArm64Elf(root, "usr/bin/box64")
        createFile(root, "opt/proton-10-arm64ec/bin/wine")
        createFile(root, "home/xuser/.wine/drive_c/windows/system32/libwow64fex.dll")
        createFile(root, "home/xuser/.wine/drive_c/Program Files (x86)/MetaTrader 5/terminal64.exe", "MZ")

        val inspection = WinlatorMaliRuntimeLayout.inspect(root)

        assertTrue(inspection.runtimeFilesReady)
        assertEquals("libwow64fex.dll", inspection.hostDll?.name)
        assertEquals("terminal64.exe", inspection.terminal?.name)
    }

    @Test
    fun rejectsWrongArchitectureBox64AndInvalidTerminalFiles() {
        val root = temporaryFolder.root
        createFile(root, ".winlator/.img_version", "21")
        createFile(root, "usr/bin/box64", "not-an-elf")
        createFile(root, "opt/proton-10-arm64ec/bin/wine")
        createFile(root, "home/xuser/.wine/drive_c/windows/system32/wowbox64.dll")
        createFile(root, "home/xuser/.wine/drive_c/Program Files/MetaTrader 5/terminal64.exe", "not-an-exe")

        val inspection = WinlatorMaliRuntimeLayout.inspect(root)

        assertFalse(inspection.runtimeFilesReady)
        assertTrue(inspection.missingComponents.contains("Valid ARM64 Box64 ELF at usr/bin/box64"))
        assertTrue(inspection.missingComponents.contains("Valid MetaTrader 5 terminal64.exe inside a Wine prefix"))
    }

    @Test
    fun discoversTheWinePrefixInsideAWinlatorContainer() {
        val root = temporaryFolder.root
        createFile(root, ".winlator/.img_version", "21")
        createArm64Elf(root, "usr/bin/box64")
        createFile(root, "opt/proton-10-arm64ec/bin/wine")
        createFile(root, "home/xuser-3/.wine/drive_c/windows/system32/wowbox64.dll")
        createFile(root, "home/xuser-3/.wine/drive_c/Program Files/MetaTrader 5/terminal64.exe", "MZ")

        val inspection = WinlatorMaliRuntimeLayout.inspect(root)

        assertTrue(inspection.runtimeFilesReady)
        assertEquals("xuser-3", inspection.winePrefix?.parentFile?.name)
        assertEquals("terminal64.exe", inspection.terminal?.name)
    }

    private fun createArm64Elf(root: File, relativePath: String): File {
        val file = File(root, relativePath)
        file.parentFile?.mkdirs()
        val header = ByteArray(20)
        header[0] = 0x7f.toByte()
        header[1] = 'E'.code.toByte()
        header[2] = 'L'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = 2
        header[5] = 1
        header[18] = 183.toByte()
        file.writeBytes(header)
        return file
    }

    private fun createFile(root: File, relativePath: String, content: String = ""): File {
        val file = File(root, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }
}
