package com.example.data.repository

import com.example.data.local.BotEntity
import com.example.data.local.BotVersionEntity
import com.example.data.local.DailyReviewEntity
import com.example.data.local.StrategyEntity
import com.example.data.local.TradeEntity
import com.example.data.local.TradingDatabase
import com.example.data.local.UserSettingsEntity
import com.example.domain.model.Bot
import com.example.domain.model.BotStatus
import com.example.domain.model.BotType
import com.example.domain.model.BotVersion
import com.example.domain.model.CurrencyCode
import com.example.domain.model.DailyReview
import com.example.domain.model.Emotion
import com.example.domain.model.Strategy
import com.example.domain.model.Trade
import com.example.domain.model.TradeDirection
import com.example.domain.model.TradeResult
import com.example.domain.model.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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

    // --- JSON Backup & Restore ---
    suspend fun exportFullBackupJson(trades: List<Trade>, strategies: List<Strategy>, bots: List<Bot>): String = withContext(Dispatchers.Default) {
        val root = JSONObject()
        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())

        val tradesArray = JSONArray()
        for (t in trades) {
            val obj = JSONObject()
            obj.put("date", t.date)
            obj.put("time", t.time)
            obj.put("result", t.result.name)
            obj.put("pnl", t.pnl)
            obj.put("instrument", t.instrument)
            obj.put("direction", t.direction.name)
            obj.put("entryPrice", t.entryPrice ?: JSONObject.NULL)
            obj.put("exitPrice", t.exitPrice ?: JSONObject.NULL)
            obj.put("stopLoss", t.stopLoss ?: JSONObject.NULL)
            obj.put("takeProfit", t.takeProfit ?: JSONObject.NULL)
            obj.put("lotSize", t.lotSize ?: JSONObject.NULL)
            obj.put("strategyName", t.strategyName)
            obj.put("botName", t.botName)
            obj.put("notes", t.notes)
            obj.put("emotion", t.emotion.name)
            obj.put("followedRules", t.followedRules)
            tradesArray.put(obj)
        }
        root.put("trades", tradesArray)

        val strategiesArray = JSONArray()
        for (s in strategies) {
            val obj = JSONObject()
            obj.put("name", s.name)
            obj.put("description", s.description)
            obj.put("checklist", JSONArray(s.checklist))
            obj.put("tags", JSONArray(s.tags))
            obj.put("colorHex", s.colorHex)
            strategiesArray.put(obj)
        }
        root.put("strategies", strategiesArray)

        val botsArray = JSONArray()
        for (b in bots) {
            val obj = JSONObject()
            obj.put("name", b.name)
            obj.put("description", b.description)
            obj.put("version", b.version)
            obj.put("type", b.type.name)
            obj.put("instrument", b.instrument)
            obj.put("timeframe", b.timeframe)
            obj.put("strategyName", b.strategyName)
            obj.put("status", b.status.name)
            obj.put("notes", b.notes)
            obj.put("code", b.code)
            obj.put("backtestNotes", b.backtestNotes)
            botsArray.put(obj)
        }
        root.put("bots", botsArray)

        root.toString(2)
    }

    suspend fun restoreFullBackupJson(jsonString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            if (root.has("strategies")) {
                val array = root.getJSONArray("strategies")
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val checklistJson = obj.optJSONArray("checklist")
                    val checklist = mutableListOf<String>()
                    if (checklistJson != null) {
                        for (k in 0 until checklistJson.length()) checklist.add(checklistJson.getString(k))
                    }
                    val tagsJson = obj.optJSONArray("tags")
                    val tags = mutableListOf<String>()
                    if (tagsJson != null) {
                        for (k in 0 until tagsJson.length()) tags.add(tagsJson.getString(k))
                    }
                    strategyDao.insertStrategy(
                        StrategyEntity(
                            name = obj.getString("name"),
                            description = obj.optString("description", ""),
                            checklist = checklist,
                            tags = tags,
                            colorHex = obj.optString("colorHex", "#10B981")
                        )
                    )
                }
            }

            if (root.has("bots")) {
                val array = root.getJSONArray("bots")
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    botDao.insertBot(
                        BotEntity(
                            name = obj.getString("name"),
                            description = obj.optString("description", ""),
                            version = obj.optString("version", "1.0.0"),
                            type = try { BotType.valueOf(obj.getString("type")) } catch (e: Exception) { BotType.EXPERT_ADVISOR },
                            instrument = obj.optString("instrument", "Universal"),
                            timeframe = obj.optString("timeframe", "H1"),
                            strategyId = null,
                            strategyName = obj.optString("strategyName", ""),
                            status = try { BotStatus.valueOf(obj.getString("status")) } catch (e: Exception) { BotStatus.TESTING },
                            tags = emptyList(),
                            notes = obj.optString("notes", ""),
                            code = obj.optString("code", ""),
                            backtestNotes = obj.optString("backtestNotes", ""),
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }

            if (root.has("trades")) {
                val array = root.getJSONArray("trades")
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    tradeDao.insertTrade(
                        TradeEntity(
                            date = obj.getString("date"),
                            time = obj.optString("time", "12:00"),
                            timestamp = System.currentTimeMillis(),
                            result = try { TradeResult.valueOf(obj.getString("result")) } catch (e: Exception) { TradeResult.WIN },
                            pnl = obj.getDouble("pnl"),
                            instrument = obj.getString("instrument"),
                            direction = try { TradeDirection.valueOf(obj.getString("direction")) } catch (e: Exception) { TradeDirection.BUY },
                            entryPrice = if (obj.has("entryPrice") && !obj.isNull("entryPrice")) obj.getDouble("entryPrice") else null,
                            exitPrice = if (obj.has("exitPrice") && !obj.isNull("exitPrice")) obj.getDouble("exitPrice") else null,
                            stopLoss = if (obj.has("stopLoss") && !obj.isNull("stopLoss")) obj.getDouble("stopLoss") else null,
                            takeProfit = if (obj.has("takeProfit") && !obj.isNull("takeProfit")) obj.getDouble("takeProfit") else null,
                            lotSize = if (obj.has("lotSize") && !obj.isNull("lotSize")) obj.getDouble("lotSize") else null,
                            riskRewardRatio = null,
                            strategyId = null,
                            strategyName = obj.optString("strategyName", ""),
                            botId = null,
                            botName = obj.optString("botName", ""),
                            notes = obj.optString("notes", ""),
                            emotion = try { Emotion.valueOf(obj.optString("emotion", "CALM")) } catch (e: Exception) { Emotion.CALM },
                            followedRules = obj.optBoolean("followedRules", true),
                            screenshotUri = null,
                            tags = emptyList()
                        )
                    )
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        tradeDao.deleteAllTrades()
        strategyDao.deleteAllStrategies()
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

    suspend fun fetchFinnhubUsCalendar(apiKey: String): Result<List<com.example.domain.model.NewsRelease>> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Finnhub API key is not configured."))
            }
            try {
                val client = okhttp3.OkHttpClient()
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val cal = java.util.Calendar.getInstance()
                cal.add(java.util.Calendar.DAY_OF_YEAR, -90)
                val from = sdf.format(cal.time)
                cal.add(java.util.Calendar.DAY_OF_YEAR, 120)
                val to = sdf.format(cal.time)

                val url = "https://finnhub.io/api/v1/calendar/economic?from=$from&to=$to&token=$apiKey"
                val req = okhttp3.Request.Builder().url(url).build()
                val body = client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("Finnhub HTTP ${resp.code}")
                    resp.body?.string() ?: throw IllegalStateException("Empty Finnhub response")
                }
                val calendarArr = JSONObject(body).optJSONArray("economicCalendar") ?: JSONArray()
                val releases = mutableListOf<com.example.domain.model.NewsRelease>()
                for (i in 0 until calendarArr.length()) {
                    val item = calendarArr.getJSONObject(i)
                    if (!item.optString("country", "").equals("US", ignoreCase = true)) continue
                    val eventName = item.optString("event", "")
                    val eventType = when {
                        eventName.contains("Non Farm Payrolls", ignoreCase = true) ||
                            eventName.contains("Nonfarm Payrolls", ignoreCase = true) -> com.example.domain.model.NewsEventType.NFP
                        eventName.contains("CPI", ignoreCase = true) && !eventName.contains("Core", ignoreCase = true) -> com.example.domain.model.NewsEventType.CPI
                        else -> null
                    } ?: continue

                    val forecast = item.optDouble("estimate", Double.NaN).takeIf { !it.isNaN() } ?: continue
                    val previous = item.optDouble("prev", Double.NaN).takeIf { !it.isNaN() } ?: 0.0
                    val actual = item.optDouble("actual", Double.NaN).takeIf { !it.isNaN() }
                    val timeStr = item.optString("time", "")
                    val datePart = timeStr.take(10).ifBlank { sdf.format(java.util.Date()) }
                    val ts = try {
                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).parse(timeStr)?.time
                            ?: sdf.parse(datePart)?.time
                            ?: System.currentTimeMillis()
                    } catch (_: Exception) {
                        System.currentTimeMillis()
                    }
                    releases.add(
                        com.example.domain.model.NewsRelease(
                            eventType = eventType,
                            dateString = datePart,
                            timestamp = ts,
                            forecast = forecast,
                            previous = previous,
                            actual = actual,
                            surprise = actual?.let { it - forecast },
                            unit = item.optString("unit", if (eventType == com.example.domain.model.NewsEventType.NFP) "K" else "%"),
                            isUpcoming = actual == null
                        )
                    )
                }
                Result.success(releases)
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
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
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
        pinLockEnabled = pinLockEnabled,
        pinCode = pinCode,
        eveningReminderEnabled = eveningReminderEnabled,
        reminderHour = reminderHour,
        reminderMinute = reminderMinute,
        isDarkTheme = isDarkTheme,
        fredApiKey = fredApiKey,
        finnhubApiKey = finnhubApiKey,
        geminiApiKey = geminiApiKey,
        isOutlookEnabled = isOutlookEnabled,
        newsAlertEnabled = newsAlertEnabled
    )

    private fun UserSettings.toEntity() = UserSettingsEntity(
        id = 1,
        currency = currency,
        monthlyProfitTarget = monthlyProfitTarget,
        maxDailyLossLimit = maxDailyLossLimit,
        pinLockEnabled = pinLockEnabled,
        pinCode = pinCode,
        eveningReminderEnabled = eveningReminderEnabled,
        reminderHour = reminderHour,
        reminderMinute = reminderMinute,
        isDarkTheme = isDarkTheme,
        fredApiKey = fredApiKey,
        finnhubApiKey = finnhubApiKey,
        geminiApiKey = geminiApiKey,
        isOutlookEnabled = isOutlookEnabled,
        newsAlertEnabled = newsAlertEnabled
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
}
