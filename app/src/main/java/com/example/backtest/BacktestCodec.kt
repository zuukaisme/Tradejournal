package com.example.backtest

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PushbackInputStream
import java.nio.charset.Charset
import java.util.Locale

private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
    var bytesRead: Long = 0L
        private set

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) bytesRead++
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = super.read(buffer, offset, length)
        if (count > 0) bytesRead += count
        return count
    }
}

data class DecodedCsv(
    val text: String,
    val detectedEncoding: String,
    val detectedDelimiter: String,
    val estimatedRows: Int
)

data class CsvStreamInspection(
    val file: File,
    val fileSizeBytes: Long,
    val detectedEncoding: String,
    val detectedDelimiter: String,
    val estimatedRows: Int,
    val sampleLines: List<String>
)

object BacktestCodec {

    private val moshi: Moshi = Moshi.Builder().build()
    private val errorAdapter = moshi.adapter(ErrorCheckDto::class.java)
    private val resultAdapter = moshi.adapter(BacktestResultDto::class.java)
    private val progressAdapter = moshi.adapter(BacktestProgressDto::class.java)

    fun decodeCsvBytes(rawBytes: ByteArray): DecodedCsv {
        val isUtf16LeBom = rawBytes.size >= 2 &&
            rawBytes[0] == 0xFF.toByte() &&
            rawBytes[1] == 0xFE.toByte()
        val isUtf16BeBom = rawBytes.size >= 2 &&
            rawBytes[0] == 0xFE.toByte() &&
            rawBytes[1] == 0xFF.toByte()
        val isUtf8Bom = rawBytes.size >= 3 &&
            rawBytes[0] == 0xEF.toByte() &&
            rawBytes[1] == 0xBB.toByte() &&
            rawBytes[2] == 0xBF.toByte()

        val (text, encoding) = when {
            isUtf16LeBom -> {
                String(rawBytes, Charsets.UTF_16).removePrefix("\uFEFF") to "UTF-16LE (BOM)"
            }
            isUtf16BeBom -> {
                String(rawBytes, Charsets.UTF_16).removePrefix("\uFEFF") to "UTF-16BE (BOM)"
            }
            isUtf8Bom -> {
                String(rawBytes, Charsets.UTF_8).removePrefix("\uFEFF") to "UTF-8 (BOM)"
            }
            else -> {
                String(rawBytes, Charsets.UTF_8).removePrefix("\uFEFF") to "UTF-8"
            }
        }

        val nonBlankLines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        val firstLine = nonBlankLines.firstOrNull().orEmpty()
        val delimiter = when {
            firstLine.contains('\t') -> "Tab (MT5)"
            firstLine.contains(';') -> "Semicolon"
            firstLine.contains(',') -> "Comma"
            else -> "Unknown"
        }
        val estimatedRows = (nonBlankLines.size - 1).coerceAtLeast(0)

        return DecodedCsv(
            text = text,
            detectedEncoding = encoding,
            detectedDelimiter = delimiter,
            estimatedRows = estimatedRows
        )
    }

    /**
     * Streams arbitrarily large CSV/MT5 exports (222MB+ 1m history files) to an internal
     * UTF-8 cache file using a 64KB buffer so JVM heap never allocates a 200MB+ ByteArray/String.
     */
    fun streamCsvToCacheFile(inputStream: InputStream, destFile: File, onProgress: (Long) -> Unit = {}): CsvStreamInspection {
        destFile.parentFile?.mkdirs()
        val (pushback, charset, encodingLabel) = detectBomStream(inputStream)

        val sampleLines = ArrayList<String>(610)
        var nonBlankCount = 0
        var firstNonBlank = ""
        var estimatedBytesRead = 0L
        var lastProgressBytes = 0L

        BufferedReader(InputStreamReader(pushback, charset), 65_536).use { reader ->
            BufferedWriter(OutputStreamWriter(FileOutputStream(destFile), Charsets.UTF_8), 65_536).use { writer ->
                var firstLine = true
                while (true) {
                    val rawLine = reader.readLine() ?: break
                    estimatedBytesRead += rawLine.length.toLong() * if (charset == Charsets.UTF_8) 1 else 2
                    estimatedBytesRead += if (charset == Charsets.UTF_8) 1 else 2
                    if (estimatedBytesRead - lastProgressBytes >= 1024L * 1024L) {
                        lastProgressBytes = estimatedBytesRead
                        onProgress(estimatedBytesRead)
                    }
                    val line = if (firstLine) {
                        firstLine = false
                        rawLine.removePrefix("\uFEFF")
                    } else {
                        rawLine
                    }
                    if (line.isNotBlank()) {
                        nonBlankCount++
                        val trimmed = line.trim()
                        if (firstNonBlank.isEmpty()) {
                            firstNonBlank = trimmed
                        }
                        if (sampleLines.size < 605) {
                            sampleLines.add(trimmed)
                        }
                        writer.write(trimmed)
                        writer.newLine()
                    }
                }
            }
        }

        onProgress(estimatedBytesRead)
        val delimiter = when {
            firstNonBlank.contains('\t') -> "Tab (MT5)"
            firstNonBlank.contains(';') -> "Semicolon"
            firstNonBlank.contains(',') -> "Comma"
            else -> "Unknown"
        }

        return CsvStreamInspection(
            file = destFile,
            fileSizeBytes = destFile.length(),
            detectedEncoding = encodingLabel,
            detectedDelimiter = delimiter,
            estimatedRows = (nonBlankCount - 1).coerceAtLeast(0),
            sampleLines = sampleLines
        )
    }

    /**
     * Reads text source files (.mq5, .mq4, .mqh, .txt, .py) on Dispatchers.IO with UTF-16/UTF-8 BOM
     * detection and a generous size cap so huge files fail gracefully instead of causing OOM.
     */
    fun readTextStreamWithBom(inputStream: InputStream, maxBytes: Long = 64L * 1024L * 1024L): String {
        val counted = CountingInputStream(BufferedInputStream(inputStream, 65_536))
        val pushback = PushbackInputStream(counted, 4)
        val prefix = ByteArray(3)
        var prefixSize = 0
        while (prefixSize < prefix.size) {
            val count = pushback.read(prefix, prefixSize, prefix.size - prefixSize)
            if (count <= 0) break
            prefixSize += count
        }

        val utf16Le = prefixSize >= 2 && prefix[0] == 0xFF.toByte() && prefix[1] == 0xFE.toByte()
        val utf16Be = prefixSize >= 2 && prefix[0] == 0xFE.toByte() && prefix[1] == 0xFF.toByte()
        val utf8Bom = prefixSize >= 3 &&
            prefix[0] == 0xEF.toByte() &&
            prefix[1] == 0xBB.toByte() &&
            prefix[2] == 0xBF.toByte()
        val bomSize = when {
            utf16Le || utf16Be -> 2
            utf8Bom -> 3
            else -> 0
        }
        if (prefixSize > bomSize) pushback.unread(prefix, bomSize, prefixSize - bomSize)
        if (counted.bytesRead > maxBytes) {
            val mb = maxBytes / (1024L * 1024L)
            throw IllegalArgumentException("The selected file exceeds the $mb MB import limit.")
        }

        val charset = when {
            utf16Le -> Charsets.UTF_16LE
            utf16Be -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        val reader = InputStreamReader(pushback, charset)
        val output = StringBuilder()
        val buffer = CharArray(16_384)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            if (counted.bytesRead > maxBytes) {
                val mb = maxBytes / (1024L * 1024L)
                throw IllegalArgumentException("The selected file exceeds the $mb MB import limit.")
            }
            output.append(buffer, 0, count)
        }
        return output.toString().removePrefix("\uFEFF")
    }

    private fun detectBomStream(inputStream: InputStream): Triple<PushbackInputStream, Charset, String> {
        val pb = PushbackInputStream(BufferedInputStream(inputStream, 65_536), 4)
        val bom = ByteArray(4)
        var readCount = 0
        while (readCount < 4) {
            val n = pb.read(bom, readCount, 4 - readCount)
            if (n <= 0) break
            readCount += n
        }
        return when {
            readCount >= 2 && bom[0] == 0xFF.toByte() && bom[1] == 0xFE.toByte() -> {
                if (readCount > 2) pb.unread(bom, 2, readCount - 2)
                Triple(pb, Charsets.UTF_16LE, "UTF-16LE (BOM)")
            }
            readCount >= 2 && bom[0] == 0xFE.toByte() && bom[1] == 0xFF.toByte() -> {
                if (readCount > 2) pb.unread(bom, 2, readCount - 2)
                Triple(pb, Charsets.UTF_16BE, "UTF-16BE (BOM)")
            }
            readCount >= 3 && bom[0] == 0xEF.toByte() && bom[1] == 0xBB.toByte() && bom[2] == 0xBF.toByte() -> {
                if (readCount > 3) pb.unread(bom, 3, readCount - 3)
                Triple(pb, Charsets.UTF_8, "UTF-8 (BOM)")
            }
            else -> {
                if (readCount > 0) pb.unread(bom, 0, readCount)
                Triple(pb, Charsets.UTF_8, "UTF-8")
            }
        }
    }

    fun parseBacktestJson(json: String): BacktestParseOutcome {
        return try {
            val trimmed = json.trim()
            if (trimmed.isEmpty()) {
                return BacktestParseOutcome.Failure(
                    BacktestError(
                        error = "Backtester returned an empty response.",
                        details = ""
                    )
                )
            }

            val errDto = errorAdapter.fromJson(trimmed)
            if (errDto?.error != null) {
                return BacktestParseOutcome.Failure(
                    BacktestError(
                        error = errDto.error,
                        details = errDto.details.orEmpty()
                    )
                )
            }

            val dto = resultAdapter.fromJson(trimmed)
                ?: return BacktestParseOutcome.Failure(
                    BacktestError(
                        error = "Failed to parse backtest JSON result.",
                        details = trimmed.take(500)
                    )
                )

            val result = dto.toDomain()
            BacktestParseOutcome.Success(result)
        } catch (e: Exception) {
            BacktestParseOutcome.Failure(
                BacktestError(
                    error = "Malformed JSON response: ${e.message ?: e::class.simpleName}",
                    details = json.take(1000)
                )
            )
        }
    }
    fun parseProgressJson(json: String): BacktestProgressSnapshot? {
        return try {
            val trimmed = json.trim()
            if (trimmed.isEmpty()) return null
            progressAdapter.fromJson(trimmed)?.toDomain()
        } catch (_: Exception) {
            null
        }
    }
}

@JsonClass(generateAdapter = true)
internal data class ErrorCheckDto(
    val error: String? = null,
    val details: String? = null
)

@JsonClass(generateAdapter = true)
internal data class BacktestProgressDto(
    val candlesProcessed: Int = 0,
    val totalCandles: Int = 0,
    val enteredTrades: Int = 0,
    val closedTrades: Int = 0,
    val openTrades: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val breakEvens: Int = 0,
    val skippedByStopCap: Int = 0,
    val netPoints: Double = 0.0,
    val netR: Double = 0.0,
    val currentTime: String = ""
) {
    fun toDomain() = BacktestProgressSnapshot(
        candlesProcessed = candlesProcessed,
        totalCandles = totalCandles,
        enteredTrades = enteredTrades,
        closedTrades = closedTrades,
        openTrades = openTrades,
        wins = wins,
        losses = losses,
        breakEvens = breakEvens,
        skippedByStopCap = skippedByStopCap,
        netPoints = netPoints,
        netR = netR,
        currentTime = currentTime
    )
}

@JsonClass(generateAdapter = true)
internal data class BacktestResultDto(
    val summary: BacktestSummaryDto,
    val points: BacktestPointsDto,
    val risk: BacktestRiskDto,
    val bySide: BySideStatsDto,
    val timing: BacktestTimingDto,
    val equityCurve: List<Double> = emptyList(),
    val trades: List<TradeDto> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    fun toDomain(): BacktestResult {
        val domainTrades = trades.map { it.toDomain() }
        return BacktestResult(
            summary = summary.toDomain(domainTrades),
            points = points.toDomain(),
            risk = risk.toDomain(),
            bySide = bySide.toDomain(),
            timing = timing.toDomain(),
            equityCurve = equityCurve,
            trades = domainTrades,
            warnings = warnings
        )
    }
}

@JsonClass(generateAdapter = true)
internal data class BacktestSummaryDto(
    val totalTrades: Int,
    val wins: Int,
    val losses: Int,
    val winRatePct: Double,
    val netPoints: Double,
    val netR: Double,
    val avgR: Double,
    val profitFactor: Double? = null,
    val skippedByStopCap: Int,
    val breakEvens: Int? = null,
    val enteredTrades: Int? = null,
    val openTrades: Int? = null,
    val breakEvenRatePct: Double? = null,
    val lossRatePct: Double? = null,
    val tpExits: Int? = null,
    val slExits: Int? = null,
    val expectancyPoints: Double? = null,
    val totalCandles: Int? = null,
    val currentStreak: Int? = null
) {
    fun toDomain(trades: List<Trade> = emptyList()): BacktestSummary {
        val resolvedOpen = (openTrades ?: 0).coerceAtLeast(0)
        val resolvedEntered = enteredTrades ?: (totalTrades + resolvedOpen)
        val resolvedBreakEvens = breakEvens ?: (totalTrades - wins - losses).coerceAtLeast(0)
        val resolvedBePct = breakEvenRatePct ?: if (totalTrades > 0) (resolvedBreakEvens * 100.0) / totalTrades else 0.0
        val resolvedLossPct = lossRatePct ?: if (totalTrades > 0) (losses * 100.0) / totalTrades else 0.0
        val resolvedTpExits = tpExits ?: trades.count { it.reason == ExitReason.TP }
        val resolvedSlExits = slExits ?: trades.count { it.reason == ExitReason.SL }
        val resolvedExpectancy = expectancyPoints ?: if (totalTrades > 0) netPoints / totalTrades else 0.0
        return BacktestSummary(
            totalTrades = totalTrades,
            wins = wins,
            losses = losses,
            winRatePct = winRatePct,
            netPoints = netPoints,
            netR = netR,
            avgR = avgR,
            profitFactor = profitFactor,
            skippedByStopCap = skippedByStopCap,
            breakEvens = resolvedBreakEvens,
            enteredTrades = resolvedEntered,
            openTrades = resolvedOpen,
            breakEvenRatePct = resolvedBePct,
            lossRatePct = resolvedLossPct,
            tpExits = resolvedTpExits,
            slExits = resolvedSlExits,
            expectancyPoints = resolvedExpectancy,
            totalCandles = totalCandles ?: 0,
            currentStreak = currentStreak ?: 0
        )
    }
}

@JsonClass(generateAdapter = true)
internal data class BacktestPointsDto(
    val avgWinPoints: Double,
    val avgLossPoints: Double,
    val largestWinPoints: Double,
    val largestLossPoints: Double,
    val avgStopDistancePoints: Double,
    val maxStopDistancePoints: Double,
    val avgTargetDistancePoints: Double
) {
    fun toDomain() = BacktestPoints(
        avgWinPoints = avgWinPoints,
        avgLossPoints = avgLossPoints,
        largestWinPoints = largestWinPoints,
        largestLossPoints = largestLossPoints,
        avgStopDistancePoints = avgStopDistancePoints,
        maxStopDistancePoints = maxStopDistancePoints,
        avgTargetDistancePoints = avgTargetDistancePoints
    )
}

@JsonClass(generateAdapter = true)
internal data class BacktestRiskDto(
    val maxDrawdownPoints: Double,
    val maxDrawdownR: Double,
    val maxConsecutiveWins: Int,
    val maxConsecutiveLosses: Int
) {
    fun toDomain() = BacktestRisk(
        maxDrawdownPoints = maxDrawdownPoints,
        maxDrawdownR = maxDrawdownR,
        maxConsecutiveWins = maxConsecutiveWins,
        maxConsecutiveLosses = maxConsecutiveLosses
    )
}

@JsonClass(generateAdapter = true)
internal data class SideStatDto(
    val trades: Int,
    val winRatePct: Double,
    val netPoints: Double
) {
    fun toDomain() = SideStat(
        trades = trades,
        winRatePct = winRatePct,
        netPoints = netPoints
    )
}

@JsonClass(generateAdapter = true)
internal data class BySideStatsDto(
    val buy: SideStatDto,
    val sell: SideStatDto
) {
    fun toDomain() = BySideStats(
        buy = buy.toDomain(),
        sell = sell.toDomain()
    )
}

@JsonClass(generateAdapter = true)
internal data class BacktestTimingDto(
    val avgTradeDurationCandles: Double
) {
    fun toDomain() = BacktestTiming(
        avgTradeDurationCandles = avgTradeDurationCandles
    )
}

@JsonClass(generateAdapter = true)
internal data class TradeDto(
    val side: String,
    val entry: Double,
    val sl: Double,
    val tp: Double,
    val exit: Double,
    val openTime: String,
    val closeTime: String,
    val reason: String,
    val points: Double,
    val r: Double,
    val durationCandles: Int
) {
    fun toDomain() = Trade(
        side = when (side.trim().lowercase(Locale.US)) {
            "sell" -> Side.SELL
            else -> Side.BUY
        },
        entry = entry,
        sl = sl,
        tp = tp,
        exit = exit,
        openTime = openTime,
        closeTime = closeTime,
        reason = when (reason.trim().lowercase(Locale.US)) {
            "tp" -> ExitReason.TP
            "be", "breakeven", "break_even" -> ExitReason.BE
            "exit", "close" -> ExitReason.EXIT
            else -> ExitReason.SL
        },
        points = points,
        r = r,
        durationCandles = durationCandles
    )
}
