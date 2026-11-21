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
        assertTrue(inspection.missingComponents.contains("ARM64 Box64 at usr/bin/box64"))
    }

    @Test
    fun recognizesArm64EcRuntimeAndInstalledMt5Terminal() {
        val root = temporaryFolder.root
        createFile(root, ".winlator/.img_version", "21")
        createFile(root, "usr/bin/box64")
        createFile(root, "opt/proton-10-arm64ec/bin/wine")
        createFile(root, "home/xuser/.wine/drive_c/windows/system32/wowbox64.dll")
        createFile(root, "home/xuser/.wine/drive_c/Program Files/MetaTrader 5/terminal64.exe")

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
        createFile(root, "usr/bin/box64")
        createFile(root, "opt/proton-10-arm64ec/bin/wine")
        createFile(root, "home/xuser/.wine/drive_c/windows/system32/libwow64fex.dll")
        createFile(root, "home/xuser/.wine/drive_c/Program Files (x86)/MetaTrader 5/terminal64.exe")

        val inspection = WinlatorMaliRuntimeLayout.inspect(root)

        assertTrue(inspection.runtimeFilesReady)
        assertEquals("libwow64fex.dll", inspection.hostDll?.name)
        assertEquals("terminal64.exe", inspection.terminal?.name)
    }

    private fun createFile(root: File, relativePath: String, content: String = ""): File {
        val file = File(root, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }
}
