package com.example.backtest

import android.content.Context
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.File
import java.util.UUID

@JsonClass(generateAdapter = true)
data class SavedCsvDataset(
    val id: String,
    val displayName: String,
    val storedFileName: String,
    val estimatedRows: Int,
    val fileSizeBytes: Long,
    val detectedEncoding: String,
    val detectedDelimiter: String,
    val detectedTimeframeLabel: String,
    val detectedTimeframeMinutes: Int?,
    val importedAt: Long,
    val marketName: String = ""
)

/**
 * Persists imported backtest CSV files inside `filesDir/saved_csvs/` with a JSON manifest so users
 * never need to re-import multi-hundred-MB history exports across app restarts.
 */
object SavedCsvStore {

    private const val DIR_NAME = "saved_csvs"
    private const val INDEX_FILE = "index.json"
    private const val PREFS_NAME = "tradepulse_saved_csvs"
    private const val KEY_LAST_SELECTED_ID = "last_selected_csv_id"

    private val moshi: Moshi = Moshi.Builder().build()
    private val listAdapter = moshi.adapter<List<SavedCsvDataset>>(
        Types.newParameterizedType(List::class.java, SavedCsvDataset::class.java)
    ).indent("  ")

    fun defaultRootDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    fun getLastSelectedId(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_SELECTED_ID, null)

    fun setLastSelectedId(context: Context, id: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (id == null) remove(KEY_LAST_SELECTED_ID) else putString(KEY_LAST_SELECTED_ID, id)
            }
            .apply()
    }

    @Synchronized
    fun listDatasets(rootDir: File): List<SavedCsvDataset> {
        rootDir.mkdirs()
        val indexFile = File(rootDir, INDEX_FILE)
        if (!indexFile.exists()) return emptyList()
        val parsed = runCatching { decodeIndexJson(indexFile.readText(Charsets.UTF_8)) }
            .getOrDefault(emptyList())
        // Filter out any orphaned entries whose file was externally removed
        val valid = parsed.filter { dataset ->
            val file = File(rootDir, dataset.storedFileName)
            file.exists() && file.isFile && file.length() > 0L
        }.sortedByDescending { it.importedAt }
        if (valid.size != parsed.size) {
            writeIndexJson(indexFile, valid)
        }
        return valid
    }

    @Synchronized
    fun saveImportedFile(
        rootDir: File,
        tempCsvFile: File,
        displayName: String,
        inspection: CsvStreamInspection,
        detectedTimeframe: DetectedTimeframe,
        marketName: String = inferMarketName(displayName)
    ): SavedCsvDataset {
        rootDir.mkdirs()
        val id = UUID.randomUUID().toString()
        val storedFileName = "csv_${System.currentTimeMillis()}_${id.take(8)}.csv"
        val destFile = File(rootDir, storedFileName)

        if (!tempCsvFile.renameTo(destFile)) {
            tempCsvFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 65_536)
                }
            }
            tempCsvFile.delete()
        }

        val entry = SavedCsvDataset(
            id = id,
            displayName = displayName.ifBlank { "candles.csv" },
            storedFileName = storedFileName,
            estimatedRows = inspection.estimatedRows,
            fileSizeBytes = destFile.length(),
            detectedEncoding = inspection.detectedEncoding,
            detectedDelimiter = inspection.detectedDelimiter,
            detectedTimeframeLabel = detectedTimeframe.label,
            detectedTimeframeMinutes = detectedTimeframe.minutes,
            importedAt = System.currentTimeMillis(),
            marketName = marketName.trim().ifBlank { "CUSTOM" }.uppercase()
        )

        val currentList = listDatasets(rootDir)
        val remaining = currentList.filterNot {
            it.displayName.equals(entry.displayName, ignoreCase = true) &&
                it.estimatedRows == entry.estimatedRows &&
                it.fileSizeBytes == entry.fileSizeBytes
        }
        // If replacing an identical previous snapshot, remove its old file too
        val replaced = currentList.filter { previous ->
            previous.displayName.equals(entry.displayName, ignoreCase = true) &&
                previous.estimatedRows == entry.estimatedRows &&
                previous.fileSizeBytes == entry.fileSizeBytes
        }
        replaced.forEach {
            val f = File(rootDir, it.storedFileName)
            BinaryCandleCache.cacheFileFor(f).delete()
            f.delete()
        }

        val updated = listOf(entry) + remaining
        writeIndexJson(File(rootDir, INDEX_FILE), updated)
        return entry
    }

    fun resolveFile(rootDir: File, dataset: SavedCsvDataset): File =
        File(rootDir, dataset.storedFileName)

    @Synchronized
    fun deleteDataset(rootDir: File, datasetId: String): List<SavedCsvDataset> {
        rootDir.mkdirs()
        val current = listDatasets(rootDir)
        val target = current.firstOrNull { it.id == datasetId }
        target?.let {
            val f = File(rootDir, it.storedFileName)
            BinaryCandleCache.cacheFileFor(f).delete()
            f.delete()
        }
        val remaining = current.filterNot { it.id == datasetId }
        writeIndexJson(File(rootDir, INDEX_FILE), remaining)
        return remaining
    }


    @Synchronized
    fun updateMarketName(rootDir: File, datasetId: String, marketName: String): List<SavedCsvDataset> {
        val current = listDatasets(rootDir)
        val normalized = marketName.trim().uppercase().ifBlank { "CUSTOM" }
        val updated = current.map { dataset ->
            if (dataset.id == datasetId) dataset.copy(marketName = normalized) else dataset
        }
        writeIndexJson(File(rootDir, INDEX_FILE), updated)
        return updated
    }

    fun inferMarketName(fileName: String): String {
        val normalized = fileName.uppercase().replace(Regex("[^A-Z0-9]"), "")
        val symbols = listOf(
            "EURUSD", "GBPUSD", "USDJPY", "USDCHF", "USDCAD", "AUDUSD", "NZDUSD",
            "EURGBP", "EURJPY", "GBPJPY", "XAUUSD", "XAGUSD", "USOIL", "UKOIL",
            "WTI", "BRENT", "BTCUSD", "ETHUSD", "NAS100", "US30", "SPX500",
            "GER40", "UK100", "JP225"
        )
        return symbols.firstOrNull { normalized.contains(it) } ?: "CUSTOM"
    }

    private fun decodeIndexJson(raw: String): List<SavedCsvDataset> {
        if (raw.isBlank()) return emptyList()
        return listAdapter.fromJson(raw).orEmpty().filter {
            it.id.isNotBlank() && it.storedFileName.isNotBlank()
        }
    }

    private fun writeIndexJson(indexFile: File, items: List<SavedCsvDataset>) {
        indexFile.writeText(listAdapter.toJson(items), Charsets.UTF_8)
    }
}
