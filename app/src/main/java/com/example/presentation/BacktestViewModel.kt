package com.example.presentation

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.backtest.BacktestConfig
import com.example.backtest.BacktestCsvParser
import com.example.backtest.BacktestResult
import com.example.backtest.BacktestStrategies
import com.example.backtest.Candle
import com.example.backtest.NamedStrategy
import com.example.backtest.runBacktest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BacktestUiState(
    val fileName: String? = null,
    val candlesCount: Int = 0,
    val skippedCsvRows: Int = 0,
    val detectedEncoding: String = "",
    val detectedDelimiter: String = "",
    val candles: List<Candle> = emptyList(),
    val availableStrategies: List<NamedStrategy> = BacktestStrategies.all,
    val selectedStrategy: NamedStrategy = BacktestStrategies.all.first(),
    val maxStopPointsInput: String = "",
    val pointSizeInput: String = "1.0",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val result: BacktestResult? = null
)

class BacktestViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(BacktestUiState())
    val uiState: StateFlow<BacktestUiState> = _uiState.asStateFlow()

    fun selectStrategy(strategy: NamedStrategy) {
        _uiState.update { it.copy(selectedStrategy = strategy, errorMessage = null) }
    }

    fun updateMaxStopPoints(input: String) {
        _uiState.update { it.copy(maxStopPointsInput = input, errorMessage = null) }
    }

    fun updatePointSize(input: String) {
        _uiState.update { it.copy(pointSizeInput = input, errorMessage = null) }
    }

    fun loadCsvUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val displayName = resolveDisplayName(context, uri)
                    val parseResult = context.contentResolver.openInputStream(uri)?.use { stream ->
                        BacktestCsvParser.parse(stream)
                    } ?: throw IllegalStateException("Unable to open selected document.")
                    displayName to parseResult
                }
                val (displayName, parseResult) = parsed
                _uiState.update {
                    it.copy(
                        fileName = displayName,
                        candles = parseResult.candles,
                        candlesCount = parseResult.candles.size,
                        skippedCsvRows = parseResult.skippedRows,
                        detectedEncoding = parseResult.detectedEncoding,
                        detectedDelimiter = parseResult.detectedDelimiter,
                        isLoading = false,
                        errorMessage = if (parseResult.candles.isEmpty()) {
                            "Parsed 0 valid candles from file."
                        } else null,
                        result = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to parse CSV file."
                    )
                }
            }
        }
    }

    fun run() {
        val snapshot = _uiState.value
        if (snapshot.candles.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please select a CSV file with candle data first.") }
            return
        }

        val maxStopText = snapshot.maxStopPointsInput.trim()
        val maxStopPoints: Double? = if (maxStopText.isEmpty()) {
            null
        } else {
            val parsed = maxStopText.toDoubleOrNull()
            if (parsed == null || parsed <= 0.0) {
                _uiState.update { it.copy(errorMessage = "maxStopPoints must be a positive number or blank.") }
                return
            }
            parsed
        }

        val pointSizeText = snapshot.pointSizeInput.trim()
        val pointSize = pointSizeText.toDoubleOrNull()
        if (pointSize == null || pointSize <= 0.0) {
            _uiState.update { it.copy(errorMessage = "pointSize must be a positive number (e.g. 1.0 or 0.01).") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val config = BacktestConfig(
                    maxStopPoints = maxStopPoints,
                    pointSize = pointSize
                )
                val backtestResult = withContext(Dispatchers.Default) {
                    runBacktest(
                        candles = snapshot.candles,
                        strategy = snapshot.selectedStrategy,
                        config = config
                    )
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        result = backtestResult
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Backtest execution failed."
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
