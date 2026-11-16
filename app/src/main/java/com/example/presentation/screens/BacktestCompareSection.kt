package com.example.presentation.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.backtest.SavedBacktestRun
import com.example.presentation.components.LiquidGlassCard
import com.example.ui.theme.AmberGold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BacktestCompareContent(
    savedRuns: List<SavedBacktestRun>,
    runAId: Long?,
    runBId: Long?,
    onSelectRun: (Long) -> Unit,
    onDeleteRun: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val runA = savedRuns.firstOrNull { it.id == runAId } ?: savedRuns.getOrNull(0)
    val runB = savedRuns.firstOrNull { it.id == runBId && it.id != runA?.id }
        ?: savedRuns.firstOrNull { it.id != runA?.id }

    val colorA = MaterialTheme.colorScheme.primary
    val colorB = AmberGold
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
                        Icon(
                            imageVector = Icons.Default.CompareArrows,
                            contentDescription = null,
                            tint = colorA,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "No Saved Backtest Runs Yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Run a backtest and tap Save Run on the results card to log historical simulations and compare strategies side by side.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            item {
                StrategyLeaderboardCard(
                    rows = strategyRows,
                    colorPositive = colorA,
                    colorNegative = MaterialTheme.colorScheme.error
                )
            }

            if (runA != null && runB != null) {
                item {
                    SideBySideComparisonCard(
                        runA = runA,
                        runB = runB,
                        colorA = colorA,
                        colorB = colorB
                    )
                }
            }

            item {
                Text(
                    text = "Saved Runs (${savedRuns.size}) • Tap to select for A/B compare",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(savedRuns, key = { it.id }) { run ->
                val badge = when (run.id) {
                    runA?.id -> "A"
                    runB?.id -> "B"
                    else -> null
                }
                val badgeColor = if (badge == "A") colorA else colorB
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
private fun SideBySideComparisonCard(
    runA: SavedBacktestRun,
    runB: SavedBacktestRun,
    colorA: Color,
    colorB: Color
) {
    LiquidGlassCard(testTag = "backtest_ab_compare_card") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Side-by-Side Comparison",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Default.CompareArrows,
                contentDescription = null,
                tint = colorA,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SlotHeaderChip("A", runA.strategyName, colorA, Modifier.weight(1f))
            SlotHeaderChip("B", runB.strategyName, colorB, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (runA.equityCurve.isNotEmpty() || runB.equityCurve.isNotEmpty()) {
            DualEquityCurveCanvas(
                curveA = runA.equityCurve,
                curveB = runB.equityCurve,
                colorA = colorA,
                colorB = colorB,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(8.dp))

        CompareMetricRow(
            label = "Win Rate",
            valA = String.format(Locale.US, "%.1f%%", runA.winRatePct),
            valB = String.format(Locale.US, "%.1f%%", runB.winRatePct),
            aBetter = runA.winRatePct > runB.winRatePct,
            bBetter = runB.winRatePct > runA.winRatePct,
            colorA = colorA,
            colorB = colorB
        )
        CompareMetricRow(
            label = "Net Points",
            valA = formatSigned(runA.netPoints, 1),
            valB = formatSigned(runB.netPoints, 1),
            aBetter = runA.netPoints > runB.netPoints,
            bBetter = runB.netPoints > runA.netPoints,
            colorA = colorA,
            colorB = colorB
        )
        CompareMetricRow(
            label = "Net R",
            valA = "${formatSigned(runA.netR, 2)}R",
            valB = "${formatSigned(runB.netR, 2)}R",
            aBetter = runA.netR > runB.netR,
            bBetter = runB.netR > runA.netR,
            colorA = colorA,
            colorB = colorB
        )
        CompareMetricRow(
            label = "Profit Factor",
            valA = runA.profitFactor?.let { String.format(Locale.US, "%.2f", it) } ?: "INF",
            valB = runB.profitFactor?.let { String.format(Locale.US, "%.2f", it) } ?: "INF",
            aBetter = (runA.profitFactor ?: Double.MAX_VALUE) > (runB.profitFactor ?: Double.MAX_VALUE),
            bBetter = (runB.profitFactor ?: Double.MAX_VALUE) > (runA.profitFactor ?: Double.MAX_VALUE),
            colorA = colorA,
            colorB = colorB
        )
        CompareMetricRow(
            label = "Max DD (R)",
            valA = "${String.format(Locale.US, "%.2f", runA.maxDrawdownR)}R",
            valB = "${String.format(Locale.US, "%.2f", runB.maxDrawdownR)}R",
            aBetter = runA.maxDrawdownR < runB.maxDrawdownR,
            bBetter = runB.maxDrawdownR < runA.maxDrawdownR,
            colorA = colorA,
            colorB = colorB
        )
        CompareMetricRow(
            label = "Trades",
            valA = "${runA.totalTrades}",
            valB = "${runB.totalTrades}",
            aBetter = false,
            bBetter = false,
            colorA = colorA,
            colorB = colorB
        )
    }
}

@Composable
private fun SlotHeaderChip(
    slot: String,
    name: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = color, shape = RoundedCornerShape(4.dp)) {
                Text(
                    text = slot,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompareMetricRow(
    label: String,
    valA: String,
    valB: String,
    aBetter: Boolean,
    bBetter: Boolean,
    colorA: Color,
    colorB: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = valA,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = if (aBetter) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (aBetter) colorA else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = valB,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = if (bBetter) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (bBetter) colorB else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun DualEquityCurveCanvas(
    curveA: List<Double>,
    curveB: List<Double>,
    colorA: Color,
    colorB: Color,
    modifier: Modifier = Modifier
) {
    val seriesA = listOf(0.0) + curveA
    val seriesB = listOf(0.0) + curveB
    val allVals = seriesA + seriesB
    val minVal = allVals.minOrNull() ?: 0.0
    val maxVal = allVals.maxOrNull() ?: 0.0
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)

    Canvas(modifier = modifier) {
        val range = (maxVal - minVal).takeIf { it > 0.0 } ?: 1.0
        val pad = 8.dp.toPx()
        val usableH = (size.height - pad * 2).coerceAtLeast(1f)

        fun yFor(v: Double): Float {
            val ratio = ((v - minVal) / range).toFloat()
            return pad + usableH * (1f - ratio)
        }

        if (0.0 in minVal..maxVal) {
            val zeroY = yFor(0.0)
            drawLine(
                color = gridColor,
                start = Offset(0f, zeroY),
                end = Offset(size.width, zeroY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            )
        }

        fun drawCurve(series: List<Double>, color: Color) {
            if (series.size < 2) return
            val path = Path()
            val stepX = size.width / (series.size - 1)
            series.forEachIndexed { idx, v ->
                val x = idx * stepX
                val y = yFor(v)
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        drawCurve(seriesA, colorA)
        drawCurve(seriesB, colorB)
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
    val positive = MaterialTheme.colorScheme.primary
    val negative = MaterialTheme.colorScheme.error

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (slotBadge != null) Modifier.border(1.2.dp, badgeColor.copy(alpha = 0.7f), shape) else Modifier
            )
            .clip(shape)
            .clickable { onClick() }
            .testTag("saved_backtest_run_${run.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (slotBadge != null) {
                        Surface(color = badgeColor, shape = RoundedCornerShape(4.dp)) {
                            Text(
                                text = "Slot $slotBadge",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = run.strategyName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${run.csvFileName} • $dateStr",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${run.totalTrades} trades • WR ${String.format(Locale.US, "%.1f", run.winRatePct)}% • Net ${formatSigned(run.netPoints, 1)} pts (${formatSigned(run.netR, 2)}R)",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    fontFamily = FontFamily.Monospace,
                    color = if (run.netPoints >= 0.0) positive else negative
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete saved run",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun formatSigned(value: Double, decimals: Int): String {
    val sign = if (value > 0.0) "+" else ""
    return "$sign${String.format(Locale.US, "%.${decimals}f", value)}"
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
        Text("Strategy Performance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "Ranked by combined net points across saved runs",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val maxMagnitude = rows.maxOfOrNull { kotlin.math.abs(it.totalPoints) }?.coerceAtLeast(1.0) ?: 1.0
        val chartRows = rows.take(8)
        if (chartRows.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text("Net Points • Top ${chartRows.size}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            chartRows.forEachIndexed { index, row ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${index + 1}. ${row.strategyName}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            String.format(Locale.US, "%+.1f", row.totalPoints),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (row.totalPoints >= 0.0) colorPositive else colorNegative
                        )
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth().height(10.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth((kotlin.math.abs(row.totalPoints) / maxMagnitude).toFloat().coerceIn(0f, 1f))
                                .height(10.dp)
                                .background(if (row.totalPoints >= 0.0) colorPositive else colorNegative, RoundedCornerShape(6.dp))
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(8.dp))
        Text("Leaderboard", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("#${index + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = colorPositive, modifier = Modifier.width(30.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.strategyName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${row.runCount} runs • ${row.totalTrades} trades • WR ${String.format(Locale.US, "%.1f", row.winRatePct)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        String.format(Locale.US, "%+.1f pts", row.totalPoints),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (row.totalPoints >= 0.0) colorPositive else colorNegative
                    )
                    Text(
                        String.format(Locale.US, "%+.2fR • DD %.2fR", row.totalR, row.maxDrawdownR),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            if (index != rows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        }
        Text(
            "Use the same symbol, dataset and timeframe when comparing strategies. Results from different datasets or timeframes are not directly equivalent.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
