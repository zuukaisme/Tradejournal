package com.example

import com.example.backtest.AccountBacktestSimulator
import com.example.backtest.ExitReason
import com.example.backtest.Side
import com.example.backtest.Trade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountBacktestSimulatorTest {

    private fun sampleTrade(
        side: Side = Side.BUY,
        entry: Double = 2500.0,
        sl: Double = 2490.0,
        tp: Double = 2520.0,
        exit: Double = 2520.0,
        reason: ExitReason = ExitReason.TP,
        points: Double = 20.0
    ) = Trade(
        side = side,
        entry = entry,
        sl = sl,
        tp = tp,
        exit = exit,
        openTime = "2025.02.10 10:00:00",
        closeTime = "2025.02.10 11:00:00",
        reason = reason,
        points = points,
        r = points / 10.0,
        durationCandles = 4,
        openIndex = 10,
        closeIndex = 14
    )

    @Test
    fun simulateFixedLot_computesExactCurrencyPnLCommissionDrawdownAndConstantLot() {
        val trades = listOf(
            sampleTrade(points = 200.0, reason = ExitReason.TP),  // +200 * 0.5 * 1 - 3.5 = +96.5 -> 10096.5
            sampleTrade(side = Side.SELL, points = -150.0, reason = ExitReason.SL), // -150 * 0.5 * 1 - 3.5 = -78.5 -> 10018.0
            sampleTrade(points = 0.0, reason = ExitReason.BE),    // 0 - 3.5 = -3.5 -> 10014.5
            sampleTrade(points = 400.0, reason = ExitReason.TP)   // +200 - 3.5 = +196.5 -> 10211.0
        )

        val res = AccountBacktestSimulator.simulateFixedLot(
            trades = trades,
            startingBalance = 10_000.0,
            fixedLotSize = 0.50,
            leverage = 100,
            valuePerPointPerLot = 1.0,
            contractSizePerLot = 100.0,
            commissionPerLot = 7.0,
            stopOutLevelPct = 50.0
        )

        assertEquals(10_000.0, res.startingBalance, 1e-6)
        assertEquals(10_211.0, res.endingBalance, 1e-6)
        assertEquals(211.0, res.netProfit, 1e-6)
        assertEquals(2.11, res.roiPct, 1e-6)
        assertEquals(0.50, res.fixedLotSize, 1e-6)
        assertEquals(100, res.leverage)
        assertEquals(14.0, res.totalCommission, 1e-6)
        assertEquals(293.0, res.grossProfit, 1e-6)
        assertEquals(-82.0, res.grossLoss, 1e-6)
        assertEquals(293.0 / 82.0, res.profitFactor!!, 1e-6)
        assertEquals(2, res.winningTrades)
        assertEquals(2, res.losingTrades)
        assertEquals(0, res.breakEvenTrades)
        assertEquals(82.0, res.maxDrawdownCurrency, 1e-6)
        assertEquals((82.0 / 10096.5) * 100.0, res.maxDrawdownPct, 1e-6)
        assertEquals(5, res.balanceCurve.size)
        assertTrue(res.items.all { it.lotSize == 0.50 })
        // Required margin per trade: 2500 * 100 * 0.5 / 100 = 1250.0
        assertEquals(1250.0, res.items.first().requiredMargin, 1e-6)
        assertFalse(res.isAccountBlown)
        assertEquals(0, res.stopOutCount)
        assertEquals(0, res.insufficientMarginCount)
    }

    @Test
    fun simulateFixedLot_detectsInsufficientMarginStopOutAndAccountBlown() {
        val heavyLossTrade = sampleTrade(
            entry = 2600.0,
            points = -600.0,
            reason = ExitReason.SL
        )
        // Margin for 1.0 lot @ 2600 with 100 contract & 1:30 leverage = 260000 / 30 = 8666.67
        // On a $500 balance -> insufficient margin and immediate stop-out & blown balance!
        val res = AccountBacktestSimulator.simulateFixedLot(
            trades = listOf(heavyLossTrade),
            startingBalance = 500.0,
            fixedLotSize = 1.0,
            leverage = 30,
            valuePerPointPerLot = 1.0,
            contractSizePerLot = 100.0,
            commissionPerLot = 5.0,
            stopOutLevelPct = 50.0
        )

        assertEquals(1, res.insufficientMarginCount)
        assertEquals(1, res.stopOutCount)
        assertEquals(1, res.firstStopOutTradeIndex)
        assertTrue(res.isAccountBlown)
        assertTrue(res.endingBalance < 0.0)
        assertTrue(res.items.single().hasMarginAlert)
        assertTrue(res.items.single().hasMarginInsufficient)
        assertTrue(res.items.single().hasStopOut)
    }

    @Test
    fun simulateFixedLot_handlesEmptyTradesGracefully() {
        val res = AccountBacktestSimulator.simulateFixedLot(
            trades = emptyList(),
            startingBalance = 2500.0,
            fixedLotSize = 0.10,
            leverage = 200,
            valuePerPointPerLot = 1.0,
            contractSizePerLot = 100.0,
            commissionPerLot = 0.0,
            stopOutLevelPct = 50.0
        )

        assertEquals(2500.0, res.startingBalance, 1e-6)
        assertEquals(2500.0, res.endingBalance, 1e-6)
        assertEquals(0.0, res.netProfit, 1e-6)
        assertEquals(listOf(2500.0), res.balanceCurve)
        assertTrue(res.items.isEmpty())
        assertNull(res.lowestMarginLevelPct)
        assertNotNull(res.profitFactor)
        assertEquals(0.0, res.profitFactor!!, 1e-6)
    }
}
