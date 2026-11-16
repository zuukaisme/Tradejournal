package com.example

import com.example.backtest.BacktestCodec
import com.example.backtest.AiStrategyGenerator
import com.example.backtest.BacktestTimeframe
import com.example.backtest.CandleTimeframeResampler
import com.example.backtest.BacktestParseOutcome
import com.example.backtest.ExitReason
import com.example.backtest.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class BacktestUnitTest {

    @Test
    fun aiStrategyPromptCoversMt5TimeAndSkippedCandleState() {
        val prompt = AiStrategyGenerator.SYSTEM_PROMPT
        assertTrue(prompt.contains("YYYY.MM.DD HH:MM:SS"))
        assertTrue(prompt.contains("SERVER_GMT_OFFSET"))
        assertTrue(prompt.contains("last_processed_index"))
        assertTrue(prompt.contains("process every unseen candle"))
        assertTrue(prompt.contains("does not call it"))
    }

    @Test
    fun readTextStreamWithBom_decodesUtf16AndEnforcesByteLimit() {
        val text = "class ImportedStrategy:\n    pass\n"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        val decoded = BacktestCodec.readTextStreamWithBom(ByteArrayInputStream(bytes))
        assertEquals(text, decoded)

        val rejected = runCatching {
            BacktestCodec.readTextStreamWithBom(ByteArrayInputStream(bytes), 8L)
        }
        assertTrue(rejected.isFailure)
        assertTrue(rejected.exceptionOrNull()?.message?.contains("limit") == true)
    }

    @Test
    fun streamCsvToCacheFile_reportsProgressAndKeepsOnlyPreviewInMemory() {
        val csv = buildString {
            appendLine("<DATE>\t<TIME>\t<OPEN>\t<HIGH>\t<LOW>\t<CLOSE>\t<TICKVOL>")
            repeat(700) {
                appendLine("2025.01.02\t09:00:00\t100\t102\t99\t101\t10")
            }
        }
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + csv.toByteArray(Charsets.UTF_16LE)
        val output = File.createTempFile("tradepulse-stream-", ".csv")
        var progress = 0L

        try {
            val inspection = BacktestCodec.streamCsvToCacheFile(ByteArrayInputStream(bytes), output) {
                progress = it
            }

            assertEquals("UTF-16LE (BOM)", inspection.detectedEncoding)
            assertEquals("Tab (MT5)", inspection.detectedDelimiter)
            assertEquals(700, inspection.estimatedRows)
            assertTrue(inspection.sampleLines.size <= 605)
            assertTrue(output.length() > 0L)
            assertTrue(progress > 0L)
            assertTrue(output.readText(Charsets.UTF_8).startsWith("<DATE>\t<TIME>"))
        } finally {
            output.delete()
        }
    }

    @Test
    fun resampleCsvFile_matchesInMemoryResamplerForChronologicalData() {
        val csv = buildString {
            appendLine("<DATE>\t<TIME>\t<OPEN>\t<HIGH>\t<LOW>\t<CLOSE>\t<TICKVOL>")
            appendLine("2025.02.07\t10:00:00\t2850.0\t2854.0\t2849.0\t2852.0\t100")
            appendLine("2025.02.07\t10:05:00\t2852.0\t2858.5\t2851.0\t2856.0\t150")
            appendLine("2025.02.07\t10:10:00\t2856.0\t2857.0\t2847.5\t2853.5\t200")
            appendLine("2025.02.07\t10:15:00\t2853.5\t2861.0\t2853.0\t2860.0\t120")
            appendLine("2025.02.07\t10:20:00\t2860.0\t2862.0\t2858.0\t2859.0\t80")
        }
        val source = File.createTempFile("tradepulse-source-", ".csv")
        val output = File.createTempFile("tradepulse-resampled-", ".csv")

        try {
            source.writeText(csv, Charsets.UTF_8)
            val expected = CandleTimeframeResampler.resampleCsv(csv, BacktestTimeframe.M15)
            val actual = CandleTimeframeResampler.resampleCsvFile(source, BacktestTimeframe.M15, output)

            assertEquals(expected.sourceCandleCount, actual.sourceCandleCount)
            assertEquals(expected.outputCandleCount, actual.outputCandleCount)
            assertEquals(expected.csvText, output.readText(Charsets.UTF_8))
        } finally {
            source.delete()
            output.delete()
        }
    }


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

    @Test
    fun aiModelCatalog_containsLatestModelsForClaudeGeminiAndOpenAi() {
        val claudeIds = com.example.backtest.AiModelCatalog.modelsFor(com.example.backtest.AiProvider.CLAUDE).map { it.id }
        assertTrue(claudeIds.contains("claude-opus-5-5"))
        assertTrue(claudeIds.contains("claude-fable-5-2"))
        assertTrue(claudeIds.contains("claude-haiku-5-5"))

        val geminiIds = com.example.backtest.AiModelCatalog.modelsFor(com.example.backtest.AiProvider.GEMINI).map { it.id }
        assertTrue(geminiIds.contains("gemini-4-argon"))
        assertTrue(geminiIds.contains("gemini-4-hydrogen"))

        val openAiIds = com.example.backtest.AiModelCatalog.modelsFor(com.example.backtest.AiProvider.OPENAI).map { it.id }
        assertTrue(openAiIds.contains("gpt-6-astra"))
        assertTrue(openAiIds.contains("gpt-6.1-sol"))
    }

    @Test
    fun aiStrategyGenerator_extractsFencedPythonAndDetectsStrategyClass() {
        val raw = """
            Here is your strategy:
            ```python
            from backtest import Strategy, Signal

            class EmaPullbackStrategy(Strategy):
                def on_candle(self, i, candles):
                    return None
            ```
        """.trimIndent()

        val extracted = com.example.backtest.AiStrategyGenerator.extractPythonCode(raw)
        assertTrue(extracted.startsWith("from backtest import Strategy, Signal"))
        assertEquals("EmaPullbackStrategy", com.example.backtest.AiStrategyGenerator.detectStrategyClassName(extracted))

        val sysPrompt = com.example.backtest.AiStrategyGenerator.SYSTEM_PROMPT
        assertTrue(sysPrompt.contains("Python 3.11 standard library ONLY"))
        assertTrue(sysPrompt.contains("Signal(side, sl, tp)"))
        assertTrue(sysPrompt.contains("Candle"))
        val detailed = com.example.backtest.AiStrategyGenerator.buildDetailedUserPrompt("Buy 20 SMA cross")
        assertTrue(detailed.contains("Signal(side=\"buy\" | \"sell\", sl=float, tp=float)"))
    }

    @Test
    fun candleTimeframeResampler_detectsM5WithGapsAndResamplesToM15AndH1() {
        val xauM5Csv = buildString {
            appendLine("<DATE>\t<TIME>\t<OPEN>\t<HIGH>\t<LOW>\t<CLOSE>\t<TICKVOL>\t<VOL>\t<SPREAD>")
            // 10:00..10:10 -> 10:00 M15 bucket
            appendLine("2025.02.07\t10:00:00\t2850.0\t2854.0\t2849.0\t2852.0\t100\t0\t12")
            appendLine("2025.02.07\t10:05:00\t2852.0\t2858.5\t2851.0\t2856.0\t150\t0\t12")
            appendLine("2025.02.07\t10:10:00\t2856.0\t2857.0\t2847.5\t2853.5\t200\t0\t12")
            // 10:15..10:25 -> 10:15 M15 bucket
            appendLine("2025.02.07\t10:15:00\t2853.5\t2861.0\t2853.0\t2860.0\t120\t0\t12")
            appendLine("2025.02.07\t10:20:00\t2860.0\t2862.0\t2858.0\t2859.0\t80\t0\t12")
            // Weekend gap to Monday 2025.02.10 -> 00:00 M15 & H1 bucket
            appendLine("2025.02.10\t00:00:00\t2865.0\t2870.0\t2864.0\t2868.0\t300\t0\t15")
            appendLine("2025.02.10\t00:05:00\t2868.0\t2869.0\t2866.0\t2867.5\t110\t0\t15")
        }

        val detected = com.example.backtest.CandleTimeframeResampler.detectTimeframe(xauM5Csv)
        assertEquals(5, detected.minutes)
        assertEquals("5m (M5)", detected.label)

        val targets = com.example.backtest.CandleTimeframeResampler.availableTargets(detected.minutes)
        assertEquals(
            listOf(
                com.example.backtest.BacktestTimeframe.NATIVE,
                com.example.backtest.BacktestTimeframe.M15,
                com.example.backtest.BacktestTimeframe.M30,
                com.example.backtest.BacktestTimeframe.H1,
                com.example.backtest.BacktestTimeframe.H2,
                com.example.backtest.BacktestTimeframe.H4,
                com.example.backtest.BacktestTimeframe.D1,
                com.example.backtest.BacktestTimeframe.W1
            ),
            targets
        )

        val m15 = com.example.backtest.CandleTimeframeResampler.resampleCsv(
            xauM5Csv,
            com.example.backtest.BacktestTimeframe.M15
        )
        assertEquals(7, m15.sourceCandleCount)
        assertEquals(3, m15.outputCandleCount)
        val m15Lines = m15.csvText.trim().lines()
        assertEquals("datetime,open,high,low,close,volume", m15Lines[0])
        assertEquals("2025-02-07 10:00:00,2850,2858.5,2847.5,2853.5,450", m15Lines[1])
        assertEquals("2025-02-07 10:15:00,2853.5,2862,2853,2859,200", m15Lines[2])
        assertEquals("2025-02-10 00:00:00,2865,2870,2864,2867.5,410", m15Lines[3])

        val h1 = com.example.backtest.CandleTimeframeResampler.resampleCsv(
            xauM5Csv,
            com.example.backtest.BacktestTimeframe.H1
        )
        assertEquals(2, h1.outputCandleCount)
        val h1Lines = h1.csvText.trim().lines()
        assertEquals("2025-02-07 10:00:00,2850,2862,2847.5,2859,650", h1Lines[1])
        assertEquals("2025-02-10 00:00:00,2865,2870,2864,2867.5,410", h1Lines[2])
    }

    @Test
    fun candleTimeframeResampler_sortsUnsortedUnixCsvAndAggregatesDaily() {
        val unsortedUnixCsv = buildString {
            appendLine("time,open,high,low,close")
            appendLine("1736953200,105,110,104,108") // 2025-01-15 15:00:00 UTC
            appendLine("1736949600,100,106,99,105")  // 2025-01-15 14:00:00 UTC
        }

        val detected = com.example.backtest.CandleTimeframeResampler.detectTimeframe(unsortedUnixCsv)
        assertEquals(60, detected.minutes)
        assertEquals("1H (H1)", detected.label)

        val daily = com.example.backtest.CandleTimeframeResampler.resampleCsv(
            unsortedUnixCsv,
            com.example.backtest.BacktestTimeframe.D1
        )
        assertEquals(1, daily.outputCandleCount)
        val lines = daily.csvText.trim().lines()
        assertEquals("2025-01-15 00:00:00,100,110,99,108,0", lines[1])
    }

    @Test
    fun parseProgressJson_andBreakEvenSummary_parseAllProgressMetrics() {
        val progressJson = """
            {
              "candlesProcessed": 1500,
              "totalCandles": 3000,
              "enteredTrades": 21,
              "closedTrades": 20,
              "openTrades": 1,
              "wins": 11,
              "losses": 6,
              "breakEvens": 3,
              "skippedByStopCap": 2,
              "netPoints": 42.5,
              "netR": 3.25,
              "currentTime": "2025.02.10 14:30:00"
            }
        """.trimIndent()

        val progress = BacktestCodec.parseProgressJson(progressJson)!!
        assertEquals(1500, progress.candlesProcessed)
        assertEquals(3000, progress.totalCandles)
        assertEquals(0.5f, progress.candleProgressFraction, 1e-5f)
        assertEquals(21, progress.enteredTrades)
        assertEquals(20, progress.closedTrades)
        assertEquals(1, progress.openTrades)
        assertEquals(11, progress.wins)
        assertEquals(6, progress.losses)
        assertEquals(3, progress.breakEvens)
        assertEquals(55.0, progress.winRatePct, 1e-6)
        assertEquals(30.0, progress.lossRatePct, 1e-6)
        assertEquals(15.0, progress.breakEvenRatePct, 1e-6)
        assertEquals("2025.02.10 14:30:00", progress.currentTime)

        val resultJson = """
            {
              "summary": {
                "totalTrades": 4,
                "enteredTrades": 5,
                "openTrades": 1,
                "wins": 2,
                "losses": 1,
                "breakEvens": 1,
                "winRatePct": 50.0,
                "lossRatePct": 25.0,
                "breakEvenRatePct": 25.0,
                "netPoints": 18.0,
                "netR": 2.0,
                "avgR": 0.5,
                "expectancyPoints": 4.5,
                "profitFactor": 2.5,
                "skippedByStopCap": 2,
                "tpExits": 2,
                "slExits": 1,
                "totalCandles": 500,
                "currentStreak": 2
              },
              "points": {
                "avgWinPoints": 14.0,
                "avgLossPoints": -10.0,
                "largestWinPoints": 16.0,
                "largestLossPoints": -10.0,
                "avgStopDistancePoints": 10.0,
                "maxStopDistancePoints": 10.0,
                "avgTargetDistancePoints": 15.0
              },
              "risk": {
                "maxDrawdownPoints": 10.0,
                "maxDrawdownR": 1.0,
                "maxConsecutiveWins": 2,
                "maxConsecutiveLosses": 1
              },
              "bySide": {
                "buy": {"trades": 3, "winRatePct": 66.7, "netPoints": 28.0},
                "sell": {"trades": 1, "winRatePct": 0.0, "netPoints": -10.0}
              },
              "timing": {"avgTradeDurationCandles": 5.0},
              "equityCurve": [-10.0, -10.0, 2.0, 18.0],
              "trades": [
                {
                  "side": "buy",
                  "entry": 2600.0,
                  "sl": 2600.0,
                  "tp": 2620.0,
                  "exit": 2600.0,
                  "openTime": "2025.02.10 10:00:00",
                  "closeTime": "2025.02.10 10:30:00",
                  "reason": "be",
                  "points": 0.0,
                  "r": 0.0,
                  "durationCandles": 6
                }
              ],
              "warnings": []
            }
        """.trimIndent()

        val parsed = (BacktestCodec.parseBacktestJson(resultJson) as BacktestParseOutcome.Success).result
        assertEquals(5, parsed.summary.enteredTrades)
        assertEquals(1, parsed.summary.openTrades)
        assertEquals(1, parsed.summary.breakEvens)
        assertEquals(25.0, parsed.summary.breakEvenRatePct, 1e-6)
        assertEquals(25.0, parsed.summary.lossRatePct, 1e-6)
        assertEquals(500, parsed.summary.totalCandles)
        assertEquals(2, parsed.summary.currentStreak)
        assertEquals(ExitReason.BE, parsed.trades.first().reason)
        assertTrue(com.example.backtest.AiStrategyGenerator.SYSTEM_PROMPT.contains("manage_trade"))
    }

    @Test
    fun aiStrategyGenerator_validateStrategyContractFlagsMissingObjectsAndAntiPatterns() {
        val broken = """
            import pandas as pd
            class MyStrat:
                def on_candle(self, i, candles):
                    future = candles[i + 1]
                    ema_prev = 0.0
                    return None
                def manage_trade(self, trade, i, candles):
                    return None
        """.trimIndent()
        val warnings = AiStrategyGenerator.validateStrategyContract(broken)
        assertTrue(warnings.any { it.contains("Strategy") })
        assertTrue(warnings.any { it.contains("Signal") })
        assertTrue(warnings.any { it.contains("pandas") })
        assertTrue(warnings.any { it.contains("candles[i +") })
        assertTrue(warnings.any { it.contains("manage_trade") })
        assertTrue(warnings.any { it.contains("catch-up") })

        val clean = """
            class CleanStrat(Strategy):
                def __init__(self):
                    self.last_processed_index = -1
                    self.ema = None
                def on_candle(self, i, candles):
                    self.last_processed_index = i
                    return Signal("buy", candles[i].close - 1.0, candles[i].close + 1.5)
        """.trimIndent()
        val cleanWarnings = AiStrategyGenerator.validateStrategyContract(clean)
        assertTrue(cleanWarnings.isEmpty())
    }

    @Test
    fun savedCsvStore_persistsListsAndDeletesCsvDatasets() {
        val root = java.nio.file.Files.createTempDirectory("tradepulse-csv-store-").toFile()
        val tempCsv = File.createTempFile("tradepulse-temp-", ".csv")
        try {
            tempCsv.writeText("<DATE>\t<TIME>\t<OPEN>\t<HIGH>\t<LOW>\t<CLOSE>\n2025.02.07\t10:00:00\t100\t102\t99\t101\n")
            val inspection = com.example.backtest.CsvStreamInspection(
                file = tempCsv,
                estimatedRows = 1,
                fileSizeBytes = tempCsv.length(),
                detectedEncoding = "UTF-8",
                detectedDelimiter = "Tab (MT5)",
                sampleLines = listOf("2025.02.07\t10:00:00\t100\t102\t99\t101")
            )
            val tf = com.example.backtest.DetectedTimeframe(label = "1m (M1)", minutes = 1)
            val saved = com.example.backtest.SavedCsvStore.saveImportedFile(
                rootDir = root,
                tempCsvFile = tempCsv,
                displayName = "XAUUSD_M1.csv",
                inspection = inspection,
                detectedTimeframe = tf
            )
            val listed = com.example.backtest.SavedCsvStore.listDatasets(root)
            assertEquals(1, listed.size)
            assertEquals("XAUUSD_M1.csv", listed.first().displayName)
            assertTrue(com.example.backtest.SavedCsvStore.resolveFile(root, saved).exists())

            val afterDelete = com.example.backtest.SavedCsvStore.deleteDataset(root, saved.id)
            assertEquals(0, afterDelete.size)
            assertTrue(!com.example.backtest.SavedCsvStore.resolveFile(root, saved).exists())
        } finally {
            tempCsv.delete()
            root.deleteRecursively()
        }
    }

    @Test
    fun csvCandleWindowReader_readsWindowAroundOpenAndCloseIndices() {
        val csvFile = File.createTempFile("tradepulse-replay-", ".csv")
        try {
            val content = buildString {
                appendLine("<DATE>\t<TIME>\t<OPEN>\t<HIGH>\t<LOW>\t<CLOSE>\t<TICKVOL>")
                for (i in 0 until 60) {
                    val price = 2800.0 + i * 0.5
                    appendLine("2025.02.07\t10:${String.format(java.util.Locale.US, "%02d", i)}:00\t$price\t${price + 2.0}\t${price - 1.5}\t${price + 1.0}\t100")
                }
            }
            csvFile.writeText(content)
            val trade = com.example.backtest.Trade(
                side = Side.BUY,
                entry = 2812.5,
                sl = 2808.0,
                tp = 2820.0,
                exit = 2820.0,
                openTime = "2025.02.07 10:25:00",
                closeTime = "2025.02.07 10:32:00",
                reason = ExitReason.TP,
                points = 7.5,
                r = 1.67,
                durationCandles = 8,
                openIndex = 25,
                closeIndex = 32
            )
            val window = com.example.backtest.CsvCandleWindowReader.readTradeWindow(
                csvFile = csvFile,
                trade = trade,
                paddingBefore = 5,
                paddingAfter = 4
            )
            assertEquals(17, window.candles.size) // indices 20..36 inclusive
            assertEquals(5, window.entryCandlePos)
            assertEquals(12, window.exitCandlePos)
            assertEquals("2025.02.07 10:25:00", window.candles[window.entryCandlePos].time)
            assertEquals("2025.02.07 10:32:00", window.candles[window.exitCandlePos].time)
        } finally {
            csvFile.delete()
        }
    }

    @Test
    fun batchCodec_encodesSpecsAndParsesMixedSuccessAndFailure() {
        val specs = listOf(
            com.example.backtest.BatchStrategySpec(id = 11L, name = "Alpha", className = "AlphaStrat", code = "pass"),
            com.example.backtest.BatchStrategySpec(id = 12L, name = "Beta", className = "BetaStrat", code = "pass")
        )
        val encoded = BacktestCodec.encodeBatchStrategySpecs(specs)
        assertTrue(encoded.contains("\"AlphaStrat\""))

        val batchJson = """
            {
              "totalCandles": 250,
              "results": [
                {
                  "id": 11,
                  "name": "Alpha",
                  "className": "AlphaStrat",
                  "result": {
                    "summary": {"totalTrades": 1, "wins": 1, "losses": 0, "winRatePct": 100.0, "netPoints": 12.0, "netR": 1.5, "avgR": 1.5, "profitFactor": null, "skippedByStopCap": 0},
                    "points": {"avgWinPoints": 12.0, "avgLossPoints": 0.0, "largestWinPoints": 12.0, "largestLossPoints": 0.0, "avgStopDistancePoints": 8.0, "maxStopDistancePoints": 8.0, "avgTargetDistancePoints": 12.0},
                    "risk": {"maxDrawdownPoints": 0.0, "maxDrawdownR": 0.0, "maxConsecutiveWins": 1, "maxConsecutiveLosses": 0},
                    "bySide": {"buy": {"trades": 1, "winRatePct": 100.0, "netPoints": 12.0}, "sell": {"trades": 0, "winRatePct": 0.0, "netPoints": 0.0}},
                    "timing": {"avgTradeDurationCandles": 4.0},
                    "equityCurve": [12.0],
                    "trades": [
                      {
                        "side": "buy", "entry": 2800.0, "sl": 2792.0, "tp": 2812.0, "exit": 2812.0,
                        "openTime": "2025.02.07 10:00:00", "closeTime": "2025.02.07 10:04:00",
                        "reason": "tp", "points": 12.0, "r": 1.5, "durationCandles": 4,
                        "openIndex": 10, "closeIndex": 14
                      }
                    ],
                    "warnings": []
                  }
                },
                {
                  "id": 12,
                  "name": "Beta",
                  "className": "BetaStrat",
                  "error": "NameError: missing helper",
                  "details": "Trace"
                }
              ]
            }
        """.trimIndent()

        val outcome = BacktestCodec.parseBatchBacktestJson(batchJson)
        assertTrue(outcome is com.example.backtest.BatchBacktestParseOutcome.Success)
        val ok = outcome as com.example.backtest.BatchBacktestParseOutcome.Success
        assertEquals(250, ok.totalCandles)
        assertEquals(2, ok.runs.size)
        assertEquals(10, ok.runs[0].result!!.trades[0].openIndex)
        assertEquals(14, ok.runs[0].result!!.trades[0].closeIndex)
        assertEquals("NameError: missing helper", ok.runs[1].error!!.error)
    }

    @Test
    fun parseProgressJson_parsesSimultaneousStrategyStatuses() {
        val progressJson = """
            {
              "candlesProcessed": 1200,
              "totalCandles": 2400,
              "enteredTrades": 15,
              "closedTrades": 13,
              "openTrades": 2,
              "wins": 8,
              "losses": 4,
              "breakEvens": 1,
              "skippedByStopCap": 0,
              "netPoints": 34.5,
              "netR": 2.75,
              "currentTime": "2025.02.10 12:00:00",
              "strategyIndex": 2,
              "totalStrategies": 3,
              "currentStrategyName": "2/3 active",
              "strategyStatuses": [
                {
                  "strategyId": 1,
                  "name": "Buy Scalp",
                  "closedTrades": 8,
                  "openTrades": 1,
                  "wins": 5,
                  "losses": 2,
                  "breakEvens": 1,
                  "netPoints": 22.0,
                  "netR": 1.85,
                  "status": "running",
                  "error": ""
                },
                {
                  "strategyId": 2,
                  "name": "Sell Scalp",
                  "closedTrades": 5,
                  "openTrades": 1,
                  "wins": 3,
                  "losses": 2,
                  "breakEvens": 0,
                  "netPoints": 12.5,
                  "netR": 0.90,
                  "status": "running",
                  "error": ""
                },
                {
                  "strategyId": 3,
                  "name": "Broken",
                  "closedTrades": 0,
                  "openTrades": 0,
                  "wins": 0,
                  "losses": 0,
                  "breakEvens": 0,
                  "netPoints": 0.0,
                  "netR": 0.0,
                  "status": "error",
                  "error": "ZeroDivisionError: division by zero"
                }
              ]
            }
        """.trimIndent()

        val snapshot = BacktestCodec.parseProgressJson(progressJson)!!
        assertEquals(3, snapshot.totalStrategies)
        assertEquals(2, snapshot.strategyIndex)
        assertEquals(3, snapshot.strategyStatuses.size)
        assertEquals("Buy Scalp", snapshot.strategyStatuses[0].name)
        assertEquals(8, snapshot.strategyStatuses[0].closedTrades)
        assertEquals(1.85, snapshot.strategyStatuses[0].netR, 1e-6)
        assertEquals("error", snapshot.strategyStatuses[2].status)
        assertTrue(snapshot.strategyStatuses[2].error.contains("ZeroDivisionError"))
    }
}
