package com.example.backtest

enum class Side {
    BUY,
    SELL
}

enum class ExitReason {
    TP,
    SL
}

data class BacktestSummary(
    val totalTrades: Int,
    val wins: Int,
    val losses: Int,
    val winRatePct: Double,
    val netPoints: Double,
    val netR: Double,
    val avgR: Double,
    val profitFactor: Double?,
    val skippedByStopCap: Int
)

data class BacktestPoints(
    val avgWinPoints: Double,
    val avgLossPoints: Double,
    val largestWinPoints: Double,
    val largestLossPoints: Double,
    val avgStopDistancePoints: Double,
    val maxStopDistancePoints: Double,
    val avgTargetDistancePoints: Double
)

data class BacktestRisk(
    val maxDrawdownPoints: Double,
    val maxDrawdownR: Double,
    val maxConsecutiveWins: Int,
    val maxConsecutiveLosses: Int
)

data class SideStat(
    val trades: Int,
    val winRatePct: Double,
    val netPoints: Double
)

data class BySideStats(
    val buy: SideStat,
    val sell: SideStat
)

data class BacktestTiming(
    val avgTradeDurationCandles: Double
)

data class Trade(
    val side: Side,
    val entry: Double,
    val sl: Double,
    val tp: Double,
    val exit: Double,
    val openTime: String,
    val closeTime: String,
    val reason: ExitReason,
    val points: Double,
    val r: Double,
    val durationCandles: Int
)

data class BacktestResult(
    val summary: BacktestSummary,
    val points: BacktestPoints,
    val risk: BacktestRisk,
    val bySide: BySideStats,
    val timing: BacktestTiming,
    val equityCurve: List<Double>,
    val trades: List<Trade>,
    val warnings: List<String>
)

data class BacktestError(
    val error: String,
    val details: String = ""
)

sealed interface BacktestParseOutcome {
    data class Success(val result: BacktestResult) : BacktestParseOutcome
    data class Failure(val error: BacktestError) : BacktestParseOutcome
}

data class SavedBacktestRun(
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
    val equityCurve: List<Double>,
    val createdAt: Long = System.currentTimeMillis()
)
