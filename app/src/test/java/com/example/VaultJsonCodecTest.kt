package com.example

import com.example.data.local.BacktestRunEntity
import com.example.data.local.BotEntity
import com.example.data.local.DailyReviewEntity
import com.example.data.local.PythonStrategyEntity
import com.example.data.local.StrategyEntity
import com.example.data.local.TradeEntity
import com.example.data.local.UserSettingsEntity
import com.example.data.local.VaultJsonCodec
import com.example.data.local.VaultSnapshot
import com.example.domain.model.AppThemePreset
import com.example.domain.model.BotStatus
import com.example.domain.model.BotType
import com.example.domain.model.BottomNavStyle
import com.example.domain.model.CurrencyCode
import com.example.domain.model.Emotion
import com.example.domain.model.TradeDirection
import com.example.domain.model.TradeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VaultJsonCodecTest {

    private fun defaultSettings() = UserSettingsEntity(
        id = 1,
        currency = CurrencyCode.USD,
        monthlyProfitTarget = 5000.0,
        maxDailyLossLimit = 1000.0,
        maxTrailingDrawdown = 2500.0,
        pinLockEnabled = false,
        pinCode = "",
        eveningReminderEnabled = false,
        reminderHour = 20,
        reminderMinute = 0,
        isDarkTheme = true,
        themePreset = AppThemePreset.EMERALD_OBSIDIAN,
        fredApiKey = "",
        finnhubApiKey = "",
        geminiApiKey = "",
        claudeApiKey = "",
        openAiApiKey = "",
        backtestAiProvider = "CLAUDE",
        backtestAiModel = "claude-opus-5-5",
        isOutlookEnabled = true,
        newsAlertEnabled = false,
        bottomNavStyle = BottomNavStyle.LIQUID_GLASS
    )

    @Test
    fun encodeAndDecode_roundTripsAllVaultSectionsAndFiveApiKeys() {
        val fullSettings = UserSettingsEntity(
            id = 1,
            currency = CurrencyCode.EUR,
            monthlyProfitTarget = 8500.0,
            maxDailyLossLimit = 450.0,
            maxTrailingDrawdown = 1800.0,
            pinLockEnabled = true,
            pinCode = "2468",
            eveningReminderEnabled = true,
            reminderHour = 21,
            reminderMinute = 30,
            isDarkTheme = false,
            themePreset = AppThemePreset.HIGH_CONTRAST_LIGHT,
            fredApiKey = "fred-key-111",
            finnhubApiKey = "finnhub-key-222",
            geminiApiKey = "gemini-key-333",
            claudeApiKey = "claude-key-444",
            openAiApiKey = "openai-key-555",
            backtestAiProvider = "OPENAI",
            backtestAiModel = "gpt-6.1-sol",
            isOutlookEnabled = false,
            newsAlertEnabled = true,
            bottomNavStyle = BottomNavStyle.NEON_ORBIT
        )

        val snapshot = VaultSnapshot(
            settings = fullSettings,
            trades = listOf(
                TradeEntity(
                    id = 10,
                    date = "2026-03-15",
                    time = "14:25",
                    timestamp = 1773584700000L,
                    result = TradeResult.WIN,
                    pnl = 640.5,
                    instrument = "XAUUSD",
                    direction = TradeDirection.BUY,
                    entryPrice = 2910.25,
                    exitPrice = 2925.75,
                    stopLoss = 2902.0,
                    takeProfit = 2926.0,
                    lotSize = 0.5,
                    riskRewardRatio = 1.9,
                    strategyId = 1,
                    strategyName = "London Sweep",
                    botId = 2,
                    botName = "GoldPulse EA",
                    notes = "Clean sweep of Asian low",
                    emotion = Emotion.CONFIDENT,
                    followedRules = true,
                    screenshotUri = null,
                    isNewsTrade = true,
                    tags = listOf("CPI", "London")
                )
            ),
            strategies = listOf(
                StrategyEntity(
                    id = 1,
                    name = "London Sweep",
                    description = "Liquidity sweep at session open",
                    checklist = listOf("Sweep high/low", "M5 displacement"),
                    tags = listOf("XAUUSD", "Session"),
                    colorHex = "#10B981"
                )
            ),
            pythonStrategies = listOf(
                PythonStrategyEntity(
                    id = 3,
                    name = "Gold Ema Pullback",
                    className = "GoldEmaPullback",
                    code = "class GoldEmaPullback:\n    pass",
                    updatedAt = 1773584800000L
                )
            ),
            bots = listOf(
                BotEntity(
                    id = 2,
                    name = "GoldPulse EA",
                    description = "Breakout robot",
                    version = "2.1.0",
                    type = BotType.EXPERT_ADVISOR,
                    instrument = "XAUUSD",
                    timeframe = "M15",
                    strategyId = 1,
                    strategyName = "London Sweep",
                    status = BotStatus.LIVE,
                    tags = listOf("M15", "Gold"),
                    notes = "Low spread broker",
                    code = "void OnTick() {}",
                    backtestNotes = "PF 2.1 on 2025 tick data",
                    createdAt = 1770000000000L,
                    updatedAt = 1773584900000L
                )
            ),
            dailyReviews = listOf(
                DailyReviewEntity(
                    date = "2026-03-15",
                    disciplineRating = 5,
                    reflection = "Followed every rule cleanly.",
                    lessonsLearned = "Wait for candle close.",
                    followedAllRules = true,
                    timestamp = 1773590000000L
                )
            ),
            backtestRuns = listOf(
                BacktestRunEntity(
                    id = 4,
                    strategyName = "Gold Ema Pullback",
                    csvFileName = "XAUUSD_M5.csv -> 15m (M15)",
                    maxStopPoints = 250.0,
                    pointSize = 0.01,
                    totalTrades = 42,
                    wins = 28,
                    losses = 14,
                    winRatePct = 66.67,
                    netPoints = 1450.0,
                    netR = 14.5,
                    avgR = 0.345,
                    profitFactor = 2.15,
                    maxDrawdownPoints = 320.0,
                    maxDrawdownR = 3.2,
                    equityCurveCsv = "100.0,220.0,1450.0",
                    createdAt = 1773591000000L
                ),
                BacktestRunEntity(
                    id = 5,
                    strategyName = "No Loss Run",
                    csvFileName = "XAUUSD_M5.csv",
                    maxStopPoints = 150.0,
                    pointSize = 0.01,
                    totalTrades = 5,
                    wins = 5,
                    losses = 0,
                    winRatePct = 100.0,
                    netPoints = 500.0,
                    netR = 5.0,
                    avgR = 1.0,
                    profitFactor = null,
                    maxDrawdownPoints = 0.0,
                    maxDrawdownR = 0.0,
                    equityCurveCsv = "100.0,500.0",
                    createdAt = 1773592000000L
                )
            )
        )

        val json = VaultJsonCodec.encode(snapshot, timestamp = 1773600000000L)
        val decoded = VaultJsonCodec.decode(json, defaultSettings())

        assertEquals(VaultJsonCodec.CURRENT_VERSION, decoded.schemaVersion)
        assertEquals(1773600000000L, decoded.exportedAt)
        assertTrue(decoded.hasSettingsSection)

        val restoredSettings = decoded.settings!!
        assertEquals(CurrencyCode.EUR, restoredSettings.currency)
        assertEquals(8500.0, restoredSettings.monthlyProfitTarget, 1e-6)
        assertEquals(450.0, restoredSettings.maxDailyLossLimit, 1e-6)
        assertEquals(1800.0, restoredSettings.maxTrailingDrawdown, 1e-6)
        assertTrue(restoredSettings.pinLockEnabled)
        assertEquals("2468", restoredSettings.pinCode)
        assertTrue(restoredSettings.eveningReminderEnabled)
        assertEquals(21, restoredSettings.reminderHour)
        assertEquals(30, restoredSettings.reminderMinute)
        assertFalse(restoredSettings.isDarkTheme)
        assertEquals(AppThemePreset.HIGH_CONTRAST_LIGHT, restoredSettings.themePreset)
        assertEquals(BottomNavStyle.NEON_ORBIT, restoredSettings.bottomNavStyle)
        assertFalse(restoredSettings.isOutlookEnabled)
        assertTrue(restoredSettings.newsAlertEnabled)
        assertEquals("fred-key-111", restoredSettings.fredApiKey)
        assertEquals("finnhub-key-222", restoredSettings.finnhubApiKey)
        assertEquals("gemini-key-333", restoredSettings.geminiApiKey)
        assertEquals("claude-key-444", restoredSettings.claudeApiKey)
        assertEquals("openai-key-555", restoredSettings.openAiApiKey)
        assertEquals("OPENAI", restoredSettings.backtestAiProvider)
        assertEquals("gpt-6.1-sol", restoredSettings.backtestAiModel)
        assertEquals(5, VaultJsonCodec.countConfiguredApiKeys(restoredSettings))

        assertEquals(1, decoded.trades.size)
        val t = decoded.trades[0]
        assertEquals("XAUUSD", t.instrument)
        assertTrue(t.isNewsTrade)
        assertEquals(listOf("CPI", "London"), t.tags)
        assertEquals(1.9, t.riskRewardRatio!!, 1e-6)
        assertEquals(Emotion.CONFIDENT, t.emotion)

        assertEquals(1, decoded.strategies.size)
        assertEquals(listOf("Sweep high/low", "M5 displacement"), decoded.strategies[0].checklist)

        assertEquals(1, decoded.pythonStrategies.size)
        assertEquals("GoldEmaPullback", decoded.pythonStrategies[0].className)

        assertEquals(1, decoded.bots.size)
        assertEquals(BotStatus.LIVE, decoded.bots[0].status)

        assertEquals(1, decoded.dailyReviews.size)
        assertEquals("2026-03-15", decoded.dailyReviews[0].date)

        assertEquals(2, decoded.backtestRuns.size)
        assertEquals(2.15, decoded.backtestRuns[0].profitFactor!!, 1e-6)
        assertNull(decoded.backtestRuns[1].profitFactor)
    }

    @Test
    fun decode_supportsLegacyV1BackupWithoutSettingsOrBacktestSections() {
        val v1Json = """
            {
              "version": 1,
              "app": "TradePulse",
              "timestamp": 1700000000000,
              "strategies": [
                {
                  "name": "Range Break",
                  "description": "NY open breakout",
                  "checklist": ["Confirm volume"],
                  "tags": ["NAS100"],
                  "colorHex": "#3B82F6"
                }
              ],
              "bots": [],
              "trades": [
                {
                  "date": "2026-01-10",
                  "time": "15:00",
                  "timestamp": 1700000000000,
                  "result": "LOSS",
                  "pnl": -125.0,
                  "instrument": "NAS100",
                  "direction": "SELL",
                  "strategyName": "Range Break",
                  "notes": "Choppy open",
                  "emotion": "FRUSTRATED",
                  "followedRules": false,
                  "tags": ["NY"]
                }
              ]
            }
        """.trimIndent()

        val decoded = VaultJsonCodec.decode(v1Json, defaultSettings())
        assertEquals(1, decoded.schemaVersion)
        assertFalse(decoded.hasSettingsSection)
        assertNull(decoded.settings)
        assertEquals(1, decoded.strategies.size)
        assertEquals(1, decoded.trades.size)
        assertEquals(TradeResult.LOSS, decoded.trades[0].result)
        assertEquals(TradeDirection.SELL, decoded.trades[0].direction)
        assertTrue(decoded.pythonStrategies.isEmpty())
        assertTrue(decoded.dailyReviews.isEmpty())
        assertTrue(decoded.backtestRuns.isEmpty())
    }

    @Test
    fun decode_preservesExistingApiKeysWhenIncomingVaultHasBlankKeys() {
        val existingSettings = defaultSettings().copy(
            claudeApiKey = "existing-claude-key",
            geminiApiKey = "existing-gemini-key"
        )
        val incomingEmptyKeysSnapshot = VaultSnapshot(
            settings = defaultSettings().copy(
                fredApiKey = "new-fred-key",
                claudeApiKey = "",
                geminiApiKey = "   "
            ),
            trades = emptyList(),
            strategies = emptyList(),
            pythonStrategies = emptyList(),
            bots = emptyList(),
            dailyReviews = emptyList(),
            backtestRuns = emptyList()
        )

        val json = VaultJsonCodec.encode(incomingEmptyKeysSnapshot)
        val decoded = VaultJsonCodec.decode(json, existingSettings)
        val merged = decoded.settings!!

        assertEquals("new-fred-key", merged.fredApiKey)
        assertEquals("existing-claude-key", merged.claudeApiKey)
        assertEquals("existing-gemini-key", merged.geminiApiKey)
    }

    @Test
    fun hasMeaningfulData_returnsFalseForFreshDefaultsAndTrueWhenAnyUserRecordOrKeyExists() {
        val emptySnapshot = VaultSnapshot(
            settings = defaultSettings(),
            trades = emptyList(),
            strategies = emptyList(),
            pythonStrategies = emptyList(),
            bots = emptyList(),
            dailyReviews = emptyList(),
            backtestRuns = emptyList()
        )
        assertFalse(VaultJsonCodec.hasMeaningfulData(emptySnapshot))

        val withApiKey = emptySnapshot.copy(
            settings = defaultSettings().copy(claudeApiKey = "sk-ant-test")
        )
        assertTrue(VaultJsonCodec.hasMeaningfulData(withApiKey))

        val withPythonStrategy = emptySnapshot.copy(
            pythonStrategies = listOf(
                PythonStrategyEntity(name = "Test", className = "Test", code = "", updatedAt = 1L)
            )
        )
        assertTrue(VaultJsonCodec.hasMeaningfulData(withPythonStrategy))
    }
}
