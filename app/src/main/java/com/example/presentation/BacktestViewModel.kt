package com.example.presentation

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.backtest.AiModelCatalog
import com.example.backtest.AiModelOption
import com.example.backtest.AiProvider
import com.example.backtest.AiStrategyGenerator
import com.example.backtest.BacktestCodec
import com.example.backtest.BacktestError
import com.example.backtest.BacktestParseOutcome
import com.example.backtest.BacktestProgressSnapshot
import com.example.backtest.BacktestResult
import com.example.backtest.BacktestTimeframe
import com.example.backtest.BatchBacktestParseOutcome
import com.example.backtest.BatchStrategyRunResult
import com.example.backtest.BatchStrategySpec
import com.example.backtest.BinaryCandleCache
import com.example.backtest.CandleTimeframeResampler
import com.example.backtest.CsvCandleWindowReader
import com.example.backtest.ResampledCsvFileResult
import com.example.backtest.SavedBacktestRun
import com.example.backtest.SavedCsvDataset
import com.example.backtest.SavedCsvStore
import com.example.backtest.StrategyLiveProgress
import com.example.backtest.Trade
import com.example.backtest.TradeReplayWindow
import com.example.backtest.worker.WorkerPoolBacktestCoordinator
import com.example.data.local.TradingDatabase
import com.example.data.repository.TradingRepository
import com.example.domain.model.PythonStrategy
import com.example.domain.model.UserSettings
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class BacktestUiState(
    val selectedTab: Int = 0, // 0 = Run, 1 = Replay, 2 = AI Builder, 3 = Compare
    val fileName: String? = null,
    val estimatedRows: Int = 0,
    val detectedEncoding: String = "",
    val detectedDelimiter: String = "",
    val detectedTimeframeLabel: String = "",
    val detectedTimeframeMinutes: Int? = null,
    val availableTimeframes: List<BacktestTimeframe> = listOf(BacktestTimeframe.NATIVE),
    val selectedTimeframe: BacktestTimeframe = BacktestTimeframe.NATIVE,
    val lastRunTimeframeLabel: String? = null,
    val lastRunCandleCount: Int? = null,
    val hasCsvBytes: Boolean = false,
    val csvFileSizeBytes: Long = 0L,
    // Saved persistent CSV datasets
    val savedCsvs: List<SavedCsvDataset> = emptyList(),
    val activeSavedCsvId: String? = null,
    // Strategies & Multi-strategy execution
    val availableStrategies: List<PythonStrategy> = emptyList(),
    val selectedStrategy: PythonStrategy? = null,
    val isMultiStrategyMode: Boolean = false,
    val timeSplitLargeCsv: Boolean = true,
    val selectedStrategyIds: Set<Long> = emptySet(),
    val batchResults: List<BatchStrategyRunResult> = emptyList(),
    val activeBatchStrategyId: Long? = null,
    val maxStopPointsInput: String = "",
    val pointSizeInput: String = "1.0",
    val isLoading: Boolean = false,
    val isImportingCsv: Boolean = false,
    val importProgress: Float = 0f,
    val importBytesRead: Long = 0L,
    val importTotalBytes: Long = 0L,
    val liveProgress: BacktestProgressSnapshot? = null,
    val backtestError: BacktestError? = null,
    val result: BacktestResult? = null,
    // Dedicated Replay tab + optional full-screen modal state
    val isReplayOpen: Boolean = false,
    val isReplayLoading: Boolean = false,
    val replayStrategyName: String = "",
    val replaySourceTrades: List<Trade> = emptyList(),
    val replayTradeIndex: Int = -1,
    val replayWindow: TradeReplayWindow? = null,
    val replayError: String? = null,
    val replayOutcomeFilter: Int = 0, // 0 = All, 1 = Losses, 2 = Wins, 3 = BE
    val replayPlaybackSpeed: Int = 1, // 1, 2, 4
    // AI Strategy Builder state (Backtester only)
    val selectedProvider: AiProvider = AiProvider.CLAUDE,
    val selectedModelId: String = AiModelCatalog.defaultModelFor(AiProvider.CLAUDE).id,
    val availableModels: List<AiModelOption> = AiModelCatalog.modelsFor(AiProvider.CLAUDE),
    val useCustomModel: Boolean = false,
    val customModelInput: String = "",
    val apiKeyInput: String = "",
    val isFetchingModels: Boolean = false,
    val strategyPromptInput: String = "",
    val isGeneratingStrategy: Boolean = false,
    val generatedCode: String? = null,
    val generatedStrategyName: String = "",
    val generatedClassName: String = "",
    val aiContractWarnings: List<String> = emptyList(),
    val aiError: String? = null,
    val aiStatusMessage: String? = null,
    // Saved Runs & Comparison
    val savedRuns: List<SavedBacktestRun> = emptyList(),
    val compareRunAId: Long? = null,
    val compareRunBId: Long? = null,
    val runSavedFeedback: String? = null
)

class BacktestViewModel(application: Application) : AndroidViewModel(application) {

    private val database = TradingDatabase.getDatabase(application, viewModelScope)
    private val repository = TradingRepository(database, application.filesDir)

    private var csvCacheFile: File? = null
    private var lastRunBacktestFile: File? = null
    private var csvImportJob: Job? = null
    private var replayLoadJob: Job? = null
    private val resampledCache = mutableMapOf<Pair<String, BacktestTimeframe>, ResampledCsvFileResult>()
    private var latestSettings: UserSettings = UserSettings()
    private var hasHydratedAiPrefs = false
    private val liveModelsByProvider = mutableMapOf<AiProvider, List<AiModelOption>>()

    private val _uiState = MutableStateFlow(BacktestUiState())
    val uiState: StateFlow<BacktestUiState> = _uiState.asStateFlow()

    init {
        restoreSavedCsvDatasets()

        viewModelScope.launch {
            repository.getAllPythonStrategies().collect { strategies ->
                _uiState.update { current ->
                    val currentId = current.selectedStrategy?.id
                    val matched = strategies.firstOrNull { it.id == currentId } ?: strategies.firstOrNull()
                    val validIds = strategies.map { it.id }.toSet()
                    val keptMultiIds = current.selectedStrategyIds.intersect(validIds).ifEmpty {
                        matched?.let { setOf(it.id) } ?: emptySet()
                    }
                    val updatedBatch = current.batchResults.takeIf { it.isNotEmpty() }.orEmpty()
                    current.copy(
                        availableStrategies = strategies,
                        selectedStrategy = matched,
                        selectedStrategyIds = keptMultiIds,
                        batchResults = updatedBatch
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.getAllBacktestRuns().collect { runs ->
                _uiState.update { current ->
                    val validIds = runs.map { it.id }.toSet()
                    val a = current.compareRunAId?.takeIf { it in validIds } ?: runs.getOrNull(0)?.id
                    val b = current.compareRunBId?.takeIf { it in validIds && it != a }
                        ?: runs.firstOrNull { it.id != a }?.id
                    current.copy(
                        savedRuns = runs,
                        compareRunAId = a,
                        compareRunBId = b
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.getSettings().collect { settings ->
                latestSettings = settings
                if (!hasHydratedAiPrefs) {
                    hasHydratedAiPrefs = true
                    val provider = runCatching { AiProvider.valueOf(settings.backtestAiProvider) }
                        .getOrDefault(AiProvider.CLAUDE)
                    val curated = AiModelCatalog.modelsFor(provider)
                    val savedModel = settings.backtestAiModel
                    val inCurated = curated.any { it.id == savedModel }
                    val key = keyForProvider(settings, provider)
                    _uiState.update {
                        it.copy(
                            selectedProvider = provider,
                            availableModels = curated,
                            selectedModelId = if (inCurated) savedModel else curated.first().id,
                            useCustomModel = savedModel.isNotBlank() && !inCurated,
                            customModelInput = if (!inCurated) savedModel else "",
                            apiKeyInput = key
                        )
                    }
                } else {
                    _uiState.update { current ->
                        val restoredKey = keyForProvider(settings, current.selectedProvider)
                        if (current.apiKeyInput.isBlank() && restoredKey.isNotBlank()) {
                            current.copy(apiKeyInput = restoredKey)
                        } else {
                            current
                        }
                    }
                }
            }
        }
    }

    private fun restoreSavedCsvDatasets() {
        val appContext = getApplication<Application>()
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val root = SavedCsvStore.defaultRootDir(appContext)
                val items = SavedCsvStore.listDatasets(root)
                val preferredId = SavedCsvStore.getLastSelectedId(appContext)
                val active = items.firstOrNull { it.id == preferredId } ?: items.firstOrNull()
                Triple(root, items, active)
            }
            val (root, items, active) = loaded
            if (active != null) {
                val activeFile = SavedCsvStore.resolveFile(root, active)
                csvCacheFile = activeFile
                val targets = CandleTimeframeResampler.availableTargets(active.detectedTimeframeMinutes)
                _uiState.update {
                    it.copy(
                        savedCsvs = items,
                        activeSavedCsvId = active.id,
                        fileName = active.displayName,
                        estimatedRows = active.estimatedRows,
                        detectedEncoding = active.detectedEncoding,
                        detectedDelimiter = active.detectedDelimiter,
                        detectedTimeframeLabel = active.detectedTimeframeLabel,
                        detectedTimeframeMinutes = active.detectedTimeframeMinutes,
                        availableTimeframes = targets,
                        selectedTimeframe = BacktestTimeframe.NATIVE,
                        hasCsvBytes = activeFile.exists() && active.estimatedRows > 0,
                        csvFileSizeBytes = activeFile.length().takeIf { it > 0L } ?: active.fileSizeBytes
                    )
                }
            } else {
                _uiState.update { it.copy(savedCsvs = emptyList(), activeSavedCsvId = null) }
            }
        }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
        if (tabIndex == 1) {
            ensureReplayLoadedForActiveResult()
        }
    }

    private fun ensureReplayLoadedForActiveResult() {
        val snapshot = _uiState.value
        val activeRes = snapshot.result ?: return
        if (activeRes.trades.isEmpty()) return
        val needsSync = snapshot.replaySourceTrades != activeRes.trades ||
            snapshot.replayTradeIndex !in activeRes.trades.indices
        if (needsSync) {
            val stratName = snapshot.activeBatchStrategyId?.let { id ->
                snapshot.batchResults.firstOrNull { it.strategyId == id }?.strategyName
            } ?: snapshot.selectedStrategy?.name ?: "Strategy"
            _uiState.update {
                it.copy(
                    replayStrategyName = stratName,
                    replaySourceTrades = activeRes.trades,
                    replayTradeIndex = 0,
                    replayError = null
                )
            }
            loadReplayWindow(activeRes.trades)
        } else if (snapshot.replayWindow == null && !snapshot.isReplayLoading) {
            loadReplayWindow(activeRes.trades)
        }
    }

    // --- Saved CSV Controls ---

    fun selectSavedCsv(datasetId: String) {
        val snapshot = _uiState.value
        if (snapshot.activeSavedCsvId == datasetId || snapshot.isLoading || snapshot.isImportingCsv) return
        val dataset = snapshot.savedCsvs.firstOrNull { it.id == datasetId } ?: return
        val appContext = getApplication<Application>()
        val root = SavedCsvStore.defaultRootDir(appContext)
        val file = SavedCsvStore.resolveFile(root, dataset)
        if (!file.exists() || file.length() == 0L) {
            deleteSavedCsv(datasetId)
            return
        }
        SavedCsvStore.setLastSelectedId(appContext, dataset.id)
        csvCacheFile = file
        lastRunBacktestFile = null
        val targets = CandleTimeframeResampler.availableTargets(dataset.detectedTimeframeMinutes)
        _uiState.update {
            it.copy(
                activeSavedCsvId = dataset.id,
                fileName = dataset.displayName,
                estimatedRows = dataset.estimatedRows,
                detectedEncoding = dataset.detectedEncoding,
                detectedDelimiter = dataset.detectedDelimiter,
                detectedTimeframeLabel = dataset.detectedTimeframeLabel,
                detectedTimeframeMinutes = dataset.detectedTimeframeMinutes,
                availableTimeframes = targets,
                selectedTimeframe = BacktestTimeframe.NATIVE,
                lastRunTimeframeLabel = null,
                lastRunCandleCount = null,
                hasCsvBytes = dataset.estimatedRows > 0,
                csvFileSizeBytes = file.length().takeIf { it > 0L } ?: dataset.fileSizeBytes,
                backtestError = null,
                result = null,
                batchResults = emptyList(),
                activeBatchStrategyId = null
            )
        }
    }

    fun deleteSavedCsv(datasetId: String) {
        val appContext = getApplication<Application>()
        viewModelScope.launch {
            val (root, remaining) = withContext(Dispatchers.IO) {
                val r = SavedCsvStore.defaultRootDir(appContext)
                val rem = SavedCsvStore.deleteDataset(r, datasetId)
                // Clean any resampled cache associated with deleted dataset
                resampledCache.keys.filter { it.first == datasetId }.toList().forEach { key ->
                    resampledCache.remove(key)?.file?.delete()
                }
                r to rem
            }
            val wasActive = _uiState.value.activeSavedCsvId == datasetId
            if (wasActive) {
                val next = remaining.firstOrNull()
                if (next != null) {
                    SavedCsvStore.setLastSelectedId(appContext, next.id)
                    val activeNextFile = SavedCsvStore.resolveFile(root, next)
                    csvCacheFile = activeNextFile
                    val targets = CandleTimeframeResampler.availableTargets(next.detectedTimeframeMinutes)
                    _uiState.update {
                        it.copy(
                            savedCsvs = remaining,
                            activeSavedCsvId = next.id,
                            fileName = next.displayName,
                            estimatedRows = next.estimatedRows,
                            detectedEncoding = next.detectedEncoding,
                            detectedDelimiter = next.detectedDelimiter,
                            detectedTimeframeLabel = next.detectedTimeframeLabel,
                            detectedTimeframeMinutes = next.detectedTimeframeMinutes,
                            availableTimeframes = targets,
                            selectedTimeframe = BacktestTimeframe.NATIVE,
                            hasCsvBytes = next.estimatedRows > 0,
                            csvFileSizeBytes = activeNextFile.length().takeIf { l -> l > 0L } ?: next.fileSizeBytes,
                            result = null,
                            batchResults = emptyList(),
                            activeBatchStrategyId = null
                        )
                    }
                } else {
                    SavedCsvStore.setLastSelectedId(appContext, null)
                    csvCacheFile = null
                    lastRunBacktestFile = null
                    _uiState.update {
                        it.copy(
                            savedCsvs = emptyList(),
                            activeSavedCsvId = null,
                            fileName = null,
                            estimatedRows = 0,
                            detectedEncoding = "",
                            detectedDelimiter = "",
                            detectedTimeframeLabel = "",
                            detectedTimeframeMinutes = null,
                            availableTimeframes = listOf(BacktestTimeframe.NATIVE),
                            selectedTimeframe = BacktestTimeframe.NATIVE,
                            hasCsvBytes = false,
                            csvFileSizeBytes = 0L,
                            result = null,
                            batchResults = emptyList(),
                            activeBatchStrategyId = null
                        )
                    }
                }
            } else {
                _uiState.update { it.copy(savedCsvs = remaining) }
            }
        }
    }

    // --- Backtest Runner Controls ---

    fun selectStrategy(strategy: PythonStrategy) {
        _uiState.update {
            it.copy(
                selectedStrategy = strategy,
                selectedStrategyIds = if (it.selectedStrategyIds.isEmpty()) setOf(strategy.id) else it.selectedStrategyIds,
                backtestError = null
            )
        }
    }

    fun toggleMultiStrategyMode(enabled: Boolean) {
        _uiState.update { current ->
            val nextIds = if (enabled && current.selectedStrategyIds.isEmpty()) {
                current.selectedStrategy?.let { setOf(it.id) }
                    ?: current.availableStrategies.take(1).map { it.id }.toSet()
            } else {
                current.selectedStrategyIds
            }
            current.copy(
                isMultiStrategyMode = enabled,
                selectedStrategyIds = nextIds,
                backtestError = null
            )
        }
    }

    fun setTimeSplitLargeCsv(enabled: Boolean) {
        _uiState.update { it.copy(timeSplitLargeCsv = enabled, backtestError = null) }
    }

    fun toggleStrategySelection(strategyId: Long) {
        _uiState.update { current ->
            val updated = if (strategyId in current.selectedStrategyIds) {
                current.selectedStrategyIds - strategyId
            } else {
                current.selectedStrategyIds + strategyId
            }
            current.copy(selectedStrategyIds = updated, backtestError = null)
        }
    }

    fun selectAllStrategies() {
        _uiState.update { current ->
            current.copy(
                selectedStrategyIds = current.availableStrategies.map { it.id }.toSet(),
                backtestError = null
            )
        }
    }

    fun clearStrategySelection() {
        _uiState.update { current ->
            current.copy(selectedStrategyIds = emptySet(), backtestError = null)
        }
    }

    fun selectBatchResult(strategyId: Long) {
        val picked = _uiState.value.batchResults.firstOrNull { it.strategyId == strategyId }
        val newRes = picked?.result
        _uiState.update { current ->
            current.copy(
                activeBatchStrategyId = strategyId,
                result = newRes ?: current.result,
                replayStrategyName = picked?.strategyName ?: current.replayStrategyName,
                replaySourceTrades = newRes?.trades ?: current.replaySourceTrades,
                replayTradeIndex = if (newRes != null && newRes.trades.isNotEmpty()) 0 else -1,
                replayWindow = if (newRes != null) null else current.replayWindow,
                replayError = null
            )
        }
        if (newRes != null && newRes.trades.isNotEmpty()) {
            loadReplayWindow(newRes.trades)
        }
    }

    fun updateMaxStopPoints(input: String) {
        _uiState.update { it.copy(maxStopPointsInput = input, backtestError = null) }
    }

    fun updatePointSize(input: String) {
        _uiState.update { it.copy(pointSizeInput = input, backtestError = null) }
    }

    fun selectTimeframe(timeframe: BacktestTimeframe) {
        _uiState.update { it.copy(selectedTimeframe = timeframe, backtestError = null) }
    }

    fun loadCsvUri(uri: Uri) {
        csvImportJob?.cancel()
        val context = getApplication<Application>()
        val displayName = resolveDisplayName(context, uri)
        val totalBytes = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0).takeIf { it > 0L } else null
            } ?: 0L
        val tempFile = File(context.cacheDir, "tradepulse_csv_${totalBytes}_${displayName.hashCode()}_${System.currentTimeMillis()}.csv")

        lateinit var job: Job
        job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            _uiState.update {
                it.copy(
                    isImportingCsv = true,
                    importProgress = 0f,
                    importBytesRead = 0L,
                    importTotalBytes = totalBytes,
                    backtestError = null,
                    result = null,
                    batchResults = emptyList(),
                    activeBatchStrategyId = null
                )
            }
            try {
                val outcome = withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Unable to open selected CSV document.")
                    val inspection = stream.use {
                        BacktestCodec.streamCsvToCacheFile(it, tempFile) { bytesRead ->
                            job.ensureActive()
                            _uiState.update { current ->
                                current.copy(
                                    importBytesRead = bytesRead,
                                    importProgress = if (totalBytes > 0L) {
                                        (bytesRead.toDouble() / totalBytes).toFloat().coerceIn(0f, 0.99f)
                                    } else 0f
                                )
                            }
                        }
                    }
                    val sampleText = inspection.sampleLines.joinToString("\n")
                    val detectedTf = CandleTimeframeResampler.detectTimeframe(sampleText)
                    val targets = CandleTimeframeResampler.availableTargets(detectedTf.minutes)
                    val root = SavedCsvStore.defaultRootDir(context)
                    val savedDataset = if (inspection.estimatedRows > 0) {
                        SavedCsvStore.saveImportedFile(
                            rootDir = root,
                            tempCsvFile = tempFile,
                            displayName = displayName,
                            inspection = inspection,
                            detectedTimeframe = detectedTf
                        ).also {
                            SavedCsvStore.setLastSelectedId(context, it.id)
                        }
                    } else {
                        tempFile.delete()
                        null
                    }
                    val allDatasets = SavedCsvStore.listDatasets(root)
                    val resolvedFile = savedDataset?.let { SavedCsvStore.resolveFile(root, it) }
                    Triple(inspection, detectedTf, targets) to Triple(savedDataset, allDatasets, resolvedFile)
                }

                val (csvMeta, storeMeta) = outcome
                val (inspection, detectedTf, targets) = csvMeta
                val (savedDataset, allDatasets, resolvedFile) = storeMeta

                if (resolvedFile != null) {
                    csvCacheFile = resolvedFile
                    viewModelScope.launch(Dispatchers.IO) {
                        BinaryCandleCache.ensureCache(resolvedFile)
                    }
                }
                lastRunBacktestFile = null

                _uiState.update {
                    it.copy(
                        savedCsvs = allDatasets,
                        activeSavedCsvId = savedDataset?.id ?: it.activeSavedCsvId,
                        fileName = savedDataset?.displayName ?: displayName,
                        estimatedRows = inspection.estimatedRows,
                        detectedEncoding = inspection.detectedEncoding,
                        detectedDelimiter = inspection.detectedDelimiter,
                        detectedTimeframeLabel = detectedTf.label,
                        detectedTimeframeMinutes = detectedTf.minutes,
                        availableTimeframes = targets,
                        selectedTimeframe = BacktestTimeframe.NATIVE,
                        lastRunTimeframeLabel = null,
                        lastRunCandleCount = null,
                        hasCsvBytes = inspection.estimatedRows > 0,
                        csvFileSizeBytes = resolvedFile?.length()?.takeIf { l -> l > 0L } ?: inspection.fileSizeBytes,
                        isImportingCsv = false,
                        importProgress = 1f,
                        importBytesRead = totalBytes.takeIf { it > 0L } ?: inspection.fileSizeBytes,
                        importTotalBytes = totalBytes.takeIf { it > 0L } ?: inspection.fileSizeBytes,
                        backtestError = if (inspection.estimatedRows == 0) {
                            BacktestError("Selected CSV file is empty or contains no candle rows.")
                        } else null,
                        result = null,
                        batchResults = emptyList(),
                        activeBatchStrategyId = null
                    )
                }
            } catch (t: Throwable) {
                tempFile.delete()
                if (t is kotlinx.coroutines.CancellationException) {
                    _uiState.update { it.copy(isImportingCsv = false, importProgress = 0f) }
                } else {
                    _uiState.update {
                        it.copy(
                            hasCsvBytes = false,
                            isImportingCsv = false,
                            importProgress = 0f,
                            detectedTimeframeLabel = "",
                            detectedTimeframeMinutes = null,
                            availableTimeframes = listOf(BacktestTimeframe.NATIVE),
                            selectedTimeframe = BacktestTimeframe.NATIVE,
                            backtestError = BacktestError(
                                error = t.message ?: "Failed to import CSV file.",
                                details = t.stackTraceToString().take(1200)
                            )
                        )
                    }
                }
            } finally {
                if (csvImportJob === job) csvImportJob = null
            }
        }
        csvImportJob = job
        job.start()
    }

    fun cancelCsvImport() {
        csvImportJob?.cancel()
    }

    fun run() {
        val snapshot = _uiState.value
        val sourceFile = csvCacheFile
        if (sourceFile == null || !sourceFile.exists() || sourceFile.length() == 0L) {
            _uiState.update {
                it.copy(backtestError = BacktestError("Please select or import a CSV / MT5 export file first."))
            }
            return
        }

        val selectedList = if (snapshot.isMultiStrategyMode) {
            snapshot.availableStrategies.filter { it.id in snapshot.selectedStrategyIds }
        } else {
            listOfNotNull(snapshot.selectedStrategy)
        }

        if (selectedList.isEmpty()) {
            _uiState.update {
                it.copy(
                    backtestError = BacktestError(
                        if (snapshot.isMultiStrategyMode) {
                            "Select at least one Python strategy to run simultaneously."
                        } else {
                            "Please upload or generate a Python strategy first."
                        }
                    )
                )
            }
            return
        }

        val maxStopText = snapshot.maxStopPointsInput.trim()
        val maxStopPoints: Double = if (maxStopText.isEmpty()) {
            0.0
        } else {
            val parsed = maxStopText.toDoubleOrNull()
            if (parsed == null || parsed < 0.0) {
                _uiState.update {
                    it.copy(backtestError = BacktestError("maxStopPoints must be 0, positive, or blank for no cap."))
                }
                return
            }
            parsed
        }

        val pointSizeText = snapshot.pointSizeInput.trim()
        val pointSize: Double = if (pointSizeText.isEmpty()) {
            1.0
        } else {
            val parsed = pointSizeText.toDoubleOrNull()
            if (parsed == null || parsed <= 0.0) {
                _uiState.update {
                    it.copy(backtestError = BacktestError("pointSize must be a positive number (defaults to 1.0)."))
                }
                return
            }
            parsed
        }

        val isBatchRun = snapshot.isMultiStrategyMode && selectedList.size > 1
        val workerCount = WorkerPoolBacktestCoordinator.optimalWorkerCount(selectedList.size)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    liveProgress = BacktestProgressSnapshot(
                        totalCandles = snapshot.estimatedRows,
                        strategyIndex = if (isBatchRun) selectedList.size else 0,
                        totalStrategies = if (isBatchRun) selectedList.size else 0,
                        currentStrategyName = if (isBatchRun) "${workerCount} worker processes" else selectedList.first().name,
                        currentTime = "Preparing binary candle cache...",
                        strategyStatuses = if (isBatchRun) {
                            selectedList.map { s ->
                                StrategyLiveProgress(
                                    strategyId = s.id,
                                    name = s.name,
                                    totalCandles = snapshot.estimatedRows,
                                    status = "queued"
                                )
                            }
                        } else {
                            emptyList()
                        }
                    ),
                    backtestError = null
                )
            }
            try {
                val appContext = getApplication<Application>()
                val activeDatasetKey = snapshot.activeSavedCsvId ?: sourceFile.name
                val preparedFileAndTf = withContext(Dispatchers.IO) {
                    val targetTf = snapshot.selectedTimeframe
                    val backtestFile: File
                    val candleCount: Int
                    if (targetTf == BacktestTimeframe.NATIVE) {
                        backtestFile = sourceFile
                        candleCount = snapshot.estimatedRows
                    } else {
                        val cacheKey = activeDatasetKey to targetTf
                        val resampledDir = File(appContext.cacheDir, "resampled_csvs").apply { mkdirs() }
                        val outputFile = File(
                            resampledDir,
                            "resampled_${activeDatasetKey}_${targetTf.shortLabel}.csv"
                        )
                        val metadataFile = File(outputFile.parentFile, "${outputFile.name}.meta")
                        val sourceSignature = listOf(
                            sourceFile.canonicalPath,
                            sourceFile.length().toString(),
                            sourceFile.lastModified().toString(),
                            targetTf.minutes.toString(),
                            "resampler-v2"
                        ).joinToString("\n")
                        val diskCached = if (outputFile.exists() && metadataFile.exists()) {
                            runCatching {
                                val metadata = metadataFile.readLines()
                                if (metadata.size == 2 && metadata[0] == sourceSignature) {
                                    metadata[1].toIntOrNull()?.takeIf { it > 0 }?.let { count ->
                                        ResampledCsvFileResult(
                                            file = outputFile,
                                            sourceCandleCount = snapshot.estimatedRows,
                                            outputCandleCount = count,
                                            targetTimeframe = targetTf
                                        )
                                    }
                                } else null
                            }.getOrNull()
                        } else null
                        val inMemory = resampledCache[cacheKey]?.takeIf {
                            diskCached != null && it.file.absolutePath == diskCached.file.absolutePath &&
                                it.outputCandleCount == diskCached.outputCandleCount
                        }
                        val resampled = inMemory ?: diskCached ?: CandleTimeframeResampler.resampleCsvFile(
                            sourceFile = sourceFile,
                            target = targetTf,
                            outputFile = outputFile
                        ).also { generated ->
                            val metadataTemp = File(metadataFile.parentFile, "${metadataFile.name}.tmp")
                            metadataTemp.writeText(
                                sourceSignature + "\n" + generated.outputCandleCount,
                                Charsets.UTF_8
                            )
                            if (metadataFile.exists()) metadataFile.delete()
                            if (!metadataTemp.renameTo(metadataFile)) {
                                metadataTemp.copyTo(metadataFile, overwrite = true)
                                metadataTemp.delete()
                            }
                        }.also { resampledCache[cacheKey] = it }
                        if (inMemory == null && diskCached != null) resampledCache[cacheKey] = diskCached
                        backtestFile = resampled.file
                        candleCount = resampled.outputCandleCount
                    }
                    // Build/verify fast columnar .btbin cache once so all worker processes map arrays in ms
                    BinaryCandleCache.ensureCache(backtestFile) { indexedRows ->
                        _uiState.update { current ->
                            if (!current.isLoading) current else current.copy(
                                liveProgress = current.liveProgress?.copy(
                                    candlesProcessed = indexedRows.coerceAtMost(candleCount.coerceAtLeast(1)),
                                    totalCandles = candleCount.coerceAtLeast(indexedRows),
                                    currentTime = "Indexing binary cache ($indexedRows rows)..."
                                )
                            )
                        }
                    }
                    Triple(backtestFile, candleCount, targetTf)
                }

                val (backtestFile, candleCount, usedTimeframe) = preparedFileAndTf
                val specs = selectedList.map {
                    BatchStrategySpec(id = it.id, name = it.name, className = it.className, code = it.code)
                }

                val rawOutcome: Any = if (isBatchRun) {
                    WorkerPoolBacktestCoordinator.runBatchParallel(
                        context = appContext,
                        csvFile = backtestFile,
                        specs = specs,
                        estimatedRows = candleCount,
                        maxStopPoints = maxStopPoints,
                        pointSize = pointSize,
                        onProgress = { progressSnapshot ->
                            _uiState.update { current ->
                                if (current.isLoading) current.copy(liveProgress = progressSnapshot) else current
                            }
                        }
                    )
                } else {
                    WorkerPoolBacktestCoordinator.runSingle(
                        context = appContext,
                        csvFile = backtestFile,
                        spec = specs.first(),
                        estimatedRows = candleCount,
                        maxStopPoints = maxStopPoints,
                        pointSize = pointSize,
                        enableTimeSplit = snapshot.timeSplitLargeCsv,
                        onProgress = { progressSnapshot ->
                            _uiState.update { current ->
                                if (current.isLoading) current.copy(liveProgress = progressSnapshot) else current
                            }
                        }
                    )
                }

                lastRunBacktestFile = backtestFile

                val runTfLabel = if (usedTimeframe == BacktestTimeframe.NATIVE) {
                    snapshot.detectedTimeframeLabel.ifBlank { "Native" }
                } else {
                    val srcShort = snapshot.detectedTimeframeLabel.substringBefore(" ")
                    if (srcShort.isNotBlank() && srcShort != "Unknown") {
                        "${usedTimeframe.label} (from $srcShort)"
                    } else {
                        usedTimeframe.label
                    }
                }

                when (rawOutcome) {
                    is BacktestParseOutcome.Success -> {
                        val ranStrategy = selectedList.first()
                        val initialTrades = rawOutcome.result.trades
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                liveProgress = null,
                                result = rawOutcome.result,
                                batchResults = emptyList(),
                                activeBatchStrategyId = null,
                                backtestError = null,
                                runSavedFeedback = null,
                                lastRunTimeframeLabel = runTfLabel,
                                lastRunCandleCount = candleCount,
                                replayStrategyName = ranStrategy.name,
                                replaySourceTrades = initialTrades,
                                replayTradeIndex = if (initialTrades.isNotEmpty()) 0 else -1,
                                replayWindow = null,
                                replayError = null
                            )
                        }
                        if (initialTrades.isNotEmpty()) {
                            loadReplayWindow(initialTrades)
                        }
                        saveSingleResult(ranStrategy.name, rawOutcome.result, snapshot)
                    }
                    is BacktestParseOutcome.Failure -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                liveProgress = null,
                                result = null,
                                batchResults = emptyList(),
                                activeBatchStrategyId = null,
                                backtestError = rawOutcome.error
                            )
                        }
                    }
                    is BatchBacktestParseOutcome.Success -> {
                        val firstOk = rawOutcome.runs.firstOrNull { it.result != null }
                        val allErrored = firstOk == null
                        val firstErr = if (allErrored) {
                            rawOutcome.runs.firstOrNull()?.error
                                ?: BacktestError("All selected strategies failed to produce results.")
                        } else null
                        val initialTrades = firstOk?.result?.trades.orEmpty()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                liveProgress = null,
                                result = firstOk?.result,
                                batchResults = rawOutcome.runs,
                                activeBatchStrategyId = firstOk?.strategyId,
                                backtestError = firstErr,
                                runSavedFeedback = null,
                                lastRunTimeframeLabel = runTfLabel,
                                lastRunCandleCount = candleCount,
                                replayStrategyName = firstOk?.strategyName ?: "",
                                replaySourceTrades = initialTrades,
                                replayTradeIndex = if (initialTrades.isNotEmpty()) 0 else -1,
                                replayWindow = null,
                                replayError = null
                            )
                        }
                        if (initialTrades.isNotEmpty()) {
                            loadReplayWindow(initialTrades)
                        }
                        saveBatchResults(rawOutcome.runs, snapshot)
                    }
                    is BatchBacktestParseOutcome.Failure -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                liveProgress = null,
                                result = null,
                                batchResults = emptyList(),
                                activeBatchStrategyId = null,
                                backtestError = rawOutcome.error
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        liveProgress = null,
                        result = null,
                        backtestError = BacktestError(
                            error = t.message ?: "Python backtest execution failed.",
                            details = t.stackTraceToString().take(1500)
                        )
                    )
                }
            }
        }
    }

    private fun buildCsvLabel(snapshot: BacktestUiState): String {
        val baseCsvName = snapshot.fileName ?: "candles.csv"
        return if (snapshot.selectedTimeframe == BacktestTimeframe.NATIVE) {
            val detected = snapshot.detectedTimeframeLabel.substringBefore(" ")
            if (detected.isNotBlank() && detected != "Unknown") "$baseCsvName ($detected)" else baseCsvName
        } else {
            "$baseCsvName (${snapshot.selectedTimeframe.shortLabel})"
        }
    }

    private fun saveSingleResult(strategyName: String, result: BacktestResult, snapshot: BacktestUiState) {
        val run = SavedBacktestRun(
            strategyName = strategyName,
            csvFileName = buildCsvLabel(snapshot),
            maxStopPoints = snapshot.maxStopPointsInput.toDoubleOrNull() ?: 0.0,
            pointSize = snapshot.pointSizeInput.toDoubleOrNull() ?: 1.0,
            totalTrades = result.summary.totalTrades,
            wins = result.summary.wins,
            losses = result.summary.losses,
            winRatePct = result.summary.winRatePct,
            netPoints = result.summary.netPoints,
            netR = result.summary.netR,
            avgR = result.summary.avgR,
            profitFactor = result.summary.profitFactor,
            maxDrawdownPoints = result.risk.maxDrawdownPoints,
            maxDrawdownR = result.risk.maxDrawdownR,
            equityCurve = result.equityCurve,
            createdAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            runCatching { repository.saveBacktestRun(run) }
                .onSuccess {
                    _uiState.update { it.copy(runSavedFeedback = "Automatically saved ${run.strategyName} to Compare tab.") }
                }
                .onFailure {
                    _uiState.update { it.copy(runSavedFeedback = "Could not save this run. Please check available device storage.") }
                }
        }
    }

    private fun saveBatchResults(runs: List<BatchStrategyRunResult>, snapshot: BacktestUiState) {
        val csvLabel = buildCsvLabel(snapshot)
        val maxStop = snapshot.maxStopPointsInput.toDoubleOrNull() ?: 0.0
        val pointSize = snapshot.pointSizeInput.toDoubleOrNull() ?: 1.0
        val successful = runs.mapNotNull { item ->
            item.result?.let { item.strategyName to it }
        }
        if (successful.isEmpty()) return
        viewModelScope.launch {
            var savedCount = 0
            successful.forEach { (name, res) ->
                val entity = SavedBacktestRun(
                    strategyName = name,
                    csvFileName = csvLabel,
                    maxStopPoints = maxStop,
                    pointSize = pointSize,
                    totalTrades = res.summary.totalTrades,
                    wins = res.summary.wins,
                    losses = res.summary.losses,
                    winRatePct = res.summary.winRatePct,
                    netPoints = res.summary.netPoints,
                    netR = res.summary.netR,
                    avgR = res.summary.avgR,
                    profitFactor = res.summary.profitFactor,
                    maxDrawdownPoints = res.risk.maxDrawdownPoints,
                    maxDrawdownR = res.risk.maxDrawdownR,
                    equityCurve = res.equityCurve,
                    createdAt = System.currentTimeMillis()
                )
                runCatching { repository.saveBacktestRun(entity) }
                    .onSuccess { savedCount++ }
            }
            if (savedCount > 0) {
                _uiState.update {
                    it.copy(runSavedFeedback = "Saved $savedCount strategy runs from batch to Compare tab.")
                }
            }
        }
    }

    fun saveCurrentResult() {
        val snapshot = _uiState.value
        val result = snapshot.result ?: return
        val activeName = snapshot.activeBatchStrategyId?.let { id ->
            snapshot.batchResults.firstOrNull { it.strategyId == id }?.strategyName
        } ?: snapshot.selectedStrategy?.name ?: "Strategy"
        saveSingleResult(activeName, result, snapshot)
    }

    // --- Candlestick Trade Replay Controls ---

    fun openTradeReplay(tradeIndexInResult: Int) {
        val snapshot = _uiState.value
        val result = snapshot.result ?: return
        if (result.trades.isEmpty()) return
        val clamped = tradeIndexInResult.coerceIn(result.trades.indices)
        val stratName = snapshot.activeBatchStrategyId?.let { id ->
            snapshot.batchResults.firstOrNull { it.strategyId == id }?.strategyName
        } ?: snapshot.selectedStrategy?.name ?: "Strategy"

        _uiState.update {
            it.copy(
                selectedTab = 1, // Switch directly to dedicated Replay tab
                isReplayOpen = false,
                replayStrategyName = stratName,
                replaySourceTrades = result.trades,
                replayTradeIndex = clamped,
                replayWindow = null,
                replayError = null
            )
        }
        loadReplayWindow(result.trades)
    }

    fun setReplayFullscreen(open: Boolean) {
        _uiState.update { it.copy(isReplayOpen = open) }
    }

    fun selectReplayFilter(filterIndex: Int) {
        _uiState.update { it.copy(replayOutcomeFilter = filterIndex) }
    }

    fun cycleReplaySpeed() {
        _uiState.update { current ->
            val next = when (current.replayPlaybackSpeed) {
                1 -> 2
                2 -> 4
                else -> 1
            }
            current.copy(replayPlaybackSpeed = next)
        }
    }

    fun selectReplayTradeAt(index: Int) {
        val trades = _uiState.value.replaySourceTrades
        if (index !in trades.indices) return
        _uiState.update {
            it.copy(
                replayTradeIndex = index,
                replayError = null
            )
        }
    }

    fun closeTradeReplay() {
        _uiState.update {
            it.copy(
                isReplayOpen = false
            )
        }
    }

    private fun loadReplayWindow(trades: List<Trade>) {
        replayLoadJob?.cancel()
        val csvFile = lastRunBacktestFile ?: csvCacheFile
        if (csvFile == null || !csvFile.exists()) {
            _uiState.update {
                it.copy(
                    isReplayLoading = false,
                    replayWindow = null,
                    replayError = "Active CSV file is not available on disk."
                )
            }
            return
        }
        replayLoadJob = viewModelScope.launch {
            _uiState.update { it.copy(isReplayLoading = true, replayError = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    CsvCandleWindowReader.readTradesWindow(csvFile, trades)
                }
            }
            result.onSuccess { window ->
                _uiState.update {
                    it.copy(
                        isReplayLoading = false,
                        replayWindow = window,
                        replayError = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isReplayLoading = false,
                        replayWindow = null,
                        replayError = err.message ?: "Failed to read candlestick window from CSV."
                    )
                }
            }
        }
    }

    fun selectCompareSlot(runId: Long) {
        _uiState.update { current ->
            when {
                current.compareRunAId == runId -> current
                current.compareRunBId == runId -> current
                else -> current.copy(
                    compareRunBId = current.compareRunAId,
                    compareRunAId = runId
                )
            }
        }
    }

    fun deleteSavedRun(runId: Long) {
        viewModelScope.launch {
            repository.deleteBacktestRun(runId)
        }
    }

    // --- AI Strategy Builder (Backtester Only) ---

    fun selectProvider(provider: AiProvider) {
        val currentState = _uiState.value
        if (currentState.selectedProvider == provider) return

        persistProviderCredentials(
            provider = currentState.selectedProvider,
            apiKey = currentState.apiKeyInput,
            nextProvider = provider,
            nextModel = mergedModelsFor(provider).first().id
        )

        val models = mergedModelsFor(provider)
        val savedKey = keyForProvider(latestSettings, provider)
        _uiState.update {
            it.copy(
                selectedProvider = provider,
                availableModels = models,
                selectedModelId = models.first().id,
                useCustomModel = false,
                customModelInput = "",
                apiKeyInput = savedKey,
                aiError = null,
                aiStatusMessage = null
            )
        }
    }

    fun selectModel(modelId: String) {
        val provider = _uiState.value.selectedProvider
        _uiState.update {
            it.copy(
                selectedModelId = modelId,
                useCustomModel = false,
                aiError = null,
                aiStatusMessage = null
            )
        }
        persistAiPreferences(provider, modelId)
    }

    fun enableCustomModel(customId: String) {
        val provider = _uiState.value.selectedProvider
        _uiState.update {
            it.copy(
                useCustomModel = true,
                customModelInput = customId,
                aiError = null
            )
        }
        if (customId.isNotBlank()) {
            persistAiPreferences(provider, customId.trim())
        }
    }

    fun updateCustomModelInput(input: String) {
        _uiState.update { it.copy(customModelInput = input, useCustomModel = true, aiError = null) }
    }

    fun updateApiKeyInput(input: String) {
        val provider = _uiState.value.selectedProvider
        _uiState.update { it.copy(apiKeyInput = input, aiError = null, aiStatusMessage = null) }
        latestSettings = withProviderKey(latestSettings, provider, input.trim())
    }

    fun saveCurrentApiKey() {
        val state = _uiState.value
        val trimmed = state.apiKeyInput.trim()
        val effectiveModel = if (state.useCustomModel) state.customModelInput.trim() else state.selectedModelId
        val updated = withProviderKey(latestSettings, state.selectedProvider, trimmed).copy(
            backtestAiProvider = state.selectedProvider.name,
            backtestAiModel = effectiveModel.ifBlank { state.selectedModelId }
        )
        latestSettings = updated
        viewModelScope.launch {
            repository.saveSettings(updated)
            _uiState.update {
                it.copy(aiStatusMessage = "${state.selectedProvider.displayName} API key saved locally.")
            }
        }
    }

    fun updateStrategyPrompt(input: String) {
        _uiState.update { it.copy(strategyPromptInput = input, aiError = null) }
    }

    fun updateGeneratedStrategyName(name: String) {
        _uiState.update { it.copy(generatedStrategyName = name) }
    }

    fun updateGeneratedClassName(className: String) {
        _uiState.update { it.copy(generatedClassName = className) }
    }

    fun updateGeneratedCode(code: String) {
        val warnings = AiStrategyGenerator.validateStrategyContract(code)
        _uiState.update { it.copy(generatedCode = code, aiContractWarnings = warnings) }
    }

    fun fetchLiveModels() {
        val snapshot = _uiState.value
        val provider = snapshot.selectedProvider
        val key = snapshot.apiKeyInput.trim()
        if (key.isEmpty()) {
            _uiState.update { it.copy(aiError = "Enter your ${provider.keyLabel} to fetch live models.") }
            return
        }

        val updatedSettings = withProviderKey(latestSettings, provider, key)
        latestSettings = updatedSettings

        viewModelScope.launch {
            repository.saveSettings(updatedSettings)
            _uiState.update { it.copy(isFetchingModels = true, aiError = null, aiStatusMessage = null) }
            val result = withContext(Dispatchers.IO) {
                AiStrategyGenerator.fetchAvailableModels(provider, key)
            }
            result.onSuccess { liveModels ->
                liveModelsByProvider[provider] = liveModels
                val merged = mergedModelsFor(provider)
                _uiState.update {
                    it.copy(
                        isFetchingModels = false,
                        availableModels = merged,
                        aiStatusMessage = "Loaded ${liveModels.size} live ${provider.displayName} models from API."
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isFetchingModels = false,
                        aiError = err.message ?: "Failed to fetch live models."
                    )
                }
            }
        }
    }

    fun generateStrategyWithAi() {
        val snapshot = _uiState.value
        val provider = snapshot.selectedProvider
        val key = snapshot.apiKeyInput.trim()
        val effectiveModel = if (snapshot.useCustomModel) {
            snapshot.customModelInput.trim()
        } else {
            snapshot.selectedModelId
        }
        val prompt = snapshot.strategyPromptInput.trim()

        if (key.isEmpty()) {
            _uiState.update { it.copy(aiError = "Enter your ${provider.keyLabel} first.") }
            return
        }
        if (effectiveModel.isEmpty()) {
            _uiState.update { it.copy(aiError = "Select or enter a model ID first.") }
            return
        }
        if (prompt.isEmpty()) {
            _uiState.update { it.copy(aiError = "Describe your strategy rules first.") }
            return
        }

        val updatedSettings = withProviderKey(latestSettings, provider, key).copy(
            backtestAiProvider = provider.name,
            backtestAiModel = effectiveModel
        )
        latestSettings = updatedSettings

        viewModelScope.launch {
            repository.saveSettings(updatedSettings)
            _uiState.update {
                it.copy(
                    isGeneratingStrategy = true,
                    aiError = null,
                    aiStatusMessage = null,
                    aiContractWarnings = emptyList()
                )
            }

            val genResult = withContext(Dispatchers.IO) {
                AiStrategyGenerator.generateStrategy(
                    provider = provider,
                    modelId = effectiveModel,
                    apiKey = key,
                    userDescription = prompt
                )
            }

            genResult.onSuccess { output ->
                val warnings = AiStrategyGenerator.validateStrategyContract(output.code)
                _uiState.update {
                    it.copy(
                        isGeneratingStrategy = false,
                        generatedCode = output.code,
                        generatedStrategyName = output.suggestedDisplayName,
                        generatedClassName = output.detectedClassName,
                        aiContractWarnings = warnings,
                        aiError = null,
                        aiStatusMessage = "Generated ${output.detectedClassName.ifBlank { "strategy" }} with $effectiveModel."
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isGeneratingStrategy = false,
                        aiError = err.message ?: "Failed to generate strategy."
                    )
                }
            }
        }
    }

    fun saveGeneratedStrategy(switchToRunner: Boolean) {
        val snapshot = _uiState.value
        val code = snapshot.generatedCode?.trim().orEmpty()
        if (code.isEmpty()) {
            _uiState.update { it.copy(aiError = "Generate or enter Python strategy code first.") }
            return
        }
        val name = snapshot.generatedStrategyName.trim().ifBlank { "AI Backtest Strategy" }
        val className = snapshot.generatedClassName.trim()

        viewModelScope.launch {
            runCatching {
                val strategy = PythonStrategy(
                    name = name,
                    className = className,
                    code = code,
                    updatedAt = System.currentTimeMillis()
                )
                val newId = repository.insertPythonStrategy(strategy)
                strategy.copy(id = newId)
            }.onSuccess { persisted ->
                _uiState.update {
                    it.copy(
                        selectedStrategy = persisted,
                        selectedStrategyIds = it.selectedStrategyIds + persisted.id,
                        selectedTab = if (switchToRunner) 0 else it.selectedTab,
                        aiError = null,
                        aiStatusMessage = "Saved \"${persisted.name}\" to Python strategies."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(aiError = error.message ?: "Could not save strategy.")
                }
            }
        }
    }

    private fun mergedModelsFor(provider: AiProvider): List<AiModelOption> {
        val curated = AiModelCatalog.modelsFor(provider)
        val live = liveModelsByProvider[provider].orEmpty()
        if (live.isEmpty()) return curated
        val curatedIds = curated.map { it.id }.toSet()
        val extraLive = live.filterNot { it.id in curatedIds }
        return curated + extraLive
    }

    private fun keyForProvider(settings: UserSettings, provider: AiProvider): String = when (provider) {
        AiProvider.CLAUDE -> settings.claudeApiKey
        AiProvider.GEMINI -> settings.geminiApiKey
        AiProvider.OPENAI -> settings.openAiApiKey
    }

    private fun withProviderKey(settings: UserSettings, provider: AiProvider, key: String): UserSettings =
        when (provider) {
            AiProvider.CLAUDE -> settings.copy(claudeApiKey = key)
            AiProvider.GEMINI -> settings.copy(geminiApiKey = key)
            AiProvider.OPENAI -> settings.copy(openAiApiKey = key)
        }

    private fun persistProviderCredentials(
        provider: AiProvider,
        apiKey: String,
        nextProvider: AiProvider,
        nextModel: String
    ) {
        val updated = withProviderKey(latestSettings, provider, apiKey.trim()).copy(
            backtestAiProvider = nextProvider.name,
            backtestAiModel = nextModel
        )
        latestSettings = updated
        viewModelScope.launch {
            repository.saveSettings(updated)
        }
    }

    private fun persistAiPreferences(provider: AiProvider, modelId: String) {
        val updated = latestSettings.copy(
            backtestAiProvider = provider.name,
            backtestAiModel = modelId
        )
        latestSettings = updated
        viewModelScope.launch {
            repository.saveSettings(updated)
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        return uri.lastPathSegment ?: "candles.csv"
    }
}
