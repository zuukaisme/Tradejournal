package com.example.backtest

enum class Side {
    BUY,
    SELL
}

enum class ExitReason {
    TP,
    SL,
    BE,
    EXIT
}

data class BacktestProgressSnapshot(
    val candlesProcessed: Int = 0,
    val totalCandles: Int = 0,
    val enteredTrades: Int = 0,
    val closedTrades: Int = 0,
    val openTrades: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val breakEvens: Int = 0,
    val skippedByStopCap: Int = 0,
    val netPoints: Double = 0.0,
    val netR: Double = 0.0,
    val currentTime: String = "",
    val strategyIndex: Int = 0,
    val totalStrategies: Int = 0,
    val currentStrategyName: String = ""
) {
    val candleProgressFraction: Float
        get() = if (totalCandles > 0) (candlesProcessed.toFloat() / totalCandles).coerceIn(0f, 1f) else 0f

    val winRatePct: Double
        get() = if (closedTrades > 0) (wins * 100.0) / closedTrades else 0.0

    val lossRatePct: Double
        get() = if (closedTrades > 0) (losses * 100.0) / closedTrades else 0.0

    val breakEvenRatePct: Double
        get() = if (closedTrades > 0) (breakEvens * 100.0) / closedTrades else 0.0
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
    val skippedByStopCap: Int,
    val breakEvens: Int = 0,
    val enteredTrades: Int = totalTrades,
    val openTrades: Int = 0,
    val breakEvenRatePct: Double = 0.0,
    val lossRatePct: Double = if (totalTrades > 0) (losses * 100.0) / totalTrades else 0.0,
    val tpExits: Int = 0,
    val slExits: Int = 0,
    val expectancyPoints: Double = if (totalTrades > 0) netPoints / totalTrades else 0.0,
    val totalCandles: Int = 0,
    val currentStreak: Int = 0
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
    val durationCandles: Int,
    val openIndex: Int = -1,
    val closeIndex: Int = -1
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

data class BatchStrategySpec(
    val id: Long,
    val name: String,
    val className: String,
    val code: String
)

data class BatchStrategyRunResult(
    val strategyId: Long,
    val strategyName: String,
    val className: String,
    val result: BacktestResult? = null,
    val error: BacktestError? = null
)

sealed interface BatchBacktestParseOutcome {
    data class Success(val totalCandles: Int, val runs: List<BatchStrategyRunResult>) : BatchBacktestParseOutcome
    data class Failure(val error: BacktestError) : BatchBacktestParseOutcome
}
