package com.example.presentation.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.backtest.BacktestResult
import com.example.backtest.ExitReason
import com.example.backtest.Side
import com.example.backtest.Trade
import com.example.presentation.BacktestViewModel
import com.example.presentation.components.LiquidGlassCard
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.NeonEmerald
import java.util.Locale

@Composable
fun BacktestScreen(
    modifier: Modifier = Modifier,
    backtestViewModel: BacktestViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by backtestViewModel.uiState.collectAsState()
    var strategyMenuExpanded by remember { mutableStateOf(false) }

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            backtestViewModel.loadCsvUri(context, uri)
        }
    }

    val positiveColor = MaterialTheme.colorScheme.primary
    val negativeColor = MaterialTheme.colorScheme.error
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Controls Card
        item {
            LiquidGlassCard {
                Text(
                    text = "Configuration",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Pick CSV Button
                OutlinedButton(
                    onClick = { csvPickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("pick_csv_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = "Pick CSV", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = state.fileName ?: "Pick CSV / MT5 Export",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (state.fileName != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${state.candlesCount} candles • ${state.detectedEncoding} • ${state.detectedDelimiter}" +
                            if (state.skippedCsvRows > 0) " • ${state.skippedCsvRows} skipped" else "",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = positiveColor
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Strategy Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { strategyMenuExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("strategy_dropdown_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.selectedStrategy.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select strategy")
                        }
                    }

                    DropdownMenu(
                        expanded = strategyMenuExpanded,
                        onDismissRequest = { strategyMenuExpanded = false }
                    ) {
                        state.availableStrategies.forEach { strat ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(strat.name, fontWeight = FontWeight.Bold)
                                        Text(
                                            strat.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    backtestViewModel.selectStrategy(strat)
                                    strategyMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Config inputs: maxStopPoints & pointSize
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = state.maxStopPointsInput,
                        onValueChange = { backtestViewModel.updateMaxStopPoints(it) },
                        label = { Text("Max Stop (pts)", fontSize = 12.sp) },
                        placeholder = { Text("Optional") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("max_stop_points_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = state.pointSizeInput,
                        onValueChange = { backtestViewModel.updatePointSize(it) },
                        label = { Text("Point Size", fontSize = 12.sp) },
                        placeholder = { Text("1.0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("point_size_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Run Button + Loading state
                Button(
                    onClick = { backtestViewModel.run() },
                    enabled = !state.isLoading && state.candles.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = positiveColor,
                        contentColor = onPrimaryColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("run_backtest_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = onPrimaryColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Running Backtest...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Run Backtest", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Error Banner
        if (state.errorMessage != null) {
            item {
                Surface(
                    color = negativeColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = "Error", tint = negativeColor, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = negativeColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        val result = state.result
        if (result != null) {
            // Warnings Banner
            if (result.warnings.isNotEmpty()) {
                item {
                    Surface(
                        color = AmberGold.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backtest_warnings_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Warnings",
                                tint = AmberGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                result.warnings.forEach { warning ->
                                    Text(
                                        text = warning,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Row of 4 Summary Stat Cards
            item {
                BacktestSummaryCardsRow(result = result)
            }

            // Equity Curve Line Chart (Canvas)
            item {
                LiquidGlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Equity Curve (Points)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        val netPts = result.summary.netPoints
                        Text(
                            text = formatSignedPoints(netPts),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (netPts >= 0.0) positiveColor else negativeColor
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    BacktestEquityCurveCanvas(
                        equityCurve = result.equityCurve,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                    )
                }
            }

            // Points Section
            item {
                LiquidGlassCard {
                    Text(
                        text = "Points Breakdown",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MetricRow("Total / Wins / Losses", "${result.summary.totalTrades} / ${result.summary.wins} / ${result.summary.losses}")
                    MetricRow("Avg Win", formatSignedPoints(result.points.avgWinPoints), positiveColor)
                    MetricRow("Avg Loss", formatSignedPoints(-result.points.avgLossPoints), negativeColor)
                    MetricRow("Largest Win", formatSignedPoints(result.points.largestWinPoints), positiveColor)
                    MetricRow("Largest Loss", formatSignedPoints(-result.points.largestLossPoints), negativeColor)
                    MetricRow("Avg Stop Distance", "${formatOneDec(result.points.avgStopDistancePoints)} pts")
                    MetricRow("Max Stop Distance", "${formatOneDec(result.points.maxStopDistancePoints)} pts")
                    MetricRow("Avg Target Distance", "${formatOneDec(result.points.avgTargetDistancePoints)} pts")
                    MetricRow("Skipped by Stop Cap", "${result.summary.skippedByStopCap}")
                }
            }

            // Risk & Timing Section
            item {
                LiquidGlassCard {
                    Text(
                        text = "Risk & Timing",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MetricRow("Max Drawdown (Pts)", "${formatOneDec(result.risk.maxDrawdownPoints)} pts", negativeColor)
                    MetricRow("Max Drawdown (%)", "${formatOneDec(result.risk.maxDrawdownPct)}%", negativeColor)
                    MetricRow("Max Drawdown (R)", "${formatTwoDec(result.risk.maxDrawdownR)} R", negativeColor)
                    MetricRow("Net R / Avg R", "${formatSignedR(result.summary.netR)} / ${formatSignedR(result.summary.avgR)}")
                    MetricRow("Max Consecutive Wins", "${result.risk.maxConsecutiveWins}", positiveColor)
                    MetricRow("Max Consecutive Losses", "${result.risk.maxConsecutiveLosses}", negativeColor)
                    MetricRow("Avg Duration", "${formatOneDec(result.timing.avgTradeDurationCandles)} candles")
                }
            }

            // Buy vs Sell Section
            item {
                LiquidGlassCard {
                    Text(
                        text = "Buy vs Sell",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SideBreakdownBox(
                            title = "BUY",
                            titleColor = positiveColor,
                            trades = result.bySide.buy.trades,
                            winRatePct = result.bySide.buy.winRatePct,
                            netPoints = result.bySide.buy.netPoints,
                            modifier = Modifier.weight(1f)
                        )
                        SideBreakdownBox(
                            title = "SELL",
                            titleColor = negativeColor,
                            trades = result.bySide.sell.trades,
                            winRatePct = result.bySide.sell.winRatePct,
                            netPoints = result.bySide.sell.netPoints,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Trades Header
            item {
                Text(
                    text = "Trades (${result.trades.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (result.trades.isEmpty()) {
                item {
                    LiquidGlassCard {
                        Text(
                            text = "No closed trades in this backtest run.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                itemsIndexed(result.trades) { idx, trade ->
                    BacktestTradeCard(index = idx + 1, trade = trade)
                }
            }
        } else if (!state.isLoading) {
            item {
                LiquidGlassCard {
                    Text(
                        text = "Ready to Backtest",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pick an MT5 tab-separated export or standard comma/semicolon CSV, choose a strategy, optionally set maxStopPoints and pointSize, and tap Run Backtest.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BacktestSummaryCardsRow(result: BacktestResult) {
    val summary = result.summary
    val positiveColor = MaterialTheme.colorScheme.primary
    val negativeColor = MaterialTheme.colorScheme.error
    val pfText = when {
        summary.profitFactor.isInfinite() -> "INF"
        else -> formatTwoDec(summary.profitFactor)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CompactStatCard(
            label = "Win Rate",
            value = "${formatOneDec(summary.winRatePct)}%",
            valueColor = if (summary.winRatePct >= 50.0) positiveColor else negativeColor,
            modifier = Modifier.weight(1f)
        )
        CompactStatCard(
            label = "Net Pts",
            value = formatSignedPoints(summary.netPoints),
            valueColor = if (summary.netPoints >= 0.0) positiveColor else negativeColor,
            modifier = Modifier.weight(1f)
        )
        CompactStatCard(
            label = "PF",
            value = pfText,
            valueColor = if (summary.profitFactor >= 1.0) positiveColor else negativeColor,
            modifier = Modifier.weight(1f)
        )
        CompactStatCard(
            label = "Max DD",
            value = "${formatOneDec(result.risk.maxDrawdownPct)}%",
            valueColor = negativeColor,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CompactStatCard(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
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
            color = valueColor,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun SideBreakdownBox(
    title: String,
    titleColor: Color,
    trades: Int,
    winRatePct: Double,
    netPoints: Double,
    modifier: Modifier = Modifier
) {
    val positiveColor = MaterialTheme.colorScheme.primary
    val negativeColor = MaterialTheme.colorScheme.error
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = titleColor
            )
            Spacer(modifier = Modifier.height(6.dp))
            MetricRow("Trades", "$trades")
            MetricRow("Win Rate", "${formatOneDec(winRatePct)}%", if (winRatePct >= 50.0) positiveColor else negativeColor)
            MetricRow("Net Pts", formatSignedPoints(netPoints), if (netPoints >= 0.0) positiveColor else negativeColor)
        }
    }
}

@Composable
private fun BacktestEquityCurveCanvas(
    equityCurve: List<Double>,
    modifier: Modifier = Modifier
) {
    val series = remember(equityCurve) { listOf(0.0) + equityCurve }
    val finalVal = series.lastOrNull() ?: 0.0
    val positiveColor = MaterialTheme.colorScheme.primary
    val negativeColor = MaterialTheme.colorScheme.error
    val lineColor = if (finalVal >= 0.0) positiveColor else negativeColor
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)

    if (equityCurve.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "No closed trades to plot",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(modifier = modifier) {
        val minVal = series.minOrNull() ?: 0.0
        val maxVal = series.maxOrNull() ?: 0.0

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "High: ${formatSignedPoints(maxVal)}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = positiveColor
            )
            Text(
                text = "Low: ${formatSignedPoints(minVal)}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = if (minVal < 0.0) negativeColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val range = (maxVal - minVal).takeIf { it > 0.0 } ?: 1.0
            val padTop = 10.dp.toPx()
            val padBottom = 10.dp.toPx()
            val usableH = (size.height - padTop - padBottom).coerceAtLeast(1f)

            fun yFor(value: Double): Float {
                val ratio = ((value - minVal) / range).toFloat()
                return padTop + usableH * (1f - ratio)
            }

            // Zero baseline
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

            val linePath = Path()
            val fillPath = Path()
            val stepX = if (series.size > 1) size.width / (series.size - 1) else size.width

            series.forEachIndexed { idx, value ->
                val x = idx * stepX
                val y = yFor(value)
                if (idx == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, size.height)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }

            fillPath.lineTo((series.size - 1) * stepX, size.height)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.28f),
                        Color.Transparent
                    )
                )
            )

            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Final point marker
            val lastX = (series.size - 1) * stepX
            val lastY = yFor(finalVal)
            drawCircle(
                color = lineColor,
                radius = 4.dp.toPx(),
                center = Offset(lastX, lastY)
            )
        }
    }
}

@Composable
private fun BacktestTradeCard(index: Int, trade: Trade) {
    val isWin = trade.points > 0.0
    val positiveColor = MaterialTheme.colorScheme.primary
    val negativeColor = MaterialTheme.colorScheme.error
    val pnlColor = if (isWin) positiveColor else negativeColor
    val sideColor = if (trade.side == Side.BUY) positiveColor else negativeColor

    LiquidGlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "#$index",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = sideColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = trade.side.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = sideColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = pnlColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = trade.reason.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = pnlColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${formatSignedPoints(trade.points)} pts",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = pnlColor,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = formatSignedR(trade.r),
                    style = MaterialTheme.typography.labelSmall,
                    color = pnlColor,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Entry: ${formatPrice(trade.entry)} -> Exit: ${formatPrice(trade.exit)}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "${trade.durationCandles}c",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "SL: ${formatPrice(trade.sl)} | TP: ${formatPrice(trade.tp)}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "${trade.openTime} -> ${trade.closeTime}",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatOneDec(value: Double): String =
    String.format(Locale.US, "%.1f", value)

private fun formatTwoDec(value: Double): String =
    String.format(Locale.US, "%.2f", value)

private fun formatSignedPoints(value: Double): String {
    val sign = if (value >= 0.0) "+" else ""
    return "$sign${String.format(Locale.US, "%.1f", value)}"
}

private fun formatSignedR(value: Double): String {
    val sign = if (value >= 0.0) "+" else ""
    return "$sign${String.format(Locale.US, "%.2f", value)}R"
}

private fun formatPrice(value: Double): String =
    String.format(Locale.US, "%.2f", value)
