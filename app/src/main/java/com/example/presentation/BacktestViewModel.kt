package com.example.presentation

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.backtest.BacktestCodec
import com.example.backtest.BacktestError
import com.example.backtest.BacktestParseOutcome
import com.example.backtest.BacktestResult
import com.example.backtest.PythonBacktestRunner
import com.example.data.local.TradingDatabase
import com.example.data.repository.TradingRepository
import com.example.domain.model.PythonStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BacktestUiState(
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
    val result: BacktestResult? = null
)

class BacktestViewModel(application: Application) : AndroidViewModel(application) {

    private val database = TradingDatabase.getDatabase(application, viewModelScope)
    private val repository = TradingRepository(database)

    private var csvRawBytes: ByteArray? = null

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
    }

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
                it.copy(backtestError = BacktestError("Please upload and select a Python strategy in Strategies first."))
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
                                backtestError = null
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
