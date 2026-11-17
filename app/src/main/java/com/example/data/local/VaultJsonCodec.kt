package com.example.data.local

import com.example.domain.model.AppThemePreset
import com.example.domain.model.BotStatus
import com.example.domain.model.BotType
import com.example.domain.model.BottomNavStyle
import com.example.domain.model.CurrencyCode
import com.example.domain.model.Emotion
import com.example.domain.model.TradeDirection
import com.example.domain.model.TradeResult
import org.json.JSONArray
import org.json.JSONObject

data class VaultSnapshot(
    val settings: UserSettingsEntity,
    val trades: List<TradeEntity>,
    val strategies: List<StrategyEntity>,
    val pythonStrategies: List<PythonStrategyEntity>,
    val bots: List<BotEntity>,
    val dailyReviews: List<DailyReviewEntity>,
    val backtestRuns: List<BacktestRunEntity>,
    val accountProfiles: List<AccountProfileEntity> = emptyList()
)

data class ParsedVaultPayload(
    val schemaVersion: Int,
    val exportedAt: Long,
    val hasSettingsSection: Boolean,
    val settings: UserSettingsEntity?,
    val trades: List<TradeEntity>,
    val strategies: List<StrategyEntity>,
    val pythonStrategies: List<PythonStrategyEntity>,
    val bots: List<BotEntity>,
    val dailyReviews: List<DailyReviewEntity>,
    val backtestRuns: List<BacktestRunEntity>,
    val accountProfiles: List<AccountProfileEntity> = emptyList(),
    val activeAccountProfileName: String = ""
)

data class VaultRestoreSummary(
    val success: Boolean,
    val schemaVersion: Int = 2,
    val settingsRestored: Boolean = false,
    val apiKeysRestoredCount: Int = 0,
    val tradesRestored: Int = 0,
    val strategiesRestored: Int = 0,
    val pythonStrategiesRestored: Int = 0,
    val botsRestored: Int = 0,
    val reviewsRestored: Int = 0,
    val backtestRunsRestored: Int = 0,
    val accountProfilesRestored: Int = 0,
    val errorMessage: String? = null
) {
    fun toUserMessage(): String {
        if (!success) return errorMessage ?: "Invalid TradePulse Vault file."
        val parts = mutableListOf<String>()
        if (apiKeysRestoredCount > 0) parts.add("$apiKeysRestoredCount API key(s)")
        if (settingsRestored) parts.add("settings & theme")
        if (accountProfilesRestored > 0) parts.add("$accountProfilesRestored account profile(s)")
        if (tradesRestored > 0) parts.add("$tradesRestored trade(s)")
        if (strategiesRestored > 0) parts.add("$strategiesRestored checklist(s)")
        if (pythonStrategiesRestored > 0) parts.add("$pythonStrategiesRestored .py strategy(s)")
        if (botsRestored > 0) parts.add("$botsRestored bot(s)")
        if (reviewsRestored > 0) parts.add("$reviewsRestored review(s)")
        if (backtestRunsRestored > 0) parts.add("$backtestRunsRestored backtest(s)")
        return if (parts.isEmpty()) {
            "Vault verified — all records were already up to date."
        } else {
            "Restored " + parts.joinToString(", ") + "."
        }
    }
}

object VaultJsonCodec {

    const val CURRENT_VERSION = 3

    fun countConfiguredApiKeys(settings: UserSettingsEntity): Int {
        return listOf(
            settings.fredApiKey,
            settings.finnhubApiKey,
            settings.geminiApiKey,
            settings.claudeApiKey,
            settings.openAiApiKey
        ).count { it.isNotBlank() }
    }

    fun hasMeaningfulData(snapshot: VaultSnapshot): Boolean {
        return snapshot.trades.isNotEmpty() ||
            snapshot.strategies.isNotEmpty() ||
            snapshot.pythonStrategies.isNotEmpty() ||
            snapshot.bots.isNotEmpty() ||
            snapshot.dailyReviews.isNotEmpty() ||
            snapshot.backtestRuns.isNotEmpty() ||
            snapshot.accountProfiles.isNotEmpty() ||
            snapshot.settings.testerLotSize != 0.10 ||
            snapshot.settings.testerLeverage != 100 ||
            countConfiguredApiKeys(snapshot.settings) > 0
    }

    fun encode(snapshot: VaultSnapshot, timestamp: Long = System.currentTimeMillis()): String {
        val root = JSONObject()
        root.put("version", CURRENT_VERSION)
        root.put("app", "TradePulse")
        root.put("timestamp", timestamp)

        // Settings & API keys & Active Tester Profile overrides
        val s = snapshot.settings
        val activeProfileName = snapshot.accountProfiles
            .firstOrNull { it.id == s.activeAccountProfileId }
            ?.name
            .orEmpty()
        val settingsObj = JSONObject().apply {
            put("currency", s.currency.name)
            put("monthlyProfitTarget", s.monthlyProfitTarget)
            put("maxDailyLossLimit", s.maxDailyLossLimit)
            put("maxTrailingDrawdown", s.maxTrailingDrawdown)
            put("pinLockEnabled", s.pinLockEnabled)
            put("pinCode", s.pinCode)
            put("eveningReminderEnabled", s.eveningReminderEnabled)
            put("reminderHour", s.reminderHour)
            put("reminderMinute", s.reminderMinute)
            put("isDarkTheme", s.isDarkTheme)
            put("themePreset", s.themePreset.name)
            put("bottomNavStyle", s.bottomNavStyle.name)
            put("isOutlookEnabled", s.isOutlookEnabled)
            put("newsAlertEnabled", s.newsAlertEnabled)
            put("fredApiKey", s.fredApiKey)
            put("finnhubApiKey", s.finnhubApiKey)
            put("geminiApiKey", s.geminiApiKey)
            put("claudeApiKey", s.claudeApiKey)
            put("openAiApiKey", s.openAiApiKey)
            put("backtestAiProvider", s.backtestAiProvider)
            put("backtestAiModel", s.backtestAiModel)
            put("activeAccountProfileId", s.activeAccountProfileId)
            put("activeAccountProfileName", activeProfileName)
            put("testerLotSize", s.testerLotSize)
            put("testerLeverage", s.testerLeverage)
        }
        root.put("settings", settingsObj)

        // Custom Account Profiles
        val profilesArray = JSONArray()
        for (p in snapshot.accountProfiles) {
            profilesArray.put(
                JSONObject().apply {
                    put("name", p.name)
                    put("accountCategory", p.accountCategory)
                    put("startingBalance", p.startingBalance)
                    put("defaultLotSize", p.defaultLotSize)
                    put("leverage", p.leverage)
                    put("commissionPerLot", p.commissionPerLot)
                    put("valuePerPointPerLot", p.valuePerPointPerLot)
                    put("contractSizePerLot", p.contractSizePerLot)
                    put("stopOutLevelPct", p.stopOutLevelPct)
                    put("instrumentLabel", p.instrumentLabel)
                    put("notes", p.notes)
                    put("createdAt", p.createdAt)
                    put("updatedAt", p.updatedAt)
                }
            )
        }
        root.put("accountProfiles", profilesArray)

        // Trades
        val tradesArray = JSONArray()
        for (t in snapshot.trades) {
            tradesArray.put(
                JSONObject().apply {
                    put("date", t.date)
                    put("time", t.time)
                    put("timestamp", t.timestamp)
                    put("result", t.result.name)
                    put("pnl", t.pnl)
                    put("instrument", t.instrument)
                    put("direction", t.direction.name)
                    put("entryPrice", t.entryPrice ?: JSONObject.NULL)
                    put("exitPrice", t.exitPrice ?: JSONObject.NULL)
                    put("stopLoss", t.stopLoss ?: JSONObject.NULL)
                    put("takeProfit", t.takeProfit ?: JSONObject.NULL)
                    put("lotSize", t.lotSize ?: JSONObject.NULL)
                    put("riskRewardRatio", t.riskRewardRatio ?: JSONObject.NULL)
                    put("strategyName", t.strategyName)
                    put("botName", t.botName)
                    put("notes", t.notes)
                    put("emotion", t.emotion.name)
                    put("followedRules", t.followedRules)
                    put("isNewsTrade", t.isNewsTrade)
                    put("tags", JSONArray(t.tags))
                }
            )
        }
        root.put("trades", tradesArray)

        // Checklist Strategies
        val strategiesArray = JSONArray()
        for (strat in snapshot.strategies) {
            strategiesArray.put(
                JSONObject().apply {
                    put("name", strat.name)
                    put("description", strat.description)
                    put("checklist", JSONArray(strat.checklist))
                    put("tags", JSONArray(strat.tags))
                    put("colorHex", strat.colorHex)
                }
            )
        }
        root.put("strategies", strategiesArray)

        // Python Backtest Strategies
        val pyArray = JSONArray()
        for (py in snapshot.pythonStrategies) {
            pyArray.put(
                JSONObject().apply {
                    put("name", py.name)
                    put("className", py.className)
                    put("code", py.code)
                    put("updatedAt", py.updatedAt)
                }
            )
        }
        root.put("pythonStrategies", pyArray)

        // Bots
        val botsArray = JSONArray()
        for (b in snapshot.bots) {
            botsArray.put(
                JSONObject().apply {
                    put("name", b.name)
                    put("description", b.description)
                    put("version", b.version)
                    put("type", b.type.name)
                    put("instrument", b.instrument)
                    put("timeframe", b.timeframe)
                    put("strategyName", b.strategyName)
                    put("status", b.status.name)
                    put("tags", JSONArray(b.tags))
                    put("notes", b.notes)
                    put("code", b.code)
                    put("backtestNotes", b.backtestNotes)
                    put("createdAt", b.createdAt)
                    put("updatedAt", b.updatedAt)
                }
            )
        }
        root.put("bots", botsArray)

        // Daily Reviews
        val reviewsArray = JSONArray()
        for (r in snapshot.dailyReviews) {
            reviewsArray.put(
                JSONObject().apply {
                    put("date", r.date)
                    put("disciplineRating", r.disciplineRating)
                    put("reflection", r.reflection)
                    put("lessonsLearned", r.lessonsLearned)
                    put("followedAllRules", r.followedAllRules)
                    put("timestamp", r.timestamp)
                }
            )
        }
        root.put("dailyReviews", reviewsArray)

        // Saved Backtest Runs
        val runsArray = JSONArray()
        for (run in snapshot.backtestRuns) {
            runsArray.put(
                JSONObject().apply {
                    put("strategyName", run.strategyName)
                    put("csvFileName", run.csvFileName)
                    put("maxStopPoints", run.maxStopPoints)
                    put("pointSize", run.pointSize)
                    put("totalTrades", run.totalTrades)
                    put("wins", run.wins)
                    put("losses", run.losses)
                    put("winRatePct", run.winRatePct)
                    put("netPoints", run.netPoints)
                    put("netR", run.netR)
                    put("avgR", run.avgR)
                    put("profitFactor", run.profitFactor ?: JSONObject.NULL)
                    put("maxDrawdownPoints", run.maxDrawdownPoints)
                    put("maxDrawdownR", run.maxDrawdownR)
                    put("equityCurveCsv", run.equityCurveCsv)
                    put("createdAt", run.createdAt)
                }
            )
        }
        root.put("backtestRuns", runsArray)

        return root.toString(2)
    }

    fun decode(jsonString: String, fallbackSettings: UserSettingsEntity): ParsedVaultPayload {
        val root = JSONObject(jsonString)
        val version = root.optInt("version", 1)
        val exportedAt = root.optLong("timestamp", System.currentTimeMillis())

        val hasSettings = root.has("settings") && !root.isNull("settings")
        var activeProfileName = ""
        val parsedSettings = if (hasSettings) {
            val obj = root.getJSONObject("settings")
            activeProfileName = obj.optString("activeAccountProfileName", "")
            fallbackSettings.copy(
                id = 1,
                currency = enumOrDefault(obj.optString("currency"), fallbackSettings.currency),
                monthlyProfitTarget = obj.optDouble("monthlyProfitTarget", fallbackSettings.monthlyProfitTarget),
                maxDailyLossLimit = obj.optDouble("maxDailyLossLimit", fallbackSettings.maxDailyLossLimit),
                maxTrailingDrawdown = obj.optDouble("maxTrailingDrawdown", fallbackSettings.maxTrailingDrawdown),
                pinLockEnabled = obj.optBoolean("pinLockEnabled", fallbackSettings.pinLockEnabled),
                pinCode = obj.optString("pinCode", fallbackSettings.pinCode),
                eveningReminderEnabled = obj.optBoolean("eveningReminderEnabled", fallbackSettings.eveningReminderEnabled),
                reminderHour = obj.optInt("reminderHour", fallbackSettings.reminderHour),
                reminderMinute = obj.optInt("reminderMinute", fallbackSettings.reminderMinute),
                isDarkTheme = obj.optBoolean("isDarkTheme", fallbackSettings.isDarkTheme),
                themePreset = enumOrDefault(obj.optString("themePreset"), fallbackSettings.themePreset),
                bottomNavStyle = enumOrDefault(obj.optString("bottomNavStyle"), fallbackSettings.bottomNavStyle),
                isOutlookEnabled = obj.optBoolean("isOutlookEnabled", fallbackSettings.isOutlookEnabled),
                newsAlertEnabled = obj.optBoolean("newsAlertEnabled", fallbackSettings.newsAlertEnabled),
                fredApiKey = pickNonBlankOrFallback(obj.optString("fredApiKey", ""), fallbackSettings.fredApiKey),
                finnhubApiKey = pickNonBlankOrFallback(obj.optString("finnhubApiKey", ""), fallbackSettings.finnhubApiKey),
                geminiApiKey = pickNonBlankOrFallback(obj.optString("geminiApiKey", ""), fallbackSettings.geminiApiKey),
                claudeApiKey = pickNonBlankOrFallback(obj.optString("claudeApiKey", ""), fallbackSettings.claudeApiKey),
                openAiApiKey = pickNonBlankOrFallback(obj.optString("openAiApiKey", ""), fallbackSettings.openAiApiKey),
                backtestAiProvider = obj.optString("backtestAiProvider", fallbackSettings.backtestAiProvider)
                    .ifBlank { fallbackSettings.backtestAiProvider },
                backtestAiModel = obj.optString("backtestAiModel", fallbackSettings.backtestAiModel)
                    .ifBlank { fallbackSettings.backtestAiModel },
                activeAccountProfileId = obj.optLong("activeAccountProfileId", fallbackSettings.activeAccountProfileId),
                testerLotSize = obj.optDouble("testerLotSize", fallbackSettings.testerLotSize),
                testerLeverage = obj.optInt("testerLeverage", fallbackSettings.testerLeverage)
            )
        } else {
            null
        }

        val strategies = mutableListOf<StrategyEntity>()
        root.optJSONArray("strategies")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                strategies.add(
                    StrategyEntity(
                        name = obj.getString("name"),
                        description = obj.optString("description", ""),
                        checklist = readStringList(obj.optJSONArray("checklist")),
                        tags = readStringList(obj.optJSONArray("tags")),
                        colorHex = obj.optString("colorHex", "#10B981")
                    )
                )
            }
        }

        val pythonStrategies = mutableListOf<PythonStrategyEntity>()
        root.optJSONArray("pythonStrategies")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                pythonStrategies.add(
                    PythonStrategyEntity(
                        name = obj.getString("name"),
                        className = obj.optString("className", ""),
                        code = obj.getString("code"),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val bots = mutableListOf<BotEntity>()
        root.optJSONArray("bots")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                bots.add(
                    BotEntity(
                        name = obj.getString("name"),
                        description = obj.optString("description", ""),
                        version = obj.optString("version", "1.0.0"),
                        type = enumOrDefault(obj.optString("type"), BotType.EXPERT_ADVISOR),
                        instrument = obj.optString("instrument", "Universal"),
                        timeframe = obj.optString("timeframe", "H1"),
                        strategyId = null,
                        strategyName = obj.optString("strategyName", ""),
                        status = enumOrDefault(obj.optString("status"), BotStatus.TESTING),
                        tags = readStringList(obj.optJSONArray("tags")),
                        notes = obj.optString("notes", ""),
                        code = obj.optString("code", ""),
                        backtestNotes = obj.optString("backtestNotes", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val trades = mutableListOf<TradeEntity>()
        root.optJSONArray("trades")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                trades.add(
                    TradeEntity(
                        date = obj.getString("date"),
                        time = obj.optString("time", "12:00"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        result = enumOrDefault(obj.optString("result"), TradeResult.WIN),
                        pnl = obj.getDouble("pnl"),
                        instrument = obj.getString("instrument"),
                        direction = enumOrDefault(obj.optString("direction"), TradeDirection.BUY),
                        entryPrice = optNullableDouble(obj, "entryPrice"),
                        exitPrice = optNullableDouble(obj, "exitPrice"),
                        stopLoss = optNullableDouble(obj, "stopLoss"),
                        takeProfit = optNullableDouble(obj, "takeProfit"),
                        lotSize = optNullableDouble(obj, "lotSize"),
                        riskRewardRatio = optNullableDouble(obj, "riskRewardRatio"),
                        strategyId = null,
                        strategyName = obj.optString("strategyName", ""),
                        botId = null,
                        botName = obj.optString("botName", ""),
                        notes = obj.optString("notes", ""),
                        emotion = enumOrDefault(obj.optString("emotion"), Emotion.CALM),
                        followedRules = obj.optBoolean("followedRules", true),
                        screenshotUri = null,
                        isNewsTrade = obj.optBoolean("isNewsTrade", false),
                        tags = readStringList(obj.optJSONArray("tags"))
                    )
                )
            }
        }

        val reviews = mutableListOf<DailyReviewEntity>()
        root.optJSONArray("dailyReviews")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                reviews.add(
                    DailyReviewEntity(
                        date = obj.getString("date"),
                        disciplineRating = obj.optInt("disciplineRating", 5),
                        reflection = obj.optString("reflection", ""),
                        lessonsLearned = obj.optString("lessonsLearned", ""),
                        followedAllRules = obj.optBoolean("followedAllRules", true),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        }

        val runs = mutableListOf<BacktestRunEntity>()
        root.optJSONArray("backtestRuns")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                runs.add(
                    BacktestRunEntity(
                        strategyName = obj.getString("strategyName"),
                        csvFileName = obj.optString("csvFileName", "candles.csv"),
                        maxStopPoints = obj.optDouble("maxStopPoints", 0.0),
                        pointSize = obj.optDouble("pointSize", 1.0),
                        totalTrades = obj.optInt("totalTrades", 0),
                        wins = obj.optInt("wins", 0),
                        losses = obj.optInt("losses", 0),
                        winRatePct = obj.optDouble("winRatePct", 0.0),
                        netPoints = obj.optDouble("netPoints", 0.0),
                        netR = obj.optDouble("netR", 0.0),
                        avgR = obj.optDouble("avgR", 0.0),
                        profitFactor = optNullableDouble(obj, "profitFactor"),
                        maxDrawdownPoints = obj.optDouble("maxDrawdownPoints", 0.0),
                        maxDrawdownR = obj.optDouble("maxDrawdownR", 0.0),
                        equityCurveCsv = obj.optString("equityCurveCsv", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val accountProfiles = mutableListOf<AccountProfileEntity>()
        root.optJSONArray("accountProfiles")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                accountProfiles.add(
                    AccountProfileEntity(
                        name = obj.optString("name", "Custom Account").ifBlank { "Custom Account" },
                        accountCategory = obj.optString("accountCategory", "Standard").ifBlank { "Standard" },
                        startingBalance = obj.optDouble("startingBalance", 10000.0),
                        defaultLotSize = obj.optDouble("defaultLotSize", 0.10),
                        leverage = obj.optInt("leverage", 100).coerceAtLeast(1),
                        commissionPerLot = obj.optDouble("commissionPerLot", 0.0),
                        valuePerPointPerLot = obj.optDouble("valuePerPointPerLot", 1.0),
                        contractSizePerLot = obj.optDouble("contractSizePerLot", 100.0),
                        stopOutLevelPct = obj.optDouble("stopOutLevelPct", 50.0),
                        instrumentLabel = obj.optString("instrumentLabel", "XAUUSD (0.01 pt)"),
                        notes = obj.optString("notes", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        }

        return ParsedVaultPayload(
            schemaVersion = version,
            exportedAt = exportedAt,
            hasSettingsSection = hasSettings,
            settings = parsedSettings,
            trades = trades,
            strategies = strategies,
            pythonStrategies = pythonStrategies,
            bots = bots,
            dailyReviews = reviews,
            backtestRuns = runs,
            accountProfiles = accountProfiles,
            activeAccountProfileName = activeProfileName
        )
    }

    private fun pickNonBlankOrFallback(incoming: String, existing: String): String {
        return if (incoming.isNotBlank()) incoming else existing
    }

    private fun optNullableDouble(obj: JSONObject, key: String): Double? {
        return if (obj.has(key) && !obj.isNull(key)) obj.optDouble(key) else null
    }

    private fun readStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val list = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            list.add(array.optString(i, ""))
        }
        return list.filter { it.isNotEmpty() }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String, fallback: T): T {
        if (raw.isBlank()) return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }
}
