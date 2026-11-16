package com.example.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.backtest.ExitReason
import com.example.backtest.ReplayCandle
import com.example.backtest.Side
import com.example.backtest.Trade
import com.example.backtest.TradeReplayWindow
import com.example.presentation.components.LiquidGlassCard
import com.example.ui.theme.AmberGold
import com.example.ui.theme.SlateBreakEven
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Full-screen candlestick replay inspector showing historical candles around a closed trade,
 * with explicit Stop Loss (SL), Take Profit (TP), Entry, and Exit overlays plus step-by-step playback.
 */
@Composable
fun BacktestReplayScreen(
    strategyName: String,
    trades: List<Trade>,
    currentIndex: Int,
    window: TradeReplayWindow?,
    isLoading: Boolean,
    error: String?,
    onSelectTradeIndex: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)

    val safeIndex = currentIndex.coerceIn(0, (trades.size - 1).coerceAtLeast(0))
    val trade = trades.getOrNull(safeIndex) ?: return

    // 0 = All, 1 = Losses (SL), 2 = Wins (TP/positive), 3 = Break-Even
    var outcomeFilter by remember { mutableIntStateOf(0) }

    val matchingIndices = remember(trades, outcomeFilter) {
        trades.indices.filter { idx ->
            val t = trades[idx]
            val isBe = t.reason == ExitReason.BE || (abs(t.points) <= 1e-6 && abs(t.r) <= 1e-6)
            val isWin = !isBe && (t.points > 1e-6 || t.r > 1e-6)
            when (outcomeFilter) {
                1 -> !isBe && !isWin
                2 -> isWin
                3 -> isBe
                else -> true
            }
        }
    }

    val totalCandles = window?.candles?.size ?: 0
    var revealedCount by remember(safeIndex, totalCandles) {
        mutableIntStateOf(totalCandles.coerceAtLeast(1))
    }
    var isPlaying by remember(safeIndex) { mutableStateOf(false) }

    LaunchedEffect(isPlaying, safeIndex, totalCandles) {
        if (!isPlaying || totalCandles <= 0) return@LaunchedEffect
        if (revealedCount >= totalCandles) {
            val startAt = (window?.entryCandlePos ?: 0).coerceAtLeast(1)
            revealedCount = startAt
        }
        while (isPlaying && revealedCount < totalCandles) {
            delay(140L)
            revealedCount = (revealedCount + 1).coerceAtMost(totalCandles)
        }
        isPlaying = false
    }

    val positiveColor = MaterialTheme.colorScheme.primary
    val negativeColor = MaterialTheme.colorScheme.error
    val isBe = trade.reason == ExitReason.BE || (abs(trade.points) <= 1e-6 && abs(trade.r) <= 1e-6)
    val isWin = !isBe && (trade.points > 1e-6 || trade.r > 1e-6)
    val outcomeColor = when {
        isWin -> positiveColor
        isBe -> SlateBreakEven
        else -> negativeColor
    }
    val sideColor = if (trade.side == Side.BUY) positiveColor else negativeColor

    val posInFiltered = matchingIndices.indexOf(safeIndex)
    val prevMatchingIdx = when {
        matchingIndices.isEmpty() -> null
        posInFiltered > 0 -> matchingIndices[posInFiltered - 1]
        else -> matchingIndices.lastOrNull { it < safeIndex }
    }
    val nextMatchingIdx = when {
        matchingIndices.isEmpty() -> null
        posInFiltered in 0 until (matchingIndices.size - 1) -> matchingIndices[posInFiltered + 1]
        else -> matchingIndices.firstOrNull { it > safeIndex }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // Top App Header
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.testTag("replay_close_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Replay")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Trade Replay • #${safeIndex + 1} / ${trades.size}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = strategyName.ifBlank { "Strategy" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Surface(
                            color = sideColor.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = trade.side.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = sideColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                        Surface(
                            color = outcomeColor.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            val label = when {
                                isWin -> "WON (${trade.reason.name})"
                                isBe -> "BE"
                                else -> "LOST (${trade.reason.name})"
                            }
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = outcomeColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // Outcome filter strip (quickly jump between losses / wins)
            val totalWins = remember(trades) { trades.count { t -> val be = t.reason == ExitReason.BE || (abs(t.points) <= 1e-6 && abs(t.r) <= 1e-6); !be && (t.points > 1e-6 || t.r > 1e-6) } }
            val totalLosses = remember(trades) { trades.count { t -> val be = t.reason == ExitReason.BE || (abs(t.points) <= 1e-6 && abs(t.r) <= 1e-6); val w = !be && (t.points > 1e-6 || t.r > 1e-6); !be && !w } }
            val filterItems = listOf(
                "All (${trades.size})",
                "Losses ($totalLosses)",
                "Wins ($totalWins)",
                "BE (${trades.size - totalLosses - totalWins})"
            )
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(filterItems) { fIdx, label ->
                    FilterChip(
                        selected = outcomeFilter == fIdx,
                        onClick = {
                            outcomeFilter = fIdx
                            val targetSet = trades.indices.filter { idx ->
                                val t = trades[idx]
                                val be = t.reason == ExitReason.BE || (abs(t.points) <= 1e-6 && abs(t.r) <= 1e-6)
                                val win = !be && (t.points > 1e-6 || t.r > 1e-6)
                                when (fIdx) {
                                    1 -> !be && !win
                                    2 -> win
                                    3 -> be
                                    else -> true
                                }
                            }
                            if (targetSet.isNotEmpty() && safeIndex !in targetSet) {
                                onSelectTradeIndex(targetSet.first())
                            }
                        },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (outcomeFilter == fIdx) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (fIdx == 1) negativeColor else positiveColor,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("replay_filter_$fIdx")
                    )
                }
            }

            // Main scrollable body: Chart + Playback Controls + Telemetry
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Candlestick Canvas Card
                Surface(
                    color = Color(0xFF0A111A),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .testTag("replay_chart_surface")
                ) {
                    when {
                        isLoading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = positiveColor)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Reading candle window from CSV...",
                                        color = Color(0xFF94A3B8),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        error != null -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = error,
                                    color = negativeColor,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        window == null || window.candles.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No matching candles found in the active CSV for ${trade.openTime}.",
                                    color = Color(0xFF94A3B8),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        else -> {
                            val visibleCandles = window.candles.take(revealedCount.coerceIn(1, window.candles.size))
                            CandlestickReplayCanvas(
                                visibleCandles = visibleCandles,
                                totalWindowCount = window.candles.size,
                                trade = trade,
                                entryPos = window.entryCandlePos,
                                exitPos = window.exitCandlePos,
                                bullColor = Color(0xFF10B981),
                                bearColor = Color(0xFFEF4444),
                                tpColor = Color(0xFF22C55E),
                                slColor = Color(0xFFF43F5E),
                                entryLineColor = Color(0xFF38BDF8),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp)
                            )
                        }
                    }
                }

                // Legend row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ReplayLegendDot("TP Zone", Color(0xFF22C55E))
                    ReplayLegendDot("SL Zone", Color(0xFFF43F5E))
                    ReplayLegendDot("Entry", Color(0xFF38BDF8))
                    ReplayLegendDot("Exit", AmberGold)
                }

                if (window != null && window.candles.isNotEmpty()) {
                    LiquidGlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val activeCandle = window.candles.getOrNull((revealedCount - 1).coerceIn(0, window.candles.lastIndex))
                            Text(
                                text = "Bar $revealedCount / ${window.candles.size}" +
                                    (activeCandle?.let { " • ${it.time}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    isPlaying = false
                                    revealedCount = window.candles.size
                                }
                            ) {
                                Text("Show All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (window.candles.size > 1) {
                            Slider(
                                value = revealedCount.toFloat(),
                                onValueChange = {
                                    isPlaying = false
                                    revealedCount = it.toInt().coerceIn(1, window.candles.size)
                                },
                                valueRange = 1f..window.candles.size.toFloat(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("replay_scrubber")
                            )
                        }

                        if (window.truncatedMiddle) {
                            Text(
                                text = "Long-duration trade compressed in middle bars while keeping full entry & exit context.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Telemetry Card
                LiquidGlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Trade #${safeIndex + 1} Execution",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        val ptsSign = if (trade.points > 0) "+" else ""
                        val rSign = if (trade.r > 0) "+" else ""
                        Text(
                            text = "${ptsSign}${String.format(Locale.US, "%.1f", trade.points)} pts ($rSign${String.format(Locale.US, "%.2f", trade.r)}R)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = outcomeColor,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ReplayMetricRow("Entry Price", String.format(Locale.US, "%.2f", trade.entry), Color(0xFF38BDF8))
                    ReplayMetricRow("Stop Loss (SL)", String.format(Locale.US, "%.2f", trade.sl), negativeColor)
                    ReplayMetricRow("Take Profit (TP)", String.format(Locale.US, "%.2f", trade.tp), positiveColor)
                    ReplayMetricRow("Exit Price (${trade.reason.name})", String.format(Locale.US, "%.2f", trade.exit), outcomeColor)
                    ReplayMetricRow("Duration", "${trade.durationCandles} candles")
                    ReplayMetricRow("Opened", trade.openTime)
                    ReplayMetricRow("Closed", trade.closeTime)
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Bottom Navigation & Play Controls Bar
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { prevMatchingIdx?.let(onSelectTradeIndex) },
                        enabled = prevMatchingIdx != null,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("replay_prev_trade"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                        Text("Prev")
                    }

                    Button(
                        onClick = {
                            if (window != null && window.candles.isNotEmpty()) {
                                if (!isPlaying && revealedCount >= window.candles.size) {
                                    revealedCount = (window.entryCandlePos).coerceIn(1, window.candles.size)
                                }
                                isPlaying = !isPlaying
                            }
                        },
                        enabled = window != null && window.candles.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = positiveColor,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("replay_play_pause_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPlaying) "Pause" else "Replay", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            if (window != null && window.candles.isNotEmpty()) {
                                isPlaying = true
                                revealedCount = 1
                            }
                        },
                        enabled = window != null && window.candles.isNotEmpty(),
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Restart Replay")
                    }

                    OutlinedButton(
                        onClick = { nextMatchingIdx?.let(onSelectTradeIndex) },
                        enabled = nextMatchingIdx != null,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("replay_next_trade"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Next")
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun CandlestickReplayCanvas(
    visibleCandles: List<ReplayCandle>,
    totalWindowCount: Int,
    trade: Trade,
    entryPos: Int,
    exitPos: Int,
    bullColor: Color,
    bearColor: Color,
    tpColor: Color,
    slColor: Color,
    entryLineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (visibleCandles.isEmpty()) return@Canvas

        val labelAxisWidth = 54.dp.toPx()
        val chartRight = (size.width - labelAxisWidth).coerceAtLeast(40f)
        val padTop = 14.dp.toPx()
        val padBottom = 14.dp.toPx()
        val chartHeight = (size.height - padTop - padBottom).coerceAtLeast(40f)

        var minPrice = visibleCandles.minOf { it.low }
        var maxPrice = visibleCandles.maxOf { it.high }
        minPrice = minOf(minPrice, trade.sl, trade.tp, trade.entry, trade.exit)
        maxPrice = maxOf(maxPrice, trade.sl, trade.tp, trade.entry, trade.exit)
        val rawSpan = (maxPrice - minPrice).takeIf { it > 0.0 } ?: 1.0
        val paddedMin = minPrice - rawSpan * 0.08
        val paddedMax = maxPrice + rawSpan * 0.08
        val priceRange = (paddedMax - paddedMin).coerceAtLeast(1e-6)

        fun yForPrice(price: Double): Float {
            val frac = ((price - paddedMin) / priceRange).toFloat()
            return padTop + chartHeight * (1f - frac)
        }

        val yEntry = yForPrice(trade.entry)
        val ySl = yForPrice(trade.sl)
        val yTp = yForPrice(trade.tp)
        val yExit = yForPrice(trade.exit)

        // Risk (SL) & Reward (TP) shaded horizontal bands from entry onward
        val slotCount = totalWindowCount.coerceAtLeast(visibleCandles.size).coerceAtLeast(1)
        val slotWidth = chartRight / slotCount.toFloat()
        val entryX = (if (entryPos >= 0) (entryPos + 0.5f) * slotWidth else 0f).coerceIn(0f, chartRight)
        val zoneRight = chartRight

        // TP reward zone
        drawRect(
            color = tpColor.copy(alpha = 0.13f),
            topLeft = Offset(entryX, min(yEntry, yTp)),
            size = Size((zoneRight - entryX).coerceAtLeast(0f), abs(yEntry - yTp))
        )
        // SL risk zone
        drawRect(
            color = slColor.copy(alpha = 0.15f),
            topLeft = Offset(entryX, min(yEntry, ySl)),
            size = Size((zoneRight - entryX).coerceAtLeast(0f), abs(yEntry - ySl))
        )

        // Horizontal grid lines + price labels on right axis
        val textPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 10.sp.toPx()
            color = android.graphics.Color.argb(190, 148, 163, 184)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        for (step in 0..4) {
            val frac = step / 4f
            val y = padTop + chartHeight * frac
            val priceAtLine = paddedMax - priceRange * frac
            drawLine(
                color = Color(0xFF1E293B),
                start = Offset(0f, y),
                end = Offset(chartRight, y),
                strokeWidth = 1.dp.toPx()
            )
            drawContext.canvas.nativeCanvas.drawText(
                String.format(Locale.US, "%.2f", priceAtLine),
                chartRight + 4.dp.toPx(),
                y + 3.dp.toPx(),
                textPaint
            )
        }

        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)

        // SL dashed line + tag
        drawLine(
            color = slColor,
            start = Offset(0f, ySl),
            end = Offset(chartRight, ySl),
            strokeWidth = 1.6.dp.toPx(),
            pathEffect = dashEffect
        )
        // TP dashed line
        drawLine(
            color = tpColor,
            start = Offset(0f, yTp),
            end = Offset(chartRight, yTp),
            strokeWidth = 1.6.dp.toPx(),
            pathEffect = dashEffect
        )
        // Entry line
        drawLine(
            color = entryLineColor,
            start = Offset(0f, yEntry),
            end = Offset(chartRight, yEntry),
            strokeWidth = 1.4.dp.toPx(),
            pathEffect = dashEffect
        )

        // Draw OHLC candlesticks up to visibleCandles.size
        val candleBodyWidth = (slotWidth * 0.62f).coerceIn(2f, 16.dp.toPx())
        val wickWidth = (slotWidth * 0.14f).coerceIn(1f, 2.5.dp.toPx())

        visibleCandles.forEachIndexed { idx, candle ->
            val cx = (idx + 0.5f) * slotWidth
            val isBull = candle.close >= candle.open
            val color = if (isBull) bullColor else bearColor

            val yHigh = yForPrice(candle.high)
            val yLow = yForPrice(candle.low)
            val yOpen = yForPrice(candle.open)
            val yClose = yForPrice(candle.close)

            drawLine(
                color = color,
                start = Offset(cx, yHigh),
                end = Offset(cx, yLow),
                strokeWidth = wickWidth
            )

            val topBody = min(yOpen, yClose)
            val bodyHeight = max(abs(yClose - yOpen), 1.5.dp.toPx())
            drawRect(
                color = color,
                topLeft = Offset(cx - candleBodyWidth / 2f, topBody),
                size = Size(candleBodyWidth, bodyHeight)
            )
        }

        // Draw Entry & Exit markers once revealed
        val tagPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 9.5.sp.toPx()
            isFakeBoldText = true
            typeface = android.graphics.Typeface.MONOSPACE
        }

        // SL & TP labels on left edge
        tagPaint.color = android.graphics.Color.rgb(244, 63, 94)
        drawContext.canvas.nativeCanvas.drawText("SL ${String.format(Locale.US, "%.2f", trade.sl)}", 4f, (ySl - 4f).coerceAtLeast(12f), tagPaint)

        tagPaint.color = android.graphics.Color.rgb(34, 197, 94)
        drawContext.canvas.nativeCanvas.drawText("TP ${String.format(Locale.US, "%.2f", trade.tp)}", 4f, (yTp - 4f).coerceAtLeast(12f), tagPaint)

        if (entryPos in 0 until visibleCandles.size) {
            val ex = (entryPos + 0.5f) * slotWidth
            drawLine(
                color = entryLineColor.copy(alpha = 0.45f),
                start = Offset(ex, padTop),
                end = Offset(ex, size.height - padBottom),
                strokeWidth = 1.2.dp.toPx()
            )
            drawCircle(
                color = entryLineColor,
                radius = 5.dp.toPx(),
                center = Offset(ex, yEntry)
            )
            tagPaint.color = android.graphics.Color.rgb(56, 189, 248)
            drawContext.canvas.nativeCanvas.drawText("IN", (ex + 5.dp.toPx()).coerceAtMost(chartRight - 22f), yEntry - 6f, tagPaint)
        }

        if (exitPos in 0 until visibleCandles.size) {
            val xx = (exitPos + 0.5f) * slotWidth
            val exitMarkerColor = when (trade.reason) {
                ExitReason.TP -> tpColor
                ExitReason.SL -> slColor
                else -> AmberGold
            }
            drawLine(
                color = exitMarkerColor.copy(alpha = 0.45f),
                start = Offset(xx, padTop),
                end = Offset(xx, size.height - padBottom),
                strokeWidth = 1.2.dp.toPx()
            )
            drawCircle(
                color = exitMarkerColor,
                radius = 5.5.dp.toPx(),
                center = Offset(xx, yExit)
            )
            tagPaint.color = when (trade.reason) {
                ExitReason.TP -> android.graphics.Color.rgb(34, 197, 94)
                ExitReason.SL -> android.graphics.Color.rgb(244, 63, 94)
                else -> android.graphics.Color.rgb(245, 158, 11)
            }
            drawContext.canvas.nativeCanvas.drawText(
                trade.reason.name,
                (xx + 5.dp.toPx()).coerceAtMost(chartRight - 24f),
                (yExit + 12f).coerceAtMost(size.height - 4f),
                tagPaint
            )
        }
    }
}

@Composable
private fun ReplayLegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReplayMetricRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = valueColor
        )
    }
}
