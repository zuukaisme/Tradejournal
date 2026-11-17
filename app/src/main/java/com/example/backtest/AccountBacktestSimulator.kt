package com.example.backtest

import com.example.domain.model.TradingAccountProfile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class AccountInstrumentPreset(
    val label: String,
    val valuePerPointPerLot: Double,
    val contractSizePerLot: Double,
    val hint: String
)

data class AccountStarterTemplate(
    val title: String,
    val category: String,
    val startingBalance: Double,
    val defaultLotSize: Double,
    val leverage: Int,
    val commissionPerLot: Double,
    val valuePerPointPerLot: Double,
    val contractSizePerLot: Double,
    val stopOutLevelPct: Double,
    val instrumentLabel: String
)

data class SimulatedAccountTrade(
    val index: Int,
    val side: Side,
    val openTime: String,
    val closeTime: String,
    val entryPrice: Double,
    val exitPrice: Double,
    val reason: ExitReason,
    val points: Double,
    val lotSize: Double,
    val requiredMargin: Double,
    val freeMarginAtEntry: Double,
    val marginLevelPct: Double,
    val grossPnL: Double,
    val commission: Double,
    val netPnL: Double,
    val runningBalanceAfter: Double,
    val hasMarginInsufficient: Boolean,
    val hasStopOut: Boolean
) {
    val hasMarginAlert: Boolean get() = hasMarginInsufficient || hasStopOut
}

data class AccountSimulationResult(
    val startingBalance: Double,
    val endingBalance: Double,
    val netProfit: Double,
    val roiPct: Double,
    val fixedLotSize: Double,
    val leverage: Int,
    val valuePerPointPerLot: Double,
    val contractSizePerLot: Double,
    val commissionPerLot: Double,
    val stopOutLevelPct: Double,
    val grossProfit: Double,
    val grossLoss: Double,
    val totalCommission: Double,
    val profitFactor: Double?,
    val maxDrawdownCurrency: Double,
    val maxDrawdownPct: Double,
    val peakBalance: Double,
    val lowestBalance: Double,
    val peakMarginUsed: Double,
    val avgMarginPerTrade: Double,
    val lowestFreeMargin: Double,
    val lowestMarginLevelPct: Double?,
    val insufficientMarginCount: Int,
    val stopOutCount: Int,
    val firstStopOutTradeIndex: Int?,
    val isAccountBlown: Boolean,
    val winningTrades: Int,
    val losingTrades: Int,
    val breakEvenTrades: Int,
    val balanceCurve: List<Double>,
    val items: List<SimulatedAccountTrade>
)

object AccountBacktestSimulator {

    val instrumentPresets: List<AccountInstrumentPreset> = listOf(
        AccountInstrumentPreset(
            label = "XAUUSD (0.01 pt)",
            valuePerPointPerLot = 1.0,
            contractSizePerLot = 100.0,
            hint = "100 oz/lot • Use when Backtest Point Size = 0.01 ($1.00 per pt @ 1.0 lot)"
        ),
        AccountInstrumentPreset(
            label = "XAUUSD ($1.00 move)",
            valuePerPointPerLot = 100.0,
            contractSizePerLot = 100.0,
            hint = "100 oz/lot • Use when Backtest Point Size = 1.0 ($100 per $1 move @ 1.0 lot)"
        ),
        AccountInstrumentPreset(
            label = "EURUSD (Pip)",
            valuePerPointPerLot = 10.0,
            contractSizePerLot = 100_000.0,
            hint = "100k units/lot • Use when Point Size = 0.0001 ($10 per pip @ 1.0 lot)"
        ),
        AccountInstrumentPreset(
            label = "US30 / NAS100",
            valuePerPointPerLot = 1.0,
            contractSizePerLot = 1.0,
            hint = "1 index contract/lot • $1.00 per point @ 1.0 lot"
        ),
        AccountInstrumentPreset(
            label = "SyntX (Vol / PainX)",
            valuePerPointPerLot = 1.0,
            contractSizePerLot = 1.0,
            hint = "Synthetic index • $1.00 per point @ 1.0 lot"
        )
    )

    val starterTemplates: List<AccountStarterTemplate> = listOf(
        AccountStarterTemplate(
            title = "Standard Broker",
            category = "Standard",
            startingBalance = 10_000.0,
            defaultLotSize = 0.10,
            leverage = 100,
            commissionPerLot = 0.0,
            valuePerPointPerLot = 1.0,
            contractSizePerLot = 100.0,
            stopOutLevelPct = 50.0,
            instrumentLabel = "XAUUSD (0.01 pt)"
        ),
        AccountStarterTemplate(
            title = "Raw ECN Spread",
            category = "Raw ECN",
            startingBalance = 25_000.0,
            defaultLotSize = 0.25,
            leverage = 500,
            commissionPerLot = 7.0,
            valuePerPointPerLot = 1.0,
            contractSizePerLot = 100.0,
            stopOutLevelPct = 30.0,
            instrumentLabel = "XAUUSD (0.01 pt)"
        ),
        AccountStarterTemplate(
            title = "Funded 100K Prop",
            category = "Funded Prop",
            startingBalance = 100_000.0,
            defaultLotSize = 1.00,
            leverage = 30,
            commissionPerLot = 5.0,
            valuePerPointPerLot = 1.0,
            contractSizePerLot = 100.0,
            stopOutLevelPct = 50.0,
            instrumentLabel = "XAUUSD (0.01 pt)"
        ),
        AccountStarterTemplate(
            title = "Small / Cent Desk",
            category = "Micro / Cent",
            startingBalance = 500.0,
            defaultLotSize = 0.01,
            leverage = 500,
            commissionPerLot = 0.0,
            valuePerPointPerLot = 1.0,
            contractSizePerLot = 100.0,
            stopOutLevelPct = 20.0,
            instrumentLabel = "XAUUSD (0.01 pt)"
        )
    )

    fun fallbackProfile(testerLot: Double, testerLeverage: Int): TradingAccountProfile {
        return TradingAccountProfile(
            id = 0L,
            name = "Default Account",
            accountCategory = "Standard",
            startingBalance = 10_000.0,
            defaultLotSize = testerLot.coerceAtLeast(0.001),
            leverage = testerLeverage.coerceIn(1, 5000),
            commissionPerLot = 0.0,
            valuePerPointPerLot = 1.0,
            contractSizePerLot = 100.0,
            stopOutLevelPct = 50.0,
            instrumentLabel = "XAUUSD (0.01 pt)"
        )
    }

    fun simulateFixedLot(
        trades: List<Trade>,
        startingBalance: Double,
        fixedLotSize: Double,
        leverage: Int,
        valuePerPointPerLot: Double,
        contractSizePerLot: Double,
        commissionPerLot: Double,
        stopOutLevelPct: Double
    ): AccountSimulationResult {
        val safeStart = startingBalance.coerceAtLeast(1.0)
        val safeLot = fixedLotSize.coerceAtLeast(0.001)
        val safeLev = leverage.coerceIn(1, 5000)
        val safeValuePerPt = valuePerPointPerLot.coerceAtLeast(0.0001)
        val safeContract = contractSizePerLot.coerceAtLeast(0.0)
        val safeCommission = commissionPerLot.coerceAtLeast(0.0)
        val safeStopOut = stopOutLevelPct.coerceIn(0.0, 200.0)

        var runningBalance = safeStart
        var peakBalance = safeStart
        var lowestBalance = safeStart
        var maxDrawdownCurrency = 0.0
        var maxDrawdownPct = 0.0
        var grossProfit = 0.0
        var grossLossAbs = 0.0
        var totalCommission = 0.0
        var totalMarginSum = 0.0
        var peakMarginUsed = 0.0
        var lowestFreeMargin = safeStart
        var lowestMarginLevelPct: Double? = null
        var insufficientMarginCount = 0
        var stopOutCount = 0
        var firstStopOutIdx: Int? = null
        var isAccountBlown = false
        var winningTrades = 0
        var losingTrades = 0
        var breakEvenTrades = 0

        val curve = ArrayList<Double>(trades.size + 1)
        curve.add(safeStart)
        val simulatedItems = ArrayList<SimulatedAccountTrade>(trades.size)

        trades.forEachIndexed { idx, trade ->
            val tradeNumber = idx + 1
            val requiredMargin = if (safeContract > 0.0 && trade.entry > 0.0) {
                (trade.entry * safeContract * safeLot) / safeLev.toDouble()
            } else {
                0.0
            }
            val freeMargin = runningBalance - requiredMargin
            val marginInsufficient = requiredMargin > runningBalance || runningBalance <= 0.0

            val grossPnL = trade.points * safeLot * safeValuePerPt
            val commission = safeLot * safeCommission
            val netPnL = grossPnL - commission
            val balanceAfter = runningBalance + netPnL

            val lowestEquityDuringTrade = min(runningBalance, balanceAfter)
            val marginLevelPct = if (requiredMargin > 0.0) {
                (runningBalance / requiredMargin) * 100.0
            } else {
                10_000.0
            }
            val worstMarginLevelPct = if (requiredMargin > 0.0) {
                (lowestEquityDuringTrade / requiredMargin) * 100.0
            } else {
                10_000.0
            }
            val hitStopOut = balanceAfter <= 0.0 ||
                (requiredMargin > 0.0 && safeStopOut > 0.0 && worstMarginLevelPct <= safeStopOut)

            if (marginInsufficient) insufficientMarginCount++
            if (hitStopOut) {
                stopOutCount++
                if (firstStopOutIdx == null) firstStopOutIdx = tradeNumber
            }
            if (balanceAfter <= 0.0) {
                isAccountBlown = true
            }

            totalMarginSum += requiredMargin
            peakMarginUsed = max(peakMarginUsed, requiredMargin)
            lowestFreeMargin = min(lowestFreeMargin, freeMargin)
            if (requiredMargin > 0.0) {
                val trackedLevel = worstMarginLevelPct
                lowestMarginLevelPct = if (lowestMarginLevelPct == null) trackedLevel else min(lowestMarginLevelPct!!, trackedLevel)
            }

            if (netPnL > 0.0001) {
                winningTrades++
                grossProfit += netPnL
            } else if (netPnL < -0.0001) {
                losingTrades++
                grossLossAbs += abs(netPnL)
            } else {
                breakEvenTrades++
            }
            totalCommission += commission

            runningBalance = balanceAfter
            curve.add(runningBalance)
            peakBalance = max(peakBalance, runningBalance)
            lowestBalance = min(lowestBalance, runningBalance)

            val ddCurrency = (peakBalance - runningBalance).coerceAtLeast(0.0)
            if (ddCurrency > maxDrawdownCurrency) {
                maxDrawdownCurrency = ddCurrency
            }
            val ddPct = if (peakBalance > 0.0) (ddCurrency / peakBalance) * 100.0 else 0.0
            if (ddPct > maxDrawdownPct) {
                maxDrawdownPct = ddPct
            }

            simulatedItems.add(
                SimulatedAccountTrade(
                    index = tradeNumber,
                    side = trade.side,
                    openTime = trade.openTime,
                    closeTime = trade.closeTime,
                    entryPrice = trade.entry,
                    exitPrice = trade.exit,
                    reason = trade.reason,
                    points = trade.points,
                    lotSize = safeLot,
                    requiredMargin = requiredMargin,
                    freeMarginAtEntry = freeMargin,
                    marginLevelPct = marginLevelPct,
                    grossPnL = grossPnL,
                    commission = commission,
                    netPnL = netPnL,
                    runningBalanceAfter = runningBalance,
                    hasMarginInsufficient = marginInsufficient,
                    hasStopOut = hitStopOut
                )
            )
        }

        val netProfit = runningBalance - safeStart
        val roiPct = (netProfit / safeStart) * 100.0
        val profitFactor = when {
            grossLossAbs > 0.0001 -> grossProfit / grossLossAbs
            grossProfit > 0.0001 -> null
            else -> 0.0
        }
        val avgMargin = if (trades.isNotEmpty()) totalMarginSum / trades.size else 0.0

        return AccountSimulationResult(
            startingBalance = safeStart,
            endingBalance = runningBalance,
            netProfit = netProfit,
            roiPct = roiPct,
            fixedLotSize = safeLot,
            leverage = safeLev,
            valuePerPointPerLot = safeValuePerPt,
            contractSizePerLot = safeContract,
            commissionPerLot = safeCommission,
            stopOutLevelPct = safeStopOut,
            grossProfit = grossProfit,
            grossLoss = -grossLossAbs,
            totalCommission = totalCommission,
            profitFactor = profitFactor,
            maxDrawdownCurrency = maxDrawdownCurrency,
            maxDrawdownPct = maxDrawdownPct,
            peakBalance = peakBalance,
            lowestBalance = lowestBalance,
            peakMarginUsed = peakMarginUsed,
            avgMarginPerTrade = avgMargin,
            lowestFreeMargin = lowestFreeMargin,
            lowestMarginLevelPct = lowestMarginLevelPct,
            insufficientMarginCount = insufficientMarginCount,
            stopOutCount = stopOutCount,
            firstStopOutTradeIndex = firstStopOutIdx,
            isAccountBlown = isAccountBlown,
            winningTrades = winningTrades,
            losingTrades = losingTrades,
            breakEvenTrades = breakEvenTrades,
            balanceCurve = curve,
            items = simulatedItems
        )
    }
}
