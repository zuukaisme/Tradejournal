package com.example.backtest

import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.PushbackInputStream
import java.nio.charset.Charset
import java.util.Locale

data class ReplayCandle(
    val index: Int,
    val time: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double
)

data class TradeReplayMarker(
    val trade: Trade,
    val entryCandlePos: Int,
    val exitCandlePos: Int
)

data class ContinuousCandleSlice(
    val candles: List<ReplayCandle>,
    val totalCandles: Int,
    val startIndex: Int,
    val endIndex: Int
)

data class TradeReplayWindow(
    val candles: List<ReplayCandle>,
    val entryCandlePos: Int,
    val exitCandlePos: Int,
    val truncatedMiddle: Boolean = false,
    val markers: List<TradeReplayMarker> = emptyList(),
    val totalDatasetCandles: Int = candles.size,
    val windowStartIndex: Int = candles.firstOrNull()?.index ?: 0,
    val windowEndIndex: Int = candles.lastOrNull()?.index ?: -1
)

/**
 * Streams targeted OHLC candle windows from disk using O(1) memory (or O(1) `.btbin` random-access),
 * supporting both MT5 exports (<DATE>\t<TIME>...) and standard CSVs without loading the file.
 */
object CsvCandleWindowReader {

    private data class ColumnMap(
        val delimiter: Char,
        val openIdx: Int,
        val highIdx: Int,
        val lowIdx: Int,
        val closeIdx: Int,
        val hasDateAndTime: Boolean,
        val dateIdx: Int,
        val timeIdx: Int,
        val tsIdx: Int
    )

    fun readTradeWindow(
        csvFile: File,
        trade: Trade,
        paddingBefore: Int = 20,
        paddingAfter: Int = 15,
        maxWindowCandles: Int = 220
    ): TradeReplayWindow {
        if (!csvFile.exists() || !csvFile.isFile) {
            return TradeReplayWindow(emptyList(), -1, -1)
        }

        BinaryCandleCache.readTradeWindow(
            csvFile = csvFile,
            trade = trade,
            paddingBefore = paddingBefore,
            paddingAfter = paddingAfter,
            maxWindowCandles = maxWindowCandles
        )?.let { cachedWindow ->
            if (cachedWindow.candles.isNotEmpty()) {
                return cachedWindow
            }
        }

        val (pushback, charset) = openUtfAwareReader(csvFile)
        BufferedReader(InputStreamReader(pushback, charset), 65_536).use { reader ->
            var headerLine: String? = null
            var isFirst = true
            while (true) {
                val raw = reader.readLine() ?: break
                val cleaned = if (isFirst) {
                    isFirst = false
                    raw.removePrefix("\uFEFF")
                } else {
                    raw
                }
                if (cleaned.isNotBlank()) {
                    headerLine = cleaned.trim()
                    break
                }
            }
            val header = headerLine ?: return TradeReplayWindow(emptyList(), -1, -1)
            val cols = parseHeader(header) ?: return TradeReplayWindow(emptyList(), -1, -1)

            val hasIndices = trade.openIndex >= 0 && trade.closeIndex >= trade.openIndex
            if (hasIndices) {
                return readByIndices(reader, cols, trade, paddingBefore, paddingAfter, maxWindowCandles)
            }
            return readByTimestamps(reader, cols, trade, paddingBefore, paddingAfter, maxWindowCandles)
        }
    }

    /**
     * Reads a contiguous 1:1 native candle slice `[startIndex..endIndex]` (no bucketing/downsampling).
     * Uses `.btbin` O(1) disk seeks when valid, falling back to streaming CSV scan.
     */
    fun readContinuousSlice(
        csvFile: File,
        startIndex: Int,
        endIndex: Int,
        knownTotalCandles: Int = 0
    ): ContinuousCandleSlice {
        if (!csvFile.exists() || !csvFile.isFile) {
            return ContinuousCandleSlice(emptyList(), 0, 0, -1)
        }
        BinaryCandleCache.readContinuousSlice(csvFile, startIndex, endIndex)?.let { cached ->
            if (cached.candles.isNotEmpty() || cached.totalCandles == 0) {
                return cached
            }
        }

        val safeStart = startIndex.coerceAtLeast(0)
        val safeEnd = endIndex.coerceAtLeast(safeStart)
        val (pushback, charset) = openUtfAwareReader(csvFile)
        return BufferedReader(InputStreamReader(pushback, charset), 65_536).use { reader ->
            var headerLine: String? = null
            var first = true
            while (true) {
                val raw = reader.readLine() ?: break
                val cleaned = if (first) {
                    first = false
                    raw.removePrefix("\uFEFF")
                } else raw
                if (cleaned.isNotBlank()) {
                    headerLine = cleaned.trim()
                    break
                }
            }
            val cols = headerLine?.let(::parseHeader)
                ?: return@use ContinuousCandleSlice(emptyList(), 0, 0, -1)

            val collected = ArrayList<ReplayCandle>((safeEnd - safeStart + 1).coerceIn(16, 4096))
            var currentIndex = 0
            while (true) {
                val raw = reader.readLine() ?: break
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) continue
                if (currentIndex in safeStart..safeEnd) {
                    parseCandleRow(currentIndex, trimmed, cols)?.let(collected::add)
                }
                currentIndex++
                if (currentIndex > safeEnd && knownTotalCandles > 0) {
                    break
                }
            }
            val totalCount = if (knownTotalCandles > 0) maxOf(knownTotalCandles, currentIndex) else currentIndex
            ContinuousCandleSlice(
                candles = collected,
                totalCandles = totalCount,
                startIndex = collected.firstOrNull()?.index ?: safeStart,
                endIndex = collected.lastOrNull()?.index ?: (safeStart - 1)
            )
        }
    }

    /**
     * Loads a high-resolution 1:1 contiguous candle window around [focusGlobalIndex] (or spanning
     * all trades directly when the dataset/span fits cleanly inside [maxWindowCandles]) so candles
     * render crisp and TradingView-sized without crushing multi-day bars into coarse buckets.
     */
    fun readTradesWindow(
        csvFile: File,
        trades: List<Trade>,
        focusGlobalIndex: Int = -1,
        maxWindowCandles: Int = 1800,
        knownTotalCandles: Int = 0
    ): TradeReplayWindow {
        val indexedTrades = trades.filter { it.openIndex >= 0 && it.closeIndex >= it.openIndex }
            .sortedBy { it.openIndex }
        if (!csvFile.exists()) return TradeReplayWindow(emptyList(), -1, -1)

        if (indexedTrades.isEmpty()) {
            // Fallback for timestamp-only legacy trade records
            val firstTrade = trades.firstOrNull() ?: return TradeReplayWindow(emptyList(), -1, -1)
            return readTradeWindow(csvFile, firstTrade, paddingBefore = 40, paddingAfter = 40)
        }

        val cachedCount = BinaryCandleCache.readCandleCount(csvFile) ?: knownTotalCandles
        val minTradeOpen = indexedTrades.minOf { it.openIndex }
        val maxTradeClose = indexedTrades.maxOf { it.closeIndex }
        val defaultCenter = if (focusGlobalIndex >= 0) {
            focusGlobalIndex
        } else {
            (indexedTrades.first().closeIndex + 8).coerceAtLeast(0)
        }

        val totalKnownOrMin = if (cachedCount > 0) cachedCount else (maxTradeClose + 60)
        val fullSpanStart = (minTradeOpen - 40).coerceAtLeast(0)
        val fullSpanEnd = (maxTradeClose + 40).coerceAtMost((totalKnownOrMin - 1).coerceAtLeast(0))

        val (startIndex, endIndex) = if (totalKnownOrMin <= maxWindowCandles) {
            0 to (totalKnownOrMin - 1).coerceAtLeast(0)
        } else if ((fullSpanEnd - fullSpanStart + 1) <= maxWindowCandles) {
            fullSpanStart to fullSpanEnd
        } else {
            val half = maxWindowCandles / 2
            val s = (defaultCenter - half).coerceAtLeast(0)
            val e = (s + maxWindowCandles - 1).coerceAtMost((totalKnownOrMin - 1).coerceAtLeast(s))
            val adjustedS = (e - maxWindowCandles + 1).coerceAtLeast(0)
            adjustedS to e
        }

        val slice = readContinuousSlice(
            csvFile = csvFile,
            startIndex = startIndex,
            endIndex = endIndex,
            knownTotalCandles = cachedCount
        )
        if (slice.candles.isEmpty()) return TradeReplayWindow(emptyList(), -1, -1)

        val markers = indexedTrades
            .filter { it.closeIndex >= slice.startIndex && it.openIndex <= slice.endIndex }
            .map { trade ->
                TradeReplayMarker(
                    trade = trade,
                    entryCandlePos = (trade.openIndex - slice.startIndex).coerceIn(0, slice.candles.lastIndex),
                    exitCandlePos = (trade.closeIndex - slice.startIndex).coerceIn(0, slice.candles.lastIndex)
                )
            }
        val selected = markers.firstOrNull()
        return TradeReplayWindow(
            candles = slice.candles,
            entryCandlePos = selected?.entryCandlePos ?: -1,
            exitCandlePos = selected?.exitCandlePos ?: -1,
            truncatedMiddle = false,
            markers = markers,
            totalDatasetCandles = slice.totalCandles,
            windowStartIndex = slice.startIndex,
            windowEndIndex = slice.endIndex
        )
    }

    private fun readByIndices(
        reader: BufferedReader,
        cols: ColumnMap,
        trade: Trade,
        paddingBefore: Int,
        paddingAfter: Int,
        maxWindowCandles: Int
    ): TradeReplayWindow {
        val startIdx = (trade.openIndex - paddingBefore).coerceAtLeast(0)
        val endIdx = trade.closeIndex + paddingAfter
        val rawSpan = (endIdx - startIdx + 1).coerceAtLeast(1)

        // If trade duration fits within cap, keep every single candle cleanly
        if (rawSpan <= maxWindowCandles) {
            val collected = ArrayList<ReplayCandle>(rawSpan)
            var currentIdx = 0
            while (true) {
                val rawLine = reader.readLine() ?: break
                val line = rawLine.trim()
                if (line.isEmpty()) continue
                if (currentIdx in startIdx..endIdx) {
                    parseCandleRow(currentIdx, line, cols)?.let { collected.add(it) }
                }
                if (currentIdx >= endIdx) break
                currentIdx++
            }
            val entryPos = collected.indexOfFirst { it.index == trade.openIndex }
                .let { if (it >= 0) it else 0 }
            val exitPos = collected.indexOfFirst { it.index == trade.closeIndex }
                .let { if (it >= 0) it else collected.lastIndex }
            return TradeReplayWindow(
                candles = collected,
                entryCandlePos = entryPos,
                exitCandlePos = exitPos,
                truncatedMiddle = false
            )
        }

        // Huge duration trade (e.g. >200 bars open): preserve context around entry + sample middle + full close tail
        val headEnd = (trade.openIndex + 40).coerceAtMost(endIdx)
        val tailStart = (trade.closeIndex - 60).coerceAtLeast(headEnd + 1)
        val middleBudget = (maxWindowCandles - (headEnd - startIdx + 1) - (endIdx - tailStart + 1)).coerceAtLeast(20)
        val middleSpan = (tailStart - headEnd - 1).coerceAtLeast(1)
        val middleStep = ((middleSpan + middleBudget - 1) / middleBudget).coerceAtLeast(1)

        val collected = ArrayList<ReplayCandle>(maxWindowCandles + 16)
        var currentIdx = 0
        while (true) {
            val rawLine = reader.readLine() ?: break
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (currentIdx >= startIdx) {
                val keep = when {
                    currentIdx == trade.openIndex || currentIdx == trade.closeIndex -> true
                    currentIdx <= headEnd -> true
                    currentIdx >= tailStart -> true
                    else -> ((currentIdx - headEnd) % middleStep) == 0
                }
                if (keep) {
                    parseCandleRow(currentIdx, line, cols)?.let { collected.add(it) }
                }
            }
            if (currentIdx >= endIdx) break
            currentIdx++
        }
        val entryPos = collected.indexOfFirst { it.index == trade.openIndex }
            .let { if (it >= 0) it else 0 }
        val exitPos = collected.indexOfFirst { it.index == trade.closeIndex }
            .let { if (it >= 0) it else collected.lastIndex }
        return TradeReplayWindow(
            candles = collected,
            entryCandlePos = entryPos,
            exitCandlePos = exitPos,
            truncatedMiddle = true
        )
    }

    private fun readByTimestamps(
        reader: BufferedReader,
        cols: ColumnMap,
        trade: Trade,
        paddingBefore: Int,
        paddingAfter: Int,
        maxWindowCandles: Int
    ): TradeReplayWindow {
        val beforeRing = ArrayDeque<ReplayCandle>(paddingBefore + 2)
        val collected = ArrayList<ReplayCandle>()
        var foundOpen = false
        var foundClose = false
        var remainingAfter = paddingAfter
        var currentIdx = 0
        var openMatchedIdx = -1
        var closeMatchedIdx = -1

        while (true) {
            val rawLine = reader.readLine() ?: break
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val candle = parseCandleRow(currentIdx, line, cols)
            if (candle != null) {
                if (!foundOpen) {
                    if (candle.time == trade.openTime) {
                        foundOpen = true
                        openMatchedIdx = candle.index
                        collected.addAll(beforeRing)
                        beforeRing.clear()
                        collected.add(candle)
                        if (candle.time == trade.closeTime) {
                            foundClose = true
                            closeMatchedIdx = candle.index
                        }
                    } else {
                        if (beforeRing.size >= paddingBefore) beforeRing.removeFirst()
                        beforeRing.addLast(candle)
                    }
                } else {
                    if (collected.size < maxWindowCandles || !foundClose) {
                        collected.add(candle)
                    }
                    if (!foundClose && candle.time == trade.closeTime) {
                        foundClose = true
                        closeMatchedIdx = candle.index
                    } else if (foundClose) {
                        remainingAfter--
                        if (remainingAfter <= 0) break
                    }
                }
            }
            currentIdx++
        }

        if (collected.isEmpty()) return TradeReplayWindow(emptyList(), -1, -1)
        val entryPos = collected.indexOfFirst { it.index == openMatchedIdx }.let { if (it >= 0) it else 0 }
        val exitPos = collected.indexOfFirst { it.index == closeMatchedIdx }.let { if (it >= 0) it else collected.lastIndex }
        return TradeReplayWindow(collected, entryPos, exitPos)
    }

    private fun openUtfAwareReader(file: File): Pair<PushbackInputStream, Charset> {
        val pb = PushbackInputStream(BufferedInputStream(FileInputStream(file), 65_536), 4)
        val bom = ByteArray(4)
        var read = 0
        while (read < 4) {
            val count = pb.read(bom, read, 4 - read)
            if (count <= 0) break
            read += count
        }
        return when {
            read >= 2 && bom[0] == 0xFF.toByte() && bom[1] == 0xFE.toByte() -> {
                if (read > 2) pb.unread(bom, 2, read - 2)
                pb to Charsets.UTF_16LE
            }
            read >= 2 && bom[0] == 0xFE.toByte() && bom[1] == 0xFF.toByte() -> {
                if (read > 2) pb.unread(bom, 2, read - 2)
                pb to Charsets.UTF_16BE
            }
            read >= 3 && bom[0] == 0xEF.toByte() && bom[1] == 0xBB.toByte() && bom[2] == 0xBF.toByte() -> {
                if (read > 3) pb.unread(bom, 3, read - 3)
                pb to Charsets.UTF_8
            }
            else -> {
                if (read > 0) pb.unread(bom, 0, read)
                pb to Charsets.UTF_8
            }
        }
    }

    private fun parseHeader(firstLine: String): ColumnMap? {
        val delim = when {
            firstLine.contains('\t') -> '\t'
            firstLine.contains(';') -> ';'
            else -> ','
        }
        val cols = splitCsvLine(firstLine, delim).map {
            it.trim().trim('<', '>', '"').lowercase(Locale.US)
        }
        val idxMap = HashMap<String, Int>(cols.size)
        cols.forEachIndexed { index, name ->
            if (name.isNotEmpty() && !idxMap.containsKey(name)) {
                idxMap[name] = index
            }
        }
        val openIdx = idxMap["open"] ?: return null
        val highIdx = idxMap["high"] ?: return null
        val lowIdx = idxMap["low"] ?: return null
        val closeIdx = idxMap["close"] ?: return null
        val hasDateTime = idxMap.containsKey("date") && idxMap.containsKey("time")
        val dateIdx = idxMap["date"] ?: -1
        val timeIdx = idxMap["time"] ?: -1
        val tsIdx = idxMap["datetime"] ?: idxMap["timestamp"] ?: idxMap["date"] ?: idxMap["time"] ?: return null
        return ColumnMap(delim, openIdx, highIdx, lowIdx, closeIdx, hasDateTime, dateIdx, timeIdx, tsIdx)
    }

    private fun parseCandleRow(index: Int, line: String, cols: ColumnMap): ReplayCandle? {
        val fields = splitCsvLine(line, cols.delimiter)
        val needed = maxOf(cols.openIdx, cols.highIdx, cols.lowIdx, cols.closeIdx, cols.tsIdx, cols.dateIdx, cols.timeIdx)
        if (fields.size <= needed) return null
        val time = if (cols.hasDateAndTime) {
            "${fields[cols.dateIdx].trim()} ${fields[cols.timeIdx].trim()}"
        } else {
            fields[cols.tsIdx].trim()
        }
        val o = fields[cols.openIdx].trim().toDoubleOrNull() ?: return null
        val h = fields[cols.highIdx].trim().toDoubleOrNull() ?: return null
        val l = fields[cols.lowIdx].trim().toDoubleOrNull() ?: return null
        val c = fields[cols.closeIdx].trim().toDoubleOrNull() ?: return null
        return ReplayCandle(index = index, time = time, open = o, high = h, low = l, close = c)
    }

    private fun splitCsvLine(line: String, delimiter: Char): List<String> {
        if ('"' !in line) return line.split(delimiter)
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    result.add(sb.toString())
                    sb.setLength(0)
                }
                else -> sb.append(ch)
            }
        }
        result.add(sb.toString())
        return result
    }
}
