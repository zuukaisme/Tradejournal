package com.example.mt5

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WinlatorMaliRuntimeContentsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun findsArm64EcProtonInstalledInWinlatorContentsDirectory() {
        val root = temporaryFolder.root
        val imageFs = File(root, "imagefs")
        val contents = File(root, "contents")
        val wine = File(contents, "proton/proton-10.0-4-arm64ec-23/bin/wine")
        wine.parentFile?.mkdirs()
        wine.writeText("runtime")

        val inspection = WinlatorMaliRuntimeLayout.inspect(imageFs, contents)

        assertEquals(wine, inspection.wine)
        assertTrue(inspection.missingComponents.contains("Winlator Mali ImageFS version marker"))
    }
}
