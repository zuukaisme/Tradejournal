package com.example.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.TradingDatabase
import com.example.data.repository.TradingRepository
import com.example.domain.model.Bot
import com.example.domain.model.BotVersion
import com.example.domain.model.BottomNavStyle
import com.example.domain.model.CurrencyCode
import com.example.domain.model.DailyReview
import com.example.domain.model.PerformanceSummary
import com.example.domain.model.PythonStrategy
import com.example.domain.model.Strategy
import com.example.domain.model.TimePeriod
import com.example.domain.model.Trade
import com.example.domain.model.TradeResult
import com.example.domain.model.UserSettings
import com.example.domain.usecase.StatsCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class AppScreen {
    CALENDAR,
    TRADES,
    STATS,
    MENU,
    STRATEGIES,
    STRATEGY_GUIDE,
    BOTS,
    OUTLOOK,
    BACKTEST,
    SETTINGS
}

class TradingViewModel(application: Application) : AndroidViewModel(application) {
    private val database = TradingDatabase.getDatabase(application, viewModelScope)
    val repository = TradingRepository(database)

    // Current screen navigation
    private val _currentScreen = MutableStateFlow(AppScreen.CALENDAR)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Active sheet / sub-dialog states
    private val _isTradeEntryOpen = MutableStateFlow(false)
    val isTradeEntryOpen: StateFlow<Boolean> = _isTradeEntryOpen.asStateFlow()

    private val _selectedTradeForEdit = MutableStateFlow<Trade?>(null)
    val selectedTradeForEdit: StateFlow<Trade?> = _selectedTradeForEdit.asStateFlow()

    private val _tradeEntryPreselectedDate = MutableStateFlow<String?>(null)
    val tradeEntryPreselectedDate: StateFlow<String?> = _tradeEntryPreselectedDate.asStateFlow()

    private val _selectedDateForDaySheet = MutableStateFlow<String?>(null)
    val selectedDateForDaySheet: StateFlow<String?> = _selectedDateForDaySheet.asStateFlow()

    private val _selectedStrategyForEdit = MutableStateFlow<Strategy?>(null)
    val selectedStrategyForEdit: StateFlow<Strategy?> = _selectedStrategyForEdit.asStateFlow()

    private val _isStrategyEditorOpen = MutableStateFlow(false)
    val isStrategyEditorOpen: StateFlow<Boolean> = _isStrategyEditorOpen.asStateFlow()

    private val _selectedBotForView = MutableStateFlow<Bot?>(null)
    val selectedBotForView: StateFlow<Bot?> = _selectedBotForView.asStateFlow()

    private val _isBotEditorOpen = MutableStateFlow(false)
    val isBotEditorOpen: StateFlow<Boolean> = _isBotEditorOpen.asStateFlow()

    private val _isDailyReviewOpen = MutableStateFlow(false)
    val isDailyReviewOpen: StateFlow<Boolean> = _isDailyReviewOpen.asStateFlow()

    private val _reviewDate = MutableStateFlow<String?>(null)
    val reviewDate: StateFlow<String?> = _reviewDate.asStateFlow()

    // Trade filters
    val tradeSearchQuery = MutableStateFlow("")
    val selectedResultFilter = MutableStateFlow<TradeResult?>(null)
    val selectedInstrumentFilter = MutableStateFlow<String?>(null)
    val selectedStrategyFilter = MutableStateFlow<Long?>(null)

    // Stats period
    val statsPeriod = MutableStateFlow(TimePeriod.ALL_TIME)
    val customStatsStartDate = MutableStateFlow<String?>(null)
    val customStatsEndDate = MutableStateFlow<String?>(null)

    // Flows from database
    val trades: StateFlow<List<Trade>> = repository.getAllTrades()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val strategies: StateFlow<List<Strategy>> = repository.getAllStrategies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pythonStrategies: StateFlow<List<PythonStrategy>> = repository.getAllPythonStrategies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bots: StateFlow<List<Bot>> = repository.getAllBots()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyReviews: StateFlow<List<DailyReview>> = repository.getAllReviews()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<UserSettings> = repository.getSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    val newsReleases: StateFlow<List<com.example.domain.model.NewsRelease>> = repository.getAllReleases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val outlookPredictions: StateFlow<List<com.example.domain.model.OutlookPrediction>> = repository.getAllPredictions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedNewsEventType = MutableStateFlow(com.example.domain.model.NewsEventType.NFP)

    val macroIndicators = MutableStateFlow<List<com.example.domain.model.MacroIndicator>>(emptyList())

    val isOutlookLoading = MutableStateFlow(false)
    val outlookAiSummary = MutableStateFlow<String?>(null)
    val outlookStatusMessage = MutableStateFlow<String?>(null)

    val calibrationStats: StateFlow<com.example.domain.model.ModelCalibrationStats> = outlookPredictions.combine(outlookPredictions) { preds, _ ->
        com.example.domain.usecase.GoldOutlookModel.calculateCalibration(preds)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.example.domain.model.ModelCalibrationStats(0, 0, 0, 0.0, 0.0)
    )

    // Filtered trades for list screen
    val filteredTrades: StateFlow<List<Trade>> = combine(
        trades,
        tradeSearchQuery,
        selectedResultFilter,
        selectedInstrumentFilter,
        selectedStrategyFilter
    ) { allTrades, query, resultFilter, instFilter, stratFilter ->
        allTrades.filter { trade ->
            val matchesQuery = query.isBlank() ||
                    trade.instrument.contains(query, ignoreCase = true) ||
                    trade.notes.contains(query, ignoreCase = true) ||
                    trade.strategyName.contains(query, ignoreCase = true) ||
                    trade.tags.any { it.contains(query, ignoreCase = true) }

            val matchesResult = resultFilter == null || trade.result == resultFilter
            val matchesInstrument = instFilter == null || trade.instrument.equals(instFilter, ignoreCase = true)
            val matchesStrategy = stratFilter == null || trade.strategyId == stratFilter

            matchesQuery && matchesResult && matchesInstrument && matchesStrategy
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dynamic stats summary calculated reactively
    val performanceSummary: StateFlow<PerformanceSummary> = combine(
        trades,
        statsPeriod,
        customStatsStartDate,
        customStatsEndDate
    ) { allTrades, period, start, end ->
        val filtered = StatsCalculator.filterTradesByPeriod(allTrades, period, start, end)
        StatsCalculator.calculate(filtered, period)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PerformanceSummary(
            period = TimePeriod.ALL_TIME,
            netPnL = 0.0,
            winRate = 0.0,
            totalTrades = 0,
            winningTrades = 0,
            losingTrades = 0,
            breakEvenTrades = 0,
            daysNotTraded = 0,
            winningDays = 0,
            losingDays = 0,
            breakEvenDays = 0,
            averageWin = 0.0,
            averageLoss = 0.0,
            profitFactor = 0.0,
            bestDay = 0.0,
            worstDay = 0.0,
            longestWinningStreak = 0,
            longestLosingStreak = 0,
            maxDrawdown = 0.0,
            maxDrawdownPct = 0.0
        )
    )

    // Navigation methods
    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    // Trade actions
    fun openNewTradeEntry(preselectedDate: String? = null) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        _tradeEntryPreselectedDate.value = preselectedDate ?: sdf.format(Calendar.getInstance().time)
        _selectedTradeForEdit.value = null
        _isTradeEntryOpen.value = true
    }

    fun openEditTrade(trade: Trade) {
        _selectedTradeForEdit.value = trade
        _tradeEntryPreselectedDate.value = trade.date
        _isTradeEntryOpen.value = true
    }

    fun closeTradeEntry() {
        _isTradeEntryOpen.value = false
        _selectedTradeForEdit.value = null
        _tradeEntryPreselectedDate.value = null
    }

    fun saveTrade(trade: Trade) {
        viewModelScope.launch {
            if (trade.id == 0L) {
                repository.insertTrade(trade)
            } else {
                repository.updateTrade(trade)
            }
            closeTradeEntry()
        }
    }

    fun deleteTrade(id: Long) {
        viewModelScope.launch {
            repository.deleteTrade(id)
            if (_selectedTradeForEdit.value?.id == id) {
                closeTradeEntry()
            }
        }
    }

    // Calendar Day Detail Sheet
    fun openDayDetails(date: String) {
        _selectedDateForDaySheet.value = date
    }

    fun closeDayDetails() {
        _selectedDateForDaySheet.value = null
    }

    // Strategy actions
    fun openNewStrategyEditor() {
        _selectedStrategyForEdit.value = null
        _isStrategyEditorOpen.value = true
    }

    fun openEditStrategy(strategy: Strategy) {
        _selectedStrategyForEdit.value = strategy
        _isStrategyEditorOpen.value = true
    }

    fun closeStrategyEditor() {
        _isStrategyEditorOpen.value = false
        _selectedStrategyForEdit.value = null
    }

    fun saveStrategy(strategy: Strategy) {
        viewModelScope.launch {
            if (strategy.id == 0L) {
                repository.insertStrategy(strategy)
            } else {
                repository.updateStrategy(strategy)
            }
            closeStrategyEditor()
        }
    }

    fun deleteStrategy(id: Long) {
        viewModelScope.launch {
            repository.deleteStrategy(id)
            closeStrategyEditor()
        }
    }

    // Python Backtest Strategy actions
    fun savePythonStrategy(name: String, className: String, code: String) {
        viewModelScope.launch {
            repository.insertPythonStrategy(
                PythonStrategy(
                    name = name.trim(),
                    className = className.trim(),
                    code = code,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun renamePythonStrategy(strategy: PythonStrategy, newName: String, newClassName: String = strategy.className) {
        viewModelScope.launch {
            repository.updatePythonStrategy(
                strategy.copy(
                    name = newName.trim(),
                    className = newClassName.trim(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deletePythonStrategy(id: Long) {
        viewModelScope.launch {
            repository.deletePythonStrategy(id)
        }
    }

    // Bot actions
    fun openBotDetails(bot: Bot) {
        _selectedBotForView.value = bot
    }

    fun closeBotDetails() {
        _selectedBotForView.value = null
    }

    fun openNewBotEditor() {
        _selectedBotForView.value = null
        _isBotEditorOpen.value = true
    }

    fun openEditBotCode(bot: Bot) {
        _selectedBotForView.value = bot
        _isBotEditorOpen.value = true
    }

    fun closeBotEditor() {
        _isBotEditorOpen.value = false
    }

    fun saveBot(bot: Bot) {
        viewModelScope.launch {
            if (bot.id == 0L) {
                val newId = repository.insertBot(bot)
                repository.addBotVersion(
                    BotVersion(
                        botId = newId,
                        version = bot.version,
                        code = bot.code,
                        changeNote = "Initial release ${bot.version}"
                    )
                )
            } else {
                repository.updateBot(bot)
            }
            closeBotEditor()
            _selectedBotForView.value = bot
        }
    }

    fun saveBotCodeUpdate(botId: Long, newCode: String, changeNote: String, newVersionStr: String? = null) {
        viewModelScope.launch {
            val currentBot = repository.getBotById(botId) ?: return@launch
            val updatedBot = currentBot.copy(
                code = newCode,
                version = newVersionStr ?: currentBot.version,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateBot(updatedBot)
            repository.addBotVersion(
                BotVersion(
                    botId = botId,
                    version = updatedBot.version,
                    code = newCode,
                    changeNote = changeNote.ifBlank { "Code update" }
                )
            )
            _selectedBotForView.value = updatedBot
            closeBotEditor()
        }
    }

    fun deleteBot(id: Long) {
        viewModelScope.launch {
            repository.deleteBot(id)
            closeBotDetails()
            closeBotEditor()
        }
    }

    fun restoreBotVersion(botId: Long, version: BotVersion) {
        viewModelScope.launch {
            val bot = repository.getBotById(botId) ?: return@launch
            val updatedBot = bot.copy(
                code = version.code,
                version = version.version,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateBot(updatedBot)
            _selectedBotForView.value = updatedBot
        }
    }

    // Daily Review
    fun openDailyReview(date: String? = null) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        _reviewDate.value = date ?: sdf.format(Calendar.getInstance().time)
        _isDailyReviewOpen.value = true
    }

    fun closeDailyReview() {
        _isDailyReviewOpen.value = false
        _reviewDate.value = null
    }

    fun saveDailyReview(review: DailyReview) {
        viewModelScope.launch {
            repository.saveReview(review)
            closeDailyReview()
        }
    }

    // Settings
    fun updateSettings(newSettings: UserSettings) {
        viewModelScope.launch {
            repository.saveSettings(newSettings)
        }
    }

    fun updateCurrency(currency: CurrencyCode) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(currency = currency))
        }
    }

    fun updateBottomNavStyle(style: BottomNavStyle) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(bottomNavStyle = style))
        }
    }

    fun updateGoals(monthlyProfitTarget: Double, maxDailyLossLimit: Double) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(
                current.copy(
                    monthlyProfitTarget = monthlyProfitTarget,
                    maxDailyLossLimit = maxDailyLossLimit
                )
            )
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            macroIndicators.value = emptyList()
            outlookAiSummary.value = null
            outlookStatusMessage.value = null
        }
    }

    // Outlook actions
    fun setNewsEventType(type: com.example.domain.model.NewsEventType) {
        selectedNewsEventType.value = type
        outlookAiSummary.value = null
        outlookStatusMessage.value = null
    }

    fun togglePredictionTraded(prediction: com.example.domain.model.OutlookPrediction) {
        viewModelScope.launch {
            val updated = prediction.copy(userTradedRelease = !prediction.userTradedRelease)
            repository.updatePrediction(updated)
        }
    }

    fun refreshOutlook() {
        viewModelScope.launch {
            isOutlookLoading.value = true
            outlookStatusMessage.value = null
            val currentSettings = settings.value
            val fredKey = currentSettings.fredApiKey.trim()
            val finnhubKey = currentSettings.finnhubApiKey.trim()

            if (fredKey.isBlank() && finnhubKey.isBlank()) {
                outlookStatusMessage.value = "Add a FRED or Finnhub API key in Settings to fetch live macro data."
                isOutlookLoading.value = false
                return@launch
            }

            if (fredKey.isNotBlank()) {
                val fredRes = repository.fetchFredMacroIndicators(fredKey)
                fredRes.onSuccess { indicators ->
                    macroIndicators.value = indicators
                }.onFailure { err ->
                    outlookStatusMessage.value = "FRED fetch failed: ${err.message}"
                }
            }

            if (finnhubKey.isNotBlank()) {
                val calRes = repository.fetchFinnhubUsCalendar(finnhubKey)
                calRes.onSuccess { fetched ->
                    if (fetched.isNotEmpty()) {
                        repository.insertAllReleases(fetched)
                    }
                }.onFailure { err ->
                    outlookStatusMessage.value = "Finnhub fetch failed: ${err.message}"
                }
            }

            val event = selectedNewsEventType.value
            val allEventReleases = newsReleases.value.filter { it.eventType == event }
            val upcoming = allEventReleases.firstOrNull { it.isUpcoming } ?: allEventReleases.firstOrNull()
            val historical = allEventReleases.filter { !it.isUpcoming }
            val indicators = macroIndicators.value

            if (upcoming != null || indicators.isNotEmpty()) {
                val claimsInd = indicators.firstOrNull { it.id == "claims" }
                val claimsTrend = if (claimsInd != null && claimsInd.previousValue != 0.0) {
                    ((claimsInd.currentValue - claimsInd.previousValue) / claimsInd.previousValue) * 100.0
                } else 0.0
                val unrateInd = indicators.firstOrNull { it.id == "unrate" }
                val unrateChange = if (unrateInd != null) unrateInd.currentValue - unrateInd.previousValue else 0.0
                val wageInd = indicators.firstOrNull { it.id == "wages" }
                val wageGrowth = if (wageInd != null && wageInd.previousValue != 0.0) {
                    ((wageInd.currentValue - wageInd.previousValue) / wageInd.previousValue) * 100.0
                } else 0.0

                val newPred = com.example.domain.usecase.GoldOutlookModel.computePrediction(
                    eventType = event,
                    releaseDate = upcoming?.dateString ?: "Pending Calendar",
                    releaseTimestamp = upcoming?.timestamp ?: System.currentTimeMillis(),
                    forecast = upcoming?.forecast ?: 0.0,
                    previous = upcoming?.previous ?: 0.0,
                    claimsTrend = claimsTrend,
                    unemploymentChange = unrateChange,
                    wageGrowthYoY = wageGrowth,
                    historicalReleases = historical
                )
                repository.savePrediction(newPred)
            } else if (outlookStatusMessage.value == null) {
                outlookStatusMessage.value = "No live releases or indicators returned yet."
            }

            isOutlookLoading.value = false
        }
    }

    fun generateAiExplanation(prediction: com.example.domain.model.OutlookPrediction) {
        viewModelScope.launch {
            isOutlookLoading.value = true
            outlookStatusMessage.value = null
            val geminiKey = settings.value.geminiApiKey.trim()
            if (geminiKey.isBlank()) {
                outlookStatusMessage.value = "Add your Gemini API key in Settings to generate an AI summary."
                isOutlookLoading.value = false
                return@launch
            }
            val probRisePct = (prediction.probGoldRise * 100).toInt()
            val probFallPct = (prediction.probGoldFall * 100).toInt()
            val indicatorText = macroIndicators.value.joinToString("; ") {
                "${it.name}: ${it.currentValue}${it.unit} (prev ${it.previousValue}${it.unit})"
            }.ifBlank { "No macro indicators cached" }

            val prompt = """
                Summarize this quantitative statistical gold (XAUUSD) event outlook in 3 concise sentences without changing the probabilities:
                Event: ${prediction.eventType.name} on ${prediction.releaseDate}
                Model Probabilities: $probRisePct% Buy Gold / $probFallPct% Sell Gold (${prediction.confidence.label}, sample size ${prediction.sampleSize})
                Statistical Rationale: ${prediction.reasoning}
                Cached Macro Inputs: $indicatorText
                Include a brief risk note.
            """.trimIndent()

            repository.requestGeminiSummary(geminiKey, prompt)
                .onSuccess { summary -> outlookAiSummary.value = summary }
                .onFailure { err -> outlookStatusMessage.value = "AI summary failed: ${err.message}" }
            isOutlookLoading.value = false
        }
    }
}
