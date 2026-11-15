package com.example.backtest

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
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

data class ResampledCsvFileResult(
    val file: File,
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


    fun resampleCsvFile(sourceFile: File, target: BacktestTimeframe, outputFile: File): ResampledCsvFileResult {
        require(target != BacktestTimeframe.NATIVE) { "Native timeframe does not need resampling." }
        require(sourceFile.exists() && sourceFile.isFile) { "The cached CSV file is unavailable." }
        outputFile.parentFile?.mkdirs()

        var header: CsvHeaderInfo? = null
        var firstLine = ""
        var sourceCount = 0
        var lastEpoch = Long.MIN_VALUE
        var chronological = true

        BufferedReader(InputStreamReader(FileInputStream(sourceFile), Charsets.UTF_8), 65_536).use { reader ->
            while (true) {
                val rawLine = reader.readLine() ?: break
                val line = rawLine.trim()
                if (line.isEmpty()) continue
                firstLine = line
                break
            }
            header = parseHeader(firstLine)
                ?: throw IllegalArgumentException("Unrecognized CSV header.")
            val parsedHeader = header!!
            require(parsedHeader.openIdx != null && parsedHeader.highIdx != null &&
                parsedHeader.lowIdx != null && parsedHeader.closeIdx != null) {
                "CSV must contain open, high, low, and close columns."
            }
            require(parsedHeader.singleTimeIdx != null ||
                (parsedHeader.dateIdx != null && parsedHeader.timeIdx != null)) {
                "CSV must contain date/time columns to resample timeframes."
            }

            while (true) {
                val line = reader.readLine() ?: break
                val candle = parseResampleLine(line, parsedHeader) ?: continue
                if (candle.epochMillis < lastEpoch) chronological = false
                lastEpoch = candle.epochMillis
                sourceCount++
            }
        }

        require(sourceCount > 0) { "Could not parse candle timestamps/prices for resampling." }

        if (!chronological) {
            val fallback = resampleCsv(sourceFile.readText(Charsets.UTF_8), target)
            outputFile.writeText(fallback.csvText, Charsets.UTF_8)
            return ResampledCsvFileResult(outputFile, fallback.sourceCandleCount, fallback.outputCandleCount, target)
        }

        var currentBucket = Long.MIN_VALUE
        var bucketOpen = 0.0
        var bucketHigh = 0.0
        var bucketLow = 0.0
        var bucketClose = 0.0
        var bucketVolume = 0.0
        var outputCount = 0

        BufferedReader(FileReader(sourceFile), 65_536).use { reader ->
            reader.readLine()
            BufferedWriter(OutputStreamWriter(FileOutputStream(outputFile), Charsets.UTF_8), 65_536).use { writer ->
                writer.write("datetime,open,high,low,close,volume")
                writer.newLine()

                fun flushBucket() {
                    val row = StringBuilder(80)
                    formatUtcTimestampFast(currentBucket, row)
                    row.append(',')
                    appendFormattedNumber(bucketOpen, row).append(',')
                    appendFormattedNumber(bucketHigh, row).append(',')
                    appendFormattedNumber(bucketLow, row).append(',')
                    appendFormattedNumber(bucketClose, row).append(',')
                    appendFormattedNumber(bucketVolume, row)
                    writer.write(row.toString())
                    writer.newLine()
                    outputCount++
                }

                while (true) {
                    val line = reader.readLine() ?: break
                    if (!headerSkipped) {
                        if (line.isNotBlank()) headerSkipped = true
                        continue
                    }
                    val candle = parseResampleLine(line, header!!) ?: continue
                    val bucket = bucketStartMillisFast(candle.epochMillis, target)
                    if (bucket != currentBucket) {
                        if (currentBucket != Long.MIN_VALUE) flushBucket()
                        currentBucket = bucket
                        bucketOpen = candle.open
                        bucketHigh = candle.high
                        bucketLow = candle.low
                        bucketClose = candle.close
                        bucketVolume = candle.volume
                    } else {
                        if (candle.high > bucketHigh) bucketHigh = candle.high
                        if (candle.low < bucketLow) bucketLow = candle.low
                        bucketClose = candle.close
                        bucketVolume += candle.volume
                    }
                }
                if (currentBucket != Long.MIN_VALUE) flushBucket()
            }
        }

        return ResampledCsvFileResult(outputFile, sourceCount, outputCount, target)
    }

    private fun parseResampleLine(line: String, header: CsvHeaderInfo): ParsedCandle? {
        val clean = line.trim()
        if (clean.isEmpty()) return null
        val cols = clean.split(header.delimiter)
        val rawTs = extractTimestampString(cols, header) ?: return null
        val epoch = parseEpochMillisFast(rawTs) ?: return null
        val open = cols.getOrNull(header.openIdx ?: -1)?.trim()?.toDoubleOrNull() ?: return null
        val high = cols.getOrNull(header.highIdx ?: -1)?.trim()?.toDoubleOrNull() ?: return null
        val low = cols.getOrNull(header.lowIdx ?: -1)?.trim()?.toDoubleOrNull() ?: return null
        val close = cols.getOrNull(header.closeIdx ?: -1)?.trim()?.toDoubleOrNull() ?: return null
        val volume = header.volIdx?.let { cols.getOrNull(it)?.trim()?.toDoubleOrNull() } ?: 0.0
        return ParsedCandle(epoch, open, high, low, close, volume)
    }

    fun resampleCsv(csvText: String, target: BacktestTimeframe): ResampledCsvResult {
        val cleanText = csvText.removePrefix("\uFEFF")

        if (target == BacktestTimeframe.NATIVE) {
            var nonBlank = 0
            var pos = 0
            val len = cleanText.length
            while (pos < len) {
                var nextNl = cleanText.indexOf('\n', pos)
                if (nextNl < 0) nextNl = len
                var hasContent = false
                for (k in pos until nextNl) {
                    val ch = cleanText[k]
                    if (ch != ' ' && ch != '\t' && ch != '\r') {
                        hasContent = true
                        break
                    }
                }
                if (hasContent) nonBlank++
                pos = nextNl + 1
            }
            val count = (nonBlank - 1).coerceAtLeast(0)
            return ResampledCsvResult(
                csvText = cleanText,
                sourceCandleCount = count,
                outputCandleCount = count,
                targetTimeframe = target
            )
        }

        val totalLen = cleanText.length
        var scanPos = 0
        var firstLine = ""
        while (scanPos < totalLen) {
            var nl = cleanText.indexOf('\n', scanPos)
            if (nl < 0) nl = totalLen
            val candidate = cleanText.substring(scanPos, nl).trim()
            scanPos = nl + 1
            if (candidate.isNotEmpty()) {
                firstLine = candidate
                break
            }
        }
        require(firstLine.isNotEmpty()) { "The CSV file does not contain enough candle rows to resample." }

        val header = parseHeader(firstLine)
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

        val candles = ArrayList<ParsedCandle>((totalLen / 48).coerceAtLeast(16))
        var isChronological = true
        var lastEpoch = Long.MIN_VALUE
        val delim = header.delimiter
        val hasDateTimePair = header.dateIdx != null && header.timeIdx != null
        val dateIdxVal = header.dateIdx ?: -1
        val timeIdxVal = header.timeIdx ?: -1
        val singleIdxVal = header.singleTimeIdx ?: -1
        val volIdxVal = header.volIdx

        while (scanPos < totalLen) {
            var nl = cleanText.indexOf('\n', scanPos)
            if (nl < 0) nl = totalLen
            val line = cleanText.substring(scanPos, nl).trim()
            scanPos = nl + 1
            if (line.isEmpty()) continue

            val cols = line.split(delim)
            val rawTs = if (hasDateTimePair) {
                val d = cols.getOrNull(dateIdxVal)?.trim().orEmpty()
                val t = cols.getOrNull(timeIdxVal)?.trim().orEmpty()
                if (d.isEmpty() || t.isEmpty()) continue else "$d $t"
            } else {
                cols.getOrNull(singleIdxVal)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            }

            val epoch = parseEpochMillisFast(rawTs) ?: continue
            val o = cols.getOrNull(openIdx)?.trim()?.toDoubleOrNull() ?: continue
            val h = cols.getOrNull(highIdx)?.trim()?.toDoubleOrNull() ?: continue
            val l = cols.getOrNull(lowIdx)?.trim()?.toDoubleOrNull() ?: continue
            val c = cols.getOrNull(closeIdx)?.trim()?.toDoubleOrNull() ?: continue
            val v = volIdxVal?.let { cols.getOrNull(it)?.trim()?.toDoubleOrNull() } ?: 0.0

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

        val estOutRows = if (target.minutes > 0) (candles.size / 3).coerceAtLeast(64) else candles.size
        val out = StringBuilder(estOutRows * 44)
        out.append("datetime,open,high,low,close,volume\n")

        var currentBucket = Long.MIN_VALUE
        var bOpen = 0.0
        var bHigh = 0.0
        var bLow = 0.0
        var bClose = 0.0
        var bVol = 0.0
        var outputCount = 0

        fun flushBucket(bucketStartMs: Long) {
            formatUtcTimestampFast(bucketStartMs, out)
            out.append(',')
            appendFormattedNumber(bOpen, out).append(',')
            appendFormattedNumber(bHigh, out).append(',')
            appendFormattedNumber(bLow, out).append(',')
            appendFormattedNumber(bClose, out).append(',')
            appendFormattedNumber(bVol, out).append('\n')
            outputCount++
        }

        for (candle in candles) {
            val bucketMs = bucketStartMillisFast(candle.epochMillis, target)
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

    private fun bucketStartMillisFast(
        epochMillis: Long,
        target: BacktestTimeframe
    ): Long {
        return when (target) {
            BacktestTimeframe.NATIVE -> epochMillis
            BacktestTimeframe.D1 -> {
                val dayMs = 86_400_000L
                Math.floorDiv(epochMillis, dayMs) * dayMs
            }
            BacktestTimeframe.W1 -> {
                val dayMs = 86_400_000L
                val epochDay = Math.floorDiv(epochMillis, dayMs)
                // 1970-01-01 was Thursday (offset 3 from Monday)
                val daysSinceMonday = Math.floorMod(epochDay + 3L, 7L)
                (epochDay - daysSinceMonday) * dayMs
            }
            else -> {
                val intervalMs = target.minutes * 60_000L
                Math.floorDiv(epochMillis, intervalMs) * intervalMs
            }
        }
    }

    private fun parseEpochMillis(raw: String, @Suppress("UNUSED_PARAMETER") cal: Calendar): Long? =
        parseEpochMillisFast(raw)

    private fun parseEpochMillisFast(raw: String): Long? {
        val s = raw.trim().trim('"', '\'')
        val len = s.length
        if (len == 0) return null

        // Fast ASCII check for common YYYY.MM.DD HH:MM[:SS] or YYYY-MM-DD HH:MM[:SS]
        if (len >= 16 &&
            s[0] in '0'..'9' && s[1] in '0'..'9' && s[2] in '0'..'9' && s[3] in '0'..'9' &&
            (s[4] == '.' || s[4] == '-') &&
            s[5] in '0'..'9' && s[6] in '0'..'9' &&
            (s[7] == '.' || s[7] == '-') &&
            s[8] in '0'..'9' && s[9] in '0'..'9' &&
            (s[10] == ' ' || s[10] == 'T') &&
            s[11] in '0'..'9' && s[12] in '0'..'9' &&
            s[13] == ':' &&
            s[14] in '0'..'9' && s[15] in '0'..'9'
        ) {
            val year = parse4Digits(s, 0)
            val month = parse2Digits(s, 5)
            val day = parse2Digits(s, 8)
            val hour = parse2Digits(s, 11)
            val min = parse2Digits(s, 14)
            if (len == 16) {
                return toUtcMillisFast(year, month, day, hour, min, 0)
            }
            if (len == 19 && s[16] == ':' && s[17] in '0'..'9' && s[18] in '0'..'9') {
                val sec = parse2Digits(s, 17)
                return toUtcMillisFast(year, month, day, hour, min, sec)
            }
        }

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
            val baseMillis = toUtcMillisFast(year, month, day, hour, min, sec) ?: return null
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
            return toUtcMillisFast(year, month, day, hour, min, sec)
        }

        SLASH_DMY_MDY_REGEX.matchEntire(s)?.let { m ->
            val p1 = m.groupValues[1].toIntOrNull() ?: return null
            val p2 = m.groupValues[2].toIntOrNull() ?: return null
            val year = m.groupValues[3].toIntOrNull() ?: return null
            val (month, day) = if (p1 > 12) p2 to p1 else p1 to p2
            val hour = m.groupValues[4].toIntOrNull() ?: 0
            val min = m.groupValues[5].toIntOrNull() ?: 0
            val sec = m.groupValues[6].toIntOrNull() ?: 0
            return toUtcMillisFast(year, month, day, hour, min, sec)
        }

        return null
    }

    private fun parse2Digits(s: String, idx: Int): Int =
        (s[idx] - '0') * 10 + (s[idx + 1] - '0')

    private fun parse4Digits(s: String, idx: Int): Int =
        (s[idx] - '0') * 1000 + (s[idx + 1] - '0') * 100 + (s[idx + 2] - '0') * 10 + (s[idx + 3] - '0')

    private fun toUtcMillisFast(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int
    ): Long? {
        if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59 || second !in 0..59) {
            return null
        }
        var yr = year.toLong()
        if (month <= 2) yr -= 1L
        val era = if (yr >= 0L) yr / 400L else (yr - 399L) / 400L
        val yoe = yr - era * 400L
        val mAdj = month + if (month > 2) -3 else 9
        val doy = (153L * mAdj + 2L) / 5L + day - 1L
        val doe = yoe * 365L + yoe / 4L - yoe / 100L + doy
        val epochDays = era * 146097L + doe - 719468L
        val seconds = epochDays * 86_400L + hour * 3600L + minute * 60L + second
        return seconds * 1000L
    }

    private fun formatUtcTimestampFast(epochMillis: Long, out: StringBuilder) {
        val dayMs = 86_400_000L
        val epochDays = Math.floorDiv(epochMillis, dayMs)
        val remSecs = Math.floorMod(epochMillis, dayMs) / 1000L

        val z = epochDays + 719468L
        val era = if (z >= 0L) z / 146097L else (z - 146096L) / 146097L
        val doe = z - era * 146097L
        val yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L
        var year = (yoe + era * 400L).toInt()
        val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
        val mp = (5L * doy + 2L) / 153L
        val day = (doy - (153L * mp + 2L) / 5L + 1L).toInt()
        val month = (mp + if (mp < 10L) 3L else -9L).toInt()
        if (month <= 2) year += 1

        val hour = (remSecs / 3600L).toInt()
        val minute = ((remSecs % 3600L) / 60L).toInt()
        val second = (remSecs % 60L).toInt()

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

    private fun appendFormattedNumber(value: Double, out: StringBuilder): StringBuilder {
        if (!value.isFinite() || value == 0.0) {
            out.append('0')
            return out
        }
        val asLong = value.toLong()
        if (value == asLong.toDouble() && abs(value) < 1e14) {
            out.append(asLong)
            return out
        }
        out.append(BigDecimal.valueOf(value).stripTrailingZeros().toPlainString())
        return out
    }

    private fun newUtcCalendar(): Calendar = GregorianCalendar(UTC_TZ, Locale.US).apply {
        isLenient = true
    }
}
