package com.example

import com.example.backtest.BacktestCodec
import com.example.backtest.BacktestParseOutcome
import com.example.backtest.ExitReason
import com.example.backtest.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BacktestUnitTest {

    @Test
    fun decodeCsvBytes_handlesUtf16LeAndBeBom() {
        val mt5Text = buildString {
            appendLine("<DATE>\t<TIME>\t<OPEN>\t<HIGH>\t<LOW>\t<CLOSE>\t<TICKVOL>\t<VOL>\t<SPREAD>")
            appendLine("2025.01.02\t09:00:00\t2630.50\t2635.00\t2629.00\t2634.25\t1200\t0\t15")
            appendLine("2025.01.02\t10:00:00\t2634.25\t2636.00\t2631.50\t2632.00\t950\t0\t12")
        }
        val leBytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + mt5Text.toByteArray(Charsets.UTF_16LE)
        val decodedLe = BacktestCodec.decodeCsvBytes(leBytes)
        assertEquals("UTF-16LE (BOM)", decodedLe.detectedEncoding)
        assertEquals("Tab (MT5)", decodedLe.detectedDelimiter)
        assertEquals(2, decodedLe.estimatedRows)
        assertTrue(decodedLe.text.startsWith("<DATE>\t<TIME>"))

        val beBytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + mt5Text.toByteArray(Charsets.UTF_16BE)
        val decodedBe = BacktestCodec.decodeCsvBytes(beBytes)
        assertEquals("UTF-16BE (BOM)", decodedBe.detectedEncoding)
        assertTrue(decodedBe.text.startsWith("<DATE>\t<TIME>"))
    }

    @Test
    fun decodeCsvBytes_stripsUtf8BomAndDetectsDelimiters() {
        val csv = "time;open;high;low;close\n2025-03-01 01:00;100;106;98;105\n"
        val utf8BomBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + csv.toByteArray(Charsets.UTF_8)

        val decoded = BacktestCodec.decodeCsvBytes(utf8BomBytes)
        assertEquals("UTF-8 (BOM)", decoded.detectedEncoding)
        assertEquals("Semicolon", decoded.detectedDelimiter)
        assertEquals(1, decoded.estimatedRows)
        assertTrue(decoded.text.startsWith("time;open"))
    }

    @Test
    fun parseBacktestJson_parsesFullSuccessPayload() {
        val json = """
            {
              "summary": {
                "totalTrades": 2,
                "wins": 1,
                "losses": 1,
                "winRatePct": 50.0,
                "netPoints": 10.5,
                "netR": 1.0,
                "avgR": 0.5,
                "profitFactor": 2.0,
                "skippedByStopCap": 3
              },
              "points": {
                "avgWinPoints": 20.5,
                "avgLossPoints": -10.0,
                "largestWinPoints": 20.5,
                "largestLossPoints": -10.0,
                "avgStopDistancePoints": 10.0,
                "maxStopDistancePoints": 10.0,
                "avgTargetDistancePoints": 20.5
              },
              "risk": {
                "maxDrawdownPoints": 10.0,
                "maxDrawdownR": 1.0,
                "maxConsecutiveWins": 1,
                "maxConsecutiveLosses": 1
              },
              "bySide": {
                "buy": {"trades": 1, "winRatePct": 100.0, "netPoints": 20.5},
                "sell": {"trades": 1, "winRatePct": 0.0, "netPoints": -10.0}
              },
              "timing": {"avgTradeDurationCandles": 4.5},
              "equityCurve": [20.5, 10.5],
              "trades": [
                {
                  "side": "buy",
                  "entry": 2600.0,
                  "sl": 2590.0,
                  "tp": 2620.5,
                  "exit": 2620.5,
                  "openTime": "2025.01.02 09:00:00",
                  "closeTime": "2025.01.02 12:00:00",
                  "reason": "tp",
                  "points": 20.5,
                  "r": 2.0,
                  "durationCandles": 4
                },
                {
                  "side": "sell",
                  "entry": 2620.0,
                  "sl": 2630.0,
                  "tp": 2600.0,
                  "exit": 2630.0,
                  "openTime": "2025.01.02 13:00:00",
                  "closeTime": "2025.01.02 17:00:00",
                  "reason": "sl",
                  "points": -10.0,
                  "r": -1.0,
                  "durationCandles": 5
                }
              ],
              "warnings": ["Under 50 trades, results are not statistically reliable"]
            }
        """.trimIndent()

        val outcome = BacktestCodec.parseBacktestJson(json)
        assertTrue(outcome is BacktestParseOutcome.Success)
        val result = (outcome as BacktestParseOutcome.Success).result
        assertEquals(2, result.summary.totalTrades)
        assertEquals(2.0, result.summary.profitFactor!!, 1e-6)
        assertEquals(-10.0, result.points.avgLossPoints, 1e-6)
        assertEquals(Side.BUY, result.trades[0].side)
        assertEquals(ExitReason.TP, result.trades[0].reason)
        assertEquals(Side.SELL, result.trades[1].side)
        assertEquals(ExitReason.SL, result.trades[1].reason)
        assertEquals(1, result.warnings.size)
    }

    @Test
    fun parseBacktestJson_handlesNullableProfitFactorAndErrorObjects() {
        val nullPfJson = """
            {
              "summary": {"totalTrades": 1, "wins": 1, "losses": 0, "winRatePct": 100.0, "netPoints": 15.0, "netR": 1.5, "avgR": 1.5, "profitFactor": null, "skippedByStopCap": 0},
              "points": {"avgWinPoints": 15.0, "avgLossPoints": 0.0, "largestWinPoints": 15.0, "largestLossPoints": 0.0, "avgStopDistancePoints": 10.0, "maxStopDistancePoints": 10.0, "avgTargetDistancePoints": 15.0},
              "risk": {"maxDrawdownPoints": 0.0, "maxDrawdownR": 0.0, "maxConsecutiveWins": 1, "maxConsecutiveLosses": 0},
              "bySide": {"buy": {"trades": 1, "winRatePct": 100.0, "netPoints": 15.0}, "sell": {"trades": 0, "winRatePct": 0.0, "netPoints": 0.0}},
              "timing": {"avgTradeDurationCandles": 3.0},
              "equityCurve": [15.0],
              "trades": [],
              "warnings": []
            }
        """.trimIndent()

        val success = BacktestCodec.parseBacktestJson(nullPfJson) as BacktestParseOutcome.Success
        assertNull(success.result.summary.profitFactor)

        val errorJson = """
            {
              "error": "SyntaxError: invalid syntax (strategy.py, line 4)",
              "details": "Traceback (most recent call last):\n  File \"backtest.py\", line 339"
            }
        """.trimIndent()
        val failure = BacktestCodec.parseBacktestJson(errorJson) as BacktestParseOutcome.Failure
        assertEquals("SyntaxError: invalid syntax (strategy.py, line 4)", failure.error.error)
        assertTrue(failure.error.details.contains("Traceback"))

        val malformed = BacktestCodec.parseBacktestJson("not valid json")
        assertTrue(malformed is BacktestParseOutcome.Failure)
    }
}
