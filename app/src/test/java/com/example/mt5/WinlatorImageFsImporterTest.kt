package com.example.mt5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WinlatorImageFsImporterTest {
    @Test
    fun normalizesSafeArchivePaths() {
        assertEquals("usr/bin/box64", WinlatorImageFsImporter.safeRelativePath("./usr//bin/box64"))
    }

    @Test
    fun rejectsTraversalAndAbsolutePaths() {
        assertNull(WinlatorImageFsImporter.safeRelativePath("../outside"))
        assertNull(WinlatorImageFsImporter.safeRelativePath("usr/../../outside"))
        assertNull(WinlatorImageFsImporter.safeRelativePath("/system/bin"))
        assertNull(WinlatorImageFsImporter.safeRelativePath("C:/windows"))
    }
}
