package com.example.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.backtest.SavedBacktestRun
import com.example.presentation.components.LiquidGlassCard
import com.example.ui.theme.AmberGold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val CompareTerminalBg = Color(0xFF0A111A)
private val CompareTerminalBorder = Color(0xFF1E293B)
private val CompareColorA = Color(0xFF089981)
private val CompareColorB = AmberGold

private enum class GrowthChartMode(val label: String, val unit: String) {
    POINTS("Points", "pts"),
    R_GROWTH("Est. R", "R"),
    DRAWDOWN("Drawdown", "pts")
}

@Composable
fun BacktestCompareContent(
    savedRuns: List<SavedBacktestRun>,
    runAId: Long?,
    runBId: Long?,
    onSelectRun: (Long) -> Unit,
    onSwapRuns: (Long?, Long?) -> Unit = { _, _ -> },
    onDeleteRun: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val runA = savedRuns.firstOrNull { it.id == runAId } ?: savedRuns.getOrNull(0)
    val runB = savedRuns.firstOrNull { it.id == runBId && it.id != runA?.id }
        ?: savedRuns.firstOrNull { it.id != runA?.id }

    val colorA = MaterialTheme.colorScheme.primary
    val colorNegative = MaterialTheme.colorScheme.error
    val strategyRows = remember(savedRuns) {
        savedRuns.groupBy { it.strategyName }
            .map { (name, runs) ->
                val trades = runs.sumOf { it.totalTrades }
                StrategyPerformanceRow(
                    strategyName = name,
                    totalPoints = runs.sumOf { it.netPoints },
                    totalR = runs.sumOf { it.netR },
                    totalTrades = trades,
                    winRatePct = if (trades > 0) runs.sumOf { it.winRatePct * it.totalTrades } / trades else 0.0,
                    runCount = runs.size,
                    maxDrawdownR = runs.maxOfOrNull { it.maxDrawdownR } ?: 0.0
                )
            }
            .sortedByDescending { it.totalPoints }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (savedRuns.isEmpty()) {
            item {
                LiquidGlassCard(testTag = "backtest_compare_empty") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = colorA.copy(alpha = 0.16f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CompareArrows,
                                    contentDescription = null,
                                    tint = colorA,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "No Saved Backtest Runs Yet",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Run backtests to compare equity growth, drawdown & strategy edge.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            // Put Head-to-Head Growth & Comparison right at top when >= 2 runs exist!
            if (runA != null && runB != null) {
                item {
                    SideBySideComparisonCard(
                        runA = runA,
                        runB = runB,
                        colorA = CompareColorA,
                        colorB = CompareColorB,
                        onSwapRuns = { onSwapRuns(runA.id, runB.id) }
                    )
                }
            } else if (runA != null) {
                item {
                    SingleRunGrowthCard(run = runA, colorA = CompareColorA)
                }
            }

            item {
                StrategyLeaderboardCard(
                    rows = strategyRows,
                    colorPositive = colorA,
                    colorNegative = colorNegative
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Saved Runs (${savedRuns.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tap any card to assign A / B",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(savedRuns, key = { it.id }) { run ->
                val badge = when (run.id) {
                    runA?.id -> "A"
                    runB?.id -> "B"
                    else -> null
                }
                val badgeColor = if (badge == "A") CompareColorA else CompareColorB
                SavedBacktestRunCard(
                    run = run,
                    slotBadge = badge,
                    badgeColor = badgeColor,
                    onClick = { onSelectRun(run.id) },
                    onDelete = { onDeleteRun(run.id) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun SingleRunGrowthCard(
    run: SavedBacktestRun,
    colorA: Color
) {
    var mode by remember { mutableStateOf(GrowthChartMode.POINTS) }
    val series = remember(run, mode) { buildSeries(run, mode) }

    LiquidGlassCard(testTag = "backtest_single_growth_card") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Equity Growth Curve",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${run.strategyName} • Run a 2nd strategy to compare A vs B",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.ShowChart, contentDescription = null, tint = colorA)
        }
        Spacer(modifier = Modifier.height(8.dp))
        GrowthModeSelectorRow(
            selectedMode = mode,
            alignPercent = false,
            showAlignToggle = false,
            onSelectMode = { mode = it },
            onToggleAlign = {}
        )
        Spacer(modifier = Modifier.height(8.dp))
        InteractiveDualGrowthCanvas(
            seriesA = series,
            seriesB = null,
            nameA = run.strategyName,
            nameB = "",
            mode = mode,
            alignByProgress = false,
            colorA = colorA,
            colorB = CompareColorB
        )
    }
}

@Composable
private fun SideBySideComparisonCard(
    runA: SavedBacktestRun,
    runB: SavedBacktestRun,
    colorA: Color,
    colorB: Color,
    onSwapRuns: () -> Unit
) {
    var chartMode by remember { mutableStateOf(GrowthChartMode.POINTS) }
    var alignByProgress by remember { mutableStateOf(true) }

    val seriesA = remember(runA, chartMode) { buildSeries(runA, chartMode) }
    val seriesB = remember(runB, chartMode) { buildSeries(runB, chartMode) }

    val rDiff = runA.netR - runB.netR
    val ptsDiff = runA.netPoints - runB.netPoints
    val aLeads = rDiff > 0.01 || (abs(rDiff) <= 0.01 && ptsDiff >= 0.0)
    val winnerName = if (aLeads) runA.strategyName else runB.strategyName
    val winnerSlot = if (aLeads) "A" else "B"
    val winnerColor = if (aLeads) colorA else colorB

    LiquidGlassCard(testTag = "backtest_ab_compare_card") {
        // Header + Swap A <-> B Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CompareArrows,
                    contentDescription = null,
                    tint = colorA,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Head-to-Head Growth",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Surface(
                onClick = onSwapRuns,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.testTag("compare_swap_ab_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "Swap Slot A and Slot B",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Swap A ↔ B",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Slot A vs Slot B Hero Summary Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompareHeroSlotCard(
                slot = "A",
                run = runA,
                color = colorA,
                isLeader = aLeads,
                modifier = Modifier.weight(1f)
            )
            CompareHeroSlotCard(
                slot = "B",
                run = runB,
                color = colorB,
                isLeader = !aLeads,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Edge Winner Callout Banner
        Surface(
            color = winnerColor.copy(alpha = 0.12f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, winnerColor.copy(alpha = 0.38f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = winnerColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Slot $winnerSlot ($winnerName) leads by ${formatSigned(abs(rDiff), 2)}R (${formatSigned(abs(ptsDiff), 1)} pts)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = winnerColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Growth Mode & Timeline Alignment Controls
        GrowthModeSelectorRow(
            selectedMode = chartMode,
            alignPercent = alignByProgress,
            showAlignToggle = runA.equityCurve.size != runB.equityCurve.size,
            onSelectMode = { chartMode = it },
            onToggleAlign = { alignByProgress = !alignByProgress }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Interactive Dark Trading-Terminal Dual Equity Growth Chart
        if (runA.equityCurve.isNotEmpty() || runB.equityCurve.isNotEmpty()) {
            InteractiveDualGrowthCanvas(
                seriesA = seriesA,
                seriesB = seriesB,
                nameA = runA.strategyName,
                nameB = runB.strategyName,
                mode = chartMode,
                alignByProgress = alignByProgress,
                colorA = colorA,
                colorB = colorB
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(10.dp))

        // Visual Tug-of-War Metric Comparison Rows
        val pfA = runA.profitFactor ?: if (runA.wins > 0) 9.99 else 0.0
        val pfB = runB.profitFactor ?: if (runB.wins > 0) 9.99 else 0.0

        TugOfWarMetricRow(
            label = "Win Rate",
            valA = String.format(Locale.US, "%.1f%%", runA.winRatePct),
            valB = String.format(Locale.US, "%.1f%%", runB.winRatePct),
            magA = runA.winRatePct,
            magB = runB.winRatePct,
            aBetter = runA.winRatePct > runB.winRatePct,
            bBetter = runB.winRatePct > runA.winRatePct,
            colorA = colorA,
            colorB = colorB
        )
        TugOfWarMetricRow(
            label = "Net Points",
            valA = formatSigned(runA.netPoints, 1),
            valB = formatSigned(runB.netPoints, 1),
            magA = maxOf(0.0, runA.netPoints - minOf(0.0, runA.netPoints, runB.netPoints)),
            magB = maxOf(0.0, runB.netPoints - minOf(0.0, runA.netPoints, runB.netPoints)),
            aBetter = runA.netPoints > runB.netPoints,
            bBetter = runB.netPoints > runA.netPoints,
            colorA = colorA,
            colorB = colorB
        )
        TugOfWarMetricRow(
            label = "Net R Growth",
            valA = "${formatSigned(runA.netR, 2)}R",
            valB = "${formatSigned(runB.netR, 2)}R",
            magA = maxOf(0.0, runA.netR - minOf(0.0, runA.netR, runB.netR)),
            magB = maxOf(0.0, runB.netR - minOf(0.0, runA.netR, runB.netR)),
            aBetter = runA.netR > runB.netR,
            bBetter = runB.netR > runA.netR,
            colorA = colorA,
            colorB = colorB
        )
        TugOfWarMetricRow(
            label = "Avg R / Trade",
            valA = "${formatSigned(runA.avgR, 2)}R",
            valB = "${formatSigned(runB.avgR, 2)}R",
            magA = maxOf(0.0, runA.avgR - minOf(0.0, runA.avgR, runB.avgR)),
            magB = maxOf(0.0, runB.avgR - minOf(0.0, runA.avgR, runB.avgR)),
            aBetter = runA.avgR > runB.avgR,
            bBetter = runB.avgR > runA.avgR,
            colorA = colorA,
            colorB = colorB
        )
        TugOfWarMetricRow(
            label = "Profit Factor",
            valA = runA.profitFactor?.let { String.format(Locale.US, "%.2f", it) } ?: "INF",
            valB = runB.profitFactor?.let { String.format(Locale.US, "%.2f", it) } ?: "INF",
            magA = pfA,
            magB = pfB,
            aBetter = (runA.profitFactor ?: Double.MAX_VALUE) > (runB.profitFactor ?: Double.MAX_VALUE),
            bBetter = (runB.profitFactor ?: Double.MAX_VALUE) > (runA.profitFactor ?: Double.MAX_VALUE),
            colorA = colorA,
            colorB = colorB
        )
        TugOfWarMetricRow(
            label = "Max DD (R)",
            valA = "${String.format(Locale.US, "%.2f", runA.maxDrawdownR)}R",
            valB = "${String.format(Locale.US, "%.2f", runB.maxDrawdownR)}R",
            magA = runA.maxDrawdownR,
            magB = runB.maxDrawdownR,
            aBetter = runA.maxDrawdownR < runB.maxDrawdownR,
            bBetter = runB.maxDrawdownR < runA.maxDrawdownR,
            colorA = colorA,
            colorB = colorB
        )
        TugOfWarMetricRow(
            label = "Total Trades",
            valA = "${runA.totalTrades}",
            valB = "${runB.totalTrades}",
            magA = runA.totalTrades.toDouble(),
            magB = runB.totalTrades.toDouble(),
            aBetter = false,
            bBetter = false,
            colorA = colorA,
            colorB = colorB
        )
    }
}

@Composable
private fun CompareHeroSlotCard(
    slot: String,
    run: SavedBacktestRun,
    color: Color,
    isLeader: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = if (isLeader) 0.16f else 0.09f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (isLeader) 1.3.dp else 0.8.dp, color.copy(alpha = if (isLeader) 0.7f else 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(color = color, shape = RoundedCornerShape(5.dp)) {
                    Text(
                        text = slot,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
                Text(
                    text = run.strategyName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "${formatSigned(run.netR, 2)}R • ${formatSigned(run.netPoints, 1)}p",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1
            )
            Text(
                text = "${run.totalTrades} trades • WR ${String.format(Locale.US, "%.1f", run.winRatePct)}%",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun GrowthModeSelectorRow(
    selectedMode: GrowthChartMode,
    alignPercent: Boolean,
    showAlignToggle: Boolean,
    onSelectMode: (GrowthChartMode) -> Unit,
    onToggleAlign: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GrowthChartMode.entries.forEach { mode ->
                val selected = selectedMode == mode
                Surface(
                    onClick = { onSelectMode(mode) },
                    color = if (selected) CompareColorA.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (selected) CompareColorA else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.testTag("compare_growth_mode_${mode.name}")
                ) {
                    Text(
                        text = mode.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) CompareColorA else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }
        }

        if (showAlignToggle) {
            Surface(
                onClick = onToggleAlign,
                color = if (alignPercent) AmberGold.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(
                    1.dp,
                    if (alignPercent) AmberGold.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            ) {
                Text(
                    text = if (alignPercent) "0-100%" else "Raw #",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (alignPercent) AmberGold else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun InteractiveDualGrowthCanvas(
    seriesA: List<Double>,
    seriesB: List<Double>?,
    nameA: String,
    nameB: String,
    mode: GrowthChartMode,
    alignByProgress: Boolean,
    colorA: Color,
    colorB: Color
) {
    var scrubFraction by remember(seriesA, seriesB, mode, alignByProgress) { mutableStateOf<Float?>(null) }

    val axisPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    val sampledA = remember(seriesA) { seriesA }
    val sampledB = remember(seriesB, seriesA, alignByProgress) {
        if (seriesB == null) null
        else if (alignByProgress && seriesA.size >= 2 && seriesB.size >= 2) {
            resampleSeries(seriesB, seriesA.size)
        } else {
            seriesB
        }
    }

    val decimals = if (mode == GrowthChartMode.R_GROWTH) 2 else 1
    val scrubFracVal = scrubFraction
    val inspectIdxA = scrubFracVal?.let { f ->
        (f * (sampledA.size - 1).coerceAtLeast(0)).roundToInt().coerceIn(0, (sampledA.size - 1).coerceAtLeast(0))
    }
    val inspectIdxB = scrubFracVal?.let { f ->
        sampledB?.let { sb ->
            (f * (sb.size - 1).coerceAtLeast(0)).roundToInt().coerceIn(0, (sb.size - 1).coerceAtLeast(0))
        }
    }

    Surface(
        color = CompareTerminalBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CompareTerminalBorder),
        modifier = Modifier.fillMaxWidth().testTag("compare_dual_growth_surface")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Readout Strip (Shows inspected scrub point or end totals + interactive hint)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val valA = if (inspectIdxA != null) sampledA.getOrNull(inspectIdxA) ?: 0.0 else sampledA.lastOrNull() ?: 0.0
                val valB = sampledB?.let { sb ->
                    if (inspectIdxB != null) sb.getOrNull(inspectIdxB) ?: 0.0 else sb.lastOrNull() ?: 0.0
                }

                val prefix = when {
                    scrubFracVal != null && alignByProgress && sampledB != null -> "${(scrubFracVal * 100).roundToInt()}%"
                    inspectIdxA != null -> "Trade #$inspectIdxA"
                    else -> "Final"
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = prefix,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = "A: ${formatSigned(valA, decimals)}${mode.unit}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = colorA
                    )
                    if (valB != null) {
                        Text(
                            text = "B: ${formatSigned(valB, decimals)}${mode.unit}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = colorB
                        )
                    }
                }
                Text(
                    text = if (scrubFracVal == null) "Drag chart" else "Tap reset",
                    fontSize = 9.sp,
                    color = Color(0xFF64748B)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val allVals = remember(sampledA, sampledB) { sampledA + sampledB.orEmpty() }
            val minVal = allVals.minOrNull() ?: 0.0
            val maxVal = allVals.maxOrNull() ?: 0.0

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(195.dp)
                    .pointerInput(sampledA, sampledB) {
                        detectTapGestures { offset ->
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val frac = (offset.x / w).coerceIn(0f, 1f)
                            scrubFraction = if (scrubFraction != null) null else frac
                        }
                    }
                    .pointerInput(sampledA, sampledB) {
                        detectHorizontalDragGestures(
                            onDragEnd = {},
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                scrubFraction = (change.position.x / w).coerceIn(0f, 1f)
                            }
                        )
                    }
            ) {
                val axisLabelW = 48.dp.toPx()
                val plotW = (size.width - axisLabelW).coerceAtLeast(40f)
                val padTop = 10.dp.toPx()
                val padBottom = 10.dp.toPx()
                val usableH = (size.height - padTop - padBottom).coerceAtLeast(20f)
                val rawRange = (maxVal - minVal).takeIf { it > 1e-6 } ?: 1.0
                val pMin = minVal - rawRange * 0.08
                val pMax = maxVal + rawRange * 0.08
                val range = (pMax - pMin).coerceAtLeast(1e-6)

                fun yFor(v: Double): Float {
                    val ratio = ((v - pMin) / range).toFloat()
                    return padTop + usableH * (1f - ratio)
                }

                // Grid lines + right Y labels
                axisPaint.textSize = 9.5.sp.toPx()
                axisPaint.color = android.graphics.Color.argb(180, 148, 163, 184)
                for (step in 0..4) {
                    val f = step / 4f
                    val y = padTop + usableH * f
                    val v = pMax - range * f
                    drawLine(
                        color = Color(0xFF162131),
                        start = Offset(0f, y),
                        end = Offset(plotW, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        String.format(Locale.US, "%.${decimals}f", v),
                        plotW + 4.dp.toPx(),
                        y + 3.dp.toPx(),
                        axisPaint
                    )
                }

                // Zero baseline
                if (0.0 in pMin..pMax) {
                    val zeroY = yFor(0.0)
                    drawLine(
                        color = Color(0xFF475569),
                        start = Offset(0f, zeroY),
                        end = Offset(plotW, zeroY),
                        strokeWidth = 1.1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )
                }

                val maxLen = if (alignByProgress) sampledA.size else maxOf(sampledA.size, sampledB?.size ?: 0)

                fun drawGrowthSeries(series: List<Double>, color: Color, drawFill: Boolean) {
                    if (series.size < 2) return
                    val path = Path()
                    val fillPath = Path()
                    val denom = (if (alignByProgress) series.size - 1 else (maxLen - 1)).coerceAtLeast(1)
                    var lastX = 0f
                    var lastY = yFor(series.first())

                    series.forEachIndexed { idx, v ->
                        val x = (idx.toFloat() / denom) * plotW
                        val y = yFor(v)
                        if (idx == 0) {
                            path.moveTo(x, y)
                            fillPath.moveTo(x, padTop + usableH)
                            fillPath.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                        lastX = x
                        lastY = y
                    }

                    if (drawFill) {
                        fillPath.lineTo(lastX, padTop + usableH)
                        fillPath.close()
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(color.copy(alpha = 0.24f), Color.Transparent)
                            )
                        )
                    }
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    // Terminal dot
                    drawCircle(color = color.copy(alpha = 0.35f), radius = 6.dp.toPx(), center = Offset(lastX, lastY))
                    drawCircle(color = color, radius = 3.5.dp.toPx(), center = Offset(lastX, lastY))
                }

                sampledB?.let { drawGrowthSeries(it, colorB, drawFill = false) }
                drawGrowthSeries(sampledA, colorA, drawFill = true)

                // Scrub Crosshair + Dots
                scrubFracVal?.let { f ->
                    val crossX = (f * plotW).coerceIn(0f, plotW)
                    drawLine(
                        color = Color(0xFF38BDF8).copy(alpha = 0.7f),
                        start = Offset(crossX, padTop),
                        end = Offset(crossX, padTop + usableH),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f)
                    )
                    inspectIdxA?.let { idx ->
                        sampledA.getOrNull(idx)?.let { v ->
                            drawCircle(colorA, 5.dp.toPx(), Offset(crossX, yFor(v)))
                        }
                    }
                    inspectIdxB?.let { idx ->
                        sampledB?.getOrNull(idx)?.let { v ->
                            drawCircle(colorB, 5.dp.toPx(), Offset(crossX, yFor(v)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TugOfWarMetricRow(
    label: String,
    valA: String,
    valB: String,
    magA: Double,
    magB: Double,
    aBetter: Boolean,
    bBetter: Boolean,
    colorA: Color,
    colorB: Color
) {
    val maxMag = maxOf(abs(magA), abs(magB), 1e-6)
    val fracA = (abs(magA) / maxMag).toFloat().coerceIn(0.05f, 1f)
    val fracB = (abs(magB) / maxMag).toFloat().coerceIn(0.05f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (aBetter) "● $valA" else valA,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = if (aBetter) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (aBetter) colorA else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (bBetter) "$valB ●" else valB,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = if (bBetter) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (bBetter) colorB else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }

        // Mirrored Opposing Comparison Bar (Slot A grows left<-center, Slot B grows center->right)
        Row(
            modifier = Modifier.fillMaxWidth().height(5.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fracA)
                        .fillMaxHeight()
                        .background(if (aBetter) colorA else colorA.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fracB)
                        .fillMaxHeight()
                        .background(if (bBetter) colorB else colorB.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

@Composable
private fun SavedBacktestRunCard(
    run: SavedBacktestRun,
    slotBadge: String?,
    badgeColor: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(run.createdAt))
    val shape = RoundedCornerShape(14.dp)
    val positive = CompareColorA
    val negative = MaterialTheme.colorScheme.error
    val pfText = run.profitFactor?.let { String.format(Locale.US, "%.2f", it) } ?: "INF"

    Surface(
        color = if (slotBadge != null) badgeColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (slotBadge != null) Modifier.border(1.4.dp, badgeColor.copy(alpha = 0.85f), shape) else Modifier
            )
            .clip(shape)
            .clickable { onClick() }
            .testTag("saved_backtest_run_${run.id}")
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (slotBadge != null) {
                        Surface(color = badgeColor, shape = RoundedCornerShape(5.dp)) {
                            Text(
                                text = "Slot $slotBadge",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = run.strategyName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Text(
                    text = "${run.csvFileName} • $dateStr",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${run.totalTrades}t • WR ${String.format(Locale.US, "%.1f", run.winRatePct)}% • PF $pfText • DD ${String.format(Locale.US, "%.1f", run.maxDrawdownR)}R",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${formatSigned(run.netR, 2)}R (${formatSigned(run.netPoints, 1)}p)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (run.netPoints >= 0.0) positive else negative
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete saved run",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

private data class StrategyPerformanceRow(
    val strategyName: String,
    val totalPoints: Double,
    val totalR: Double,
    val totalTrades: Int,
    val winRatePct: Double,
    val runCount: Int,
    val maxDrawdownR: Double
)

@Composable
private fun StrategyLeaderboardCard(
    rows: List<StrategyPerformanceRow>,
    colorPositive: Color,
    colorNegative: Color
) {
    LiquidGlassCard(testTag = "strategy_performance_leaderboard") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Strategy Leaderboard", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Ranked by net points across saved runs",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${rows.size} strategies",
                style = MaterialTheme.typography.labelSmall,
                color = colorPositive,
                fontWeight = FontWeight.Bold
            )
        }

        val maxMagnitude = rows.maxOfOrNull { abs(it.totalPoints) }?.coerceAtLeast(1.0) ?: 1.0
        Spacer(modifier = Modifier.height(10.dp))

        rows.forEachIndexed { index, row ->
            val rankColor = when (index) {
                0 -> AmberGold
                1 -> Color(0xFFCBD5E1)
                2 -> Color(0xFFD97706)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val barColor = if (row.totalPoints >= 0.0) CompareColorA else colorNegative
            val fillFrac = (abs(row.totalPoints) / maxMagnitude).toFloat().coerceIn(0.04f, 1f)

            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = rankColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.width(30.dp)
                    ) {
                        Text(
                            text = "#${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = rankColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = row.strategyName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${row.runCount} runs • ${row.totalTrades}t • WR ${String.format(Locale.US, "%.1f", row.winRatePct)}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${formatSigned(row.totalPoints, 1)} pts",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = barColor
                        )
                        Text(
                            text = "${formatSigned(row.totalR, 2)}R • DD ${String.format(Locale.US, "%.2f", row.maxDrawdownR)}R",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fillFrac)
                            .height(7.dp)
                            .background(barColor, RoundedCornerShape(4.dp))
                    )
                }
            }
            if (index != rows.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Compare strategies tested on the same symbol, dataset & timeframe for equivalent results.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun buildSeries(run: SavedBacktestRun, mode: GrowthChartMode): List<Double> {
    val base = listOf(0.0) + run.equityCurve
    return when (mode) {
        GrowthChartMode.POINTS -> base
        GrowthChartMode.R_GROWTH -> {
            val scale = when {
                abs(run.netPoints) > 1e-6 -> run.netR / run.netPoints
                else -> 1.0
            }
            base.map { it * scale }
        }
        GrowthChartMode.DRAWDOWN -> {
            var peak = 0.0
            base.map { v ->
                if (v > peak) peak = v
                v - peak // 0 or negative underwater drawdown curve
            }
        }
    }
}

private fun resampleSeries(source: List<Double>, targetSize: Int): List<Double> {
    if (source.isEmpty() || targetSize <= 0) return emptyList()
    if (targetSize == 1 || source.size == 1) return listOf(source.last())
    val lastIdx = source.lastIndex
    return List(targetSize) { i ->
        val pos = (i.toDouble() / (targetSize - 1)) * lastIdx
        val lo = pos.toInt().coerceIn(0, lastIdx)
        val hi = (lo + 1).coerceIn(0, lastIdx)
        val t = pos - lo
        source[lo] * (1.0 - t) + source[hi] * t
    }
}

private fun formatSigned(value: Double, decimals: Int): String {
    val sign = if (value > 0.0) "+" else ""
    return "$sign${String.format(Locale.US, "%.${decimals}f", value)}"
}
