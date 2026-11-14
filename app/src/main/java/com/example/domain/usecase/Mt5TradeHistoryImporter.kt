package com.example.domain.usecase

import com.example.backtest.BacktestCodec
import com.example.domain.model.Emotion
import com.example.domain.model.Trade
import com.example.domain.model.TradeDirection
import com.example.domain.model.TradeResult
import java.util.Locale

data class Mt5ImportResult(
    val trades: List<Trade>,
    val skippedRows: Int,
    val detectedFormat: String
)

object Mt5TradeHistoryImporter {

    fun parseCsv(rawBytes: ByteArray): Mt5ImportResult {
        if (rawBytes.isEmpty()) {
            return Mt5ImportResult(emptyList(), 0, "Empty")
        }
        val decoded = BacktestCodec.decodeCsvBytes(rawBytes)
        return parseCsvText(decoded.text)
    }

    fun parseCsvText(text: String): Mt5ImportResult {
        val lines = text.lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .toList()
        if (lines.size < 2) {
            return Mt5ImportResult(emptyList(), 0, "No rows")
        }

        val headerLine = lines.first()
        val delimiter = when {
            headerLine.contains('\t') -> '\t'
            headerLine.contains(';') -> ';'
            else -> ','
        }

        val headers = splitDelimitedLine(headerLine, delimiter)
            .map { normalizeHeader(it) }
        val idx = mutableMapOf<String, Int>()
        headers.forEachIndexed { colIdx, h ->
            if (!idx.containsKey(h)) {
                idx[h] = colIdx
            } else if (h == "time" && !idx.containsKey("close time")) {
                idx["close time"] = colIdx
            } else if (h == "price" && !idx.containsKey("close price")) {
                idx["close price"] = colIdx
            }
        }

        val timeCol = firstColumn(idx, "time", "open time", "opentime", "close time", "date")
        val dateCol = firstColumn(idx, "date")
        val pureTimeCol = firstColumn(idx, "time")
        val symbolCol = firstColumn(idx, "symbol", "instrument", "pair", "asset")
        val typeCol = firstColumn(idx, "type", "direction", "side", "action")
        val volCol = firstColumn(idx, "volume", "lots", "lotsize", "qty", "size")
        val priceCol = firstColumn(idx, "price", "open price", "open", "entryprice")
        val exitPriceCol = firstColumn(idx, "exit price", "close price", "exitprice")
        val slCol = firstColumn(idx, "s / l", "sl", "stop loss", "stoploss")
        val tpCol = firstColumn(idx, "t / p", "tp", "take profit", "takeprofit")
        val profitCol = firstColumn(idx, "profit", "pnl", "net profit", "netpnl")
        val commentCol = firstColumn(idx, "comment", "notes", "strategy")

        if (symbolCol == null || profitCol == null) {
            return Mt5ImportResult(emptyList(), lines.size - 1, "Missing Symbol/Profit headers")
        }

        val trades = mutableListOf<Trade>()
        var skipped = 0

        for (i in 1 until lines.size) {
            val cols = splitDelimitedLine(lines[i], delimiter)
            if (cols.size <= maxOf(symbolCol, profitCol)) {
                skipped++
                continue
            }

            val rawType = typeCol?.let { cols.getOrNull(it)?.trim()?.lowercase(Locale.US) }.orEmpty()
            val direction = when {
                rawType.contains("sell") || rawType.contains("short") -> TradeDirection.SELL
                rawType.contains("buy") || rawType.contains("long") -> TradeDirection.BUY
                rawType.isEmpty() -> TradeDirection.BUY
                else -> {
                    // Skip MT5 balance/deposit/credit rows
                    skipped++
                    continue
                }
            }

            val symbol = cols.getOrNull(symbolCol)?.trim().orEmpty()
            val pnlRaw = cols.getOrNull(profitCol)?.trim()?.replace(" ", "")?.toDoubleOrNull()
            if (symbol.isEmpty() || pnlRaw == null) {
                skipped++
                continue
            }

            val (dateStr, timeStr) = extractDateAndTime(
                cols = cols,
                dateCol = dateCol,
                pureTimeCol = pureTimeCol,
                fallbackTimeCol = timeCol
            )

            val result = when {
                pnlRaw > 0.0 -> TradeResult.WIN
                pnlRaw < 0.0 -> TradeResult.LOSS
                else -> TradeResult.BREAK_EVEN
            }

            val entryPrice = priceCol?.let { cols.getOrNull(it)?.trim()?.toDoubleOrNull() }
            val exitPrice = exitPriceCol?.let { cols.getOrNull(it)?.trim()?.toDoubleOrNull() }
            val sl = slCol?.let { cols.getOrNull(it)?.trim()?.toDoubleOrNull() }?.takeIf { it > 0.0 }
            val tp = tpCol?.let { cols.getOrNull(it)?.trim()?.toDoubleOrNull() }?.takeIf { it > 0.0 }
            val lots = volCol?.let { cols.getOrNull(it)?.trim()?.split("/")?.firstOrNull()?.toDoubleOrNull() }
            val notes = commentCol?.let { cols.getOrNull(it)?.trim().orEmpty() }.orEmpty()

            val rr = if (entryPrice != null && sl != null && tp != null && kotlin.math.abs(entryPrice - sl) > 0.0) {
                kotlin.math.abs(tp - entryPrice) / kotlin.math.abs(entryPrice - sl)
            } else null

            trades.add(
                Trade(
                    date = dateStr,
                    time = timeStr,
                    timestamp = System.currentTimeMillis() + i,
                    result = result,
                    pnl = pnlRaw,
                    instrument = symbol.uppercase(Locale.US),
                    direction = direction,
                    entryPrice = entryPrice,
                    exitPrice = exitPrice,
                    stopLoss = sl,
                    takeProfit = tp,
                    lotSize = lots,
                    riskRewardRatio = rr,
                    notes = if (notes.isNotBlank()) "MT5 Import: $notes" else "MT5 History Import",
                    emotion = Emotion.NEUTRAL,
                    followedRules = true,
                    tags = listOf(symbol, "MT5Import")
                )
            )
        }

        val formatLabel = when (delimiter) {
            '\t' -> "MT5 Tab Export"
            ';' -> "Semicolon CSV"
            else -> "Comma CSV"
        }
        return Mt5ImportResult(trades = trades, skippedRows = skipped, detectedFormat = formatLabel)
    }

    private fun extractDateAndTime(
        cols: List<String>,
        dateCol: Int?,
        pureTimeCol: Int?,
        fallbackTimeCol: Int?
    ): Pair<String, String> {
        if (dateCol != null && pureTimeCol != null && dateCol != pureTimeCol) {
            val d = normalizeDate(cols.getOrNull(dateCol).orEmpty())
            val t = normalizeTime(cols.getOrNull(pureTimeCol).orEmpty())
            return d to t
        }
        val raw = (fallbackTimeCol?.let { cols.getOrNull(it) } ?: "").trim()
        val parts = raw.split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (parts.size >= 2) {
            normalizeDate(parts[0]) to normalizeTime(parts[1])
        } else {
            normalizeDate(raw) to "12:00"
        }
    }

    private fun normalizeDate(raw: String): String {
        val cleaned = raw.trim().replace('.', '-').replace('/', '-')
        val match = Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})").find(cleaned)
        if (match != null) {
            val (y, m, d) = match.destructured
            return "${y.padStart(4, '0')}-${m.padStart(2, '0')}-${d.padStart(2, '0')}"
        }
        return "2025-01-01"
    }

    private fun normalizeTime(raw: String): String {
        val match = Regex("(\\d{1,2}):(\\d{2})").find(raw.trim())
        if (match != null) {
            val (h, m) = match.destructured
            return "${h.padStart(2, '0')}:${m.padStart(2, '0')}"
        }
        return "12:00"
    }

    private fun normalizeHeader(raw: String): String =
        raw.trim()
            .trim('<', '>', '"')
            .lowercase(Locale.US)
            .replace("_", " ")
            .replace(Regex("\\s+"), " ")

    private fun firstColumn(idx: Map<String, Int>, vararg candidates: String): Int? {
        for (c in candidates) {
            idx[c]?.let { return it }
        }
        return null
    }

    private fun splitDelimitedLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    result.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        result.add(sb.toString())
        return result
    }
}
