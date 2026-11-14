package com.example.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.CurrencyCode
import com.example.domain.model.DayPnLSummary
import com.example.domain.model.StrategyStat
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.SlateBreakEven
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

@Composable
fun EquityCurveChart(
    equityPoints: List<Pair<String, Double>>,
    currency: CurrencyCode,
    modifier: Modifier = Modifier
) {
    val trackBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val positiveColor = MaterialTheme.colorScheme.primary
    val negativeColor = MaterialTheme.colorScheme.error

    if (equityPoints.size < 2) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(160.dp),
            shape = RoundedCornerShape(14.dp),
            color = trackBg
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Log at least 2 trades to plot equity trajectory",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        return
    }

    val values = equityPoints.map { it.second }
    val minVal = values.minOrNull() ?: 0.0
    val maxVal = values.maxOrNull() ?: 0.0
    val finalVal = values.lastOrNull() ?: 0.0

    val effectiveMin = if (minVal > 0) 0.0 else minVal * 1.1
    val effectiveMax = if (maxVal < 0) 0.0 else max(maxVal * 1.1, 10.0)
    val totalRange = max(effectiveMax - effectiveMin, 1.0)

    val lineColor = if (finalVal >= 0) positiveColor else negativeColor
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val gradientColors = if (finalVal >= 0) {
        listOf(positiveColor.copy(alpha = 0.35f), positiveColor.copy(alpha = 0.01f))
    } else {
        listOf(negativeColor.copy(alpha = 0.35f), negativeColor.copy(alpha = 0.01f))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Cumulative Return",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = CurrencyFormatter.format(finalVal, currency),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = lineColor
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(155.dp)
        ) {
            val width = size.width
            val height = size.height

            val zeroY = height - ((0.0 - effectiveMin) / totalRange * height).toFloat()
            if (zeroY in 0f..height) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, zeroY),
                    end = Offset(width, zeroY),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            }

            val stepX = width / (equityPoints.size - 1)
            val path = Path()
            val fillPath = Path()

            equityPoints.forEachIndexed { index, pair ->
                val x = index * stepX
                val y = (height - ((pair.second - effectiveMin) / totalRange * height)).toFloat()
                if (index == 0) {
                    path.moveTo(x, y)
                    fillPath.moveTo(x, height)
                    fillPath.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }

            fillPath.lineTo(width, height)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = gradientColors,
                    startY = 0f,
                    endY = height
                )
            )

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 2.5.dp.toPx())
            )

            val lastX = width
            val lastY = (height - ((finalVal - effectiveMin) / totalRange * height)).toFloat()
            drawCircle(
                color = lineColor,
                radius = 4.dp.toPx(),
                center = Offset(lastX, lastY)
            )
            drawCircle(
                color = Color.White,
                radius = 1.8.dp.toPx(),
                center = Offset(lastX, lastY)
            )
        }
    }
}

@Composable
fun DailyPnLBarChart(
    dailyPnL: List<DayPnLSummary>,
    currency: CurrencyCode,
    modifier: Modifier = Modifier
) {
    val trackBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val positiveColor = MaterialTheme.colorScheme.primary
    val negativeColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)

    if (dailyPnL.isEmpty()) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(140.dp),
            shape = RoundedCornerShape(14.dp),
            color = trackBg
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "No daily trade records to display",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        return
    }

    var selectedDay by remember { mutableStateOf<DayPnLSummary?>(null) }
    val recentDays = if (dailyPnL.size > 14) dailyPnL.takeLast(14) else dailyPnL
    val maxAbsVal = max(recentDays.maxOfOrNull { abs(it.netPnL) } ?: 100.0, 50.0)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Last ${recentDays.size} Trading Days (Tap bar)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (selectedDay != null) {
                Text(
                    text = "${selectedDay!!.date.takeLast(5)}: ${CurrencyFormatter.format(selectedDay!!.netPnL, currency)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedDay!!.netPnL >= 0) positiveColor else negativeColor
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val zeroY = height / 2f

                drawLine(
                    color = gridColor,
                    start = Offset(0f, zeroY),
                    end = Offset(width, zeroY),
                    strokeWidth = 1.5f
                )

                val barCount = recentDays.size
                val totalBarWidth = width / barCount
                val barWidth = (totalBarWidth * 0.62f).coerceAtMost(28.dp.toPx())

                recentDays.forEachIndexed { index, day ->
                    val centerX = (index + 0.5f) * totalBarWidth
                    val isSelected = selectedDay?.date == day.date
                    val barColor = when {
                        day.netPnL > 0 -> if (isSelected) positiveColor else positiveColor.copy(alpha = 0.85f)
                        day.netPnL < 0 -> if (isSelected) negativeColor else negativeColor.copy(alpha = 0.85f)
                        else -> SlateBreakEven
                    }

                    val barHeight = ((abs(day.netPnL) / maxAbsVal) * (height / 2f * 0.86f)).toFloat()
                        .coerceAtLeast(4f)

                    val top = if (day.netPnL >= 0) zeroY - barHeight else zeroY
                    val left = centerX - barWidth / 2f

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }

            Row(modifier = Modifier.fillMaxSize()) {
                recentDays.forEach { day ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable { selectedDay = day }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            recentDays.forEach { day ->
                Text(
                    text = day.date.takeLast(2),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = if (selectedDay?.date == day.date) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Horizontal ranked bar chart showing which strategy performs best by Net P&L.
 */
@Composable
fun StrategyPerformanceBarChart(
    strategyStats: List<StrategyStat>,
    currency: CurrencyCode,
    modifier: Modifier = Modifier
) {
    if (strategyStats.isEmpty()) return

    val ranked = remember(strategyStats) {
        strategyStats.sortedByDescending { it.totalPnL }
    }
    val maxAbsPnL = remember(ranked) {
        max(ranked.maxOfOrNull { abs(it.totalPnL) } ?: 1.0, 1.0)
    }
    val bestStrategy = ranked.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("strategy_pnl_ranking_chart"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Net P&L Ranking by Strategy",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (bestStrategy != null && bestStrategy.totalPnL > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Top: ${bestStrategy.strategyName}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        ranked.forEachIndexed { index, stat ->
            val ratio = (abs(stat.totalPnL) / maxAbsPnL).toFloat().coerceIn(0.05f, 1f)
            val isPositive = stat.totalPnL >= 0
            val positiveColor = MaterialTheme.colorScheme.primary
            val negativeColor = MaterialTheme.colorScheme.error
            val barColor = if (isPositive) positiveColor else negativeColor
            val isTopWinner = index == 0 && stat.totalPnL > 0
            val trackBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isTopWinner) positiveColor.copy(alpha = 0.10f) else trackBg)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${index + 1}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isTopWinner) positiveColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stat.strategyName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isTopWinner) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = CurrencyFormatter.format(stat.totalPnL, currency),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = barColor
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Canvas bar track
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                ) {
                    drawRoundRect(
                        color = trackBg,
                        topLeft = Offset.Zero,
                        size = size,
                        cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                    )
                    val filledWidth = size.width * ratio
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(barColor.copy(alpha = 0.7f), barColor)
                        ),
                        topLeft = Offset.Zero,
                        size = Size(filledWidth, size.height),
                        cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${stat.trades} trades • ${String.format(Locale.US, "%.0f%%", stat.winRate)} win",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "PF ${String.format(Locale.US, "%.2f", stat.profitFactor)} • Avg ${CurrencyFormatter.format(stat.averagePnL, currency)}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Grouped vertical Canvas comparison chart for Strategy Win Rate % and Profit Factor.
 */
@Composable
fun StrategyWinRateComparisonChart(
    strategyStats: List<StrategyStat>,
    currency: CurrencyCode,
    modifier: Modifier = Modifier
) {
    if (strategyStats.isEmpty()) return

    val topStrategies = remember(strategyStats) {
        strategyStats.sortedByDescending { it.totalPnL }.take(6)
    }
    var selectedStrat by remember(topStrategies) { mutableStateOf(topStrategies.firstOrNull()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("strategy_winrate_chart")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Win Rate & Profit Factor Comparison",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            // Legend
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Win %", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(ElectricBlue, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PF (x25)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        val trackBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        val primaryColor = MaterialTheme.colorScheme.primary
        val gridHalfColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
        val gridMinorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)

        if (selectedStrat != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = trackBg,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedStrat!!.strategyName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Win ${String.format(Locale.US, "%.1f%%", selectedStrat!!.winRate)} • PF ${String.format(Locale.US, "%.2f", selectedStrat!!.profitFactor)} • ${CurrencyFormatter.format(selectedStrat!!.totalPnL, currency)}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(145.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                // Horizontal reference grid lines (25%, 50%, 75%, 100%)
                listOf(0.25f, 0.50f, 0.75f, 1.0f).forEach { level ->
                    val y = height * (1f - level)
                    drawLine(
                        color = if (level == 0.50f) gridHalfColor else gridMinorColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f,
                        pathEffect = if (level == 0.50f) PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f) else null
                    )
                }

                val groupCount = topStrategies.size
                val groupWidth = width / groupCount
                val barWidth = (groupWidth * 0.24f).coerceAtMost(18.dp.toPx())
                val gap = 4.dp.toPx()

                topStrategies.forEachIndexed { index, stat ->
                    val centerX = (index + 0.5f) * groupWidth

                    // Win Rate Bar (0..100%)
                    val winRatio = (stat.winRate / 100.0).toFloat().coerceIn(0.04f, 1f)
                    val winHeight = height * winRatio
                    drawRoundRect(
                        color = primaryColor,
                        topLeft = Offset(centerX - barWidth - gap / 2f, height - winHeight),
                        size = Size(barWidth, winHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )

                    // Profit Factor scaled (PF 4.0 == 100% height so PF 2.0 == 50% height)
                    val pfNormalized = (stat.profitFactor.coerceAtMost(4.0) / 4.0).toFloat().coerceIn(0.04f, 1f)
                    val pfHeight = height * pfNormalized
                    drawRoundRect(
                        color = ElectricBlue,
                        topLeft = Offset(centerX + gap / 2f, height - pfHeight),
                        size = Size(barWidth, pfHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }

            Row(modifier = Modifier.fillMaxSize()) {
                topStrategies.forEach { stat ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { selectedStrat = stat }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            topStrategies.forEach { stat ->
                Text(
                    text = stat.strategyName.take(8),
                    fontSize = 10.sp,
                    color = if (selectedStrat?.strategyName == stat.strategyName) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selectedStrat?.strategyName == stat.strategyName) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
