package com.example.backtest

import java.math.BigDecimal
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

enum class BacktestTimeframe(
    val minutes: Int,
    val label: String,
    val shortLabel: String
) {
    NATIVE(0, "Native", "Native"),
    M1(1, "1m", "M1"),
    M5(5, "5m", "M5"),
    M15(15, "15m", "M15"),
    M30(30, "30m", "M30"),
    H1(60, "1H", "H1"),
    H2(120, "2H", "H2"),
    H4(240, "4H", "H4"),
    D1(1440, "1D", "D1"),
    W1(10080, "1W", "W1")
}

data class DetectedTimeframe(
    val minutes: Int?,
    val label: String
)

data class ResampledCsvResult(
    val csvText: String,
    val sourceCandleCount: Int,
    val outputCandleCount: Int,
    val targetTimeframe: BacktestTimeframe
)

private data class ParsedCandle(
    val epochMillis: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

private data class CsvHeaderInfo(
    val delimiter: Char,
    val dateIdx: Int?,
    val timeIdx: Int?,
    val singleTimeIdx: Int?,
    val openIdx: Int?,
    val highIdx: Int?,
    val lowIdx: Int?,
    val closeIdx: Int?,
    val volIdx: Int?
)

object CandleTimeframeResampler {

    private val UTC_TZ: TimeZone = TimeZone.getTimeZone("UTC")
    private val STANDARD_MINUTES = setOf(1, 5, 15, 30, 60, 120, 240, 1440, 10080)

    private val YEAR_FIRST_REGEX = Regex(
        """^(\d{4})[.\-/](\d{1,2})[.\-/](\d{1,2})(?:[T\s]+(\d{1,2}):(\d{2})(?::(\d{2}))?(?:\.\d+)?)?(Z|([+-])(\d{2}):?(\d{2}))?$"""
    )
    private val DAY_FIRST_DOT_REGEX = Regex(
        """^(\d{1,2})\.(\d{1,2})\.(\d{4})(?:[T\s]+(\d{1,2}):(\d{2})(?::(\d{2}))?)?$"""
    )
    private val SLASH_DMY_MDY_REGEX = Regex(
        """^(\d{1,2})/(\d{1,2})/(\d{4})(?:[T\s]+(\d{1,2}):(\d{2})(?::(\d{2}))?)?$"""
    )
    private val NUMERIC_EPOCH_REGEX = Regex("""^\d{9,14}(?:\.\d+)?$""")

    fun availableTargets(detectedMinutes: Int?): List<BacktestTimeframe> {
        if (detectedMinutes == null || detectedMinutes <= 0) {
            return listOf(BacktestTimeframe.NATIVE)
        }
        val higher = BacktestTimeframe.entries.filter { it.minutes > detectedMinutes }
        return listOf(BacktestTimeframe.NATIVE) + higher
    }

    fun formatTimeframeLabel(minutes: Int?): String {
        if (minutes == null || minutes <= 0) return "Unknown"
        return when (minutes) {
            1 -> "1m (M1)"
            5 -> "5m (M5)"
            15 -> "15m (M15)"
            30 -> "30m (M30)"
            60 -> "1H (H1)"
            120 -> "2H (H2)"
            240 -> "4H (H4)"
            1440 -> "1D (D1)"
            10080 -> "1W (W1)"
            else -> if (minutes >= 60 && minutes % 60 == 0) {
                "${minutes / 60}H"
            } else {
                "${minutes}m"
            }
        }
    }

    fun detectTimeframe(csvText: String): DetectedTimeframe {
        val lines = csvText.removePrefix("\uFEFF")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(605)
            .toList()
        if (lines.size < 3) return DetectedTimeframe(null, "Unknown")

        val header = parseHeader(lines[0]) ?: return DetectedTimeframe(null, "Unknown")
        if (header.singleTimeIdx == null && (header.dateIdx == null || header.timeIdx == null)) {
            return DetectedTimeframe(null, "Unknown")
        }

        val cal = newUtcCalendar()
        val deltaCounts = HashMap<Int, Int>()
        var prevMillis: Long? = null

        for (i in 1 until lines.size) {
            val cols = lines[i].split(header.delimiter)
            val rawTs = extractTimestampString(cols, header) ?: continue
            val millis = parseEpochMillis(rawTs, cal) ?: continue
            val prev = prevMillis
            if (prev != null) {
                val diffMs = abs(millis - prev)
                if (diffMs >= 60_000L) {
                    val mins = (diffMs / 60_000L).toInt()
                    if (mins in 1..43200) {
                        deltaCounts[mins] = (deltaCounts[mins] ?: 0) + 1
                    }
                }
            }
            prevMillis = millis
        }

        if (deltaCounts.isEmpty()) return DetectedTimeframe(null, "Unknown")

        // Prefer the most frequent interval; on exact ties prefer standard timeframe, then smaller interval.
        val detectedMinutes = deltaCounts.entries
            .sortedWith(
                compareByDescending<Map.Entry<Int, Int>> { it.value }
                    .thenByDescending { it.key in STANDARD_MINUTES }
                    .thenBy { it.key }
            )
            .first()
            .key

        return DetectedTimeframe(
            minutes = detectedMinutes,
            label = formatTimeframeLabel(detectedMinutes)
        )
    }

    fun resampleCsv(csvText: String, target: BacktestTimeframe): ResampledCsvResult {
        val cleanText = csvText.removePrefix("\uFEFF")
        val lines = cleanText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        if (target == BacktestTimeframe.NATIVE) {
            val count = (lines.size - 1).coerceAtLeast(0)
            return ResampledCsvResult(
                csvText = cleanText,
                sourceCandleCount = count,
                outputCandleCount = count,
                targetTimeframe = target
            )
        }

        require(lines.size >= 2) { "The CSV file does not contain enough candle rows to resample." }

        val header = parseHeader(lines[0])
            ?: throw IllegalArgumentException("Unrecognized CSV header.")

        val openIdx = header.openIdx
        val highIdx = header.highIdx
        val lowIdx = header.lowIdx
        val closeIdx = header.closeIdx
        if (openIdx == null || highIdx == null || lowIdx == null || closeIdx == null) {
            throw IllegalArgumentException("CSV must contain open, high, low, and close columns.")
        }
        if (header.singleTimeIdx == null && (header.dateIdx == null || header.timeIdx == null)) {
            throw IllegalArgumentException("CSV must contain date/time columns to resample timeframes.")
        }

        val cal = newUtcCalendar()
        val candles = ArrayList<ParsedCandle>(lines.size - 1)
        var isChronological = true
        var lastEpoch = Long.MIN_VALUE

        for (i in 1 until lines.size) {
            val cols = lines[i].split(header.delimiter)
            val rawTs = extractTimestampString(cols, header) ?: continue
            val epoch = parseEpochMillis(rawTs, cal) ?: continue
            val o = cols.getOrNull(openIdx)?.trim()?.toDoubleOrNull() ?: continue
            val h = cols.getOrNull(highIdx)?.trim()?.toDoubleOrNull() ?: continue
            val l = cols.getOrNull(lowIdx)?.trim()?.toDoubleOrNull() ?: continue
            val c = cols.getOrNull(closeIdx)?.trim()?.toDoubleOrNull() ?: continue
            val v = header.volIdx?.let { cols.getOrNull(it)?.trim()?.toDoubleOrNull() } ?: 0.0

            if (epoch < lastEpoch) {
                isChronological = false
            }
            lastEpoch = epoch
            candles.add(ParsedCandle(epoch, o, h, l, c, v))
        }

        require(candles.isNotEmpty()) { "Could not parse candle timestamps/prices for resampling." }

        if (!isChronological) {
            candles.sortBy { it.epochMillis }
        }

        val out = StringBuilder(candles.size * 42)
        out.append("datetime,open,high,low,close,volume\n")

        var currentBucket = Long.MIN_VALUE
        var bOpen = 0.0
        var bHigh = 0.0
        var bLow = 0.0
        var bClose = 0.0
        var bVol = 0.0
        var outputCount = 0

        fun flushBucket(bucketStartMs: Long) {
            formatUtcTimestamp(bucketStartMs, cal, out)
            out.append(',')
            out.append(formatNumber(bOpen)).append(',')
            out.append(formatNumber(bHigh)).append(',')
            out.append(formatNumber(bLow)).append(',')
            out.append(formatNumber(bClose)).append(',')
            out.append(formatNumber(bVol)).append('\n')
            outputCount++
        }

        for (candle in candles) {
            val bucketMs = bucketStartMillis(candle.epochMillis, target, cal)
            if (bucketMs != currentBucket) {
                if (currentBucket != Long.MIN_VALUE) {
                    flushBucket(currentBucket)
                }
                currentBucket = bucketMs
                bOpen = candle.open
                bHigh = candle.high
                bLow = candle.low
                bClose = candle.close
                bVol = candle.volume
            } else {
                if (candle.high > bHigh) bHigh = candle.high
                if (candle.low < bLow) bLow = candle.low
                bClose = candle.close
                bVol += candle.volume
            }
        }
        if (currentBucket != Long.MIN_VALUE) {
            flushBucket(currentBucket)
        }

        return ResampledCsvResult(
            csvText = out.toString(),
            sourceCandleCount = candles.size,
            outputCandleCount = outputCount,
            targetTimeframe = target
        )
    }

    private fun parseHeader(firstLine: String): CsvHeaderInfo? {
        val delimiter = when {
            firstLine.contains('\t') -> '\t'
            firstLine.contains(';') -> ';'
            firstLine.contains(',') -> ','
            else -> return null
        }
        val cols = firstLine.split(delimiter).map { it.trim().trim('<', '>').lowercase(Locale.US) }
        val idx = HashMap<String, Int>(cols.size)
        cols.forEachIndexed { index, name ->
            if (name.isNotEmpty() && name !in idx) {
                idx[name] = index
            }
        }
        val dateIdx = idx["date"]
        val timeIdx = idx["time"]
        val singleTimeIdx = idx["datetime"] ?: idx["timestamp"] ?: dateIdx ?: timeIdx
        return CsvHeaderInfo(
            delimiter = delimiter,
            dateIdx = dateIdx,
            timeIdx = timeIdx,
            singleTimeIdx = singleTimeIdx,
            openIdx = idx["open"],
            highIdx = idx["high"],
            lowIdx = idx["low"],
            closeIdx = idx["close"],
            volIdx = idx["tickvol"] ?: idx["volume"]
        )
    }

    private fun extractTimestampString(cols: List<String>, header: CsvHeaderInfo): String? {
        return if (header.dateIdx != null && header.timeIdx != null) {
            val d = cols.getOrNull(header.dateIdx)?.trim().orEmpty()
            val t = cols.getOrNull(header.timeIdx)?.trim().orEmpty()
            if (d.isEmpty() || t.isEmpty()) null else "$d $t"
        } else {
            val idx = header.singleTimeIdx ?: return null
            cols.getOrNull(idx)?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    private fun bucketStartMillis(
        epochMillis: Long,
        target: BacktestTimeframe,
        cal: Calendar
    ): Long {
        return when (target) {
            BacktestTimeframe.NATIVE -> epochMillis
            BacktestTimeframe.D1 -> {
                cal.timeInMillis = epochMillis
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            BacktestTimeframe.W1 -> {
                cal.timeInMillis = epochMillis
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val dow = cal.get(Calendar.DAY_OF_WEEK)
                val daysSinceMonday = (dow - Calendar.MONDAY + 7) % 7
                if (daysSinceMonday != 0) {
                    cal.add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
                }
                cal.timeInMillis
            }
            else -> {
                val intervalMs = target.minutes * 60_000L
                val floored = Math.floorDiv(epochMillis, intervalMs)
                floored * intervalMs
            }
        }
    }

    private fun parseEpochMillis(raw: String, cal: Calendar): Long? {
        val s = raw.trim().trim('"', '\'')
        if (s.isEmpty()) return null

        if (NUMERIC_EPOCH_REGEX.matches(s)) {
            val whole = s.substringBefore('.').toLongOrNull() ?: return null
            return if (whole < 10_000_000_000L) whole * 1000L else whole
        }

        YEAR_FIRST_REGEX.matchEntire(s)?.let { m ->
            val year = m.groupValues[1].toIntOrNull() ?: return null
            val month = m.groupValues[2].toIntOrNull() ?: return null
            val day = m.groupValues[3].toIntOrNull() ?: return null
            val hour = m.groupValues[4].toIntOrNull() ?: 0
            val min = m.groupValues[5].toIntOrNull() ?: 0
            val sec = m.groupValues[6].toIntOrNull() ?: 0
            val baseMillis = toUtcMillis(year, month, day, hour, min, sec, cal) ?: return null
            val signStr = m.groupValues[8]
            if (signStr.isNotEmpty()) {
                val offH = m.groupValues[9].toIntOrNull() ?: 0
                val offM = m.groupValues[10].toIntOrNull() ?: 0
                val offsetMs = (offH * 60L + offM) * 60_000L
                return if (signStr == "+") baseMillis - offsetMs else baseMillis + offsetMs
            }
            return baseMillis
        }

        DAY_FIRST_DOT_REGEX.matchEntire(s)?.let { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return null
            val month = m.groupValues[2].toIntOrNull() ?: return null
            val year = m.groupValues[3].toIntOrNull() ?: return null
            val hour = m.groupValues[4].toIntOrNull() ?: 0
            val min = m.groupValues[5].toIntOrNull() ?: 0
            val sec = m.groupValues[6].toIntOrNull() ?: 0
            return toUtcMillis(year, month, day, hour, min, sec, cal)
        }

        SLASH_DMY_MDY_REGEX.matchEntire(s)?.let { m ->
            val p1 = m.groupValues[1].toIntOrNull() ?: return null
            val p2 = m.groupValues[2].toIntOrNull() ?: return null
            val year = m.groupValues[3].toIntOrNull() ?: return null
            val (month, day) = if (p1 > 12) p2 to p1 else p1 to p2
            val hour = m.groupValues[4].toIntOrNull() ?: 0
            val min = m.groupValues[5].toIntOrNull() ?: 0
            val sec = m.groupValues[6].toIntOrNull() ?: 0
            return toUtcMillis(year, month, day, hour, min, sec, cal)
        }

        return null
    }

    private fun toUtcMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
        cal: Calendar
    ): Long? {
        if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59 || second !in 0..59) {
            return null
        }
        cal.clear()
        cal.set(year, month - 1, day, hour, minute, second)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun formatUtcTimestamp(epochMillis: Long, cal: Calendar, out: StringBuilder) {
        cal.timeInMillis = epochMillis
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val second = cal.get(Calendar.SECOND)

        out.append(year).append('-')
        appendTwoDigits(month, out).append('-')
        appendTwoDigits(day, out).append(' ')
        appendTwoDigits(hour, out).append(':')
        appendTwoDigits(minute, out).append(':')
        appendTwoDigits(second, out)
    }

    private fun appendTwoDigits(value: Int, out: StringBuilder): StringBuilder {
        if (value < 10) out.append('0')
        out.append(value)
        return out
    }

    private fun formatNumber(value: Double): String {
        if (!value.isFinite()) return "0"
        if (value == 0.0) return "0"
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    }

    private fun newUtcCalendar(): Calendar = GregorianCalendar(UTC_TZ, Locale.US).apply {
        isLenient = true
    }
}
