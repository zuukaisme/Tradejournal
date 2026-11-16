package com.example.backtest.worker

import com.example.backtest.BacktestPoints
import com.example.backtest.BacktestResult
import com.example.backtest.BacktestRisk
import com.example.backtest.BacktestSummary
import com.example.backtest.BacktestTiming
import com.example.backtest.BySideStats
import com.example.backtest.ExitReason
import com.example.backtest.Side
import com.example.backtest.SideStat
import com.example.backtest.Trade
import kotlin.math.abs
import kotlin.math.round

data class TimeShardPlan(
    val shardIndex: Int,
    val entryStart: Int,
    val entryEnd: Int,
    val loadStart: Int,
    val loadEnd: Int
)

/**
 * Pure Kotlin planner and merger for parallel chronological time-range sharding on large CSVs
 * (>= 50 MB). Each shard loads an overlapping indicator warm-up prefix (so EMAs, sessions, and
 * daily levels converge before `entryStart`) and a carry-over exit tail (so open trades can
 * close naturally past `entryEnd`), then deduplicates seam overlaps and computes unified metrics.
 */
object BacktestShardStitcher {

    private const val BE_EPS = 1e-9

    fun planTimeShards(
        totalCandles: Int,
        workerCount: Int,
        warmupBars: Int = 3_000,
        tailBars: Int = 1_500
    ): List<TimeShardPlan> {
        if (totalCandles <= 0 || workerCount <= 1) {
            return listOf(
                TimeShardPlan(
                    shardIndex = 0,
                    entryStart = 0,
                    entryEnd = totalCandles.coerceAtLeast(0),
                    loadStart = 0,
                    loadEnd = totalCandles.coerceAtLeast(0)
                )
            )
        }
        val k = workerCount.coerceAtMost(totalCandles).coerceAtLeast(1)
        return (0 until k).map { idx ->
            val entryStart = ((totalCandles.toLong() * idx) / k).toInt()
            val entryEnd = ((totalCandles.toLong() * (idx + 1)) / k).toInt()
            val loadStart = if (idx == 0) 0 else (entryStart - warmupBars).coerceAtLeast(0)
            val loadEnd = if (idx == k - 1) totalCandles else (entryEnd + tailBars).coerceAtMost(totalCandles)
            TimeShardPlan(
                shardIndex = idx,
                entryStart = entryStart,
                entryEnd = entryEnd,
                loadStart = loadStart,
                loadEnd = loadEnd
            )
        }
    }

    fun stitch(
        shardResults: List<BacktestResult>,
        fullTotalCandles: Int
    ): BacktestResult {
        if (shardResults.size == 1) {
            val only = shardResults.first()
            return only.copy(
                summary = only.summary.copy(totalCandles = fullTotalCandles.coerceAtLeast(only.summary.totalCandles))
            )
        }

        val combinedTrades = ArrayList<Trade>(shardResults.sumOf { it.trades.size })
        var maxClosedIndexSeen = -1
        for (shard in shardResults) {
            val sortedShardTrades = shard.trades.sortedBy { it.openIndex }
            for (t in sortedShardTrades) {
                // Skip any entry triggered on Worker k+1 while Worker k still held a seam position
                if (t.openIndex <= maxClosedIndexSeen) {
                    continue
                }
                combinedTrades.add(t)
                if (t.closeIndex > maxClosedIndexSeen) {
                    maxClosedIndexSeen = t.closeIndex
                }
            }
        }

        val totalTrades = combinedTrades.size
        val openAtEnd = shardResults.lastOrNull()?.summary?.openTrades?.coerceAtLeast(0) ?: 0
        val enteredTrades = totalTrades + openAtEnd
        val totalSkipped = shardResults.sumOf { it.summary.skippedByStopCap }

        var wins = 0
        var losses = 0
        var breakEvens = 0
        var tpExits = 0
        var slExits = 0

        var sumPts = 0.0
        var sumR = 0.0
        var grossWin = 0.0
        var grossLoss = 0.0
        var sumWinPts = 0.0
        var sumLossPts = 0.0
        var largestWin = 0.0
        var largestLoss = 0.0
        var hasWin = false
        var hasLoss = false
        var sumStops = 0.0
        var maxStopDist = 0.0
        var sumTargets = 0.0
        var sumDuration = 0L

        var peakPts = 0.0
        var eqPts = 0.0
        var maxDdPts = 0.0
        var peakR = 0.0
        var eqR = 0.0
        var maxDdR = 0.0

        var cw = 0
        var cl = 0
        var mw = 0
        var ml = 0
        var currentStreak = 0

        var buyTrades = 0
        var buyWins = 0
        var buyNetPts = 0.0
        var sellTrades = 0
        var sellWins = 0
        var sellNetPts = 0.0

        val equityCurve = ArrayList<Double>(totalTrades)

        for (t in combinedTrades) {
            val p = t.points
            val rVal = t.r
            sumPts += p
            sumR += rVal

            val stopD = abs(t.entry - t.sl)
            val targetD = abs(t.tp - t.entry)
            sumStops += stopD
            if (stopD > maxStopDist) maxStopDist = stopD
            sumTargets += targetD
            sumDuration += t.durationCandles.toLong()

            when (t.reason) {
                ExitReason.TP -> tpExits++
                ExitReason.SL -> slExits++
                else -> {}
            }

            if (rVal > BE_EPS || (abs(rVal) <= BE_EPS && p > BE_EPS)) {
                wins++
                sumWinPts += p
                grossWin += if (rVal > BE_EPS) rVal else p
                if (!hasWin || p > largestWin) {
                    largestWin = p
                    hasWin = true
                }
                cw++
                cl = 0
                currentStreak = cw
                if (cw > mw) mw = cw
            } else if (rVal < -BE_EPS || (abs(rVal) <= BE_EPS && p < -BE_EPS)) {
                losses++
                sumLossPts += p
                grossLoss += if (rVal < -BE_EPS) -rVal else -p
                if (!hasLoss || p < largestLoss) {
                    largestLoss = p
                    hasLoss = true
                }
                cl++
                cw = 0
                currentStreak = -cl
                if (cl > ml) ml = cl
            } else {
                breakEvens++
                cw = 0
                cl = 0
                currentStreak = 0
            }

            if (t.side == Side.BUY) {
                buyTrades++
                buyNetPts += p
                if (rVal > BE_EPS || (abs(rVal) <= BE_EPS && p > BE_EPS)) buyWins++
            } else {
                sellTrades++
                sellNetPts += p
                if (rVal > BE_EPS || (abs(rVal) <= BE_EPS && p > BE_EPS)) sellWins++
            }

            eqPts += p
            eqR += rVal
            equityCurve.add(round2(eqPts))
            if (eqPts > peakPts) peakPts = eqPts
            if (peakPts - eqPts > maxDdPts) maxDdPts = peakPts - eqPts
            if (eqR > peakR) peakR = eqR
            if (peakR - eqR > maxDdR) maxDdR = peakR - eqR
        }

        val warnings = mutableListOf<String>()
        if (totalTrades in 1 until 50) {
            warnings.add("Under 50 trades, results are not statistically reliable")
        }
        if (totalTrades == 0) {
            warnings.add("No trades taken")
        }

        val pf: Double? = if (grossLoss > 0.0) round2(grossWin / grossLoss) else null

        return BacktestResult(
            summary = BacktestSummary(
                totalTrades = totalTrades,
                enteredTrades = enteredTrades,
                openTrades = openAtEnd,
                wins = wins,
                losses = losses,
                breakEvens = breakEvens,
                winRatePct = if (totalTrades > 0) round1(wins * 100.0 / totalTrades) else 0.0,
                lossRatePct = if (totalTrades > 0) round1(losses * 100.0 / totalTrades) else 0.0,
                breakEvenRatePct = if (totalTrades > 0) round1(breakEvens * 100.0 / totalTrades) else 0.0,
                netPoints = round2(sumPts),
                netR = round2(sumR),
                avgR = if (totalTrades > 0) round3(sumR / totalTrades) else 0.0,
                expectancyPoints = if (totalTrades > 0) round2(sumPts / totalTrades) else 0.0,
                profitFactor = pf,
                skippedByStopCap = totalSkipped,
                tpExits = tpExits,
                slExits = slExits,
                totalCandles = fullTotalCandles,
                currentStreak = currentStreak
            ),
            points = BacktestPoints(
                avgWinPoints = if (wins > 0) round2(sumWinPts / wins) else 0.0,
                avgLossPoints = if (losses > 0) round2(sumLossPts / losses) else 0.0,
                largestWinPoints = if (hasWin) round2(largestWin) else 0.0,
                largestLossPoints = if (hasLoss) round2(largestLoss) else 0.0,
                avgStopDistancePoints = if (totalTrades > 0) round2(sumStops / totalTrades) else 0.0,
                maxStopDistancePoints = round2(maxStopDist),
                avgTargetDistancePoints = if (totalTrades > 0) round2(sumTargets / totalTrades) else 0.0
            ),
            risk = BacktestRisk(
                maxDrawdownPoints = round2(maxDdPts),
                maxDrawdownR = round2(maxDdR),
                maxConsecutiveWins = mw,
                maxConsecutiveLosses = ml
            ),
            bySide = BySideStats(
                buy = SideStat(
                    trades = buyTrades,
                    winRatePct = if (buyTrades > 0) round1(buyWins * 100.0 / buyTrades) else 0.0,
                    netPoints = round2(buyNetPts)
                ),
                sell = SideStat(
                    trades = sellTrades,
                    winRatePct = if (sellTrades > 0) round1(sellWins * 100.0 / sellTrades) else 0.0,
                    netPoints = round2(sellNetPts)
                )
            ),
            timing = BacktestTiming(
                avgTradeDurationCandles = if (totalTrades > 0) round1(sumDuration.toDouble() / totalTrades) else 0.0
            ),
            equityCurve = equityCurve,
            trades = combinedTrades,
            warnings = warnings
        )
    }

    private fun round1(v: Double): Double = round(v * 10.0) / 10.0
    private fun round2(v: Double): Double = round(v * 100.0) / 100.0
    private fun round3(v: Double): Double = round(v * 1000.0) / 1000.0
}
