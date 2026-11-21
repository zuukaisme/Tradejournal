package com.example.mt5

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProotRuntimeLayoutTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun reportsMissingNativeAndGuestRuntimeComponents() {
        val root = temporaryFolder.root
        val inspection = ProotRuntimeLayout.inspect(File(root, "rootfs"), File(root, "wineprefix"), File(root, "lib"))

        assertFalse(inspection.runtimeFilesReady)
        assertFalse(inspection.canLaunchEmbeddedTerminal)
        assertTrue(inspection.missingComponents.contains("Valid ARM64 PRoot native library (libproot.so)"))
        assertTrue(inspection.missingComponents.contains("Valid ARM64 embedded X11 server (libXlorie.so)"))
        assertTrue(inspection.missingComponents.contains("Valid ARM64 Box64 executable inside the Linux rootfs"))
    }

    @Test
    fun recognizesArm64GeminiStyleLayoutButKeepsDisplayBlocked() {
        val root = temporaryFolder.root
        val rootfs = File(root, "rootfs")
        val prefix = File(root, "wineprefix")
        val native = File(root, "lib")
        createArm64Elf(native, "libproot.so")
        createArm64Elf(native, "libXlorie.so")
        createArm64Elf(rootfs, "usr/bin/box64")
        createFile(rootfs, "opt/wine/bin/wine")
        createFile(prefix, "system.reg")
        createFile(prefix, "drive_c/Program Files/MetaTrader 5/terminal64.exe", "MZ")

        val inspection = ProotRuntimeLayout.inspect(rootfs, prefix, native)

        assertTrue(inspection.runtimeFilesReady)
        assertFalse(inspection.canLaunchEmbeddedTerminal)
        assertEquals("box64", inspection.box64?.name)
        assertEquals("wine", inspection.wine?.name)
        assertEquals("terminal64.exe", inspection.terminal?.name)
        assertEquals(2, inspection.launchBlockers.size)
    }

    @Test
    fun rejectsNativeLibrariesAndBox64WithWrongElfArchitecture() {
        val root = temporaryFolder.root
        val rootfs = File(root, "rootfs")
        val prefix = File(root, "wineprefix")
        val native = File(root, "lib")
        createFile(native, "libproot.so", "not-an-elf")
        createFile(native, "libXlorie.so", "not-an-elf")
        createFile(rootfs, "usr/bin/box64", "not-an-elf")
        createFile(rootfs, "usr/bin/wine")
        createFile(prefix, "system.reg")
        createFile(prefix, "drive_c/Program Files/MetaTrader 5/terminal64.exe", "MZ")

        val inspection = ProotRuntimeLayout.inspect(rootfs, prefix, native)

        assertFalse(inspection.runtimeFilesReady)
        assertTrue(inspection.missingComponents.contains("Valid ARM64 PRoot native library (libproot.so)"))
        assertTrue(inspection.missingComponents.contains("Valid ARM64 embedded X11 server (libXlorie.so)"))
        assertTrue(inspection.missingComponents.contains("Valid ARM64 Box64 executable inside the Linux rootfs"))
    }

    @Test
    fun rejectsNonWindowsTerminalFiles() {
        val root = temporaryFolder.root
        val rootfs = File(root, "rootfs")
        val prefix = File(root, "wineprefix")
        val native = File(root, "lib")
        createArm64Elf(native, "libproot.so")
        createArm64Elf(native, "libXlorie.so")
        createArm64Elf(rootfs, "usr/bin/box64")
        createFile(rootfs, "opt/wine/bin/wine")
        createFile(prefix, "system.reg")
        createFile(prefix, "drive_c/Program Files/MetaTrader 5/terminal64.exe", "not-an-exe")

        val inspection = ProotRuntimeLayout.inspect(rootfs, prefix, native)

        assertFalse(inspection.runtimeFilesReady)
        assertTrue(inspection.terminal == null)
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
