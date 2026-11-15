package com.example.data.repository

import com.example.backtest.SavedBacktestRun
import com.example.data.local.BacktestRunEntity
import com.example.data.local.BotEntity
import com.example.data.local.BotVersionEntity
import com.example.data.local.DailyReviewEntity
import com.example.data.local.PythonStrategyEntity
import com.example.data.local.StrategyEntity
import com.example.data.local.TradeEntity
import com.example.data.local.TradingDatabase
import com.example.data.local.UserSettingsEntity
import com.example.data.local.VaultJsonCodec
import com.example.data.local.VaultRestoreSummary
import com.example.data.local.VaultSnapshot
import com.example.domain.model.Bot
import com.example.domain.model.BotStatus
import com.example.domain.model.BotType
import com.example.domain.model.BotVersion
import com.example.domain.model.CurrencyCode
import com.example.domain.model.DailyReview
import com.example.domain.model.Emotion
import com.example.domain.model.PythonStrategy
import com.example.domain.model.Strategy
import com.example.domain.model.Trade
import com.example.domain.model.TradeDirection
import com.example.domain.model.TradeResult
import com.example.domain.model.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class TradingRepository(private val database: TradingDatabase) {
    private val tradeDao = database.tradeDao()
    private val strategyDao = database.strategyDao()
    private val botDao = database.botDao()
    private val botVersionDao = database.botVersionDao()
    private val reviewDao = database.dailyReviewDao()
    private val settingsDao = database.userSettingsDao()
    private val outlookDao = database.outlookDao()
    private val pythonStrategyDao = database.pythonStrategyDao()
    private val backtestRunDao = database.backtestRunDao()

    // --- News Outlook ---
    fun getAllReleases(): Flow<List<com.example.domain.model.NewsRelease>> = outlookDao.getAllReleases().map { list ->
        list.map { it.toDomain() }
    }

    fun getReleasesByType(type: com.example.domain.model.NewsEventType): Flow<List<com.example.domain.model.NewsRelease>> = outlookDao.getReleasesByType(type).map { list ->
        list.map { it.toDomain() }
    }

    suspend fun insertRelease(release: com.example.domain.model.NewsRelease): Long = withContext(Dispatchers.IO) {
        outlookDao.insertRelease(release.toEntity())
    }

    fun getAllPredictions(): Flow<List<com.example.domain.model.OutlookPrediction>> = outlookDao.getAllPredictions().map { list ->
        list.map { it.toDomain() }
    }

    fun getLatestPrediction(type: com.example.domain.model.NewsEventType): Flow<com.example.domain.model.OutlookPrediction?> = outlookDao.getLatestPrediction(type).map {
        it?.toDomain()
    }

    suspend fun savePrediction(prediction: com.example.domain.model.OutlookPrediction): Long = withContext(Dispatchers.IO) {
        outlookDao.insertPrediction(prediction.toEntity())
    }

    suspend fun updatePrediction(prediction: com.example.domain.model.OutlookPrediction) = withContext(Dispatchers.IO) {
        outlookDao.updatePrediction(prediction.toEntity())
    }

    suspend fun setPredictionTraded(predictionId: Long, traded: Boolean) = withContext(Dispatchers.IO) {
        // Look up and update
        // (will be called directly from ViewModel)
    }

    // --- Trades ---
    fun getAllTrades(): Flow<List<Trade>> = tradeDao.getAllTrades().map { list ->
        list.map { it.toDomain() }
    }

    fun getTradesByDate(date: String): Flow<List<Trade>> = tradeDao.getTradesByDate(date).map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getTradeById(id: Long): Trade? = withContext(Dispatchers.IO) {
        tradeDao.getTradeById(id)?.toDomain()
    }

    suspend fun insertTrade(trade: Trade): Long = withContext(Dispatchers.IO) {
        tradeDao.insertTrade(trade.toEntity())
    }

    suspend fun updateTrade(trade: Trade) = withContext(Dispatchers.IO) {
        tradeDao.updateTrade(trade.toEntity())
    }

    suspend fun deleteTrade(id: Long) = withContext(Dispatchers.IO) {
        tradeDao.deleteTradeById(id)
    }

    suspend fun insertAllTrades(trades: List<Trade>) = withContext(Dispatchers.IO) {
        if (trades.isNotEmpty()) {
            tradeDao.insertAllTrades(trades.map { it.toEntity() })
        }
    }

    // --- Strategies ---
    fun getAllStrategies(): Flow<List<Strategy>> = strategyDao.getAllStrategies().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getStrategyById(id: Long): Strategy? = withContext(Dispatchers.IO) {
        strategyDao.getStrategyById(id)?.toDomain()
    }

    suspend fun insertStrategy(strategy: Strategy): Long = withContext(Dispatchers.IO) {
        strategyDao.insertStrategy(strategy.toEntity())
    }

    suspend fun updateStrategy(strategy: Strategy) = withContext(Dispatchers.IO) {
        strategyDao.updateStrategy(strategy.toEntity())
    }

    suspend fun deleteStrategy(id: Long) = withContext(Dispatchers.IO) {
        strategyDao.deleteStrategyById(id)
    }

    // --- Python Backtest Strategies ---
    fun getAllPythonStrategies(): Flow<List<PythonStrategy>> = pythonStrategyDao.getAllPythonStrategies().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getPythonStrategyById(id: Long): PythonStrategy? = withContext(Dispatchers.IO) {
        pythonStrategyDao.getPythonStrategyById(id)?.toDomain()
    }

    suspend fun insertPythonStrategy(strategy: PythonStrategy): Long = withContext(Dispatchers.IO) {
        pythonStrategyDao.insertPythonStrategy(strategy.toEntity())
    }

    suspend fun updatePythonStrategy(strategy: PythonStrategy) = withContext(Dispatchers.IO) {
        pythonStrategyDao.updatePythonStrategy(strategy.toEntity())
    }

    suspend fun deletePythonStrategy(id: Long) = withContext(Dispatchers.IO) {
        pythonStrategyDao.deletePythonStrategyById(id)
    }

    // --- Saved Backtest Runs ---
    fun getAllBacktestRuns(): Flow<List<SavedBacktestRun>> = backtestRunDao.getAllRuns().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun saveBacktestRun(run: SavedBacktestRun): Long = withContext(Dispatchers.IO) {
        backtestRunDao.insertRun(run.toEntity())
    }

    suspend fun deleteBacktestRun(id: Long) = withContext(Dispatchers.IO) {
        backtestRunDao.deleteRunById(id)
    }

    // --- Bots ---
    fun getAllBots(): Flow<List<Bot>> = botDao.getAllBots().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getBotById(id: Long): Bot? = withContext(Dispatchers.IO) {
        botDao.getBotById(id)?.toDomain()
    }

    suspend fun insertBot(bot: Bot): Long = withContext(Dispatchers.IO) {
        botDao.insertBot(bot.toEntity())
    }

    suspend fun updateBot(bot: Bot) = withContext(Dispatchers.IO) {
        botDao.updateBot(bot.toEntity())
    }

    suspend fun deleteBot(id: Long) = withContext(Dispatchers.IO) {
        botDao.deleteBotById(id)
        botVersionDao.deleteVersionsByBotId(id)
    }

    fun getBotVersions(botId: Long): Flow<List<BotVersion>> = botVersionDao.getVersionsByBotId(botId).map { list ->
        list.map { it.toDomain() }
    }

    suspend fun addBotVersion(version: BotVersion): Long = withContext(Dispatchers.IO) {
        botVersionDao.insertVersion(version.toEntity())
    }

    // --- Daily Reviews ---
    fun getAllReviews(): Flow<List<DailyReview>> = reviewDao.getAllReviews().map { list ->
        list.map { it.toDomain() }
    }

    fun observeReviewByDate(date: String): Flow<DailyReview?> = reviewDao.observeReviewByDate(date).map {
        it?.toDomain()
    }

    suspend fun getReviewByDate(date: String): DailyReview? = withContext(Dispatchers.IO) {
        reviewDao.getReviewByDate(date)?.toDomain()
    }

    suspend fun saveReview(review: DailyReview) = withContext(Dispatchers.IO) {
        reviewDao.insertReview(review.toEntity())
    }

    // --- User Settings ---
    fun getSettings(): Flow<UserSettings> = settingsDao.getSettings().map {
        it?.toDomain() ?: UserSettings()
    }

    suspend fun getSettingsOnce(): UserSettings = withContext(Dispatchers.IO) {
        settingsDao.getSettingsOnce()?.toDomain() ?: UserSettings()
    }

    suspend fun saveSettings(settings: UserSettings) = withContext(Dispatchers.IO) {
        settingsDao.saveSettings(settings.toEntity())
    }

    // --- CSV Export ---
    suspend fun exportTradesToCsv(trades: List<Trade>): String = withContext(Dispatchers.Default) {
        val sb = StringBuilder()
        sb.append("ID,Date,Time,Instrument,Direction,Result,PnL,Strategy,Bot,EntryPrice,ExitPrice,StopLoss,TakeProfit,LotSize,RiskReward,Emotion,FollowedRules,Notes\n")
        for (t in trades) {
            val notesEscaped = "\"" + t.notes.replace("\"", "\"\"") + "\""
            sb.append("${t.id},${t.date},${t.time},${t.instrument},${t.direction.name},${t.result.name},${t.pnl},${t.strategyName},${t.botName},${t.entryPrice ?: ""},${t.exitPrice ?: ""},${t.stopLoss ?: ""},${t.takeProfit ?: ""},${t.lotSize ?: ""},${t.riskRewardRatio ?: ""},${t.emotion.name},${t.followedRules},$notesEscaped\n")
        }
        sb.toString()
    }

    // --- Complete Local Vault JSON Backup & Restore (V2 + V1 compatible) ---
    suspend fun captureVaultSnapshot(): VaultSnapshot = withContext(Dispatchers.IO) {
        val currentSettings = settingsDao.getSettingsOnce() ?: UserSettings().toEntity()
        VaultSnapshot(
            settings = currentSettings,
            trades = tradeDao.getAllTrades().first(),
            strategies = strategyDao.getAllStrategies().first(),
            pythonStrategies = pythonStrategyDao.getAllPythonStrategies().first(),
            bots = botDao.getAllBots().first(),
            dailyReviews = reviewDao.getAllReviews().first(),
            backtestRuns = backtestRunDao.getAllRuns().first()
        )
    }

    suspend fun exportCompleteVaultJson(): String = withContext(Dispatchers.Default) {
        val snapshot = captureVaultSnapshot()
        VaultJsonCodec.encode(snapshot)
    }

    suspend fun exportFullBackupJson(
        trades: List<Trade>,
        strategies: List<Strategy>,
        bots: List<Bot>
    ): String = exportCompleteVaultJson()

    suspend fun restoreCompleteVaultJson(jsonString: String): VaultRestoreSummary = withContext(Dispatchers.IO) {
        try {
            val currentSettings = settingsDao.getSettingsOnce() ?: UserSettings().toEntity()
            val payload = VaultJsonCodec.decode(jsonString, currentSettings)

            var settingsRestored = false
            var apiKeysCount = 0
            if (payload.hasSettingsSection && payload.settings != null) {
                settingsDao.saveSettings(payload.settings)
                settingsRestored = true
                apiKeysCount = VaultJsonCodec.countConfiguredApiKeys(payload.settings)
            }

            val existingStrategyNames = strategyDao.getAllStrategies().first()
                .map { it.name.trim().lowercase(java.util.Locale.US) }
                .toHashSet()
            var strategiesRestored = 0
            for (s in payload.strategies) {
                val key = s.name.trim().lowercase(java.util.Locale.US)
                if (key.isNotEmpty() && existingStrategyNames.add(key)) {
                    strategyDao.insertStrategy(s)
                    strategiesRestored++
                }
            }

            val existingPyKeys = pythonStrategyDao.getAllPythonStrategies().first()
                .map { "${it.name.trim()}::${it.code.trim()}" }
                .toHashSet()
            var pyRestored = 0
            for (py in payload.pythonStrategies) {
                val key = "${py.name.trim()}::${py.code.trim()}"
                if (existingPyKeys.add(key)) {
                    pythonStrategyDao.insertPythonStrategy(py)
                    pyRestored++
                }
            }

            val existingBotNames = botDao.getAllBots().first()
                .map { it.name.trim().lowercase(java.util.Locale.US) }
                .toHashSet()
            var botsRestored = 0
            for (b in payload.bots) {
                val key = b.name.trim().lowercase(java.util.Locale.US)
                if (key.isNotEmpty() && existingBotNames.add(key)) {
                    botDao.insertBot(b)
                    botsRestored++
                }
            }

            val existingTradeKeys = tradeDao.getAllTrades().first()
                .map { "${it.date}|${it.time}|${it.instrument}|${it.direction.name}|${it.pnl}" }
                .toHashSet()
            var tradesRestored = 0
            for (t in payload.trades) {
                val key = "${t.date}|${t.time}|${t.instrument}|${t.direction.name}|${t.pnl}"
                if (existingTradeKeys.add(key)) {
                    tradeDao.insertTrade(t)
                    tradesRestored++
                }
            }

            val existingReviewDates = reviewDao.getAllReviews().first()
                .map { it.date }
                .toHashSet()
            var reviewsRestored = 0
            for (r in payload.dailyReviews) {
                if (existingReviewDates.add(r.date)) {
                    reviewDao.insertReview(r)
                    reviewsRestored++
                }
            }

            val existingRunKeys = backtestRunDao.getAllRuns().first()
                .map { "${it.strategyName}|${it.csvFileName}|${it.totalTrades}|${it.netPoints}" }
                .toHashSet()
            var runsRestored = 0
            for (run in payload.backtestRuns) {
                val key = "${run.strategyName}|${run.csvFileName}|${run.totalTrades}|${run.netPoints}"
                if (existingRunKeys.add(key)) {
                    backtestRunDao.insertRun(run)
                    runsRestored++
                }
            }

            VaultRestoreSummary(
                success = true,
                schemaVersion = payload.schemaVersion,
                settingsRestored = settingsRestored,
                apiKeysRestoredCount = apiKeysCount,
                tradesRestored = tradesRestored,
                strategiesRestored = strategiesRestored,
                pythonStrategiesRestored = pyRestored,
                botsRestored = botsRestored,
                reviewsRestored = reviewsRestored,
                backtestRunsRestored = runsRestored
            )
        } catch (e: Exception) {
            e.printStackTrace()
            VaultRestoreSummary(
                success = false,
                errorMessage = e.message ?: "Invalid TradePulse Vault JSON format."
            )
        }
    }

    suspend fun restoreFullBackupJson(jsonString: String): Boolean {
        return restoreCompleteVaultJson(jsonString).success
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        tradeDao.deleteAllTrades()
        strategyDao.deleteAllStrategies()
        pythonStrategyDao.deleteAllPythonStrategies()
        backtestRunDao.deleteAllRuns()
        botDao.deleteAllBots()
        reviewDao.deleteAllReviews()
        outlookDao.deleteAllReleases()
        outlookDao.deleteAllPredictions()
    }

    suspend fun fetchFredMacroIndicators(apiKey: String): Result<List<com.example.domain.model.MacroIndicator>> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("FRED API key is not configured."))
            }
            try {
                val client = okhttp3.OkHttpClient()
                data class SeriesSpec(
                    val id: String,
                    val fredId: String,
                    val name: String,
                    val unit: String,
                    val higherIsBullishGold: Boolean,
                    val upText: String,
                    val downText: String
                )
                val specs = listOf(
                    SeriesSpec("claims", "IC4WSA", "Weekly Jobless Claims", "K", true, "Rising claims -> Dovish Fed -> Bullish Gold", "Tight claims -> Resilient labor -> Bearish Gold"),
                    SeriesSpec("unrate", "UNRATE", "Unemployment Rate", "%", true, "Rising unemployment -> Dovish Fed -> Bullish Gold", "Lower unemployment -> Hawkish Fed -> Bearish Gold"),
                    SeriesSpec("wages", "CES0500000003", "Avg Hourly Earnings", "$", false, "Rising wages -> Sticky inflation -> Bearish Gold", "Cooling wages -> Dovish Fed -> Bullish Gold"),
                    SeriesSpec("jolts", "JTSJOL", "JOLTS Job Openings", "K", false, "Higher openings -> Tight labor -> Bearish Gold", "Fewer openings -> Softening labor -> Bullish Gold"),
                    SeriesSpec("cpi_core", "CPILFESL", "Core CPI Index", "idx", false, "Rising core inflation -> Hawkish Fed -> Bearish Gold", "Cooling core inflation -> Bullish Gold")
                )

                val results = mutableListOf<com.example.domain.model.MacroIndicator>()
                for (spec in specs) {
                    val url = "https://api.stlouisfed.org/fred/series/observations?series_id=${spec.fredId}&api_key=$apiKey&file_type=json&sort_order=desc&limit=5"
                    val req = okhttp3.Request.Builder().url(url).build()
                    val body = client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw IllegalStateException("FRED HTTP ${resp.code}")
                        resp.body?.string() ?: throw IllegalStateException("Empty FRED response")
                    }
                    val obs = JSONObject(body).optJSONArray("observations") ?: continue
                    val numericValues = mutableListOf<Double>()
                    for (i in 0 until obs.length()) {
                        val valStr = obs.getJSONObject(i).optString("value", ".")
                        val parsed = valStr.toDoubleOrNull()
                        if (parsed != null) numericValues.add(parsed)
                        if (numericValues.size >= 2) break
                    }
                    if (numericValues.isNotEmpty()) {
                        val current = numericValues[0]
                        val previous = numericValues.getOrElse(1) { current }
                        val trend = when {
                            current > previous -> com.example.domain.model.TrendDirection.UP
                            current < previous -> com.example.domain.model.TrendDirection.DOWN
                            else -> com.example.domain.model.TrendDirection.FLAT
                        }
                        val bullish = when (trend) {
                            com.example.domain.model.TrendDirection.UP -> spec.higherIsBullishGold
                            com.example.domain.model.TrendDirection.DOWN -> !spec.higherIsBullishGold
                            com.example.domain.model.TrendDirection.FLAT -> false
                        }
                        val impact = when (trend) {
                            com.example.domain.model.TrendDirection.UP -> spec.upText
                            com.example.domain.model.TrendDirection.DOWN -> spec.downText
                            com.example.domain.model.TrendDirection.FLAT -> "Unchanged vs previous print"
                        }
                        results.add(
                            com.example.domain.model.MacroIndicator(
                                id = spec.id,
                                name = spec.name,
                                currentValue = current,
                                previousValue = previous,
                                unit = spec.unit,
                                trend = trend,
                                impactOnGold = impact,
                                isBullishForGold = bullish
                            )
                        )
                    }
                }
                Result.success(results)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun fetchXoomarEconomicCalendar(): Result<List<com.example.domain.model.NewsRelease>> =
        withContext(Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder()
                    .url("https://xoomar.com/api/markets/calendar?importance=high")
                    .header("Accept", "application/json")
                    .build()
                val body = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IllegalStateException("XOOMAR HTTP ${response.code}")
                    response.body?.string() ?: throw IllegalStateException("Empty XOOMAR response")
                }
                val events = JSONObject(body).optJSONArray("data") ?: JSONArray()
                val releases = mutableListOf<com.example.domain.model.NewsRelease>()
                for (i in 0 until events.length()) {
                    val item = events.optJSONObject(i) ?: continue
                    val eventName = item.optString("eventName", "")
                    val eventType = when {
                        eventName.contains("nonfarm payroll", ignoreCase = true) ||
                            eventName.contains("non-farm payroll", ignoreCase = true) ->
                            com.example.domain.model.NewsEventType.NFP
                        eventName.contains("CPI", ignoreCase = true) &&
                            !eventName.contains("Core", ignoreCase = true) ->
                            com.example.domain.model.NewsEventType.CPI
                        else -> null
                    } ?: continue
                    val scheduledAt = item.optString("scheduledAt", "")
                    val timestamp = try {
                        java.time.Instant.parse(scheduledAt).toEpochMilli()
                    } catch (_: Exception) {
                        continue
                    }
                    val datePart = java.time.Instant.ofEpochMilli(timestamp)
                        .atZone(java.time.ZoneOffset.UTC)
                        .toLocalDate()
                        .toString()
                    val previous = item.optString("previous", "")
                        .replace(",", "")
                        .toDoubleOrNull() ?: 0.0
                    val actual = item.optString("actual", "")
                        .replace(",", "")
                        .toDoubleOrNull()
                    val unit = item.optString("unit", "").ifBlank {
                        if (eventType == com.example.domain.model.NewsEventType.NFP) "K" else "%"
                    }
                    releases.add(
                        com.example.domain.model.NewsRelease(
                            eventType = eventType,
                            dateString = datePart,
                            timestamp = timestamp,
                            forecast = 0.0,
                            previous = previous,
                            actual = actual,
                            surprise = null,
                            unit = unit,
                            isUpcoming = actual == null
                        )
                    )
                }
                Result.success(releases.distinctBy { it.eventType to it.timestamp })
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun insertAllReleases(releases: List<com.example.domain.model.NewsRelease>) = withContext(Dispatchers.IO) {
        outlookDao.insertAllReleases(releases.map { it.toEntity() })
    }

    suspend fun requestGeminiSummary(apiKey: String, prompt: String): Result<String> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Gemini API key is not configured."))
            }
            try {
                val client = okhttp3.OkHttpClient()
                val payload = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().put("text", prompt))
                            })
                        })
                    })
                }
                val mediaType = okhttp3.MediaType.Companion.run { "application/json; charset=utf-8".toMediaType() }
                val reqBody = okhttp3.RequestBody.Companion.run { payload.toString().toRequestBody(mediaType) }
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=$apiKey"
                val req = okhttp3.Request.Builder().url(url).post(reqBody).build()
                val body = client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("Gemini HTTP ${resp.code}")
                    resp.body?.string() ?: throw IllegalStateException("Empty Gemini response")
                }
                val text = JSONObject(body)
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                Result.success(text.trim())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // --- Entity Mappers ---
    private fun TradeEntity.toDomain() = Trade(
        id = id,
        date = date,
        time = time,
        timestamp = timestamp,
        result = result,
        pnl = pnl,
        instrument = instrument,
        direction = direction,
        entryPrice = entryPrice,
        exitPrice = exitPrice,
        stopLoss = stopLoss,
        takeProfit = takeProfit,
        lotSize = lotSize,
        riskRewardRatio = riskRewardRatio,
        strategyId = strategyId,
        strategyName = strategyName,
        botId = botId,
        botName = botName,
        notes = notes,
        emotion = emotion,
        followedRules = followedRules,
        screenshotUri = screenshotUri,
        isNewsTrade = isNewsTrade,
        tags = tags
    )

    private fun Trade.toEntity() = TradeEntity(
        id = id,
        date = date,
        time = time,
        timestamp = timestamp,
        result = result,
        pnl = pnl,
        instrument = instrument,
        direction = direction,
        entryPrice = entryPrice,
        exitPrice = exitPrice,
        stopLoss = stopLoss,
        takeProfit = takeProfit,
        lotSize = lotSize,
        riskRewardRatio = riskRewardRatio,
        strategyId = strategyId,
        strategyName = strategyName,
        botId = botId,
        botName = botName,
        notes = notes,
        emotion = emotion,
        followedRules = followedRules,
        screenshotUri = screenshotUri,
        isNewsTrade = isNewsTrade,
        tags = tags
    )

    private fun StrategyEntity.toDomain() = Strategy(
        id = id,
        name = name,
        description = description,
        checklist = checklist,
        tags = tags,
        colorHex = colorHex
    )

    private fun Strategy.toEntity() = StrategyEntity(
        id = id,
        name = name,
        description = description,
        checklist = checklist,
        tags = tags,
        colorHex = colorHex
    )

    private fun BotEntity.toDomain() = Bot(
        id = id,
        name = name,
        description = description,
        version = version,
        type = type,
        instrument = instrument,
        timeframe = timeframe,
        strategyId = strategyId,
        strategyName = strategyName,
        status = status,
        tags = tags,
        notes = notes,
        code = code,
        backtestNotes = backtestNotes,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun Bot.toEntity() = BotEntity(
        id = id,
        name = name,
        description = description,
        version = version,
        type = type,
        instrument = instrument,
        timeframe = timeframe,
        strategyId = strategyId,
        strategyName = strategyName,
        status = status,
        tags = tags,
        notes = notes,
        code = code,
        backtestNotes = backtestNotes,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun BotVersionEntity.toDomain() = BotVersion(
        id = id,
        botId = botId,
        version = version,
        code = code,
        changeNote = changeNote,
        timestamp = timestamp
    )

    private fun BotVersion.toEntity() = BotVersionEntity(
        id = id,
        botId = botId,
        version = version,
        code = code,
        changeNote = changeNote,
        timestamp = timestamp
    )

    private fun DailyReviewEntity.toDomain() = DailyReview(
        date = date,
        disciplineRating = disciplineRating,
        reflection = reflection,
        lessonsLearned = lessonsLearned,
        followedAllRules = followedAllRules,
        timestamp = timestamp
    )

    private fun DailyReview.toEntity() = DailyReviewEntity(
        date = date,
        disciplineRating = disciplineRating,
        reflection = reflection,
        lessonsLearned = lessonsLearned,
        followedAllRules = followedAllRules,
        timestamp = timestamp
    )

    private fun UserSettingsEntity.toDomain() = UserSettings(
        currency = currency,
        monthlyProfitTarget = monthlyProfitTarget,
        maxDailyLossLimit = maxDailyLossLimit,
        maxTrailingDrawdown = maxTrailingDrawdown,
        pinLockEnabled = pinLockEnabled,
        pinCode = pinCode,
        eveningReminderEnabled = eveningReminderEnabled,
        reminderHour = reminderHour,
        reminderMinute = reminderMinute,
        isDarkTheme = isDarkTheme,
        themePreset = themePreset,
        fredApiKey = fredApiKey,
        finnhubApiKey = finnhubApiKey,
        geminiApiKey = geminiApiKey,
        claudeApiKey = claudeApiKey,
        openAiApiKey = openAiApiKey,
        backtestAiProvider = backtestAiProvider,
        backtestAiModel = backtestAiModel,
        isOutlookEnabled = isOutlookEnabled,
        newsAlertEnabled = newsAlertEnabled,
        bottomNavStyle = bottomNavStyle
    )

    private fun UserSettings.toEntity() = UserSettingsEntity(
        id = 1,
        currency = currency,
        monthlyProfitTarget = monthlyProfitTarget,
        maxDailyLossLimit = maxDailyLossLimit,
        maxTrailingDrawdown = maxTrailingDrawdown,
        pinLockEnabled = pinLockEnabled,
        pinCode = pinCode,
        eveningReminderEnabled = eveningReminderEnabled,
        reminderHour = reminderHour,
        reminderMinute = reminderMinute,
        isDarkTheme = isDarkTheme,
        themePreset = themePreset,
        fredApiKey = fredApiKey,
        finnhubApiKey = finnhubApiKey,
        geminiApiKey = geminiApiKey,
        claudeApiKey = claudeApiKey,
        openAiApiKey = openAiApiKey,
        backtestAiProvider = backtestAiProvider,
        backtestAiModel = backtestAiModel,
        isOutlookEnabled = isOutlookEnabled,
        newsAlertEnabled = newsAlertEnabled,
        bottomNavStyle = bottomNavStyle
    )

    private fun com.example.data.local.NewsReleaseEntity.toDomain() = com.example.domain.model.NewsRelease(
        id = id,
        eventType = eventType,
        dateString = dateString,
        timestamp = timestamp,
        forecast = forecast,
        previous = previous,
        actual = actual,
        surprise = surprise,
        unit = unit,
        gold15mDelta = gold15mDelta,
        gold60mDelta = gold60mDelta,
        isUpcoming = isUpcoming
    )

    private fun com.example.domain.model.NewsRelease.toEntity() = com.example.data.local.NewsReleaseEntity(
        id = id,
        eventType = eventType,
        dateString = dateString,
        timestamp = timestamp,
        forecast = forecast,
        previous = previous,
        actual = actual,
        surprise = surprise,
        unit = unit,
        gold15mDelta = gold15mDelta,
        gold60mDelta = gold60mDelta,
        isUpcoming = isUpcoming
    )

    private fun com.example.data.local.OutlookPredictionEntity.toDomain() = com.example.domain.model.OutlookPrediction(
        id = id,
        eventType = eventType,
        releaseDate = releaseDate,
        releaseTimestamp = releaseTimestamp,
        probGoldRise = probGoldRise,
        probGoldFall = probGoldFall,
        confidence = confidence,
        timeWindow = timeWindow,
        sampleSize = sampleSize,
        reasoning = reasoning,
        actualOutcome = actualOutcome,
        actualGoldDelta = actualGoldDelta,
        wasCorrect = wasCorrect,
        userTradedRelease = userTradedRelease
    )

    private fun com.example.domain.model.OutlookPrediction.toEntity() = com.example.data.local.OutlookPredictionEntity(
        id = id,
        eventType = eventType,
        releaseDate = releaseDate,
        releaseTimestamp = releaseTimestamp,
        probGoldRise = probGoldRise,
        probGoldFall = probGoldFall,
        confidence = confidence,
        timeWindow = timeWindow,
        sampleSize = sampleSize,
        reasoning = reasoning,
        actualOutcome = actualOutcome,
        actualGoldDelta = actualGoldDelta,
        wasCorrect = wasCorrect,
        userTradedRelease = userTradedRelease
    )

    private fun PythonStrategyEntity.toDomain() = PythonStrategy(
        id = id,
        name = name,
        className = className,
        code = code,
        updatedAt = updatedAt
    )

    private fun PythonStrategy.toEntity() = PythonStrategyEntity(
        id = id,
        name = name,
        className = className,
        code = code,
        updatedAt = updatedAt
    )

    private fun BacktestRunEntity.toDomain() = SavedBacktestRun(
        id = id,
        strategyName = strategyName,
        csvFileName = csvFileName,
        maxStopPoints = maxStopPoints,
        pointSize = pointSize,
        totalTrades = totalTrades,
        wins = wins,
        losses = losses,
        winRatePct = winRatePct,
        netPoints = netPoints,
        netR = netR,
        avgR = avgR,
        profitFactor = profitFactor,
        maxDrawdownPoints = maxDrawdownPoints,
        maxDrawdownR = maxDrawdownR,
        equityCurve = equityCurveCsv.split(",").mapNotNull { it.trim().toDoubleOrNull() },
        createdAt = createdAt
    )

    private fun SavedBacktestRun.toEntity() = BacktestRunEntity(
        id = id,
        strategyName = strategyName,
        csvFileName = csvFileName,
        maxStopPoints = maxStopPoints,
        pointSize = pointSize,
        totalTrades = totalTrades,
        wins = wins,
        losses = losses,
        winRatePct = winRatePct,
        netPoints = netPoints,
        netR = netR,
        avgR = avgR,
        profitFactor = profitFactor,
        maxDrawdownPoints = maxDrawdownPoints,
        maxDrawdownR = maxDrawdownR,
        equityCurveCsv = equityCurve.joinToString(","),
        createdAt = createdAt
    )
}
