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
import com.example.backtest.BacktestResult
import com.example.backtest.PythonBacktestRunner
import com.example.backtest.SavedBacktestRun
import com.example.data.local.TradingDatabase
import com.example.data.repository.TradingRepository
import com.example.domain.model.PythonStrategy
import com.example.domain.model.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BacktestUiState(
    val selectedTab: Int = 0, // 0 = Run Backtest, 1 = AI Strategy Builder
    val fileName: String? = null,
    val estimatedRows: Int = 0,
    val detectedEncoding: String = "",
    val detectedDelimiter: String = "",
    val hasCsvBytes: Boolean = false,
    val availableStrategies: List<PythonStrategy> = emptyList(),
    val selectedStrategy: PythonStrategy? = null,
    val maxStopPointsInput: String = "",
    val pointSizeInput: String = "1.0",
    val isLoading: Boolean = false,
    val backtestError: BacktestError? = null,
    val result: BacktestResult? = null,
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
    private val repository = TradingRepository(database)

    private var csvRawBytes: ByteArray? = null
    private var latestSettings: UserSettings = UserSettings()
    private var hasHydratedAiPrefs = false
    private val liveModelsByProvider = mutableMapOf<AiProvider, List<AiModelOption>>()

    private val _uiState = MutableStateFlow(BacktestUiState())
    val uiState: StateFlow<BacktestUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllPythonStrategies().collect { strategies ->
                _uiState.update { current ->
                    val currentId = current.selectedStrategy?.id
                    val matched = strategies.firstOrNull { it.id == currentId } ?: strategies.firstOrNull()
                    current.copy(
                        availableStrategies = strategies,
                        selectedStrategy = matched
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
                }
            }
        }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
    }

    // --- Backtest Runner Controls ---

    fun selectStrategy(strategy: PythonStrategy) {
        _uiState.update { it.copy(selectedStrategy = strategy, backtestError = null) }
    }

    fun updateMaxStopPoints(input: String) {
        _uiState.update { it.copy(maxStopPointsInput = input, backtestError = null) }
    }

    fun updatePointSize(input: String) {
        _uiState.update { it.copy(pointSizeInput = input, backtestError = null) }
    }

    fun loadCsvUri(uri: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, backtestError = null) }
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val displayName = resolveDisplayName(context, uri)
                    val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes()
                    } ?: throw IllegalStateException("Unable to open selected CSV document.")
                    val preview = BacktestCodec.decodeCsvBytes(bytes)
                    Triple(displayName, bytes, preview)
                }
                val (displayName, bytes, preview) = loaded
                csvRawBytes = bytes
                _uiState.update {
                    it.copy(
                        fileName = displayName,
                        estimatedRows = preview.estimatedRows,
                        detectedEncoding = preview.detectedEncoding,
                        detectedDelimiter = preview.detectedDelimiter,
                        hasCsvBytes = bytes.isNotEmpty(),
                        isLoading = false,
                        backtestError = if (bytes.isEmpty()) {
                            BacktestError("Selected CSV file is empty.")
                        } else null,
                        result = null
                    )
                }
            } catch (t: Throwable) {
                csvRawBytes = null
                _uiState.update {
                    it.copy(
                        hasCsvBytes = false,
                        isLoading = false,
                        backtestError = BacktestError(
                            error = t.message ?: "Failed to read CSV file.",
                            details = t.stackTraceToString().take(1200)
                        )
                    )
                }
            }
        }
    }

    fun run() {
        val snapshot = _uiState.value
        val rawBytes = csvRawBytes
        if (rawBytes == null || rawBytes.isEmpty()) {
            _uiState.update {
                it.copy(backtestError = BacktestError("Please select a CSV / MT5 export file first."))
            }
            return
        }

        val strategy = snapshot.selectedStrategy
        if (strategy == null) {
            _uiState.update {
                it.copy(backtestError = BacktestError("Please upload or generate a Python strategy first."))
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

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, backtestError = null) }
            try {
                val appContext = getApplication<Application>()
                val outcome = withContext(Dispatchers.Default) {
                    val decoded = BacktestCodec.decodeCsvBytes(rawBytes)
                    val jsonString = PythonBacktestRunner.runWithStrategyCode(
                        context = appContext,
                        csvText = decoded.text,
                        strategyCode = strategy.code,
                        className = strategy.className,
                        maxStopPoints = maxStopPoints,
                        pointSize = pointSize
                    )
                    BacktestCodec.parseBacktestJson(jsonString)
                }

                when (outcome) {
                    is BacktestParseOutcome.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                result = outcome.result,
                                backtestError = null,
                                runSavedFeedback = null
                            )
                        }
                    }
                    is BacktestParseOutcome.Failure -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                result = null,
                                backtestError = outcome.error
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
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

    fun saveCurrentResult() {
        val snapshot = _uiState.value
        val result = snapshot.result ?: return
        val run = SavedBacktestRun(
            strategyName = snapshot.selectedStrategy?.name ?: "Strategy",
            csvFileName = snapshot.fileName ?: "candles.csv",
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
            repository.saveBacktestRun(run)
            _uiState.update { it.copy(runSavedFeedback = "Saved \"${run.strategyName}\" to Compare tab.") }
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

        // Persist any typed key for previous provider before switching
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
        _uiState.update { it.copy(generatedCode = code) }
    }

    fun fetchLiveModels() {
        val snapshot = _uiState.value
        val provider = snapshot.selectedProvider
        val key = snapshot.apiKeyInput.trim()
        if (key.isEmpty()) {
            _uiState.update { it.copy(aiError = "Enter your ${provider.keyLabel} to fetch live models.") }
            return
        }

        // Save key before fetching
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
                    aiStatusMessage = null
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
                _uiState.update {
                    it.copy(
                        isGeneratingStrategy = false,
                        generatedCode = output.code,
                        generatedStrategyName = output.suggestedDisplayName,
                        generatedClassName = output.detectedClassName,
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
            val strategy = PythonStrategy(
                name = name,
                className = className,
                code = code,
                updatedAt = System.currentTimeMillis()
            )
            val newId = repository.insertPythonStrategy(strategy)
            val persisted = strategy.copy(id = newId)
            _uiState.update {
                it.copy(
                    selectedStrategy = persisted,
                    selectedTab = if (switchToRunner) 0 else it.selectedTab,
                    aiStatusMessage = "Saved \"$name\" to Python strategies."
                )
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
