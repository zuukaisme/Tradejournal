package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.AppThemePreset
import com.example.domain.model.BotStatus
import com.example.domain.model.BotType
import com.example.domain.model.BottomNavStyle
import com.example.domain.model.ConfidenceLevel
import com.example.domain.model.CurrencyCode
import com.example.domain.model.Emotion
import com.example.domain.model.GoldOutcome
import com.example.domain.model.NewsEventType
import com.example.domain.model.TradeDirection
import com.example.domain.model.TradeResult

@Entity(tableName = "trades")
data class TradeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // "YYYY-MM-DD"
    val time: String, // "HH:mm"
    val timestamp: Long,
    val result: TradeResult,
    val pnl: Double,
    val instrument: String,
    val direction: TradeDirection,
    val entryPrice: Double?,
    val exitPrice: Double?,
    val stopLoss: Double?,
    val takeProfit: Double?,
    val lotSize: Double?,
    val riskRewardRatio: Double?,
    val strategyId: Long?,
    val strategyName: String,
    val botId: Long?,
    val botName: String,
    val notes: String,
    val emotion: Emotion,
    val followedRules: Boolean,
    val screenshotUri: String?,
    val isNewsTrade: Boolean = false,
    val tags: List<String>
)

@Entity(tableName = "strategies")
data class StrategyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String,
    val checklist: List<String>,
    val tags: List<String>,
    val colorHex: String
)

@Entity(tableName = "bots")
data class BotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String,
    val version: String,
    val type: BotType,
    val instrument: String,
    val timeframe: String,
    val strategyId: Long?,
    val strategyName: String,
    val status: BotStatus,
    val tags: List<String>,
    val notes: String,
    val code: String,
    val backtestNotes: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "bot_versions")
data class BotVersionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val botId: Long,
    val version: String,
    val code: String,
    val changeNote: String,
    val timestamp: Long
)

@Entity(tableName = "daily_reviews")
data class DailyReviewEntity(
    @PrimaryKey
    val date: String, // "YYYY-MM-DD"
    val disciplineRating: Int,
    val reflection: String,
    val lessonsLearned: String,
    val followedAllRules: Boolean,
    val timestamp: Long
)

@Entity(tableName = "news_releases")
data class NewsReleaseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventType: NewsEventType,
    val dateString: String,
    val timestamp: Long,
    val forecast: Double,
    val previous: Double,
    val actual: Double?,
    val surprise: Double?,
    val unit: String,
    val gold15mDelta: Double?,
    val gold60mDelta: Double?,
    val isUpcoming: Boolean
)

@Entity(tableName = "outlook_predictions")
data class OutlookPredictionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventType: NewsEventType,
    val releaseDate: String,
    val releaseTimestamp: Long,
    val probGoldRise: Double,
    val probGoldFall: Double,
    val confidence: ConfidenceLevel,
    val timeWindow: String,
    val sampleSize: Int,
    val reasoning: String,
    val actualOutcome: GoldOutcome,
    val actualGoldDelta: Double?,
    val wasCorrect: Boolean?,
    val userTradedRelease: Boolean
)

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val currency: CurrencyCode,
    val monthlyProfitTarget: Double,
    val maxDailyLossLimit: Double,
    val maxTrailingDrawdown: Double = 2000.0,
    val pinLockEnabled: Boolean,
    val pinCode: String,
    val eveningReminderEnabled: Boolean,
    val reminderHour: Int,
    val reminderMinute: Int,
    val isDarkTheme: Boolean,
    val themePreset: AppThemePreset = AppThemePreset.EMERALD_OBSIDIAN,
    val fredApiKey: String,
    val finnhubApiKey: String,
    val geminiApiKey: String,
    val claudeApiKey: String = "",
    val openAiApiKey: String = "",
    val backtestAiProvider: String = "CLAUDE",
    val backtestAiModel: String = "claude-opus-5-5",
    val isOutlookEnabled: Boolean,
    val newsAlertEnabled: Boolean,
    val bottomNavStyle: BottomNavStyle = BottomNavStyle.LIQUID_GLASS
)

@Entity(tableName = "python_strategies")
data class PythonStrategyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val className: String,
    val code: String,
    val updatedAt: Long
)

@Entity(tableName = "backtest_runs")
data class BacktestRunEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val strategyName: String,
    val csvFileName: String,
    val maxStopPoints: Double,
    val pointSize: Double,
    val totalTrades: Int,
    val wins: Int,
    val losses: Int,
    val winRatePct: Double,
    val netPoints: Double,
    val netR: Double,
    val avgR: Double,
    val profitFactor: Double?,
    val maxDrawdownPoints: Double,
    val maxDrawdownR: Double,
    val equityCurveCsv: String,
    val createdAt: Long
)

