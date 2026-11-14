package com.example

import com.example.domain.model.NewsEventType
import com.example.domain.model.NewsRelease
import com.example.domain.model.SyntheticIndexCatalog
import com.example.domain.model.TimePeriod
import com.example.domain.model.Trade
import com.example.domain.model.TradeResult
import com.example.domain.usecase.GoldOutlookModel
import com.example.domain.usecase.StatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun stats_calculator_computes_correct_win_rate_and_pnl() {
        val trades = listOf(
            Trade(
                id = 1,
                date = "2026-11-07",
                result = TradeResult.WIN,
                pnl = 500.0,
                instrument = "XAUUSD",
                strategyId = 1,
                strategyName = "Breakout"
            ),
            Trade(
                id = 2,
                date = "2026-11-07",
                result = TradeResult.LOSS,
                pnl = -200.0,
                instrument = "PainX 400",
                strategyId = 1,
                strategyName = "Breakout"
            ),
            Trade(
                id = 3,
                date = "2026-11-08",
                result = TradeResult.WIN,
                pnl = 300.0,
                instrument = "FX Vol 20",
                strategyId = 2,
                strategyName = "Mean Reversion"
            )
        )

        val stats = StatsCalculator.calculate(trades, TimePeriod.ALL_TIME)
        assertEquals(600.0, stats.netPnL, 0.01)
        assertEquals(66.66, stats.winRate, 0.1)
        assertEquals(3, stats.totalTrades)
        assertEquals(2, stats.winningTrades)
        assertEquals(1, stats.losingTrades)
        assertEquals(4.0, stats.profitFactor, 0.01)

        val topStrategy = stats.strategyBreakdown.values.maxByOrNull { it.totalPnL }!!
        assertEquals("Breakout", topStrategy.strategyName)
        assertEquals(300.0, topStrategy.totalPnL, 0.01)
        assertEquals(2.5, topStrategy.profitFactor, 0.01)
        assertEquals(150.0, topStrategy.averagePnL, 0.01)
    }

    @Test
    fun synthetic_index_catalog_detects_wltrade_syntx_instruments() {
        assertTrue(SyntheticIndexCatalog.isSyntheticIndex("PainX 400"))
        assertTrue(SyntheticIndexCatalog.isSyntheticIndex("FX Vol 20"))
        assertTrue(SyntheticIndexCatalog.isSyntheticIndex("Boom 500"))
        assertFalse(SyntheticIndexCatalog.isSyntheticIndex("XAUUSD"))
    }

    @Test
    fun gold_outlook_model_calculates_bounded_probabilities() {
        val testReleases = listOf(
            NewsRelease(
                id = 1,
                eventType = NewsEventType.NFP,
                dateString = "2026-10-02",
                timestamp = 1759363200000L,
                forecast = 150.0,
                previous = 142.0,
                actual = 254.0,
                surprise = 104.0,
                unit = "K",
                gold15mDelta = -28.5,
                gold60mDelta = -34.8
            )
        )
        val pred = GoldOutlookModel.computePrediction(
            eventType = NewsEventType.NFP,
            releaseDate = "2026-11-14",
            releaseTimestamp = System.currentTimeMillis() + 86400000L,
            previous = 254.0,
            claimsTrend = 4.1,
            unemploymentChange = 0.1,
            wageGrowthYoY = 3.8,
            historicalReleases = testReleases
        )

        assertTrue(pred.probGoldRise in 0.20..0.80)
        assertTrue(pred.probGoldFall in 0.20..0.80)
        assertEquals(1.0, pred.probGoldRise + pred.probGoldFall, 0.001)
        assertEquals(1, pred.sampleSize)
    }
}
