package com.example

import com.example.backtest.BacktestCodec
import com.example.data.repository.formatUtcIsoDate
import com.example.data.repository.parseIso8601EpochMillis
import com.example.domain.model.AppThemePreset
import com.example.domain.model.Trade
import com.example.domain.model.TradeDirection
import com.example.domain.model.TradeResult
import com.example.domain.usecase.Mql5SyntaxHighlighter
import com.example.domain.usecase.Mt5TradeHistoryImporter
import com.example.domain.usecase.RiskCalculator
import com.example.presentation.components.isLargeMql5Code
import com.example.presentation.components.spliceMql5Lines
import com.example.presentation.screens.computeTrailingDrawdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class NewFeaturesUnitTest {

    @Test
    fun mt5Importer_parsesUtf16LeTabExportAndSkipsBalanceRows() {
        val tsv = listOf(
            "Time\tPosition\tSymbol\tType\tVolume\tPrice\tS / L\tT / P\tTime\tPrice\tCommission\tSwap\tProfit",
            "2025.03.10 09:30:00\t1001\tXAUUSD\tbuy\t0.10\t2910.00\t2900.00\t2930.00\t2025.03.10 10:15:00\t2930.00\t0.00\t0.00\t200.00",
            "2025.03.10 12:00:00\t1002\tPainX 400\tsell\t0.20\t4520.00\t4540.00\t4480.00\t2025.03.10 12:30:00\t4540.00\t0.00\t0.00\t-40.50",
            "2025.03.10 13:00:00\t1003\tUSD\tbalance\t\t\t\t\t\t\t0.00\t0.00\t1000.00"
        ).joinToString("\n")

        val body = tsv.toByteArray(StandardCharsets.UTF_16LE)
        val utf16WithBom = ByteBuffer.allocate(2 + body.size)
            .order(ByteOrder.BIG_ENDIAN)
            .put(0xFF.toByte())
            .put(0xFE.toByte())
            .put(body)
            .array()

        val result = Mt5TradeHistoryImporter.parseCsv(utf16WithBom)
        assertEquals("MT5 Tab Export", result.detectedFormat)
        assertEquals(2, result.trades.size)
        assertEquals(1, result.skippedRows)

        val first = result.trades[0]
        assertEquals("XAUUSD", first.instrument)
        assertEquals(TradeDirection.BUY, first.direction)
        assertEquals(TradeResult.WIN, first.result)
        assertEquals(200.0, first.pnl, 0.001)
        assertEquals("2025-03-10", first.date)
        assertEquals("09:30", first.time)
        assertEquals(2910.0, first.entryPrice!!, 0.001)
        assertEquals(2930.0, first.exitPrice!!, 0.001)

        val second = result.trades[1]
        assertEquals("PAINX 400", second.instrument)
        assertEquals(TradeDirection.SELL, second.direction)
        assertEquals(TradeResult.LOSS, second.result)
        assertEquals(-40.5, second.pnl, 0.001)
    }

    @Test
    fun riskCalculator_computesLotsAndRMultiplesAccurately() {
        val res = RiskCalculator.calculate(
            accountBalance = 1000.0,
            riskPercent = 2.0,
            stopDistancePoints = 50.0,
            valuePerPointPerLot = 1.0
        )
        assertEquals(20.0, res.riskAmount, 0.001)
        assertEquals(0.40, res.rawLotSize, 0.001)
        assertEquals(0.40, res.roundedLotSize, 0.001)
        assertEquals(20.0, res.oneRReward, 0.001)
        assertEquals(40.0, res.twoRReward, 0.001)
        assertEquals(60.0, res.threeRReward, 0.001)

        val zeroStop = RiskCalculator.calculate(1000.0, 1.0, 0.0, 1.0)
        assertEquals(0.0, zeroStop.roundedLotSize, 0.001)
    }

    @Test
    fun trailingDrawdown_computesPeakAndCurrentDrawdown() {
        val trades = listOf(
            Trade(id = 1, date = "2025-03-01", time = "09:00", timestamp = 1, result = TradeResult.WIN, pnl = 500.0, instrument = "XAUUSD"),
            Trade(id = 2, date = "2025-03-02", time = "10:00", timestamp = 2, result = TradeResult.LOSS, pnl = -200.0, instrument = "XAUUSD"),
            Trade(id = 3, date = "2025-03-03", time = "11:00", timestamp = 3, result = TradeResult.LOSS, pnl = -100.0, instrument = "XAUUSD"),
            Trade(id = 4, date = "2025-03-04", time = "12:00", timestamp = 4, result = TradeResult.WIN, pnl = 150.0, instrument = "XAUUSD")
        )
        val (currentDd, maxDd) = computeTrailingDrawdown(trades)
        assertEquals(300.0, maxDd, 0.001)
        assertEquals(150.0, currentDd, 0.001)
    }

    @Test
    fun themePresets_includeSixDistinctOptionsWithOneDaylightMode() {
        val presets = AppThemePreset.values()
        assertEquals(6, presets.size)
        assertTrue(presets.single { it.forcesLight } == AppThemePreset.HIGH_CONTRAST_LIGHT)
    }

    @Test
    fun isoDateCompat_parsesOffsetsAndFormatsUtcDate() {
        val nyEpoch = parseIso8601EpochMillis("2025-02-10T08:30:00-05:00")!!
        val utcEpoch = parseIso8601EpochMillis("2025-02-10T13:30:00Z")!!
        val fracEpoch = parseIso8601EpochMillis("2025-02-10T13:30:00.250+00:00")!!

        assertEquals(utcEpoch, nyEpoch)
        assertEquals(utcEpoch + 250L, fracEpoch)
        assertEquals("2025-02-10", formatUtcIsoDate(utcEpoch))

        val crossedDay = parseIso8601EpochMillis("2025-02-10T21:00:00-05:00")!!
        assertEquals("2025-02-11", formatUtcIsoDate(crossedDay))
    }

    @Test
    fun largeMql5Code_triggersVirtualizedModeForFourThousandShortLines() {
        val lines = (1..4_200).map { "int v$it = $it;" }
        val code = lines.joinToString("\n")
        assertTrue("4,200 lines under 80KB must trigger large-file mode", isLargeMql5Code(code, lines.size))
        assertFalse(isLargeMql5Code("void OnTick() {}\n", 2))

        val spliced = spliceMql5Lines(
            lines = lines,
            startIndex = 100,
            endExclusive = 102,
            replacementChunk = "int v101 = 999;\nint v102 = 888;"
        )
        val resultLines = spliced.lines()
        assertEquals(lines.size, resultLines.size)
        assertEquals("int v1 = 1;", resultLines[0])
        assertEquals("int v101 = 999;", resultLines[100])
        assertEquals("int v102 = 888;", resultLines[101])
        assertEquals("int v4200 = 4200;", resultLines.last())
    }

    @Test
    fun readTextStreamWithBom_decodesLargeMql5Utf16LeWithAndWithoutBom() {
        val mql5Source = buildString {
            appendLine("//+------------------------------------------------------------------+")
            appendLine("#property strict")
            for (i in 1..4_500) {
                appendLine("void Helper_$i() { int x = $i; }")
            }
        }

        // 1. UTF-16LE with BOM (MetaEditor default)
        val body = mql5Source.toByteArray(StandardCharsets.UTF_16LE)
        val withBom = ByteBuffer.allocate(2 + body.size)
            .put(0xFF.toByte())
            .put(0xFE.toByte())
            .put(body)
            .array()
        val decodedBom = BacktestCodec.readTextStreamWithBom(ByteArrayInputStream(withBom))
        assertFalse(decodedBom.contains('\u0000'))
        assertTrue(decodedBom.lineSequence().count() >= 4_500)
        assertTrue(decodedBom.contains("void Helper_4500()"))

        // 2. BOM-less UTF-16LE
        val decodedBomless = BacktestCodec.readTextStreamWithBom(ByteArrayInputStream(body))
        assertFalse(decodedBomless.contains('\u0000'))
        assertTrue(decodedBomless.contains("#property strict"))
        assertTrue(decodedBomless.contains("void Helper_4500()"))
    }

    @Test
    fun mql5SyntaxHighlighter_highlightsKeywordsAndSearchWithoutCrashing() {
        val sample = "input double RiskPct = 1.5; // \"ignored comment\" OnTick()"
        val highlighted = Mql5SyntaxHighlighter.highlight(sample, isDark = true, searchQuery = "RiskPct")
        assertEquals(sample, highlighted.text)
        assertTrue(highlighted.spanStyles.isNotEmpty())
    }
}
