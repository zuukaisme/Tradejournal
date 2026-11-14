package com.example.domain.model

enum class NewsEventType(val title: String, val frequency: String) {
    NFP("Non-Farm Payrolls (NFP)", "First Friday of each month"),
    CPI("Consumer Price Index (CPI)", "Mid-month release")
}

enum class ConfidenceLevel(val label: String, val colorHex: String) {
    LOW("Low Confidence", "#94A3B8"),
    MEDIUM("Medium Confidence", "#F59E0B"),
    HIGH("High Confidence", "#10B981")
}

enum class TrendDirection {
    UP, DOWN, FLAT
}

enum class GoldOutcome {
    RISE, FALL, UNRESOLVED
}

data class MacroIndicator(
    val id: String,
    val name: String,
    val currentValue: Double,
    val previousValue: Double,
    val unit: String,
    val trend: TrendDirection,
    val impactOnGold: String, // e.g. "Hawkish -> Bearish Gold"
    val isBullishForGold: Boolean
)

data class NewsRelease(
    val id: Long = 0,
    val eventType: NewsEventType,
    val dateString: String, // "YYYY-MM-DD"
    val timestamp: Long,
    val forecast: Double,
    val previous: Double,
    val actual: Double?,
    val surprise: Double?,
    val unit: String = "K",
    val gold15mDelta: Double? = null, // in dollars, e.g. +$18.50
    val gold60mDelta: Double? = null, // in dollars, e.g. +$24.20
    val isUpcoming: Boolean = false
)

data class OutlookPrediction(
    val id: Long = 0,
    val eventType: NewsEventType,
    val releaseDate: String,
    val releaseTimestamp: Long,
    val probGoldRise: Double, // 0.0 to 1.0 (e.g. 0.44 = 44%)
    val probGoldFall: Double, // 0.0 to 1.0 (e.g. 0.56 = 56%)
    val confidence: ConfidenceLevel,
    val timeWindow: String = "First 15-60 Minutes",
    val sampleSize: Int = 0,
    val reasoning: String,
    val actualOutcome: GoldOutcome = GoldOutcome.UNRESOLVED,
    val actualGoldDelta: Double? = null,
    val wasCorrect: Boolean? = null,
    val userTradedRelease: Boolean = false
)

data class ModelCalibrationStats(
    val totalPredictions: Int,
    val resolvedPredictions: Int,
    val correctPredictions: Int,
    val hitRate: Double, // e.g. 66.7%
    val brierScore: Double // 0.0 (perfect) to 1.0 (worst)
)
