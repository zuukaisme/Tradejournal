package com.example.mt5

import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TermuxHeadlessX11BridgeTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun writesAValidLoopbackMitMagicCookieAuthorityRecord() {
        val file = File(temporaryFolder.root, "Xauthority")
        val cookie = ByteArray(16) { it.toByte() }

        TermuxHeadlessX11Bridge.writeAuthorizationFile(file, cookie)

        DataInputStream(FileInputStream(file)).use { input ->
            assertEquals(0, input.readUnsignedShort())
            assertEquals(4, input.readUnsignedShort())
            assertArrayEquals(byteArrayOf(127, 0, 0, 1), ByteArray(4).also(input::readFully))
            assertEquals(1, input.readUnsignedShort())
            assertEquals("0", ByteArray(1).also(input::readFully).toString(Charsets.US_ASCII))
            assertEquals(18, input.readUnsignedShort())
            assertEquals("MIT-MAGIC-COOKIE-1", ByteArray(18).also(input::readFully).toString(Charsets.US_ASCII))
            assertEquals(16, input.readUnsignedShort())
            assertArrayEquals(cookie, ByteArray(16).also(input::readFully))
        }
    }
}
