package com.example.backtest

import java.io.InputStream
import java.nio.charset.Charset

object BacktestCsvParser {

    data class ParseResult(
        val candles: List<Candle>,
        val skippedRows: Int,
        val detectedEncoding: String,
        val detectedDelimiter: String
    )

    fun parse(inputStream: InputStream): ParseResult {
        val bytes = inputStream.readBytes()
        return parseBytes(bytes)
    }

    fun parseBytes(bytes: ByteArray): ParseResult {
        val (text, encoding) = decodeWithBomDetection(bytes)
        return parseText(text, encoding)
    }

    fun parseText(text: String, encodingLabel: String = "UTF-8"): ParseResult {
        val cleanText = text.removePrefix("\uFEFF")
        val lines = cleanText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        if (lines.isEmpty()) {
            throw IllegalArgumentException("CSV file is empty.")
        }

        val headerLine = lines.first()
        val delimiter = detectDelimiter(headerLine)
        val headers = splitLine(headerLine, delimiter).map { normalizeHeader(it) }

        val dateCol = headers.indexOfFirst { it == "date" }
        val timeCol = headers.indexOfFirst {
            it == "time" || it == "timestamp" || it == "datetime" || it == "date_time"
        }
        val openCol = headers.indexOfFirst { it == "open" || it == "o" }
        val highCol = headers.indexOfFirst { it == "high" || it == "h" }
        val lowCol = headers.indexOfFirst { it == "low" || it == "l" }
        val closeCol = headers.indexOfFirst { it == "close" || it == "c" }
        val volCol = headers.indexOfFirst {
            it == "tickvol" || it == "tick_vol" || it == "tickvolume" ||
                it == "vol" || it == "volume" || it == "v"
        }

        if (openCol == -1 || highCol == -1 || lowCol == -1 || closeCol == -1) {
            throw IllegalArgumentException(
                "Missing required OHLC headers (OPEN, HIGH, LOW, CLOSE). Found: ${headers.joinToString(", ")}"
            )
        }
        if (dateCol == -1 && timeCol == -1) {
            throw IllegalArgumentException(
                "Missing required DATE or TIME header. Found: ${headers.joinToString(", ")}"
            )
        }

        val candles = ArrayList<Candle>(lines.size - 1)
        var skippedRows = 0
        val useCommaAsDecimal = (delimiter == ';' || delimiter == '\t')

        for (lineIndex in 1 until lines.size) {
            val cols = splitLine(lines[lineIndex], delimiter)
            val maxRequiredCol = maxOf(openCol, highCol, lowCol, closeCol, dateCol, timeCol)
            if (cols.size <= maxRequiredCol) {
                skippedRows++
                continue
            }

            val timeString = buildTimeString(cols, dateCol, timeCol)
            val open = parseNumeric(cols[openCol], useCommaAsDecimal)
            val high = parseNumeric(cols[highCol], useCommaAsDecimal)
            val low = parseNumeric(cols[lowCol], useCommaAsDecimal)
            val close = parseNumeric(cols[closeCol], useCommaAsDecimal)
            val volume = if (volCol != -1 && volCol < cols.size) {
                parseNumeric(cols[volCol], useCommaAsDecimal) ?: 0.0
            } else {
                0.0
            }

            if (open == null || high == null || low == null || close == null || timeString.isBlank()) {
                skippedRows++
                continue
            }

            candles.add(
                Candle(
                    time = timeString,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = volume
                )
            )
        }

        val delimiterLabel = when (delimiter) {
            '\t' -> "Tab (MT5)"
            ';' -> "Semicolon"
            else -> "Comma"
        }

        return ParseResult(
            candles = candles,
            skippedRows = skippedRows,
            detectedEncoding = encodingLabel,
            detectedDelimiter = delimiterLabel
        )
    }

    internal fun decodeWithBomDetection(bytes: ByteArray): Pair<String, String> {
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFF && b1 == 0xFE) {
                val text = String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
                return text to "UTF-16LE (BOM)"
            }
            if (b0 == 0xFE && b1 == 0xFF) {
                val text = String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
                return text to "UTF-16BE (BOM)"
            }
        }
        if (bytes.size >= 3) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            val b2 = bytes[2].toInt() and 0xFF
            if (b0 == 0xEF && b1 == 0xBB && b2 == 0xBF) {
                val text = String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
                return text to "UTF-8 (BOM)"
            }
        }
        return String(bytes, Charset.forName("UTF-8")) to "UTF-8"
    }

    private fun detectDelimiter(headerLine: String): Char {
        val candidates = listOf('\t', ';', ',')
        var bestChar = ','
        var bestCount = -1
        for (candidate in candidates) {
            val count = countDelimiterOutsideQuotes(headerLine, candidate)
            if (count > bestCount) {
                bestCount = count
                bestChar = candidate
            }
        }
        return if (bestCount > 0) bestChar else ','
    }

    private fun countDelimiterOutsideQuotes(line: String, delimiter: Char): Int {
        var inQuotes = false
        var count = 0
        for (ch in line) {
            if (ch == '"') inQuotes = !inQuotes
            else if (ch == delimiter && !inQuotes) count++
        }
        return count
    }

    private fun splitLine(line: String, delimiter: Char): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (i in line.indices) {
            val ch = line[i]
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString().trim())
        return result
    }

    private fun normalizeHeader(raw: String): String {
        return raw.trim()
            .removeSurrounding("\"")
            .removePrefix("<")
            .removeSuffix(">")
            .trim()
            .lowercase()
            .replace(" ", "_")
    }

    private fun buildTimeString(cols: List<String>, dateCol: Int, timeCol: Int): String {
        val dateVal = if (dateCol != -1) cols[dateCol].trim() else ""
        val timeVal = if (timeCol != -1) cols[timeCol].trim() else ""
        return when {
            dateVal.isNotEmpty() && timeVal.isNotEmpty() && dateCol != timeCol -> "$dateVal $timeVal"
            timeVal.isNotEmpty() -> timeVal
            else -> dateVal
        }
    }

    private fun parseNumeric(raw: String, allowCommaDecimal: Boolean): Double? {
        val trimmed = raw.trim().removeSurrounding("\"")
        if (trimmed.isEmpty()) return null
        val direct = trimmed.toDoubleOrNull()
        if (direct != null) return direct
        if (allowCommaDecimal && trimmed.contains(',')) {
            return trimmed.replace(',', '.').toDoubleOrNull()
        }
        return null
    }
}
