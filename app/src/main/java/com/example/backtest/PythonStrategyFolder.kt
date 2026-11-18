package com.example.backtest

import android.os.Environment
import java.io.File

object PythonStrategyFolder {
    const val RELATIVE_PATH = "Downloads/Tradepulse/strategies"

    fun directory(): File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "Tradepulse/strategies"
    )

    fun ensureDirectory(): File? {
        if (!BinaryCandleCache.hasCacheStorageAccess()) return null
        val folder = directory()
        if (!folder.exists()) folder.mkdirs()
        return folder.takeIf { it.isDirectory && it.canRead() }
    }
}
