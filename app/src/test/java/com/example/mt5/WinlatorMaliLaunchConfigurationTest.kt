package com.example.mt5

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WinlatorMaliLaunchConfigurationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun buildsTheWinlatorArm64EcEnvironmentAndPortableTerminalCommand() {
        val root = temporaryFolder.root
        createFile(root, ".winlator/.img_version", "21")
        createArm64Elf(root, "usr/bin/box64")
        createArm64Elf(root, "opt/proton-10-arm64ec/bin/wine")
        createFile(root, "usr/lib/.keep")
        createFile(root, "usr/tmp/.X11-unix/X0")
        createFile(root, "home/xuser/.wine/drive_c/windows/system32/libwow64fex.dll")
        val terminal = createFile(root, "home/xuser/.wine/drive_c/Program Files/MetaTrader 5/terminal64.exe", "MZ")
        val inspection = WinlatorMaliRuntimeLayout.inspect(root)

        val config = WinlatorMaliLaunchConfigurationBuilder.build(root, inspection)

        assertTrue(config.ready)
        assertEquals(listOf(terminal.absolutePath, "/portable"), config.arguments)
        assertEquals(root.absolutePath, config.workingDirectory.absolutePath)
        assertEquals(File(root, "home/xuser/.wine").absolutePath, config.environment["WINEPREFIX"])
        assertEquals("libwow64fex.dll", config.environment["HODLL"])
        assertEquals(":0", config.environment["DISPLAY"])
        assertEquals("0", config.environment["BOX64_MMAP32"])

        val authorization = createFile(root, "x11.Xauthority", "cookie")
        val tcpConfig = WinlatorMaliLaunchConfigurationBuilder.build(
            root,
            inspection,
            displayAddress = "127.0.0.1:0",
            xAuthorityFile = authorization,
            displayAvailable = true
        )
        assertEquals("127.0.0.1:0", tcpConfig.environment["DISPLAY"])
        assertEquals(authorization.absolutePath, tcpConfig.environment["XAUTHORITY"])
    }

    @Test
    fun allowsWineVersionProbeBeforePrefixAndMt5AreInstalled() {
        val root = temporaryFolder.root
        createArm64Elf(root, "opt/proton-10-arm64ec/bin/wine")
        createFile(root, "usr/lib/.keep")
        val inspection = WinlatorMaliRuntimeLayout.inspect(root)

        val config = WinlatorMaliLaunchConfigurationBuilder.build(
            root,
            inspection,
            displayAvailable = true,
            wineProbeOnly = true
        )

        assertTrue(config.ready)
        assertEquals(listOf("--version"), config.arguments)
        assertFalse(config.environment.containsKey("WINEPREFIX"))
    }

    @Test
    fun refusesToLaunchWithoutAnArm64WineExecutableAndLiveDisplaySocket() {
        val root = temporaryFolder.root
        createFile(root, ".winlator/.img_version", "21")
        createArm64Elf(root, "usr/bin/box64")
        createFile(root, "opt/proton-10-arm64ec/bin/wine", "not-an-elf")
        createFile(root, "usr/lib/.keep")
        createFile(root, "home/xuser/.wine/drive_c/windows/system32/wowbox64.dll")
        createFile(root, "home/xuser/.wine/drive_c/Program Files/MetaTrader 5/terminal64.exe", "MZ")
        val inspection = WinlatorMaliRuntimeLayout.inspect(root)

        val config = WinlatorMaliLaunchConfigurationBuilder.build(root, inspection)

        assertFalse(config.ready)
        assertTrue(config.blockers.any { it.contains("ARM64 ELF") })
        assertTrue(config.blockers.any { it.contains("headless X11 server") })
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
