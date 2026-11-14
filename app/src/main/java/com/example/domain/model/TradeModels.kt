package com.example.domain.model

enum class TradeResult(val label: String) {
    WIN("Winning"),
    LOSS("Losing"),
    BREAK_EVEN("Break Even"),
    NO_TRADE("Didn't Trade")
}

enum class TradeDirection(val label: String) {
    BUY("Buy / Long"),
    SELL("Sell / Short")
}

enum class Emotion(val label: String) {
    CALM("Calm"),
    CONFIDENT("Confident"),
    FEARFUL("Fearful"),
    GREEDY("Greedy"),
    REVENGE("Revenge"),
    NEUTRAL("Neutral")
}

enum class BotType(val label: String) {
    EXPERT_ADVISOR("Expert Advisor (EA)"),
    INDICATOR("Custom Indicator"),
    SCRIPT("Execution Script"),
    LIBRARY("MQL5 Library")
}

enum class BotStatus(val label: String) {
    TESTING("Testing"),
    LIVE("Live Trading"),
    RETIRED("Retired")
}

enum class TimePeriod(val label: String) {
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    THIS_YEAR("This Year"),
    ALL_TIME("All Time"),
    CUSTOM("Custom")
}

enum class CurrencyCode(val symbol: String, val displayName: String) {
    USD("$", "USD ($)"),
    EUR("€", "EUR (€)"),
    GBP("£", "GBP (£)"),
    JPY("¥", "JPY (¥)"),
    AUD("A$", "AUD (A$)"),
    CAD("C$", "CAD (C$)"),
    CHF("CHF", "CHF (CHF)"),
    BTC("₿", "BTC (₿)")
}

enum class BottomNavStyle(
    val displayName: String,
    val description: String
) {
    LIQUID_GLASS("Liquid Glass", "Floating capsule with sliding glass pill"),
    NEON_ORBIT("Neon Orbit", "Pop-up emerald badge with glowing dot"),
    ARCADE_RETRO("8-Bit Arcade", "Chunky retro terminal pixel blocks"),
    MINIMAL_RAIL("Minimal Underbar", "Sleek sliding emerald underline"),
    BUBBLE_BOUNCE("Bouncy Pill", "Springy expanding label pill")
}

data class Trade(
    val id: Long = 0,
    val date: String, // "YYYY-MM-DD"
    val time: String = "", // "HH:mm"
    val timestamp: Long = System.currentTimeMillis(),
    val result: TradeResult,
    val pnl: Double,
    val instrument: String,
    val direction: TradeDirection = TradeDirection.BUY,
    val entryPrice: Double? = null,
    val exitPrice: Double? = null,
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
    val lotSize: Double? = null,
    val riskRewardRatio: Double? = null,
    val strategyId: Long? = null,
    val strategyName: String = "",
    val botId: Long? = null,
    val botName: String = "",
    val notes: String = "",
    val emotion: Emotion = Emotion.CALM,
    val followedRules: Boolean = true,
    val screenshotUri: String? = null,
    val isNewsTrade: Boolean = false,
    val tags: List<String> = emptyList()
)

data class Strategy(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val checklist: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val colorHex: String = "#10B981"
)

data class Bot(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val version: String = "1.0.0",
    val type: BotType = BotType.EXPERT_ADVISOR,
    val instrument: String = "All / Universal",
    val timeframe: String = "H1",
    val strategyId: Long? = null,
    val strategyName: String = "",
    val status: BotStatus = BotStatus.TESTING,
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val code: String = "",
    val backtestNotes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class BotVersion(
    val id: Long = 0,
    val botId: Long,
    val version: String,
    val code: String,
    val changeNote: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class DailyReview(
    val date: String, // "YYYY-MM-DD"
    val disciplineRating: Int = 5, // 1 to 5
    val reflection: String = "",
    val lessonsLearned: String = "",
    val followedAllRules: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

data class UserSettings(
    val currency: CurrencyCode = CurrencyCode.USD,
    val monthlyProfitTarget: Double = 2500.0,
    val maxDailyLossLimit: Double = 300.0,
    val pinLockEnabled: Boolean = false,
    val pinCode: String = "",
    val eveningReminderEnabled: Boolean = true,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val isDarkTheme: Boolean = true,
    val fredApiKey: String = "",
    val finnhubApiKey: String = "",
    val geminiApiKey: String = "",
    val claudeApiKey: String = "",
    val openAiApiKey: String = "",
    val backtestAiProvider: String = "CLAUDE",
    val backtestAiModel: String = "claude-opus-5-5",
    val isOutlookEnabled: Boolean = true,
    val newsAlertEnabled: Boolean = true,
    val bottomNavStyle: BottomNavStyle = BottomNavStyle.LIQUID_GLASS
)

data class DayPnLSummary(
    val date: String,
    val netPnL: Double,
    val tradesCount: Int,
    val wins: Int,
    val losses: Int,
    val breakEvens: Int,
    val noTrade: Boolean
)

data class PerformanceSummary(
    val period: TimePeriod,
    val netPnL: Double,
    val winRate: Double,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val breakEvenTrades: Int,
    val daysNotTraded: Int,
    val winningDays: Int,
    val losingDays: Int,
    val breakEvenDays: Int,
    val averageWin: Double,
    val averageLoss: Double,
    val profitFactor: Double,
    val bestDay: Double,
    val worstDay: Double,
    val longestWinningStreak: Int,
    val longestLosingStreak: Int,
    val maxDrawdown: Double,
    val maxDrawdownPct: Double,
    val dailyPnLList: List<DayPnLSummary> = emptyList(),
    val equityCurve: List<Pair<String, Double>> = emptyList(),
    val strategyBreakdown: Map<String, StrategyStat> = emptyMap(),
    val instrumentBreakdown: Map<String, InstrumentStat> = emptyMap(),
    val dayOfWeekBreakdown: Map<String, DayOfWeekStat> = emptyMap(),
    val ruleAdherenceBreakdown: RuleAdherenceStat = RuleAdherenceStat()
)

data class StrategyStat(
    val strategyName: String,
    val trades: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double,
    val totalPnL: Double,
    val grossProfit: Double = 0.0,
    val grossLoss: Double = 0.0,
    val profitFactor: Double = 0.0,
    val averagePnL: Double = 0.0
)

data class InstrumentStat(
    val instrument: String,
    val trades: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double,
    val totalPnL: Double
)

data class DayOfWeekStat(
    val dayName: String,
    val trades: Int,
    val winRate: Double,
    val netPnL: Double
)

data class RuleAdherenceStat(
    val followedTrades: Int = 0,
    val followedWins: Int = 0,
    val followedPnL: Double = 0.0,
    val violatedTrades: Int = 0,
    val violatedWins: Int = 0,
    val violatedPnL: Double = 0.0
)

data class PythonStrategy(
    val id: Long = 0,
    val name: String,
    val className: String = "",
    val code: String,
    val updatedAt: Long = System.currentTimeMillis()
)

