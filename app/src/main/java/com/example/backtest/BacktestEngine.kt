package com.example.backtest

import kotlin.math.abs

fun runBacktest(
    candles: List<Candle>,
    strategy: Strategy,
    config: BacktestConfig = BacktestConfig()
): BacktestResult = BacktestEngine.runBacktest(candles, strategy, config)

object BacktestEngine {
    const val WARMUP_CANDLES = 50

    private data class OpenPosition(
        val side: Side,
        val entry: Double,
        val sl: Double,
        val tp: Double,
        val openIndex: Int,
        val openTime: String,
        val stopPriceDist: Double,
        val targetPriceDist: Double
    )

    fun runBacktest(
        candles: List<Candle>,
        strategy: Strategy,
        config: BacktestConfig = BacktestConfig()
    ): BacktestResult {
        val safePointSize = if (config.pointSize > 0.0) config.pointSize else 1.0
        val warnings = mutableListOf<String>()

        if (candles.size <= WARMUP_CANDLES) {
            warnings.add(
                "Dataset has ${candles.size} candle(s), which does not exceed the $WARMUP_CANDLES-candle warmup period."
            )
            return emptyResult(warnings)
        }

        val closedTrades = mutableListOf<Trade>()
        val stopDistancesPoints = mutableListOf<Double>()
        val targetDistancesPoints = mutableListOf<Double>()

        var openPosition: OpenPosition? = null
        var skippedByStopCap = 0

        for (i in candles.indices) {
            val candle = candles[i]

            // 1. Check exit of any currently open position on candle i
            val currentPos = openPosition
            if (currentPos != null) {
                val (slHit, tpHit) = when (currentPos.side) {
                    Side.BUY -> (candle.low <= currentPos.sl) to (candle.high >= currentPos.tp)
                    Side.SELL -> (candle.high >= currentPos.sl) to (candle.low <= currentPos.tp)
                }

                // Rule: if SL and TP are both inside one candle, assume SL hit first
                val exitReason: ExitReason? = when {
                    slHit -> ExitReason.SL
                    tpHit -> ExitReason.TP
                    else -> null
                }

                if (exitReason != null) {
                    val exitPrice = if (exitReason == ExitReason.SL) currentPos.sl else currentPos.tp
                    val priceDelta = when (currentPos.side) {
                        Side.BUY -> exitPrice - currentPos.entry
                        Side.SELL -> currentPos.entry - exitPrice
                    }
                    val points = priceDelta / safePointSize
                    val r = if (currentPos.stopPriceDist > 0.0) {
                        priceDelta / currentPos.stopPriceDist
                    } else {
                        0.0
                    }
                    val duration = (i - currentPos.openIndex + 1).coerceAtLeast(1)

                    closedTrades.add(
                        Trade(
                            side = currentPos.side,
                            entry = currentPos.entry,
                            sl = currentPos.sl,
                            tp = currentPos.tp,
                            exit = exitPrice,
                            openTime = currentPos.openTime,
                            closeTime = candle.time,
                            reason = exitReason,
                            points = points,
                            r = r,
                            durationCandles = duration
                        )
                    )
                    openPosition = null
                }
            }

            // 2. If flat, warmed up, and there is a next candle i+1 to enter on, ask strategy
            if (openPosition == null && i >= WARMUP_CANDLES && i < candles.lastIndex) {
                val visibleCandles = candles.subList(0, i + 1)
                val signal = strategy.onCandle(i, visibleCandles) ?: continue

                val nextCandle = candles[i + 1]
                val entryPrice = nextCandle.open
                val stopPriceDist = abs(entryPrice - signal.sl)
                val targetPriceDist = abs(signal.tp - entryPrice)
                val stopPoints = stopPriceDist / safePointSize
                val targetPoints = targetPriceDist / safePointSize

                if (config.maxStopPoints != null && stopPoints > config.maxStopPoints) {
                    skippedByStopCap++
                    continue
                }

                if (stopPriceDist <= 0.0) {
                    continue
                }

                stopDistancesPoints.add(stopPoints)
                targetDistancesPoints.add(targetPoints)

                openPosition = OpenPosition(
                    side = signal.side,
                    entry = entryPrice,
                    sl = signal.sl,
                    tp = signal.tp,
                    openIndex = i + 1,
                    openTime = nextCandle.time,
                    stopPriceDist = stopPriceDist,
                    targetPriceDist = targetPriceDist
                )
            }
        }

        if (openPosition != null) {
            warnings.add("1 open trade remained unclosed at the end of the dataset and was excluded from closed metrics.")
        }

        val totalTrades = closedTrades.size
        if (totalTrades == 0) {
            warnings.add("No closed trades were executed with the selected strategy and configuration.")
        } else if (totalTrades < 50) {
            warnings.add("Under 50 trades ($totalTrades), results are not statistically reliable.")
        }

        if (skippedByStopCap > 0) {
            warnings.add("$skippedByStopCap signal(s) skipped due to exceeding maxStopPoints (${config.maxStopPoints}).")
        }

        if (totalTrades == 0) {
            return emptyResult(warnings, skippedByStopCap)
        }

        val winningTrades = closedTrades.filter { it.points > 0.0 }
        val losingTrades = closedTrades.filter { it.points < 0.0 }

        val wins = winningTrades.size
        val losses = losingTrades.size
        val winRatePct = (wins.toDouble() / totalTrades) * 100.0
        val netPoints = closedTrades.sumOf { it.points }
        val netR = closedTrades.sumOf { it.r }
        val avgR = netR / totalTrades

        val grossWinPoints = winningTrades.sumOf { it.points }
        val grossLossPoints = losingTrades.sumOf { abs(it.points) }
        val profitFactor = when {
            grossLossPoints > 0.0 -> grossWinPoints / grossLossPoints
            grossWinPoints > 0.0 -> Double.POSITIVE_INFINITY
            else -> 0.0
        }

        val avgWinPoints = if (wins > 0) grossWinPoints / wins else 0.0
        val avgLossPoints = if (losses > 0) grossLossPoints / losses else 0.0
        val largestWinPoints = winningTrades.maxOfOrNull { it.points } ?: 0.0
        val largestLossPoints = losingTrades.maxOfOrNull { abs(it.points) } ?: 0.0
        val avgStopDist = if (stopDistancesPoints.isNotEmpty()) stopDistancesPoints.average() else 0.0
        val maxStopDist = stopDistancesPoints.maxOrNull() ?: 0.0
        val avgTargetDist = if (targetDistancesPoints.isNotEmpty()) targetDistancesPoints.average() else 0.0

        // Equity curve & Risk metrics
        val equityCurve = ArrayList<Double>(totalTrades)
        var runningPoints = 0.0
        var peakPoints = 0.0
        var maxDdPoints = 0.0
        var maxDdPct = 0.0

        var runningR = 0.0
        var peakR = 0.0
        var maxDdR = 0.0

        var curWinStreak = 0
        var maxWinStreak = 0
        var curLossStreak = 0
        var maxLossStreak = 0

        for (t in closedTrades) {
            runningPoints += t.points
            equityCurve.add(runningPoints)

            if (runningPoints > peakPoints) {
                peakPoints = runningPoints
            }
            val ddPoints = peakPoints - runningPoints
            if (ddPoints > maxDdPoints) {
                maxDdPoints = ddPoints
            }
            val ddPct = when {
                peakPoints > 0.0 -> (ddPoints / peakPoints) * 100.0
                ddPoints > 0.0 -> 100.0
                else -> 0.0
            }
            if (ddPct > maxDdPct) {
                maxDdPct = ddPct
            }

            runningR += t.r
            if (runningR > peakR) {
                peakR = runningR
            }
            val ddR = peakR - runningR
            if (ddR > maxDdR) {
                maxDdR = ddR
            }

            if (t.points > 0.0) {
                curWinStreak++
                curLossStreak = 0
                if (curWinStreak > maxWinStreak) maxWinStreak = curWinStreak
            } else if (t.points < 0.0) {
                curLossStreak++
                curWinStreak = 0
                if (curLossStreak > maxLossStreak) maxLossStreak = curLossStreak
            }
        }

        val buyTrades = closedTrades.filter { it.side == Side.BUY }
        val sellTrades = closedTrades.filter { it.side == Side.SELL }

        val buyStat = SideStat(
            trades = buyTrades.size,
            winRatePct = if (buyTrades.isNotEmpty()) (buyTrades.count { it.points > 0.0 } * 100.0) / buyTrades.size else 0.0,
            netPoints = buyTrades.sumOf { it.points }
        )
        val sellStat = SideStat(
            trades = sellTrades.size,
            winRatePct = if (sellTrades.isNotEmpty()) (sellTrades.count { it.points > 0.0 } * 100.0) / sellTrades.size else 0.0,
            netPoints = sellTrades.sumOf { it.points }
        )

        val avgDuration = closedTrades.map { it.durationCandles }.average()

        return BacktestResult(
            summary = BacktestSummary(
                totalTrades = totalTrades,
                wins = wins,
                losses = losses,
                winRatePct = winRatePct,
                netPoints = netPoints,
                netR = netR,
                avgR = avgR,
                profitFactor = profitFactor,
                skippedByStopCap = skippedByStopCap
            ),
            points = BacktestPoints(
                avgWinPoints = avgWinPoints,
                avgLossPoints = avgLossPoints,
                largestWinPoints = largestWinPoints,
                largestLossPoints = largestLossPoints,
                avgStopDistancePoints = avgStopDist,
                maxStopDistancePoints = maxStopDist,
                avgTargetDistancePoints = avgTargetDist
            ),
            risk = BacktestRisk(
                maxDrawdownPoints = maxDdPoints,
                maxDrawdownPct = maxDdPct,
                maxDrawdownR = maxDdR,
                maxConsecutiveWins = maxWinStreak,
                maxConsecutiveLosses = maxLossStreak
            ),
            bySide = BySideStats(buy = buyStat, sell = sellStat),
            timing = BacktestTiming(avgTradeDurationCandles = avgDuration),
            equityCurve = equityCurve,
            trades = closedTrades,
            warnings = warnings
        )
    }

    private fun emptyResult(warnings: List<String>, skippedByStopCap: Int = 0): BacktestResult {
        return BacktestResult(
            summary = BacktestSummary(
                totalTrades = 0,
                wins = 0,
                losses = 0,
                winRatePct = 0.0,
                netPoints = 0.0,
                netR = 0.0,
                avgR = 0.0,
                profitFactor = 0.0,
                skippedByStopCap = skippedByStopCap
            ),
            points = BacktestPoints(
                avgWinPoints = 0.0,
                avgLossPoints = 0.0,
                largestWinPoints = 0.0,
                largestLossPoints = 0.0,
                avgStopDistancePoints = 0.0,
                maxStopDistancePoints = 0.0,
                avgTargetDistancePoints = 0.0
            ),
            risk = BacktestRisk(
                maxDrawdownPoints = 0.0,
                maxDrawdownPct = 0.0,
                maxDrawdownR = 0.0,
                maxConsecutiveWins = 0,
                maxConsecutiveLosses = 0
            ),
            bySide = BySideStats(
                buy = SideStat(0, 0.0, 0.0),
                sell = SideStat(0, 0.0, 0.0)
            ),
            timing = BacktestTiming(avgTradeDurationCandles = 0.0),
            equityCurve = emptyList(),
            trades = emptyList(),
            warnings = warnings
        )
    }
}
