package com.example.presentation.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.backtest.BatchStrategyRunResult
import com.example.backtest.ExitReason
import com.example.backtest.ReplayCandle
import com.example.backtest.Side
import com.example.backtest.Trade
import com.example.backtest.TradeReplayMarker
import com.example.backtest.TradeReplayWindow
import com.example.presentation.components.LiquidGlassCard
import com.example.ui.theme.AmberGold
import com.example.ui.theme.SlateBreakEven
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val TvCanvasBg = Color(0xFF0A111A)
private val TvToolbarBg = Color(0xFF0F1722)
private val TvSurfaceBorder = Color(0xFF1E293B)
private val TvBullColor = Color(0xFF089981)
private val TvBearColor = Color(0xFFF23645)
private val TvEntryColor = Color(0xFF38BDF8)
private val TvTextPrimary = Color(0xFFE2E8F0)
private val TvTextMuted = Color(0xFF94A3B8)

/**
 * Full-screen landscape TradingView-style continuous candlestick replay tab.
 * All trades live on ONE uninterrupted chronological chart timeline with bounded
 * TP (green) & SL (red) zones, viewport-locked scaling, pan/pinch-zoom, and gap-aware bar playback.
 */
@Composable
fun TradeReplayTabContent(
    strategyName: String,
    trades: List<Trade>,
    currentIndex: Int,
    cursorGlobalBar: Int,
    totalDatasetCandles: Int,
    skipEmptyGaps: Boolean,
    window: TradeReplayWindow?,
    isLoading: Boolean,
    error: String?,
    outcomeFilter: Int,
    playbackSpeed: Int,
    batchResults: List<BatchStrategyRunResult>,
    activeBatchStrategyId: Long?,
    onSelectBatchStrategy: (Long) -> Unit,
    onSelectFilter: (Int) -> Unit,
    onCycleSpeed: () -> Unit,
    onToggleSkipEmptyGaps: () -> Unit,
    onUpdateCursorBar: (Int) -> Unit,
    onEnsureSliceCovers: (Int) -> Unit = {},
    onSelectTradeIndex: (Int) -> Unit,
    onGoToBacktest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    DisposableEffect(activity) {
        val previousOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = previousOrientation
        }
    }

    if (trades.isEmpty()) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                LiquidGlassCard(
                    testTag = "replay_tab_empty_card",
                    modifier = Modifier.fillMaxWidth(0.75f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CandlestickChart,
                            contentDescription = null,
                            tint = TvBullColor,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No Trades to Replay Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Run a single or multi-strategy backtest on the Run tab to watch continuous candlestick replay with TradingView-style TP & SL zones.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onGoToBacktest,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("replay_empty_go_run")
                        ) {
                            Text("Back to Run Tab", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        return
    }

    val effectiveTotalBars = maxOf(
        totalDatasetCandles,
        window?.totalDatasetCandles ?: 0,
        (trades.maxOfOrNull { it.closeIndex } ?: 0) + 1,
        1
    )
    val safeCursor = cursorGlobalBar.coerceIn(0, effectiveTotalBars - 1)
    val safeIndex = currentIndex.coerceIn(0, trades.lastIndex)

    val totalWins = remember(trades) {
        trades.count { t ->
            val be = t.reason == ExitReason.BE || (abs(t.points) <= 1e-6 && abs(t.r) <= 1e-6)
            !be && (t.points > 1e-6 || t.r > 1e-6)
        }
    }
    val totalLosses = remember(trades) {
        trades.count { t ->
            val be = t.reason == ExitReason.BE || (abs(t.points) <= 1e-6 && abs(t.r) <= 1e-6)
            val win = !be && (t.points > 1e-6 || t.r > 1e-6)
            !be && !win
        }
    }
    val totalBe = (trades.size - totalWins - totalLosses).coerceAtLeast(0)

    val filteredIndexedTrades = remember(trades, outcomeFilter) {
        trades.withIndex().filter { (_, t) ->
            val be = t.reason == ExitReason.BE || (abs(t.points) <= 1e-6 && abs(t.r) <= 1e-6)
            val win = !be && (t.points > 1e-6 || t.r > 1e-6)
            when (outcomeFilter) {
                1 -> !be && !win
                2 -> win
                3 -> be
                else -> true
            }
        }
    }
    val matchingIndices = remember(filteredIndexedTrades) { filteredIndexedTrades.map { it.index } }

    var isPlaying by remember { mutableStateOf(false) }
    var showTradeDrawer by remember { mutableStateOf(false) }
    var strategyMenuOpen by remember { mutableStateOf(false) }
    var filterMenuOpen by remember { mutableStateOf(false) }

    // Continuous TradingView viewport state: default pitch 22.dp = large crisp candles!
    var candlePitchDp by remember { mutableFloatStateOf(12f) }
    var isFollowingCursor by remember { mutableStateOf(true) }
    var panCenterBarFloat by remember { mutableFloatStateOf(safeCursor.toFloat()) }

    LaunchedEffect(safeCursor, isFollowingCursor) {
        if (isFollowingCursor) {
            panCenterBarFloat = safeCursor.toFloat()
        }
    }

    val latestCursor by rememberUpdatedState(safeCursor)
    val latestTrades by rememberUpdatedState(trades)
    val latestSkipGaps by rememberUpdatedState(skipEmptyGaps)
    val latestTotalBars by rememberUpdatedState(effectiveTotalBars)

    // Monotonic continuous Bar Replay clock (never stops cleanly between consecutive trades!)
    val tickDelayMs = when (playbackSpeed) {
        8 -> 25L
        4 -> 55L
        2 -> 110L
        else -> 210L
    }

    LaunchedEffect(isPlaying, playbackSpeed) {
        if (!isPlaying) return@LaunchedEffect
        while (isPlaying) {
            delay(tickDelayMs)
            val cur = latestCursor
            val maxBar = (latestTotalBars - 1).coerceAtLeast(0)
            if (cur >= maxBar) {
                isPlaying = false
                break
            }
            var nextBar = cur + 1
            if (latestSkipGaps) {
                val insideAnyTrade = latestTrades.any { t ->
                    t.openIndex >= 0 && cur in t.openIndex..t.closeIndex
                }
                if (!insideAnyTrade) {
                    val lastClosedBar = latestTrades
                        .filter { it.closeIndex in 0..cur }
                        .maxOfOrNull { it.closeIndex } ?: -999
                    val nextOpenBar = latestTrades
                        .filter { it.openIndex > cur }
                        .minOfOrNull { it.openIndex }
                    val barsSinceLastClose = cur - lastClosedBar
                    if (nextOpenBar != null && barsSinceLastClose >= 14 && (nextOpenBar - cur) > 45) {
                        nextBar = (nextOpenBar - 14).coerceAtLeast(cur + 1)
                    }
                }
            }
            onUpdateCursorBar(nextBar.coerceAtMost(maxBar))
        }
    }

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

    val activeLiveTradeIdx = remember(trades, safeCursor) {
        trades.indexOfLast { t -> t.openIndex >= 0 && safeCursor in t.openIndex..t.closeIndex }
    }
    val recentClosedTradeIdx = remember(trades, safeCursor) {
        trades.indexOfLast { t ->
            t.closeIndex >= 0 && safeCursor in t.closeIndex..(t.closeIndex + 10)
        }
    }

    val successfulBatchRuns = remember(batchResults) { batchResults.filter { it.result != null } }

    Surface(
        color = TvCanvasBg,
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("replay_tab_content")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val statusTuple = remember(activeLiveTradeIdx, recentClosedTradeIdx, trades) {
                when {
                    activeLiveTradeIdx >= 0 -> {
                        val t = trades[activeLiveTradeIdx]
                        Triple("#${activeLiveTradeIdx + 1} ${t.side.name} • OPEN", TvEntryColor, Color(0xFF102A3D))
                    }
                    recentClosedTradeIdx >= 0 -> {
                        val t = trades[recentClosedTradeIdx]
                        val isBe = t.reason == ExitReason.BE || (abs(t.points) <= 1e-6 && abs(t.r) <= 1e-6)
                        val isWin = !isBe && (t.points > 1e-6 || t.r > 1e-6)
                        val col = when {
                            isWin -> TvBullColor
                            isBe -> SlateBreakEven
                            else -> TvBearColor
                        }
                        val rSign = if (t.r > 0) "+" else ""
                        Triple(
                            "#${recentClosedTradeIdx + 1} ${t.reason.name} $rSign${String.format(Locale.US, "%.2f", t.r)}R",
                            col,
                            col.copy(alpha = 0.18f)
                        )
                    }
                    else -> Triple("FLAT • No Position", TvTextMuted, Color(0xFF131D2A))
                }
            }

            Surface(
                color = TvToolbarBg,
                border = BorderStroke(0.5.dp, TvSurfaceBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Surface(
                        onClick = {
                            isPlaying = false
                            onGoToBacktest()
                        },
                        color = Color(0xFF1A293A),
                        shape = RoundedCornerShape(11.dp),
                        border = BorderStroke(1.dp, TvSurfaceBorder),
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("replay_back_to_run_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Run tab",
                                tint = TvTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1.15f)) {
                        Surface(
                            onClick = { if (successfulBatchRuns.size > 1) strategyMenuOpen = true },
                            color = Color.Transparent,
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Surface(
                                    color = TvBullColor.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.CandlestickChart,
                                            contentDescription = null,
                                            tint = TvBullColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    Text(
                                        text = "TRADE REPLAY",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                        color = TvTextMuted
                                    )
                                    Text(
                                        text = strategyName.ifBlank { "Replay" },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TvTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (successfulBatchRuns.size > 1) {
                                    Text("▾", fontSize = 11.sp, color = TvTextMuted)
                                }
                            }
                        }
                        DropdownMenu(
                            expanded = strategyMenuOpen,
                            onDismissRequest = { strategyMenuOpen = false }
                        ) {
                            successfulBatchRuns.forEach { batchItem ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${batchItem.strategyName} (${batchItem.result?.trades?.size ?: 0})",
                                            fontWeight = if (batchItem.strategyId == activeBatchStrategyId) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        strategyMenuOpen = false
                                        isPlaying = false
                                        onSelectBatchStrategy(batchItem.strategyId)
                                    }
                                )
                            }
                        }
                    }

                    Surface(
                        color = statusTuple.third,
                        shape = RoundedCornerShape(9.dp),
                        border = BorderStroke(1.dp, statusTuple.second.copy(alpha = 0.22f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = statusTuple.first,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = statusTuple.second,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 10.dp)
                        )
                    }

                    Box {
                        val filterNames = listOf(
                            "All (${trades.size})",
                            "Losses ($totalLosses)",
                            "Wins ($totalWins)",
                            "BE ($totalBe)"
                        )
                        Surface(
                            onClick = { filterMenuOpen = true },
                            color = if (outcomeFilter == 1) TvBearColor.copy(alpha = 0.16f) else Color(0xFF162131),
                            shape = RoundedCornerShape(9.dp),
                            border = BorderStroke(
                                1.dp,
                                if (outcomeFilter == 1) TvBearColor.copy(alpha = 0.5f) else TvSurfaceBorder
                            ),
                            modifier = Modifier.testTag("replay_filter_pill")
                        ) {
                            Text(
                                text = filterNames.getOrElse(outcomeFilter) { filterNames.first() },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (outcomeFilter == 1) TvBearColor else TvTextPrimary,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 10.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = filterMenuOpen,
                            onDismissRequest = { filterMenuOpen = false }
                        ) {
                            filterNames.forEachIndexed { idx, label ->
                                DropdownMenuItem(
                                    text = {
                                        Text(label, fontWeight = if (outcomeFilter == idx) FontWeight.Bold else FontWeight.Normal)
                                    },
                                    onClick = {
                                        filterMenuOpen = false
                                        onSelectFilter(idx)
                                    }
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = { showTradeDrawer = !showTradeDrawer },
                        color = if (showTradeDrawer) TvBullColor.copy(alpha = 0.18f) else Color(0xFF162131),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (showTradeDrawer) TvBullColor.copy(alpha = 0.55f) else TvSurfaceBorder),
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("replay_toggle_trades_drawer")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                contentDescription = "Toggle trade list drawer",
                                tint = if (showTradeDrawer) TvBullColor else TvTextPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "${filteredIndexedTrades.size}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (showTradeDrawer) TvBullColor else TvTextPrimary
                            )
                        }
                    }
                }
            }

            // 2) Main Full-Height Continuous Chart + Optional Slide-Over Trade Drawer
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(TvCanvasBg)
            ) {
                when {
                    isLoading && (window == null || window.candles.isEmpty()) -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = TvBullColor)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Loading candle stream...",
                                    color = TvTextMuted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    error != null && (window == null || window.candles.isEmpty()) -> {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text(text = error, color = TvBearColor, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    window == null || window.candles.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Candle data unavailable for the active CSV file.",
                                color = TvTextMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    else -> {
                        ContinuousTradingViewCanvas(
                            window = window,
                            cursorGlobalBar = safeCursor,
                            candlePitchDp = candlePitchDp,
                            isFollowingCursor = isFollowingCursor,
                            panCenterBarFloat = panCenterBarFloat,
                            activeSelectedTradeIdx = safeIndex,
                            onPan = { newCenter ->
                                isFollowingCursor = false
                                val clamped = newCenter.coerceIn(0f, (effectiveTotalBars - 1).toFloat())
                                panCenterBarFloat = clamped
                                onEnsureSliceCovers(clamped.roundToInt())
                            },
                            onZoom = { scaleFactor ->
                                candlePitchDp = (candlePitchDp * scaleFactor).coerceIn(7f, 28f)
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("continuous_replay_canvas")
                        )

                        // Floating Top-Left OHLC HUD + Legend
                        val activeLocalIdx = (safeCursor - window.windowStartIndex).coerceIn(0, window.candles.lastIndex)
                        val activeCandle = window.candles.getOrNull(activeLocalIdx)
                        if (activeCandle != null) {
                            val isBull = activeCandle.close >= activeCandle.open
                            Surface(
                                color = Color(0xCC0D1520),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(0.5.dp, TvSurfaceBorder),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = activeCandle.time,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TvTextMuted
                                    )
                                    Text(
                                        text = "O ${fmtP(activeCandle.open)}  H ${fmtP(activeCandle.high)}  L ${fmtP(activeCandle.low)}  C ${fmtP(activeCandle.close)}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isBull) TvBullColor else TvBearColor
                                    )
                                }
                            }
                        }

                        // Floating "Follow Cursor" Snap Pill when user has panned manually
                        if (!isFollowingCursor) {
                            Surface(
                                onClick = {
                                    isFollowingCursor = true
                                    panCenterBarFloat = safeCursor.toFloat()
                                },
                                color = TvEntryColor.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, TvEntryColor),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 68.dp, bottom = 26.dp)
                                    .testTag("replay_follow_cursor_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MyLocation,
                                        contentDescription = "Snap chart back to playhead",
                                        tint = TvEntryColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Follow",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TvEntryColor
                                    )
                                }
                            }
                        }
                    }
                }

                // Slide-Over Trade Drawer on Right Edge
                androidx.compose.animation.AnimatedVisibility(
                    visible = showTradeDrawer,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Surface(
                        color = Color(0xF50F1722),
                        border = BorderStroke(1.dp, TvSurfaceBorder),
                        modifier = Modifier
                            .width(270.dp)
                            .fillMaxHeight()
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Jump to Trade (${filteredIndexedTrades.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TvTextPrimary
                                )
                                IconButton(
                                    onClick = { showTradeDrawer = false },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close trade list", tint = TvTextMuted, modifier = Modifier.size(16.dp))
                                }
                            }

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                            ) {
                                val tabs = listOf("All", "Loss", "Win", "BE")
                                itemsIndexed(tabs) { idx, label ->
                                    val sel = outcomeFilter == idx
                                    Surface(
                                        onClick = { onSelectFilter(idx) },
                                        color = if (sel) (if (idx == 1) TvBearColor else TvBullColor) else Color(0xFF172334),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (sel) Color.White else TvTextMuted,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(filteredIndexedTrades, key = { it.index }) { indexed ->
                                    val t = indexed.value
                                    val isSel = indexed.index == safeIndex
                                    val isBe = t.reason == ExitReason.BE || (abs(t.points) <= 1e-6 && abs(t.r) <= 1e-6)
                                    val isWin = !isBe && (t.points > 1e-6 || t.r > 1e-6)
                                    val outcomeCol = when {
                                        isWin -> TvBullColor
                                        isBe -> SlateBreakEven
                                        else -> TvBearColor
                                    }
                                    Surface(
                                        onClick = {
                                            isPlaying = false
                                            isFollowingCursor = true
                                            onSelectTradeIndex(indexed.index)
                                        },
                                        color = if (isSel) TvBullColor.copy(alpha = 0.16f) else Color(0xFF141E2B),
                                        shape = RoundedCornerShape(8.dp),
                                        border = if (isSel) BorderStroke(1.dp, TvBullColor) else BorderStroke(0.5.dp, TvSurfaceBorder),
                                        modifier = Modifier.fillMaxWidth().testTag("replay_list_item_${indexed.index}")
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "#${indexed.index + 1} ${t.side.name} • ${t.reason.name}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = outcomeCol,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                val sign = if (t.r > 0) "+" else ""
                                                Text(
                                                    text = "$sign${String.format(Locale.US, "%.2f", t.r)}R",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = outcomeCol,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            Text(
                                                text = "${t.openTime} • ${t.durationCandles}c",
                                                fontSize = 9.sp,
                                                color = TvTextMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3) Compact Bottom Transport & Timeline Bar
            Surface(
                color = TvToolbarBg,
                border = BorderStroke(0.5.dp, TvSurfaceBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Prev Trade
                    IconButton(
                        onClick = {
                            prevMatchingIdx?.let {
                                isPlaying = false
                                isFollowingCursor = true
                                onSelectTradeIndex(it)
                            }
                        },
                        enabled = prevMatchingIdx != null,
                        modifier = Modifier.size(40.dp).testTag("replay_prev_trade")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous Trade",
                            tint = if (prevMatchingIdx != null) TvTextPrimary else TvTextMuted.copy(alpha = 0.35f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Step -1 Bar
                    IconButton(
                        onClick = {
                            isPlaying = false
                            isFollowingCursor = true
                            onUpdateCursorBar((safeCursor - 1).coerceAtLeast(0))
                        },
                        enabled = safeCursor > 0,
                        modifier = Modifier.size(38.dp).testTag("replay_step_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Step back 1 bar",
                            tint = TvTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Play / Pause Continuous Replay
                    Button(
                        onClick = {
                            if (!isPlaying && safeCursor >= effectiveTotalBars - 1) {
                                val firstBar = (trades.firstOrNull()?.openIndex?.minus(8) ?: 0).coerceAtLeast(0)
                                onUpdateCursorBar(firstBar)
                            }
                            isFollowingCursor = true
                            isPlaying = !isPlaying
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TvBullColor,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("replay_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause Replay" else "Play Replay",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isPlaying) "Pause" else "Play",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Step +1 Bar
                    IconButton(
                        onClick = {
                            isPlaying = false
                            isFollowingCursor = true
                            onUpdateCursorBar((safeCursor + 1).coerceAtMost(effectiveTotalBars - 1))
                        },
                        enabled = safeCursor < effectiveTotalBars - 1,
                        modifier = Modifier.size(38.dp).testTag("replay_step_forward")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Step forward 1 bar",
                            tint = TvTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Next Trade
                    IconButton(
                        onClick = {
                            nextMatchingIdx?.let {
                                isPlaying = false
                                isFollowingCursor = true
                                onSelectTradeIndex(it)
                            }
                        },
                        enabled = nextMatchingIdx != null,
                        modifier = Modifier.size(40.dp).testTag("replay_next_trade")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next Trade",
                            tint = if (nextMatchingIdx != null) TvTextPrimary else TvTextMuted.copy(alpha = 0.35f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Timeline Scrubber + Bar Progress
                    Column(modifier = Modifier.weight(1f)) {
                        Slider(
                            value = safeCursor.toFloat(),
                            onValueChange = {
                                isPlaying = false
                                isFollowingCursor = true
                                onUpdateCursorBar(it.toInt())
                            },
                            valueRange = 0f..(effectiveTotalBars - 1).coerceAtLeast(1).toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = TvBullColor,
                                activeTrackColor = TvBullColor,
                                inactiveTrackColor = TvSurfaceBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                                .testTag("replay_scrubber")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Bar ${safeCursor + 1} / $effectiveTotalBars",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TvTextMuted
                            )
                            Text(
                                text = "Trade #${safeIndex + 1}/${trades.size}",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TvTextMuted
                            )
                        }
                    }

                    // Speed Multiplier Chip (1x/2x/4x/8x)
                    Surface(
                        onClick = onCycleSpeed,
                        color = Color(0xFF162131),
                        shape = RoundedCornerShape(7.dp),
                        border = BorderStroke(1.dp, TvSurfaceBorder),
                        modifier = Modifier.testTag("replay_speed_button")
                    ) {
                        Text(
                            text = "${playbackSpeed}x",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TvTextPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }

                    // Skip Gaps Chip
                    Surface(
                        onClick = onToggleSkipEmptyGaps,
                        color = if (skipEmptyGaps) TvBullColor.copy(alpha = 0.18f) else Color(0xFF162131),
                        shape = RoundedCornerShape(7.dp),
                        border = BorderStroke(1.dp, if (skipEmptyGaps) TvBullColor else TvSurfaceBorder),
                        modifier = Modifier.testTag("replay_skip_gaps_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Skip flat gaps",
                                tint = if (skipEmptyGaps) TvBullColor else TvTextMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Gaps",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (skipEmptyGaps) TvBullColor else TvTextMuted
                            )
                        }
                    }

                    // Zoom Out / Zoom In
                    Surface(
                        onClick = { candlePitchDp = (candlePitchDp * 0.8f).coerceIn(10f, 48f) },
                        color = Color(0xFF162131),
                        shape = RoundedCornerShape(7.dp),
                        border = BorderStroke(1.dp, TvSurfaceBorder)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Zoom out candles",
                            tint = TvTextPrimary,
                            modifier = Modifier.padding(6.dp).size(15.dp)
                        )
                    }
                    Surface(
                        onClick = { candlePitchDp = (candlePitchDp * 1.25f).coerceIn(10f, 48f) },
                        color = Color(0xFF162131),
                        shape = RoundedCornerShape(7.dp),
                        border = BorderStroke(1.dp, TvSurfaceBorder)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Zoom in candles",
                            tint = TvTextPrimary,
                            modifier = Modifier.padding(6.dp).size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinuousTradingViewCanvas(
    window: TradeReplayWindow,
    cursorGlobalBar: Int,
    candlePitchDp: Float,
    isFollowingCursor: Boolean,
    panCenterBarFloat: Float,
    activeSelectedTradeIdx: Int,
    onPan: (Float) -> Unit,
    onZoom: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestPanCenter by rememberUpdatedState(panCenterBarFloat)
    val latestPitchDp by rememberUpdatedState(candlePitchDp)
    val latestCursorBar by rememberUpdatedState(cursorGlobalBar)
    val latestIsFollowing by rememberUpdatedState(isFollowingCursor)

    val priceAxisPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }
    val timeAxisPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }
    val badgePaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            isFakeBoldText = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (abs(zoom - 1f) > 0.01f) {
                        onZoom(zoom)
                    }
                    if (abs(pan.x) > 0.5f) {
                        val slotPx = latestPitchDp.dp.toPx().coerceAtLeast(1f)
                        val anchor = if (latestIsFollowing) latestCursorBar.toFloat() else latestPanCenter
                        onPan(anchor - pan.x / slotPx)
                    }
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    val slotPx = latestPitchDp.dp.toPx().coerceAtLeast(1f)
                    val anchor = if (latestIsFollowing) latestCursorBar.toFloat() else latestPanCenter
                    onPan(anchor - dragAmount / slotPx)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val candles = window.candles
            if (candles.isEmpty()) return@Canvas

            val labelAxisWidth = 58.dp.toPx()
            val timeAxisHeight = 18.dp.toPx()
            val chartRight = (size.width - labelAxisWidth).coerceAtLeast(60f)
            val padTop = 30.dp.toPx()
            val chartBottom = (size.height - timeAxisHeight - 4.dp.toPx()).coerceAtLeast(padTop + 40f)
            val chartHeight = (chartBottom - padTop).coerceAtLeast(40f)

            val slotPx = candlePitchDp.dp.toPx().coerceAtLeast(6f)
            val visibleBarCount = (chartRight / slotPx).coerceAtLeast(4f)

            // Position the camera: in follow mode, place cursor at ~74% width so user sees recent price action + future breathing room
            val cameraLeftFloat = if (isFollowingCursor) {
                cursorGlobalBar - visibleBarCount * 0.74f
            } else {
                panCenterBarFloat - visibleBarCount * 0.5f
            }
            val cameraRightFloat = cameraLeftFloat + visibleBarCount

            val viewMinBar = floor(cameraLeftFloat).toInt().coerceAtLeast(window.windowStartIndex)
            val viewMaxBar = ceil(cameraRightFloat).toInt()
                .coerceAtMost(min(window.windowEndIndex, cursorGlobalBar))

            if (viewMaxBar < viewMinBar) return@Canvas

            val localStart = (viewMinBar - window.windowStartIndex).coerceIn(0, candles.lastIndex)
            val localEnd = (viewMaxBar - window.windowStartIndex).coerceIn(0, candles.lastIndex)

            // Viewport-locked Y min/max across revealed visible candles ONLY + overlapping visible trades
            var minPrice = Double.POSITIVE_INFINITY
            var maxPrice = Double.NEGATIVE_INFINITY
            for (li in localStart..localEnd) {
                val c = candles[li]
                if (c.low < minPrice) minPrice = c.low
                if (c.high > maxPrice) maxPrice = c.high
            }

            // Collect trades overlapping visible revealed range [viewMinBar..min(viewMaxBar, cursorGlobalBar)]
            val overlappingMarkers = window.markers.filter { m ->
                val openBar = if (m.trade.openIndex >= 0) m.trade.openIndex else window.windowStartIndex + m.entryCandlePos
                val closeBar = if (m.trade.closeIndex >= 0) m.trade.closeIndex else window.windowStartIndex + m.exitCandlePos
                openBar <= min(viewMaxBar, cursorGlobalBar) && closeBar >= viewMinBar
            }

            // Keep the price scale anchored to visible OHLC data. Distant SL/TP values
            // must not compress ordinary candle bodies into horizontal-looking lines.

            if (!minPrice.isFinite() || !maxPrice.isFinite()) return@Canvas
            val rawSpan = (maxPrice - minPrice).takeIf { it > 1e-9 } ?: 1.0
            val paddedMin = minPrice - rawSpan * 0.09
            val paddedMax = maxPrice + rawSpan * 0.09
            val priceRange = (paddedMax - paddedMin).coerceAtLeast(1e-6)

            fun yForPrice(price: Double): Float {
                val fraction = ((price - paddedMin) / priceRange).toFloat()
                return padTop + chartHeight * (1f - fraction)
            }

            fun xCenterForGlobalBar(bar: Int): Float = (bar - cameraLeftFloat + 0.5f) * slotPx

            // 1) Horizontal Price Grid & Right Price Scale
            priceAxisPaint.textSize = 10.sp.toPx()
            priceAxisPaint.color = android.graphics.Color.argb(200, 148, 163, 184)
            for (step in 0..4) {
                val f = step / 4f
                val y = padTop + chartHeight * f
                val price = paddedMax - priceRange * f
                drawLine(
                    color = Color(0xFF162131),
                    start = Offset(0f, y),
                    end = Offset(chartRight, y),
                    strokeWidth = 1.dp.toPx()
                )
                drawContext.canvas.nativeCanvas.drawText(
                    String.format(Locale.US, "%.2f", price),
                    chartRight + 5.dp.toPx(),
                    y + 3.5.dp.toPx(),
                    priceAxisPaint
                )
            }

            // Right Axis Divider
            drawLine(
                color = TvSurfaceBorder,
                start = Offset(chartRight, 0f),
                end = Offset(chartRight, chartBottom),
                strokeWidth = 1.dp.toPx()
            )
            // Bottom Time Axis Divider
            drawLine(
                color = TvSurfaceBorder,
                start = Offset(0f, chartBottom),
                end = Offset(size.width, chartBottom),
                strokeWidth = 1.dp.toPx()
            )

            // 2) Draw TradingView-Style SL / TP Zones Strictly Bounded to Each Trade's Span
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            badgePaint.textSize = 9.sp.toPx()

            overlappingMarkers.forEach { marker ->
                val trade = marker.trade
                val openBar = if (trade.openIndex >= 0) trade.openIndex else window.windowStartIndex + marker.entryCandlePos
                val closeBar = if (trade.closeIndex >= 0) trade.closeIndex else window.windowStartIndex + marker.exitCandlePos
                val activeEndBar = min(closeBar, cursorGlobalBar).coerceAtLeast(openBar)

                val rawEntryX = xCenterForGlobalBar(openBar)
                val rawEndX = xCenterForGlobalBar(activeEndBar)
                val startX = rawEntryX.coerceIn(0f, chartRight)
                val endX = rawEndX.coerceIn(startX, chartRight)
                val zoneWidth = (endX - startX).coerceAtLeast(4.dp.toPx())

                val entryY = yForPrice(trade.entry)
                val slY = yForPrice(trade.sl)
                val tpY = yForPrice(trade.tp)

                // Green TP Profit Box (Entry <-> TP)
                drawRect(
                    color = TvBullColor.copy(alpha = 0.16f),
                    topLeft = Offset(startX, min(entryY, tpY)),
                    size = Size(zoneWidth, abs(entryY - tpY).coerceAtLeast(2f))
                )
                drawLine(
                    color = TvBullColor.copy(alpha = 0.85f),
                    start = Offset(startX, tpY),
                    end = Offset(endX, tpY),
                    strokeWidth = 1.3.dp.toPx(),
                    pathEffect = dashEffect
                )

                // Red SL Risk Box (Entry <-> SL)
                drawRect(
                    color = TvBearColor.copy(alpha = 0.16f),
                    topLeft = Offset(startX, min(entryY, slY)),
                    size = Size(zoneWidth, abs(entryY - slY).coerceAtLeast(2f))
                )
                drawLine(
                    color = TvBearColor.copy(alpha = 0.85f),
                    start = Offset(startX, slY),
                    end = Offset(endX, slY),
                    strokeWidth = 1.3.dp.toPx(),
                    pathEffect = dashEffect
                )

                // Entry Divider Line
                drawLine(
                    color = TvEntryColor.copy(alpha = 0.9f),
                    start = Offset(startX, entryY),
                    end = Offset(endX, entryY),
                    strokeWidth = 1.2.dp.toPx()
                )

                // Entry Marker & Labels if Entry is inside viewport
                if (rawEntryX in 0f..chartRight) {
                    drawCircle(color = TvEntryColor, radius = 4.dp.toPx(), center = Offset(rawEntryX, entryY))
                    badgePaint.color = android.graphics.Color.rgb(56, 189, 248)
                    drawContext.canvas.nativeCanvas.drawText(
                        if (trade.side == Side.BUY) "BUY" else "SELL",
                        (rawEntryX + 4.dp.toPx()).coerceAtMost(chartRight - 32f),
                        entryY - 5.dp.toPx(),
                        badgePaint
                    )
                    badgePaint.color = android.graphics.Color.rgb(8, 153, 129)
                    drawContext.canvas.nativeCanvas.drawText(
                        "TP",
                        (rawEntryX + 4.dp.toPx()).coerceAtMost(chartRight - 24f),
                        tpY - 3.dp.toPx(),
                        badgePaint
                    )
                    badgePaint.color = android.graphics.Color.rgb(242, 54, 69)
                    drawContext.canvas.nativeCanvas.drawText(
                        "SL",
                        (rawEntryX + 4.dp.toPx()).coerceAtMost(chartRight - 24f),
                        slY + 9.dp.toPx(),
                        badgePaint
                    )
                }

                // Exit Marker + Result Badge if closed and revealed
                if (cursorGlobalBar >= closeBar && rawEndX in 0f..chartRight) {
                    val exitY = yForPrice(trade.exit)
                    val isBe = trade.reason == ExitReason.BE || (abs(trade.points) <= 1e-6 && abs(trade.r) <= 1e-6)
                    val isWin = !isBe && (trade.points > 1e-6 || trade.r > 1e-6)
                    val exitCol = when {
                        isWin -> TvBullColor
                        isBe -> SlateBreakEven
                        else -> TvBearColor
                    }
                    drawCircle(color = exitCol, radius = 4.5.dp.toPx(), center = Offset(rawEndX, exitY))
                    val rSign = if (trade.r > 0) "+" else ""
                    val exitLabel = "${trade.reason.name} $rSign${String.format(Locale.US, "%.1f", trade.r)}R"
                    badgePaint.color = when {
                        isWin -> android.graphics.Color.rgb(16, 185, 129)
                        isBe -> android.graphics.Color.rgb(148, 163, 184)
                        else -> android.graphics.Color.rgb(242, 54, 69)
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        exitLabel,
                        (rawEndX + 5.dp.toPx()).coerceAtMost(chartRight - 58f),
                        exitY - 5.dp.toPx(),
                        badgePaint
                    )
                }
            }

            // 3) Draw Large Crisp 1:1 Native Candlesticks
            val candleBodyWidth = (slotPx * 0.48f).coerceIn(2.5.dp.toPx(), 9.dp.toPx())
            val wickWidth = (slotPx * 0.10f).coerceIn(1.1.dp.toPx(), 1.6.dp.toPx())
            val timeTickStride = max(4, (72.dp.toPx() / slotPx).roundToInt())
            timeAxisPaint.textSize = 9.sp.toPx()
            timeAxisPaint.color = android.graphics.Color.argb(185, 148, 163, 184)

            for (globalIdx in viewMinBar..viewMaxBar) {
                val localIdx = globalIdx - window.windowStartIndex
                if (localIdx !in candles.indices) continue
                val candle = candles[localIdx]
                val centerX = xCenterForGlobalBar(globalIdx)
                if (centerX < -slotPx || centerX > chartRight + slotPx) continue

                val isBull = candle.close >= candle.open
                val candleColor = if (isBull) TvBullColor else TvBearColor

                // Wick
                drawLine(
                    color = candleColor,
                    start = Offset(centerX, yForPrice(candle.high)),
                    end = Offset(centerX, yForPrice(candle.low)),
                    strokeWidth = wickWidth
                )

                // Body (clipped cleanly within chart)
                val openY = yForPrice(candle.open)
                val closeY = yForPrice(candle.close)
                val rawBodyHeight = abs(closeY - openY)
                val bodyH = max(rawBodyHeight, 2.6.dp.toPx())
                val topY = (min(openY, closeY) - (bodyH - rawBodyHeight) / 2f)
                    .coerceIn(padTop, (chartBottom - bodyH).coerceAtLeast(padTop))
                drawRoundRect(
                    color = candleColor,
                    topLeft = Offset((centerX - candleBodyWidth / 2f).coerceAtLeast(0f), topY),
                    size = Size(
                        candleBodyWidth.coerceAtMost((chartRight - (centerX - candleBodyWidth / 2f)).coerceAtLeast(1f)),
                        bodyH
                    ),
                    cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                )

                // Vertical time grid ticks
                if (globalIdx % timeTickStride == 0 && centerX in 20f..(chartRight - 36f)) {
                    drawLine(
                        color = Color(0xFF131D2B),
                        start = Offset(centerX, padTop),
                        end = Offset(centerX, chartBottom),
                        strokeWidth = 0.8.dp.toPx()
                    )
                    val shortTime = if (candle.time.length >= 16) candle.time.substring(5, 16) else candle.time.takeLast(11)
                    drawContext.canvas.nativeCanvas.drawText(
                        shortTime,
                        centerX - 18.dp.toPx(),
                        chartBottom + 12.dp.toPx(),
                        timeAxisPaint
                    )
                }
            }

            // 4) Replay Playhead Line & Live Price Tag on Right Axis
            val cursorLocalIdx = cursorGlobalBar - window.windowStartIndex
            val cursorCandle = candles.getOrNull(cursorLocalIdx)
            if (cursorCandle != null) {
                val cursorX = xCenterForGlobalBar(cursorGlobalBar)
                if (cursorX in 0f..chartRight) {
                    drawLine(
                        color = TvEntryColor.copy(alpha = 0.65f),
                        start = Offset(cursorX, padTop),
                        end = Offset(cursorX, chartBottom),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f)
                    )
                }
                val livePriceY = yForPrice(cursorCandle.close)
                val tagColor = if (cursorCandle.close >= cursorCandle.open) TvBullColor else TvBearColor
                drawLine(
                    color = tagColor.copy(alpha = 0.55f),
                    start = Offset(0f, livePriceY),
                    end = Offset(chartRight, livePriceY),
                    strokeWidth = 0.9.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                )
                drawRoundRect(
                    color = tagColor,
                    topLeft = Offset(chartRight + 2.dp.toPx(), livePriceY - 9.dp.toPx()),
                    size = Size(labelAxisWidth - 4.dp.toPx(), 18.dp.toPx()),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                badgePaint.textSize = 9.5.sp.toPx()
                badgePaint.color = android.graphics.Color.WHITE
                drawContext.canvas.nativeCanvas.drawText(
                    String.format(Locale.US, "%.2f", cursorCandle.close),
                    chartRight + 5.dp.toPx(),
                    livePriceY + 3.5.dp.toPx(),
                    badgePaint
                )
            }
        }
    }
}

/**
 * Legacy modal wrapper preserved for any secondary callers, delegating directly to the continuous
 * landscape TradingView replay experience.
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
    var localCursor by remember(currentIndex, window?.windowEndIndex) {
        androidx.compose.runtime.mutableIntStateOf(
            trades.getOrNull(currentIndex)?.closeIndex?.plus(4)?.coerceAtLeast(0)
                ?: (window?.candles?.lastIndex ?: 0)
        )
    }
    var localFilter by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var localSpeed by remember { androidx.compose.runtime.mutableIntStateOf(1) }
    var localSkipGaps by remember { mutableStateOf(true) }

    TradeReplayTabContent(
        strategyName = strategyName,
        trades = trades,
        currentIndex = currentIndex,
        cursorGlobalBar = localCursor,
        totalDatasetCandles = window?.totalDatasetCandles ?: (window?.candles?.size ?: 1),
        skipEmptyGaps = localSkipGaps,
        window = window,
        isLoading = isLoading,
        error = error,
        outcomeFilter = localFilter,
        playbackSpeed = localSpeed,
        batchResults = emptyList(),
        activeBatchStrategyId = null,
        onSelectBatchStrategy = {},
        onSelectFilter = { localFilter = it },
        onCycleSpeed = {
            localSpeed = when (localSpeed) {
                1 -> 2
                2 -> 4
                4 -> 8
                else -> 1
            }
        },
        onToggleSkipEmptyGaps = { localSkipGaps = !localSkipGaps },
        onUpdateCursorBar = { localCursor = it },
        onSelectTradeIndex = onSelectTradeIndex,
        onGoToBacktest = onClose,
        modifier = modifier
    )
}

private fun fmtP(value: Double): String = String.format(Locale.US, "%.2f", value)
