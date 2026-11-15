package com.example.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.LocalVaultStorage
import com.example.data.local.TradingDatabase
import com.example.data.local.VaultJsonCodec
import com.example.data.local.VaultRestoreSummary
import com.example.data.repository.TradingRepository
import com.example.data.repository.EconomicCalendarRepository
import com.example.domain.model.Bot
import android.content.Context
import android.net.Uri
import com.example.backtest.AiModelCatalog
import com.example.backtest.AiModelOption
import com.example.backtest.AiProvider
import com.example.backtest.AiStrategyGenerator
import com.example.domain.model.AppThemePreset
import com.example.domain.model.BotVersion
import com.example.domain.model.BottomNavStyle
import com.example.domain.model.CurrencyCode
import com.example.domain.model.DailyReview
import com.example.domain.model.EconomicCalendarEvent
import com.example.domain.model.PerformanceSummary
import com.example.domain.model.PythonStrategy
import com.example.domain.model.Strategy
import com.example.domain.model.TimePeriod
import com.example.domain.model.Trade
import com.example.domain.model.TradeResult
import com.example.domain.model.UserSettings
import com.example.domain.usecase.GoldNewsFeedMapper
import com.example.domain.usecase.GoldOutlookModel
import com.example.domain.usecase.Mt5ImportResult
import com.example.domain.usecase.Mt5TradeHistoryImporter
import com.example.domain.usecase.StatsCalculator
import com.example.notifications.EveningReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class OutlookAiConfigState(
    val selectedProvider: AiProvider = AiProvider.CLAUDE,
    val selectedModelId: String = AiModelCatalog.defaultModelFor(AiProvider.CLAUDE).id,
    val availableModels: List<AiModelOption> = AiModelCatalog.modelsFor(AiProvider.CLAUDE),
    val useCustomModel: Boolean = false,
    val customModelInput: String = "",
    val apiKeyInput: String = "",
    val isFetchingModels: Boolean = false,
    val aiStatusMessage: String? = null,
    val aiError: String? = null,
    val lastUsedModelLabel: String? = null
)

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
    RISK_CALC,
    CHARTS,
    ECONOMIC_CALENDAR,
    PSYCHOLOGY,
    SETTINGS
}

class TradingViewModel(application: Application) : AndroidViewModel(application) {
    private val database = TradingDatabase.getDatabase(application, viewModelScope)
    val repository = TradingRepository(database)
    private val economicCalendarRepository = EconomicCalendarRepository()

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

    val economicCalendarEvents = MutableStateFlow<List<EconomicCalendarEvent>>(emptyList())
    val isEconomicCalendarLoading = MutableStateFlow(false)
    val economicCalendarStatus = MutableStateFlow<String?>(null)
    private var lastEconomicCalendarFetchAt = 0L

    val settings: StateFlow<UserSettings> = repository.getSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    // Local Vault persistence state (on-device Downloads/TradePulse snapshot & fresh-install restore)
    private val _autoVaultEnabled = MutableStateFlow(LocalVaultStorage.isAutoVaultEnabled(application))
    val autoVaultEnabled: StateFlow<Boolean> = _autoVaultEnabled.asStateFlow()

    private val _lastVaultSavedAt = MutableStateFlow(LocalVaultStorage.getLastSavedAt(application))
    val lastVaultSavedAt: StateFlow<Long> = _lastVaultSavedAt.asStateFlow()

    private val _lastVaultLocation = MutableStateFlow(LocalVaultStorage.getLastLocation(application))
    val lastVaultLocation: StateFlow<String> = _lastVaultLocation.asStateFlow()

    private val _showFreshInstallRestorePrompt = MutableStateFlow(false)
    val showFreshInstallRestorePrompt: StateFlow<Boolean> = _showFreshInstallRestorePrompt.asStateFlow()

    private val _vaultNoticeMessage = MutableStateFlow<String?>(null)
    val vaultNoticeMessage: StateFlow<String?> = _vaultNoticeMessage.asStateFlow()

    private var latestSettings: UserSettings = UserSettings()
    private var hasHydratedOutlookAiPrefs = false
    private var hasTriggeredStartupGoldNewsPull = false
    private var lastOutlookFetchAt = 0L
    private var lastFetchedFredKey = ""
    private val outlookLiveModelsByProvider = mutableMapOf<AiProvider, List<AiModelOption>>()

    private val _outlookAiState = MutableStateFlow(OutlookAiConfigState())
    val outlookAiState: StateFlow<OutlookAiConfigState> = _outlookAiState.asStateFlow()

    val newsReleases: StateFlow<List<com.example.domain.model.NewsRelease>> = repository.getAllReleases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val outlookPredictions: StateFlow<List<com.example.domain.model.OutlookPrediction>> = repository.getAllPredictions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedNewsEventType = MutableStateFlow(com.example.domain.model.NewsEventType.NFP)

    val macroIndicators = MutableStateFlow<List<com.example.domain.model.MacroIndicator>>(emptyList())
    val goldNewsEvents = MutableStateFlow<List<EconomicCalendarEvent>>(emptyList())
    val lastGoldNewsSyncAt = MutableStateFlow(0L)

    val isOutlookLoading = MutableStateFlow(false)
    val outlookAiSummary = MutableStateFlow<String?>(null)
    val outlookStatusMessage = MutableStateFlow<String?>(null)

    init {
        startLocalVaultLifecycle()
        startOutlookAiSettingsSync()
    }

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

    fun refreshEconomicCalendar() {
        if (isEconomicCalendarLoading.value) return
        val now = System.currentTimeMillis()
        if (now - lastEconomicCalendarFetchAt < 5 * 60 * 1000L) return
        lastEconomicCalendarFetchAt = now
        viewModelScope.launch {
            isEconomicCalendarLoading.value = true
            economicCalendarStatus.value = null
            economicCalendarRepository.fetchEvents()
                .onSuccess { events ->
                    economicCalendarEvents.value = events
                    if (events.isEmpty()) economicCalendarStatus.value = "No events were returned for this week."
                }
                .onFailure { error ->
                    economicCalendarStatus.value = "Calendar refresh failed: ${error.message ?: "Unknown error"}"
                }
            isEconomicCalendarLoading.value = false
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

    fun updateThemePreset(preset: AppThemePreset) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(
                current.copy(
                    themePreset = preset,
                    isDarkTheme = !preset.forcesLight
                )
            )
        }
    }

    fun updateEveningReminder(enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            val updated = current.copy(eveningReminderEnabled = enabled)
            repository.saveSettings(updated)
            EveningReminderScheduler.sync(
                context = getApplication(),
                enabled = enabled,
                hour = updated.reminderHour,
                minute = updated.reminderMinute
            )
        }
    }

    fun updateGoals(
        monthlyProfitTarget: Double,
        maxDailyLossLimit: Double,
        maxTrailingDrawdown: Double = settings.value.maxTrailingDrawdown
    ) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(
                current.copy(
                    monthlyProfitTarget = monthlyProfitTarget,
                    maxDailyLossLimit = maxDailyLossLimit,
                    maxTrailingDrawdown = maxTrailingDrawdown
                )
            )
        }
    }

    fun importMt5HistoryUri(uri: Uri, onComplete: (Mt5ImportResult) -> Unit) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching Mt5ImportResult(emptyList(), 0, "Unreadable file")
                    Mt5TradeHistoryImporter.parseCsv(bytes)
                }.getOrElse {
                    Mt5ImportResult(emptyList(), 0, it.message ?: "Import failed")
                }
            }
            if (result.trades.isNotEmpty()) {
                repository.insertAllTrades(result.trades)
            }
            onComplete(result)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            macroIndicators.value = emptyList()
            goldNewsEvents.value = emptyList()
            lastGoldNewsSyncAt.value = 0L
            lastOutlookFetchAt = 0L
            outlookAiSummary.value = null
            outlookStatusMessage.value = null
        }
    }

    // Outlook actions
    fun setNewsEventType(type: com.example.domain.model.NewsEventType) {
        selectedNewsEventType.value = type
        outlookAiSummary.value = null
        outlookStatusMessage.value = null
        if (outlookPredictions.value.none { it.eventType == type }) {
            refreshOutlook(force = false)
        }
    }

    fun togglePredictionTraded(prediction: com.example.domain.model.OutlookPrediction) {
        viewModelScope.launch {
            val updated = prediction.copy(userTradedRelease = !prediction.userTradedRelease)
            repository.updatePrediction(updated)
        }
    }

    fun refreshOutlook(force: Boolean = true) {
        if (isOutlookLoading.value) return
        val now = System.currentTimeMillis()
        if (!force &&
            lastOutlookFetchAt > 0L &&
            now - lastOutlookFetchAt < 5 * 60 * 1000L &&
            (goldNewsEvents.value.isNotEmpty() || outlookPredictions.value.isNotEmpty())
        ) {
            return
        }
        lastOutlookFetchAt = now

        viewModelScope.launch {
            isOutlookLoading.value = true
            outlookStatusMessage.value = null

            val currentSettings = latestSettings
            val fredKey = currentSettings.fredApiKey.trim()

            // Pull both structured NFP/CPI calendar and live Faireconomy weekly USD Gold catalysts
            val xoomarResult = repository.fetchXoomarEconomicCalendar()
            val xoomarReleases = xoomarResult.getOrElse { emptyList() }

            val calendarResult = economicCalendarRepository.fetchEvents()
            val calendarEvents = calendarResult.getOrElse { emptyList() }
            if (calendarEvents.isNotEmpty()) {
                economicCalendarEvents.value = calendarEvents
                lastEconomicCalendarFetchAt = System.currentTimeMillis()
                goldNewsEvents.value = GoldNewsFeedMapper.filterGoldMovingEvents(calendarEvents, now)
            }
            val calendarReleases = if (calendarEvents.isNotEmpty()) {
                GoldNewsFeedMapper.toNewsReleases(calendarEvents, now)
            } else {
                emptyList()
            }

            val combinedFetched = (xoomarReleases + calendarReleases)
                .distinctBy { it.eventType to it.timestamp }
            val existingReleaseKeys = newsReleases.value
                .map { it.eventType to it.timestamp }
                .toHashSet()
            val newReleasesToInsert = combinedFetched.filter {
                (it.eventType to it.timestamp) !in existingReleaseKeys
            }
            if (newReleasesToInsert.isNotEmpty()) {
                repository.insertAllReleases(newReleasesToInsert)
            }

            var fredError: String? = null
            if (fredKey.isNotBlank()) {
                lastFetchedFredKey = fredKey
                repository.fetchFredMacroIndicators(fredKey)
                    .onSuccess { indicators -> macroIndicators.value = indicators }
                    .onFailure { err -> fredError = err.message }
            }

            val allKnownReleases = (combinedFetched + newsReleases.value)
                .distinctBy { it.eventType to it.timestamp }
            val indicators = macroIndicators.value
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

            val nextGoldCatalyst = goldNewsEvents.value.firstOrNull { it.timestamp >= now - 30 * 60_000L }
                ?: goldNewsEvents.value.firstOrNull()
            val utcDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }

            // Recompute predictions for both NFP & CPI so tab switching and Menu preview stay fresh
            var anyPredictionUpdated = false
            for (eventType in com.example.domain.model.NewsEventType.entries) {
                val eventReleases = allKnownReleases.filter { it.eventType == eventType }
                val upcoming = eventReleases.firstOrNull { it.isUpcoming } ?: eventReleases.firstOrNull()
                val historical = eventReleases.filter { !it.isUpcoming }

                if (upcoming != null || indicators.isNotEmpty() || nextGoldCatalyst != null) {
                    val releaseDate = upcoming?.dateString ?: nextGoldCatalyst?.let {
                        "${it.title} (${utcDateFormat.format(java.util.Date(it.timestamp))})"
                    } ?: "Live Macro Model"
                    val releaseTimestamp = upcoming?.timestamp
                        ?: nextGoldCatalyst?.timestamp
                        ?: now

                    val computed = GoldOutlookModel.computePrediction(
                        eventType = eventType,
                        releaseDate = releaseDate,
                        releaseTimestamp = releaseTimestamp,
                        previous = upcoming?.previous ?: 0.0,
                        claimsTrend = claimsTrend,
                        unemploymentChange = unrateChange,
                        wageGrowthYoY = wageGrowth,
                        historicalReleases = historical
                    )
                    val existing = outlookPredictions.value.firstOrNull { it.eventType == eventType }
                    val toSave = if (existing != null) {
                        computed.copy(
                            id = existing.id,
                            userTradedRelease = existing.userTradedRelease
                        )
                    } else {
                        computed
                    }
                    repository.savePrediction(toSave)
                    anyPredictionUpdated = true
                }
            }

            if (xoomarResult.isSuccess || calendarResult.isSuccess || fredError == null && fredKey.isNotBlank()) {
                lastGoldNewsSyncAt.value = System.currentTimeMillis()
            }

            if (fredError != null && force) {
                outlookStatusMessage.value = "FRED fetch failed: $fredError"
            } else if (xoomarResult.isFailure && calendarResult.isFailure) {
                val calErr = calendarResult.exceptionOrNull()?.message ?: xoomarResult.exceptionOrNull()?.message
                outlookStatusMessage.value = "Gold news auto-pull failed: ${calErr ?: "Check internet connection"}"
            } else if (!anyPredictionUpdated && outlookStatusMessage.value == null && force) {
                outlookStatusMessage.value = "No live releases or indicators returned yet."
            }

            isOutlookLoading.value = false
        }
    }

    private fun outlookPrefs() = getApplication<Application>()
        .getSharedPreferences("tradepulse_ai_prefs", Context.MODE_PRIVATE)

    private fun startOutlookAiSettingsSync() {
        viewModelScope.launch {
            repository.getSettings().collect { loaded ->
                latestSettings = loaded
                if (!hasHydratedOutlookAiPrefs) {
                    hasHydratedOutlookAiPrefs = true
                    val prefs = outlookPrefs()
                    val savedProviderRaw = prefs.getString("outlook_ai_provider", null)
                        ?: loaded.backtestAiProvider
                    val provider = AiProvider.entries.firstOrNull {
                        it.name.equals(savedProviderRaw, ignoreCase = true)
                    } ?: AiProvider.CLAUDE
                    val models = mergedOutlookModelsFor(provider)
                    val savedModelRaw = (prefs.getString("outlook_ai_model", null)
                        ?: loaded.backtestAiModel).trim()
                    val isKnownModel = models.any { it.id == savedModelRaw }
                    val isCustom = savedModelRaw.isNotBlank() && !isKnownModel
                    val savedKey = outlookKeyForProvider(loaded, provider)
                    _outlookAiState.update {
                        it.copy(
                            selectedProvider = provider,
                            availableModels = models,
                            selectedModelId = if (isKnownModel) savedModelRaw else models.first().id,
                            useCustomModel = isCustom,
                            customModelInput = if (isCustom) savedModelRaw else "",
                            apiKeyInput = savedKey
                        )
                    }
                } else {
                    val activeProvider = _outlookAiState.value.selectedProvider
                    val storedKey = outlookKeyForProvider(loaded, activeProvider)
                    if (_outlookAiState.value.apiKeyInput.isBlank() && storedKey.isNotBlank()) {
                        _outlookAiState.update { it.copy(apiKeyInput = storedKey) }
                    }
                }

                // Auto-pull Gold news once at startup when Outlook is enabled,
                // and re-pull if a FRED API key is newly added/restored later.
                val trimmedFred = loaded.fredApiKey.trim()
                if (loaded.isOutlookEnabled) {
                    if (!hasTriggeredStartupGoldNewsPull) {
                        hasTriggeredStartupGoldNewsPull = true
                        refreshOutlook(force = false)
                    } else if (trimmedFred.isNotBlank() && trimmedFred != lastFetchedFredKey) {
                        refreshOutlook(force = true)
                    }
                }
            }
        }
    }

    fun selectOutlookAiProvider(provider: AiProvider) {
        val current = _outlookAiState.value
        val previousProvider = current.selectedProvider
        if (current.apiKeyInput.isNotBlank()) {
            val prevUpdated = withOutlookProviderKey(
                latestSettings,
                previousProvider,
                current.apiKeyInput.trim()
            )
            latestSettings = prevUpdated
            viewModelScope.launch { repository.saveSettings(prevUpdated) }
        }

        val models = mergedOutlookModelsFor(provider)
        val defaultModelId = models.first().id
        val savedKey = outlookKeyForProvider(latestSettings, provider)
        persistOutlookAiChoice(provider, defaultModelId)

        _outlookAiState.update {
            it.copy(
                selectedProvider = provider,
                availableModels = models,
                selectedModelId = defaultModelId,
                useCustomModel = false,
                customModelInput = "",
                apiKeyInput = savedKey,
                aiError = null,
                aiStatusMessage = null
            )
        }
    }

    fun selectOutlookAiModel(modelId: String) {
        val provider = _outlookAiState.value.selectedProvider
        _outlookAiState.update {
            it.copy(
                selectedModelId = modelId,
                useCustomModel = false,
                aiError = null,
                aiStatusMessage = null
            )
        }
        persistOutlookAiChoice(provider, modelId)
    }

    fun enableOutlookCustomModel(customId: String) {
        val provider = _outlookAiState.value.selectedProvider
        _outlookAiState.update {
            it.copy(
                useCustomModel = true,
                customModelInput = customId,
                aiError = null
            )
        }
        if (customId.isNotBlank()) {
            persistOutlookAiChoice(provider, customId.trim())
        }
    }

    fun updateOutlookCustomModelInput(input: String) {
        val provider = _outlookAiState.value.selectedProvider
        _outlookAiState.update { it.copy(customModelInput = input, useCustomModel = true, aiError = null) }
        if (input.isNotBlank()) {
            persistOutlookAiChoice(provider, input.trim())
        }
    }

    fun updateOutlookApiKeyInput(input: String) {
        val provider = _outlookAiState.value.selectedProvider
        _outlookAiState.update { it.copy(apiKeyInput = input, aiError = null, aiStatusMessage = null) }
        latestSettings = withOutlookProviderKey(latestSettings, provider, input.trim())
    }

    fun saveOutlookApiKey() {
        val state = _outlookAiState.value
        val trimmed = state.apiKeyInput.trim()
        val effectiveModel = if (state.useCustomModel) state.customModelInput.trim() else state.selectedModelId
        persistOutlookAiChoice(state.selectedProvider, effectiveModel.ifBlank { state.selectedModelId })
        val updated = withOutlookProviderKey(latestSettings, state.selectedProvider, trimmed)
        latestSettings = updated
        viewModelScope.launch {
            repository.saveSettings(updated)
            _outlookAiState.update {
                it.copy(
                    aiError = null,
                    aiStatusMessage = "${state.selectedProvider.displayName} API key saved locally (shared with Backtester & Vault)."
                )
            }
        }
    }

    fun fetchOutlookLiveModels() {
        val state = _outlookAiState.value
        val provider = state.selectedProvider
        val key = state.apiKeyInput.trim().ifBlank { outlookKeyForProvider(latestSettings, provider).trim() }
        if (key.isEmpty()) {
            _outlookAiState.update { it.copy(aiError = "Enter your ${provider.keyLabel} to fetch live models.") }
            return
        }

        val updated = withOutlookProviderKey(latestSettings, provider, key)
        latestSettings = updated

        viewModelScope.launch {
            repository.saveSettings(updated)
            _outlookAiState.update { it.copy(isFetchingModels = true, aiError = null, aiStatusMessage = null) }
            val result = withContext(Dispatchers.IO) {
                AiStrategyGenerator.fetchAvailableModels(provider, key)
            }
            result.onSuccess { liveModels ->
                outlookLiveModelsByProvider[provider] = liveModels
                val merged = mergedOutlookModelsFor(provider)
                _outlookAiState.update {
                    it.copy(
                        isFetchingModels = false,
                        availableModels = merged,
                        aiStatusMessage = "Loaded ${liveModels.size} live ${provider.displayName} models from API."
                    )
                }
            }.onFailure { err ->
                _outlookAiState.update {
                    it.copy(
                        isFetchingModels = false,
                        aiError = err.message ?: "Failed to fetch live models."
                    )
                }
            }
        }
    }

    private fun mergedOutlookModelsFor(provider: AiProvider): List<AiModelOption> {
        val curated = AiModelCatalog.modelsFor(provider)
        val live = outlookLiveModelsByProvider[provider].orEmpty()
        if (live.isEmpty()) return curated
        val curatedIds = curated.map { it.id }.toSet()
        val extraLive = live.filterNot { it.id in curatedIds }
        return curated + extraLive
    }

    private fun persistOutlookAiChoice(provider: AiProvider, modelId: String) {
        outlookPrefs().edit()
            .putString("outlook_ai_provider", provider.name)
            .putString("outlook_ai_model", modelId)
            .apply()
    }

    private fun outlookKeyForProvider(settings: UserSettings, provider: AiProvider): String = when (provider) {
        AiProvider.CLAUDE -> settings.claudeApiKey
        AiProvider.GEMINI -> settings.geminiApiKey
        AiProvider.OPENAI -> settings.openAiApiKey
    }

    private fun withOutlookProviderKey(settings: UserSettings, provider: AiProvider, key: String): UserSettings =
        when (provider) {
            AiProvider.CLAUDE -> settings.copy(claudeApiKey = key)
            AiProvider.GEMINI -> settings.copy(geminiApiKey = key)
            AiProvider.OPENAI -> settings.copy(openAiApiKey = key)
        }

    fun generateAiExplanation(prediction: com.example.domain.model.OutlookPrediction) {
        val aiState = _outlookAiState.value
        val provider = aiState.selectedProvider
        val effectiveModel = if (aiState.useCustomModel) {
            aiState.customModelInput.trim()
        } else {
            aiState.selectedModelId.trim()
        }
        val apiKey = aiState.apiKeyInput.trim()
            .ifBlank { outlookKeyForProvider(latestSettings, provider).trim() }

        if (apiKey.isEmpty()) {
            val msg = "Enter your ${provider.keyLabel} in the AI Analysis Engine card below to run ${provider.displayName}."
            outlookStatusMessage.value = msg
            _outlookAiState.update { it.copy(aiError = msg) }
            return
        }
        if (effectiveModel.isEmpty()) {
            val msg = "Select or enter a valid ${provider.displayName} model ID first."
            outlookStatusMessage.value = msg
            _outlookAiState.update { it.copy(aiError = msg) }
            return
        }

        persistOutlookAiChoice(provider, effectiveModel)
        val updatedSettings = withOutlookProviderKey(latestSettings, provider, apiKey)
        latestSettings = updatedSettings

        viewModelScope.launch {
            isOutlookLoading.value = true
            outlookStatusMessage.value = null
            _outlookAiState.update { it.copy(aiError = null, aiStatusMessage = null) }
            repository.saveSettings(updatedSettings)

            val probRisePct = (prediction.probGoldRise * 100).toInt()
            val probFallPct = (prediction.probGoldFall * 100).toInt()
            val indicatorText = macroIndicators.value.joinToString("; ") {
                "${it.name}: ${it.currentValue}${it.unit} (prev ${it.previousValue}${it.unit})"
            }.ifBlank { "No macro indicators cached" }
            val liveNewsText = goldNewsEvents.value.take(5).joinToString("; ") { ev ->
                val actualPart = ev.actual?.let { "act=$it" } ?: "upcoming"
                val fcstPart = ev.forecast?.let { "fcst=$it" } ?: ""
                "${ev.title} (${ev.impact}, $actualPart $fcstPart)".trim()
            }.ifBlank { "No weekly USD calendar catalysts loaded" }

            val systemPrompt = "You are an institutional quantitative macro analyst specializing in XAUUSD (spot gold) volatility around US Tier-1 macroeconomic releases. Be concise, objective, and never alter or invent quantitative probabilities."
            val userPrompt = """
                Summarize this quantitative statistical gold (XAUUSD) event outlook in 3-4 concise sentences without changing the model probabilities:
                Event: ${prediction.eventType.name} on ${prediction.releaseDate}
                Model Probabilities: $probRisePct% Buy Gold / $probFallPct% Sell Gold (${prediction.confidence.label}, sample size ${prediction.sampleSize})
                Statistical Rationale: ${prediction.reasoning}
                Cached Macro Inputs: $indicatorText
                This Week USD/Gold Catalysts: $liveNewsText
                Include a brief execution/slippage risk note.
            """.trimIndent()

            val result = withContext(Dispatchers.IO) {
                AiStrategyGenerator.generateTextCompletion(
                    provider = provider,
                    modelId = effectiveModel,
                    apiKey = apiKey,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    temperature = 0.2
                )
            }

            result
                .onSuccess { summary ->
                    outlookAiSummary.value = summary
                    val modelLabel = "${provider.displayName} • $effectiveModel"
                    _outlookAiState.update {
                        it.copy(
                            lastUsedModelLabel = modelLabel,
                            aiError = null,
                            aiStatusMessage = "Generated analysis via $modelLabel."
                        )
                    }
                }
                .onFailure { err ->
                    val errMsg = err.message ?: "Unknown AI error"
                    outlookStatusMessage.value = "AI summary failed: $errMsg"
                    _outlookAiState.update { it.copy(aiError = errMsg) }
                }
            isOutlookLoading.value = false
        }
    }

    // --- Local Persistent Vault (On-device uninstall/reinstall recovery) ---

    @OptIn(FlowPreview::class)
    private fun startLocalVaultLifecycle() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val initialSnap = repository.captureVaultSnapshot()
            var hasDataAfterStartup = VaultJsonCodec.hasMeaningfulData(initialSnap)

            if (!hasDataAfterStartup) {
                val readableJson = withContext(Dispatchers.IO) {
                    LocalVaultStorage.tryFindReadableVaultJson(app)
                }
                if (!readableJson.isNullOrBlank()) {
                    val summary = repository.restoreCompleteVaultJson(readableJson)
                    if (summary.success) {
                        val updatedSnap = repository.captureVaultSnapshot()
                        hasDataAfterStartup = VaultJsonCodec.hasMeaningfulData(updatedSnap)
                        if (hasDataAfterStartup) {
                            _vaultNoticeMessage.value = "Auto-restored Vault: ${summary.toUserMessage()}"
                        }
                    }
                }
            }

            _showFreshInstallRestorePrompt.value =
                !hasDataAfterStartup && !LocalVaultStorage.isRestorePromptDismissed(app)

            // Auto-sync Downloads/TradePulse/tradepulse_vault.json whenever user data/settings change
            combine(
                trades,
                strategies,
                pythonStrategies,
                bots,
                dailyReviews,
                settings
            ) { values -> values }
                .drop(1)
                .debounce(1500L)
                .collect {
                    val snap = repository.captureVaultSnapshot()
                    if (VaultJsonCodec.hasMeaningfulData(snap)) {
                        _showFreshInstallRestorePrompt.value = false
                        if (_autoVaultEnabled.value) {
                            persistVaultSnapshotInternal(app)
                        }
                    }
                }
        }
    }

    private suspend fun persistVaultSnapshotInternal(app: Application): Result<String> {
        val snap = repository.captureVaultSnapshot()
        if (!VaultJsonCodec.hasMeaningfulData(snap)) {
            return Result.failure(IllegalStateException("Add a trade, strategy, bot, or API key first before saving a Vault."))
        }
        val json = withContext(Dispatchers.Default) { VaultJsonCodec.encode(snap) }
        val result = withContext(Dispatchers.IO) { LocalVaultStorage.saveVaultSnapshot(app, json) }
        result.onSuccess {
            _lastVaultSavedAt.value = LocalVaultStorage.getLastSavedAt(app)
            _lastVaultLocation.value = LocalVaultStorage.getLastLocation(app)
        }
        return result
    }

    fun toggleAutoVault(enabled: Boolean) {
        val app = getApplication<Application>()
        LocalVaultStorage.setAutoVaultEnabled(app, enabled)
        _autoVaultEnabled.value = enabled
        if (enabled) {
            viewModelScope.launch {
                val snap = repository.captureVaultSnapshot()
                if (VaultJsonCodec.hasMeaningfulData(snap)) {
                    persistVaultSnapshotInternal(app)
                }
            }
        }
    }

    fun saveVaultNow(onComplete: (Result<String>) -> Unit) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val result = persistVaultSnapshotInternal(app)
            onComplete(result)
        }
    }

    fun exportVaultToUri(uri: Uri, onComplete: (Result<String>) -> Unit) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val json = repository.exportCompleteVaultJson()
            val writeResult = withContext(Dispatchers.IO) {
                LocalVaultStorage.writeUriText(app, uri, json)
            }
            onComplete(writeResult.map { "Vault file saved successfully." })
        }
    }

    fun restoreVaultFromUri(uri: Uri, onComplete: (VaultRestoreSummary) -> Unit) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val readResult = withContext(Dispatchers.IO) {
                LocalVaultStorage.readUriText(app, uri)
            }
            val summary = readResult.fold(
                onSuccess = { json -> repository.restoreCompleteVaultJson(json) },
                onFailure = { err ->
                    VaultRestoreSummary(
                        success = false,
                        errorMessage = err.message ?: "Unable to read selected Vault file."
                    )
                }
            )
            if (summary.success) {
                _showFreshInstallRestorePrompt.value = false
                LocalVaultStorage.setRestorePromptDismissed(app, false)
                if (_autoVaultEnabled.value) {
                    persistVaultSnapshotInternal(app)
                }
            }
            onComplete(summary)
        }
    }

    fun restoreVaultFromJson(json: String, onComplete: (VaultRestoreSummary) -> Unit) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val summary = repository.restoreCompleteVaultJson(json)
            if (summary.success) {
                _showFreshInstallRestorePrompt.value = false
                if (_autoVaultEnabled.value) {
                    persistVaultSnapshotInternal(app)
                }
            }
            onComplete(summary)
        }
    }

    fun dismissFreshInstallRestorePrompt() {
        LocalVaultStorage.setRestorePromptDismissed(getApplication(), true)
        _showFreshInstallRestorePrompt.value = false
    }

    fun clearVaultNotice() {
        _vaultNoticeMessage.value = null
    }
}
