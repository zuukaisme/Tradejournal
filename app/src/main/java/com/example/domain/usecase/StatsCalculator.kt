package com.example.domain.usecase

import com.example.domain.model.DayOfWeekStat
import com.example.domain.model.DayPnLSummary
import com.example.domain.model.InstrumentStat
import com.example.domain.model.PerformanceSummary
import com.example.domain.model.RuleAdherenceStat
import com.example.domain.model.StrategyStat
import com.example.domain.model.TimePeriod
import com.example.domain.model.Trade
import com.example.domain.model.TradeResult
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

object StatsCalculator {

    fun filterTradesByPeriod(
        trades: List<Trade>,
        period: TimePeriod,
        customStartDate: String? = null,
        customEndDate: String? = null
    ): List<Trade> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayCal = Calendar.getInstance()
        val todayStr = sdf.format(todayCal.time)

        return when (period) {
            TimePeriod.TODAY -> trades.filter { it.date == todayStr }
            TimePeriod.THIS_WEEK -> {
                val cal = Calendar.getInstance()
                cal.firstDayOfWeek = Calendar.MONDAY
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val startOfWeek = cal.timeInMillis
                trades.filter { it.timestamp >= startOfWeek }
            }
            TimePeriod.THIS_MONTH -> {
                val currentYearMonth = todayStr.substring(0, minOf(7, todayStr.length)) // "YYYY-MM"
                trades.filter { it.date.startsWith(currentYearMonth) }
            }
            TimePeriod.THIS_YEAR -> {
                val currentYear = todayStr.substring(0, minOf(4, todayStr.length)) // "YYYY"
                trades.filter { it.date.startsWith(currentYear) }
            }
            TimePeriod.ALL_TIME -> trades
            TimePeriod.CUSTOM -> {
                if (customStartDate == null && customEndDate == null) trades
                else trades.filter { trade ->
                    val afterStart = customStartDate == null || trade.date >= customStartDate
                    val beforeEnd = customEndDate == null || trade.date <= customEndDate
                    afterStart && beforeEnd
                }
            }
        }
    }

    fun calculate(
        trades: List<Trade>,
        period: TimePeriod = TimePeriod.ALL_TIME
    ): PerformanceSummary {
        val validTrades = trades.filter { it.result != TradeResult.NO_TRADE }
        val daysNotTraded = trades.count { it.result == TradeResult.NO_TRADE }

        val totalTrades = validTrades.size
        val winningTrades = validTrades.count { it.pnl > 0 || it.result == TradeResult.WIN }
        val losingTrades = validTrades.count { it.pnl < 0 || it.result == TradeResult.LOSS }
        val breakEvenTrades = validTrades.count { it.pnl == 0.0 || it.result == TradeResult.BREAK_EVEN }

        val netPnL = validTrades.sumOf { it.pnl }
        val grossProfit = validTrades.filter { it.pnl > 0 }.sumOf { it.pnl }
        val grossLoss = abs(validTrades.filter { it.pnl < 0 }.sumOf { it.pnl })

        val winRate = if (totalTrades > 0) (winningTrades.toDouble() / totalTrades.toDouble()) * 100.0 else 0.0
        val averageWin = if (winningTrades > 0) grossProfit / winningTrades else 0.0
        val averageLoss = if (losingTrades > 0) grossLoss / losingTrades else 0.0

        val profitFactor = when {
            grossLoss == 0.0 && grossProfit > 0 -> 99.9
            grossLoss == 0.0 -> 0.0
            else -> grossProfit / grossLoss
        }

        // Daily PnL grouping
        val tradesByDate = trades.groupBy { it.date }
        val dailyPnLList = tradesByDate.map { (date, dayTrades) ->
            val dayNet = dayTrades.filter { it.result != TradeResult.NO_TRADE }.sumOf { it.pnl }
            val wins = dayTrades.count { it.pnl > 0 || it.result == TradeResult.WIN }
            val losses = dayTrades.count { it.pnl < 0 || it.result == TradeResult.LOSS }
            val be = dayTrades.count { it.pnl == 0.0 && it.result == TradeResult.BREAK_EVEN }
            val noTrade = dayTrades.any { it.result == TradeResult.NO_TRADE } && dayTrades.size == 1
            DayPnLSummary(
                date = date,
                netPnL = dayNet,
                tradesCount = dayTrades.size,
                wins = wins,
                losses = losses,
                breakEvens = be,
                noTrade = noTrade
            )
        }.sortedBy { it.date }

        val winningDays = dailyPnLList.count { it.netPnL > 0 }
        val losingDays = dailyPnLList.count { it.netPnL < 0 }
        val breakEvenDays = dailyPnLList.count { it.netPnL == 0.0 && !it.noTrade }

        val bestDay = dailyPnLList.maxOfOrNull { it.netPnL } ?: 0.0
        val worstDay = dailyPnLList.minOfOrNull { it.netPnL } ?: 0.0

        // Streaks & Drawdown (ordered chronologically)
        val sortedTrades = validTrades.sortedBy { it.timestamp }
        var currentWinStreak = 0
        var maxWinStreak = 0
        var currentLossStreak = 0
        var maxLossStreak = 0

        var runningEquity = 0.0
        var peakEquity = 0.0
        var maxDrawdown = 0.0
        val equityCurve = mutableListOf<Pair<String, Double>>()
        equityCurve.add(Pair("Start", 0.0))

        for (t in sortedTrades) {
            if (t.pnl > 0) {
                currentWinStreak++
                currentLossStreak = 0
                maxWinStreak = max(maxWinStreak, currentWinStreak)
            } else if (t.pnl < 0) {
                currentLossStreak++
                currentWinStreak = 0
                maxLossStreak = max(maxLossStreak, currentLossStreak)
            }

            runningEquity += t.pnl
            equityCurve.add(Pair(t.date, runningEquity))

            if (runningEquity > peakEquity) {
                peakEquity = runningEquity
            }
            val dd = peakEquity - runningEquity
            if (dd > maxDrawdown) {
                maxDrawdown = dd
            }
        }

        val maxDrawdownPct = if (peakEquity > 0) (maxDrawdown / peakEquity) * 100.0 else 0.0

        // Strategy Breakdown
        val strategyMap = mutableMapOf<String, StrategyStat>()
        val groupedByStrategy = validTrades.groupBy { if (it.strategyName.isNotBlank()) it.strategyName else "No Strategy" }
        for ((stratName, sTrades) in groupedByStrategy) {
            val sWins = sTrades.count { it.pnl > 0 }
            val sLosses = sTrades.count { it.pnl < 0 }
            val sWinRate = if (sTrades.isNotEmpty()) (sWins.toDouble() / sTrades.size) * 100.0 else 0.0
            val sPnL = sTrades.sumOf { it.pnl }
            val sGrossProfit = sTrades.filter { it.pnl > 0 }.sumOf { it.pnl }
            val sGrossLoss = abs(sTrades.filter { it.pnl < 0 }.sumOf { it.pnl })
            val sProfitFactor = when {
                sGrossLoss == 0.0 && sGrossProfit > 0 -> 99.9
                sGrossLoss == 0.0 -> 0.0
                else -> sGrossProfit / sGrossLoss
            }
            val sAveragePnL = if (sTrades.isNotEmpty()) sPnL / sTrades.size else 0.0
            strategyMap[stratName] = StrategyStat(
                strategyName = stratName,
                trades = sTrades.size,
                wins = sWins,
                losses = sLosses,
                winRate = sWinRate,
                totalPnL = sPnL,
                grossProfit = sGrossProfit,
                grossLoss = sGrossLoss,
                profitFactor = sProfitFactor,
                averagePnL = sAveragePnL
            )
        }

        // Instrument Breakdown
        val instrumentMap = mutableMapOf<String, InstrumentStat>()
        val groupedByInstrument = validTrades.groupBy { if (it.instrument.isNotBlank()) it.instrument.uppercase() else "OTHER" }
        for ((inst, iTrades) in groupedByInstrument) {
            val iWins = iTrades.count { it.pnl > 0 }
            val iLosses = iTrades.count { it.pnl < 0 }
            val iWinRate = if (iTrades.isNotEmpty()) (iWins.toDouble() / iTrades.size) * 100.0 else 0.0
            val iPnL = iTrades.sumOf { it.pnl }
            instrumentMap[inst] = InstrumentStat(inst, iTrades.size, iWins, iLosses, iWinRate, iPnL)
        }

        // Day of Week Breakdown
        val dayOfWeekMap = mutableMapOf<String, DayOfWeekStat>()
        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val sdfDay = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()

        val tradesByDayOfWeek = validTrades.groupBy { trade ->
            try {
                val date = sdfDay.parse(trade.date)
                if (date != null) {
                    cal.time = date
                    when (cal.get(Calendar.DAY_OF_WEEK)) {
                        Calendar.MONDAY -> "Mon"
                        Calendar.TUESDAY -> "Tue"
                        Calendar.WEDNESDAY -> "Wed"
                        Calendar.THURSDAY -> "Thu"
                        Calendar.FRIDAY -> "Fri"
                        Calendar.SATURDAY -> "Sat"
                        Calendar.SUNDAY -> "Sun"
                        else -> "Mon"
                    }
                } else "Mon"
            } catch (e: Exception) {
                "Mon"
            }
        }

        for (day in dayNames) {
            val dTrades = tradesByDayOfWeek[day] ?: emptyList()
            val dWins = dTrades.count { it.pnl > 0 }
            val dWinRate = if (dTrades.isNotEmpty()) (dWins.toDouble() / dTrades.size) * 100.0 else 0.0
            val dPnL = dTrades.sumOf { it.pnl }
            dayOfWeekMap[day] = DayOfWeekStat(day, dTrades.size, dWinRate, dPnL)
        }

        // Rule Adherence Breakdown
        val followedTrades = validTrades.filter { it.followedRules }
        val violatedTrades = validTrades.filter { !it.followedRules }
        val ruleStat = RuleAdherenceStat(
            followedTrades = followedTrades.size,
            followedWins = followedTrades.count { it.pnl > 0 },
            followedPnL = followedTrades.sumOf { it.pnl },
            violatedTrades = violatedTrades.size,
            violatedWins = violatedTrades.count { it.pnl > 0 },
            violatedPnL = violatedTrades.sumOf { it.pnl }
        )

        return PerformanceSummary(
            period = period,
            netPnL = netPnL,
            winRate = winRate,
            totalTrades = totalTrades,
            winningTrades = winningTrades,
            losingTrades = losingTrades,
            breakEvenTrades = breakEvenTrades,
            daysNotTraded = daysNotTraded,
            winningDays = winningDays,
            losingDays = losingDays,
            breakEvenDays = breakEvenDays,
            averageWin = averageWin,
            averageLoss = averageLoss,
            profitFactor = profitFactor,
            bestDay = bestDay,
            worstDay = worstDay,
            longestWinningStreak = maxWinStreak,
            longestLosingStreak = maxLossStreak,
            maxDrawdown = maxDrawdown,
            maxDrawdownPct = maxDrawdownPct,
            dailyPnLList = dailyPnLList,
            equityCurve = equityCurve,
            strategyBreakdown = strategyMap,
            instrumentBreakdown = instrumentMap,
            dayOfWeekBreakdown = dayOfWeekMap,
            ruleAdherenceBreakdown = ruleStat
        )
    }
}
