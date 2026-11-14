package com.example

import com.example.backtest.BacktestConfig
import com.example.backtest.BacktestCsvParser
import com.example.backtest.Candle
import com.example.backtest.ExitReason
import com.example.backtest.Side
import com.example.backtest.Signal
import com.example.backtest.Strategy
import com.example.backtest.ThreeBullishCandlesStrategy
import com.example.backtest.runBacktest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BacktestUnitTest {

    @Test
    fun csvParser_parsesMt5TabAndUtf16LeBom() {
        val mt5Text = buildString {
            appendLine("<DATE>\t<TIME>\t<OPEN>\t<HIGH>\t<LOW>\t<CLOSE>\t<TICKVOL>\t<VOL>\t<SPREAD>")
            appendLine("2025.01.02\t09:00:00\t2630.50\t2635.00\t2629.00\t2634.25\t1200\t0\t15")
            appendLine("2025.01.02\t10:00:00\t2634.25\t2636.00\t2631.50\t2632.00\t950\t0\t12")
        }
        val payload = mt5Text.toByteArray(Charsets.UTF_16LE)
        val withBom = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + payload

        val parsed = BacktestCsvParser.parseBytes(withBom)
        assertEquals("UTF-16LE (BOM)", parsed.detectedEncoding)
        assertEquals("Tab (MT5)", parsed.detectedDelimiter)
        assertEquals(0, parsed.skippedRows)
        assertEquals(2, parsed.candles.size)
        assertEquals("2025.01.02 09:00:00", parsed.candles[0].time)
        assertEquals(2630.50, parsed.candles[0].open, 1e-6)
        assertEquals(2635.00, parsed.candles[0].high, 1e-6)
        assertEquals(2629.00, parsed.candles[0].low, 1e-6)
        assertEquals(2634.25, parsed.candles[0].close, 1e-6)
        assertEquals(1200.0, parsed.candles[0].volume, 1e-6)
    }

    @Test
    fun csvParser_matchesColumnsByHeaderNameAndSemicolon() {
        val csv = """
            CLOSE;LOW;Timestamp;VOLUME;HIGH;OPEN
            105,5;98,0;2025-03-01 01:00;500;106,0;100,0
            108,0;104,0;2025-03-01 02:00;400;109,0;105,5
        """.trimIndent()

        val parsed = BacktestCsvParser.parseText(csv)
        assertEquals("Semicolon", parsed.detectedDelimiter)
        assertEquals(2, parsed.candles.size)
        assertEquals("2025-03-01 01:00", parsed.candles[0].time)
        assertEquals(100.0, parsed.candles[0].open, 1e-6)
        assertEquals(106.0, parsed.candles[0].high, 1e-6)
        assertEquals(98.0, parsed.candles[0].low, 1e-6)
        assertEquals(105.5, parsed.candles[0].close, 1e-6)
        assertEquals(500.0, parsed.candles[0].volume, 1e-6)
    }

    @Test
    fun engine_enforces50CandleWarmupAndNextOpenEntryAndSlFirstTieBreak() {
        // Build 60 candles cleanly numbered 0..59
        val candles = MutableList(60) { idx ->
            Candle(
                time = "T$idx",
                open = 100.0,
                high = 101.0,
                low = 99.0,
                close = 100.0
            )
        }
        // Candle 51 is where a signal from candle 50 enters at open (102.0)
        // Suppose signal is at i=49 (warmup period -> MUST be ignored!) and at i=50 (valid!).
        // On candle 51, BOTH SL (98.0) and TP (110.0) are inside the candle's range [95.0, 115.0].
        // Engine rule: SL must win tie-break.
        candles[51] = Candle(
            time = "T51",
            open = 102.0,
            high = 115.0,
            low = 95.0,
            close = 112.0
        )

        val signalIndices = mutableListOf<Int>()
        val testStrategy = object : Strategy {
            override fun onCandle(i: Int, candles: List<Candle>): Signal? {
                // Verify zero lookahead slice size
                assertEquals(i + 1, candles.size)
                signalIndices.add(i)
                return if (i == 49 || i == 50) {
                    Signal(side = Side.BUY, sl = 98.0, tp = 110.0)
                } else {
                    null
                }
            }
        }

        val result = runBacktest(
            candles = candles,
            strategy = testStrategy,
            config = BacktestConfig(maxStopPoints = null, pointSize = 0.5)
        )

        // Warmup skipped indices 0..49
        assertTrue(signalIndices.none { it < 50 })
        assertEquals(1, result.summary.totalTrades)
        assertEquals(0, result.summary.wins)
        assertEquals(1, result.summary.losses)

        val trade = result.trades.single()
        assertEquals(102.0, trade.entry, 1e-6)
        assertEquals("T51", trade.openTime)
        assertEquals("T51", trade.closeTime)
        assertEquals(ExitReason.SL, trade.reason)
        assertEquals(98.0, trade.exit, 1e-6)
        // Entry 102.0 -> SL 98.0 = -4.0 price, divided by pointSize 0.5 = -8.0 points
        assertEquals(-8.0, trade.points, 1e-6)
        assertEquals(-1.0, trade.r, 1e-6)
        assertEquals(1, trade.durationCandles)
    }

    @Test
    fun engine_enforcesOneTradeAtATimeAndMaxStopCapSkipping() {
        val candles = MutableList(60) { idx ->
            Candle(
                time = "T$idx",
                open = 100.0,
                high = 102.0,
                low = 98.0,
                close = 100.0
            )
        }
        // Candle 51 enters Trade 1 (BUY entry=100, sl=90, tp=110) - stays open during 51 & 52
        // Candle 53 hits TP=110
        candles[53] = Candle(time = "T53", open = 101.0, high = 112.0, low = 100.0, close = 111.0)
        // Candle 54 signal will have wide stop distance -> should be skipped by maxStopPoints = 5.0
        // Candle 56 signal has valid stop distance 4.0 -> enters on 57, hits TP on 58
        candles[58] = Candle(time = "T58", open = 101.0, high = 115.0, low = 99.0, close = 114.0)

        var strategyCalls = 0
        val testStrategy = object : Strategy {
            override fun onCandle(i: Int, candles: List<Candle>): Signal? {
                strategyCalls++
                return when (i) {
                    50 -> Signal(side = Side.BUY, sl = 96.0, tp = 110.0) // stop dist = 4.0 -> taken, exits 53
                    51, 52 -> Signal(side = Side.SELL, sl = 104.0, tp = 90.0) // ignored while trade open
                    54 -> Signal(side = Side.SELL, sl = 120.0, tp = 80.0) // stop dist = 20.0 > cap 5.0 -> skipped
                    56 -> Signal(side = Side.BUY, sl = 96.0, tp = 112.0) // stop dist = 4.0 -> taken, exits 58
                    else -> null
                }
            }
        }

        val result = runBacktest(
            candles = candles,
            strategy = testStrategy,
            config = BacktestConfig(maxStopPoints = 5.0, pointSize = 1.0)
        )

        assertEquals(1, result.summary.skippedByStopCap)
        assertEquals(2, result.summary.totalTrades)
        assertEquals(2, result.summary.wins)
        assertEquals(0, result.summary.losses)
        assertEquals(100.0, result.summary.winRatePct, 1e-6)
        // Trade 1: 110 - 100 = 10 pts; Trade 2: 112 - 100 = 12 pts; Total = 22 pts
        assertEquals(22.0, result.summary.netPoints, 1e-6)
        assertEquals(listOf(10.0, 22.0), result.equityCurve)
        // Duration: Trade 1 opened 51 closed 53 -> 3 candles; Trade 2 opened 57 closed 58 -> 2 candles
        assertEquals(2.5, result.timing.avgTradeDurationCandles, 1e-6)
    }

    @Test
    fun engine_guardsZeroTradesAndThreeBullishCandlesStrategyWorks() {
        val tooShort = List(50) { idx ->
            Candle("T$idx", 100.0, 102.0, 98.0, 101.0)
        }
        val shortResult = runBacktest(tooShort, ThreeBullishCandlesStrategy(), BacktestConfig())
        assertEquals(0, shortResult.summary.totalTrades)
        assertEquals(0.0, shortResult.summary.winRatePct, 1e-6)
        assertTrue(shortResult.warnings.isNotEmpty())

        // Verify ThreeBullishCandlesStrategy fires after 3 bullish candles
        val candles = List(14) { idx ->
            Candle("T$idx", 100.0, 102.0, 99.0, 99.5) // bearish, range = 3.0
        }.toMutableList()
        candles[11] = Candle("T11", 100.0, 103.0, 99.0, 102.0) // bullish, range = 4.0
        candles[12] = Candle("T12", 102.0, 105.0, 101.0, 104.0) // bullish, range = 4.0
        candles[13] = Candle("T13", 104.0, 107.0, 103.0, 106.0) // bullish, range = 4.0

        val strategy = ThreeBullishCandlesStrategy()
        assertNull(strategy.onCandle(12, candles.subList(0, 13)))
        val sig = strategy.onCandle(13, candles)
        assertNotNull(sig)
        assertEquals(Side.BUY, sig!!.side)
        // Average range = (11 * 3.0 + 3 * 4.0) / 14 = 45.0 / 14.0
        val expectedRange = 45.0 / 14.0
        assertEquals(106.0 - expectedRange, sig.sl, 1e-6)
        assertEquals(106.0 + 2.0 * expectedRange, sig.tp, 1e-6)
    }
}
