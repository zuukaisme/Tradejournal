package com.example.backtest

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.util.Locale

data class DecodedCsv(
    val text: String,
    val detectedEncoding: String,
    val detectedDelimiter: String,
    val estimatedRows: Int
)

object BacktestCodec {

    private val moshi: Moshi = Moshi.Builder().build()
    private val errorAdapter = moshi.adapter(ErrorCheckDto::class.java)
    private val resultAdapter = moshi.adapter(BacktestResultDto::class.java)

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
}

@JsonClass(generateAdapter = true)
internal data class ErrorCheckDto(
    val error: String? = null,
    val details: String? = null
)

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
    fun toDomain() = BacktestResult(
        summary = summary.toDomain(),
        points = points.toDomain(),
        risk = risk.toDomain(),
        bySide = bySide.toDomain(),
        timing = timing.toDomain(),
        equityCurve = equityCurve,
        trades = trades.map { it.toDomain() },
        warnings = warnings
    )
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
    val skippedByStopCap: Int
) {
    fun toDomain() = BacktestSummary(
        totalTrades = totalTrades,
        wins = wins,
        losses = losses,
        winRatePct = winRatePct,
        netPoints = netPoints,
        netR = netR,
        avgR = avgR,
        profitFactor = profitFactor,
        skippedByStopCap = skippedByStopCap
    )
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
            else -> ExitReason.SL
        },
        points = points,
        r = r,
        durationCandles = durationCandles
    )
}
