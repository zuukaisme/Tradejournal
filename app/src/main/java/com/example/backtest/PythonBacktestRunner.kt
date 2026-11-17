package com.example.backtest

import android.content.Context
import android.os.Build
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipFile

fun interface BacktestProgressListener {
    fun onProgress(progressJson: String)
}

internal object ChaquopyAssetIntegrity {
    val REQUIRED_BOOTSTRAP_ENTRIES = listOf(
        "java/__init__.pyc",
        "java/primitive.pyc",
        "java/config.pyc"
    )

    fun requiredAssetPaths(assetKeys: Iterable<String>, abi: String): List<String> {
        val required = mutableListOf(
            "stdlib-common.imy",
            "bootstrap.imy",
            "cacert.pem"
        )
        val nativePrefix = "bootstrap-native/$abi/"
        for (key in assetKeys) {
            if (key.startsWith(nativePrefix)) {
                required.add(key)
            }
        }
        return required
    }

    fun verifyExtractedAssets(
        chaquopyDir: File,
        requiredFiles: List<String>
    ): Boolean {
        if (!chaquopyDir.isDirectory) return false
        for (relPath in requiredFiles) {
            val f = File(chaquopyDir, relPath)
            if (!f.isFile || f.length() <= 0L) return false
        }
        val bootstrapFile = File(chaquopyDir, "bootstrap.imy")
        val bootstrapValid = runCatching {
            ZipFile(bootstrapFile).use { zip ->
                REQUIRED_BOOTSTRAP_ENTRIES.all { entry ->
                    val ze = zip.getEntry(entry)
                    ze != null && ze.size > 0L
                }
            }
        }.getOrDefault(false)
        if (!bootstrapValid) return false

        val stdlibCommon = File(chaquopyDir, "stdlib-common.imy")
        val stdlibValid = runCatching {
            ZipFile(stdlibCommon).use { zip ->
                zip.entries().hasMoreElements()
            }
        }.getOrDefault(false)
        return stdlibValid
    }
}

object PythonBacktestRunner {
    private val lock = Any()
    private const val READY_MARKER_NAME = "chaquopy_ready_v1.marker"
    private const val LOCK_FILE_NAME = "chaquopy_init.lock"

    /**
     * Ensures Chaquopy assets are cleanly extracted and verified in `filesDir/chaquopy`
     * under a kernel-level cross-process FileLock so worker processes never race on
     * `bootstrap.imy`, `SharedPreferences`, or `.tmp` files.
     */
    fun ensureAssetsPrepared(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            withCrossProcessLock(appContext) {
                prepareSharedAssetsLocked(appContext)
            }
        }
    }

    fun ensureStarted(context: Context) {
        if (Python.isStarted()) return
        synchronized(lock) {
            if (Python.isStarted()) return
            val appContext = context.applicationContext
            withCrossProcessLock(appContext) {
                prepareSharedAssetsLocked(appContext)
            }
            Python.start(AndroidPlatform(appContext))
        }
    }

    private inline fun <T> withCrossProcessLock(appContext: Context, block: () -> T): T {
        val filesDir = appContext.filesDir.apply { mkdirs() }
        val lockFile = File(filesDir, LOCK_FILE_NAME)
        return RandomAccessFile(lockFile, "rw").use { raf ->
            raf.channel.lock().use {
                block()
            }
        }
    }

    private fun prepareSharedAssetsLocked(appContext: Context) {
        val buildJsonText = runCatching {
            appContext.assets.open("chaquopy/build.json").bufferedReader(Charsets.UTF_8).use {
                it.readText()
            }
        }.getOrNull() ?: return

        val buildJson = runCatching { JSONObject(buildJsonText) }.getOrNull() ?: return
        val assetsJson = buildJson.optJSONObject("assets") ?: return
        val abi = resolveSupportedAbi(appContext) ?: return
        val assetKeys = buildList {
            val it = assetsJson.keys()
            while (it.hasNext()) add(it.next())
        }
        val requiredFiles = ChaquopyAssetIntegrity.requiredAssetPaths(assetKeys, abi)

        val chaquopyDir = File(appContext.filesDir, "chaquopy")
        val readyMarker = File(appContext.filesDir, READY_MARKER_NAME)
        val markerMatches = readyMarker.isFile &&
            runCatching { readyMarker.readText(Charsets.UTF_8) == buildJsonText }.getOrDefault(false)

        if (markerMatches && ChaquopyAssetIntegrity.verifyExtractedAssets(chaquopyDir, requiredFiles)) {
            syncSharedPreferencesLocked(appContext, assetsJson)
            return
        }

        // Previous extraction was missing, from another app version, or corrupted by an
        // unlocked multi-process race: wipe partial state and extract cleanly once.
        readyMarker.delete()
        if (chaquopyDir.exists()) {
            chaquopyDir.deleteRecursively()
        }
        clearSharedPreferencesLocked(appContext)

        AndroidPlatform(appContext).getPath()

        if (!ChaquopyAssetIntegrity.verifyExtractedAssets(chaquopyDir, requiredFiles)) {
            if (chaquopyDir.exists()) {
                chaquopyDir.deleteRecursively()
            }
            clearSharedPreferencesLocked(appContext)
            AndroidPlatform(appContext).getPath()
        }

        if (ChaquopyAssetIntegrity.verifyExtractedAssets(chaquopyDir, requiredFiles)) {
            syncSharedPreferencesLocked(appContext, assetsJson)
            val markerTmp = File(appContext.filesDir, "$READY_MARKER_NAME.tmp")
            runCatching {
                markerTmp.writeText(buildJsonText, Charsets.UTF_8)
                if (readyMarker.exists()) readyMarker.delete()
                if (!markerTmp.renameTo(readyMarker)) {
                    markerTmp.copyTo(readyMarker, overwrite = true)
                    markerTmp.delete()
                }
            }
        }
    }

    private fun resolveSupportedAbi(appContext: Context): String? {
        for (abi in Build.SUPPORTED_ABIS) {
            val ok = runCatching {
                appContext.assets.open("chaquopy/stdlib-$abi.imy").use { true }
            }.getOrDefault(false)
            if (ok) return abi
        }
        return null
    }

    private fun clearSharedPreferencesLocked(appContext: Context) {
        val sp = appContext.getSharedPreferences("chaquopy", Context.MODE_PRIVATE)
        sp.edit().clear().commit()
    }

    private fun syncSharedPreferencesLocked(appContext: Context, assetsJson: JSONObject) {
        val sp = appContext.getSharedPreferences("chaquopy", Context.MODE_PRIVATE)
        val editor = sp.edit()
        var dirty = false
        val keys = assetsJson.keys()
        while (keys.hasNext()) {
            val assetName = keys.next()
            val expectedHash = assetsJson.optString(assetName)
            val prefKey = "asset.$assetName"
            if (sp.getString(prefKey, null) != expectedHash) {
                editor.putString(prefKey, expectedHash)
                dirty = true
            }
        }
        if (dirty) {
            editor.commit()
        }
    }

    fun runWithStrategyCode(
        context: Context,
        csvText: String,
        strategyCode: String,
        className: String = "",
        maxStopPoints: Double = 0.0,
        pointSize: Double = 1.0,
        progressListener: BacktestProgressListener? = null
    ): String {
        ensureStarted(context)
        return Python.getInstance()
            .getModule("backtest")
            .callAttr(
                "run_with_strategy_code",
                csvText,
                strategyCode,
                className,
                maxStopPoints,
                pointSize,
                progressListener
            )
            .toString()
    }

    fun runWithStrategyFile(
        context: Context,
        csvPath: String,
        strategyCode: String,
        className: String = "",
        maxStopPoints: Double = 0.0,
        pointSize: Double = 1.0,
        progressListener: BacktestProgressListener? = null
    ): String {
        ensureStarted(context)
        return Python.getInstance()
            .getModule("backtest")
            .callAttr(
                "run_with_strategy_file",
                csvPath,
                strategyCode,
                className,
                maxStopPoints,
                pointSize,
                progressListener
            )
            .toString()
    }

    fun runWithMultipleStrategiesFile(
        context: Context,
        csvPath: String,
        strategiesJson: String,
        maxStopPoints: Double = 0.0,
        pointSize: Double = 1.0,
        progressListener: BacktestProgressListener? = null
    ): String {
        ensureStarted(context)
        return Python.getInstance()
            .getModule("backtest")
            .callAttr(
                "run_with_multiple_strategies_file",
                csvPath,
                strategiesJson,
                maxStopPoints,
                pointSize,
                progressListener
            )
            .toString()
    }

    fun runWithStrategyFileSlice(
        context: Context,
        csvPath: String,
        strategyCode: String,
        className: String = "",
        maxStopPoints: Double = 0.0,
        pointSize: Double = 1.0,
        progressListener: BacktestProgressListener? = null,
        loadStart: Int = 0,
        loadEnd: Int = -1,
        entryStart: Int = 0,
        entryEnd: Int = -1
    ): String {
        ensureStarted(context)
        return Python.getInstance()
            .getModule("backtest")
            .callAttr(
                "run_with_strategy_file_slice",
                csvPath,
                strategyCode,
                className,
                maxStopPoints,
                pointSize,
                progressListener,
                loadStart,
                loadEnd,
                entryStart,
                entryEnd
            )
            .toString()
    }

    fun runOptimizerWithStrategyFile(
        context: Context,
        csvPath: String,
        strategyCode: String,
        className: String,
        parameterRangesJson: String,
        maxStopPoints: Double = 0.0,
        pointSize: Double = 1.0,
        maxCombinations: Int = 2500
    ): String {
        ensureStarted(context)
        return Python.getInstance()
            .getModule("backtest")
            .callAttr(
                "run_optimizer_with_strategy_file",
                csvPath,
                strategyCode,
                className,
                parameterRangesJson,
                maxStopPoints,
                pointSize,
                maxCombinations
            )
            .toString()
    }

}
