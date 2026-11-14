package com.example.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ConfidenceLevel
import com.example.domain.model.MacroIndicator
import com.example.domain.model.NewsEventType
import com.example.domain.model.NewsRelease
import com.example.domain.model.OutlookPrediction
import com.example.domain.model.TrendDirection
import com.example.presentation.TradingViewModel
import com.example.presentation.components.LiquidGlassCard
import com.example.presentation.components.LiquidGlassTabRow
import com.example.ui.theme.AmberGold
import com.example.ui.theme.SlateBreakEven
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun OutlookScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    val selectedEvent by viewModel.selectedNewsEventType.collectAsState()
    val allReleases by viewModel.newsReleases.collectAsState()
    val allPredictions by viewModel.outlookPredictions.collectAsState()
    val macroIndicators by viewModel.macroIndicators.collectAsState()
    val isLoading by viewModel.isOutlookLoading.collectAsState()
    val aiSummary by viewModel.outlookAiSummary.collectAsState()
    val statusMessage by viewModel.outlookStatusMessage.collectAsState()
    val calibration by viewModel.calibrationStats.collectAsState()

    val eventReleases = remember(allReleases, selectedEvent) {
        allReleases.filter { it.eventType == selectedEvent }
    }

    val currentPrediction = remember(allPredictions, selectedEvent) {
        allPredictions.firstOrNull { it.eventType == selectedEvent }
    }

    // Countdown timer calculation
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTimeMillis = System.currentTimeMillis()
        }
    }

    val targetTimestamp = currentPrediction?.releaseTimestamp ?: currentTimeMillis
    val remainingMillis = (targetTimestamp - currentTimeMillis).coerceAtLeast(0L)
    val days = remainingMillis / (1000 * 60 * 60 * 24)
    val hours = (remainingMillis / (1000 * 60 * 60)) % 24
    val minutes = (remainingMillis / (1000 * 60)) % 60
    val seconds = (remainingMillis / 1000) % 60

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Liquid Glass Event Type Tabs (NFP vs CPI)
        item {
            LiquidGlassTabRow(
                tabs = listOf("Non-Farm Payrolls (NFP)", "Inflation (CPI)"),
                selectedIndex = if (selectedEvent == NewsEventType.NFP) 0 else 1,
                onTabSelected = { idx ->
                    viewModel.setNewsEventType(if (idx == 0) NewsEventType.NFP else NewsEventType.CPI)
                }
            )
        }

        if (statusMessage != null) {
            item {
                Surface(
                    color = AmberGold.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = AmberGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Live Countdown Ticker Card
        item {
            LiquidGlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Next Release Countdown",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { viewModel.refreshOutlook() },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("refresh_outlook_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CountdownUnitBlock(value = days.toInt(), label = "DAYS")
                    Text(":", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 4.dp))
                    CountdownUnitBlock(value = hours.toInt(), label = "HOURS")
                    Text(":", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 4.dp))
                    CountdownUnitBlock(value = minutes.toInt(), label = "MINS")
                    Text(":", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 4.dp))
                    CountdownUnitBlock(value = seconds.toInt(), label = "SECS")
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (currentPrediction != null) {
                        "Target Release: ${currentPrediction.releaseDate}"
                    } else {
                        "No upcoming release loaded. Add FRED/Finnhub API keys in Settings and tap Refresh."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Prediction Card
        if (currentPrediction != null) {
            item {
                PredictionCard(
                    prediction = currentPrediction,
                    onToggleTraded = { viewModel.togglePredictionTraded(currentPrediction) },
                    onExplainAi = { viewModel.generateAiExplanation(currentPrediction) },
                    aiSummary = aiSummary,
                    isLoading = isLoading
                )
            }
        }

        // Key Inputs Dashboard with Trend Arrows
        item {
            LiquidGlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Macro Leading Indicators",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "FRED Live Feed",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (macroIndicators.isEmpty()) {
                    Text(
                        text = "No macro indicators loaded yet. Enter your FRED API key in Settings and tap Refresh to pull live labor & CPI prints.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    macroIndicators.forEachIndexed { index, indicator ->
                        MacroIndicatorRow(indicator = indicator)
                        if (index < macroIndicators.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            }
        }

        // Model Calibration & Accuracy Stats
        item {
            LiquidGlassCard {
                Text(
                    text = "Statistical Model Reliability",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Historical Hit Rate",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (calibration.resolvedPredictions > 0) String.format(Locale.US, "%.1f%%", calibration.hitRate) else "—",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Brier Calibration",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (calibration.resolvedPredictions > 0) String.format(Locale.US, "%.3f", calibration.brierScore) else "—",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Sample Size",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${currentPrediction?.sampleSize ?: eventReleases.size} events",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = AmberGold.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = AmberGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Note: NFP releases occur only 12 times per year. Sample size is naturally constrained; probabilities represent mathematical odds rather than guarantees.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Historical Releases Table
        item {
            Text(
                text = "Past Releases & Gold Reaction",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (eventReleases.isEmpty()) {
            item {
                LiquidGlassCard {
                    Text(
                        text = "No cached ${selectedEvent.name} releases yet. Sync via Finnhub in Settings to populate real calendar releases.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(eventReleases) { release ->
                HistoricalReleaseCard(release = release)
            }
        }

        // Permanent Disclaimer
        item {
            LiquidGlassCard(backgroundColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Disclaimer",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PERMANENT DISCLAIMER: This outlook is derived solely from statistical logistic regression on past macroeconomic releases and market consensus. It does NOT constitute financial advice, trading signals, or execution directives. High-impact news carries severe slippage and volatility risk.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun CountdownUnitBlock(value: Int, label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.width(58.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = String.format(Locale.US, "%02d", value),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PredictionCard(
    prediction: OutlookPrediction,
    onToggleTraded: () -> Unit,
    onExplainAi: () -> Unit,
    aiSummary: String?,
    isLoading: Boolean
) {
    val probRisePct = (prediction.probGoldRise * 100).toInt()
    val probFallPct = (prediction.probGoldFall * 100).toInt()

    val confidenceColor = when (prediction.confidence) {
        ConfidenceLevel.HIGH -> MaterialTheme.colorScheme.primary
        ConfidenceLevel.MEDIUM -> AmberGold
        ConfidenceLevel.LOW -> SlateBreakEven
    }

    LiquidGlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Gold Bias Prediction",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Surface(
                color = confidenceColor.copy(alpha = 0.18f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = prediction.confidence.label,
                    color = confidenceColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "GOLD BUYS (LONG)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "$probRisePct%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "GOLD SELLS (SHORT)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "$probFallPct%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
        ) {
            Box(
                modifier = Modifier
                    .weight(prediction.probGoldRise.toFloat().coerceAtLeast(0.05f))
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary)
            )
            Box(
                modifier = Modifier
                    .weight(prediction.probGoldFall.toFloat().coerceAtLeast(0.05f))
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Target Window: ${prediction.timeWindow}",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Statistical Model Rationale",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = prediction.reasoning,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 16.sp
                )

                if (aiSummary != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = aiSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onExplainAi,
                enabled = !isLoading,
                modifier = Modifier
                    .weight(1f)
                    .testTag("explain_with_ai_button")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("AI Summary", fontSize = 12.sp)
            }

            Button(
                onClick = onToggleTraded,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (prediction.userTradedRelease) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .weight(1.2f)
                    .testTag("toggle_traded_release_button")
            ) {
                Icon(
                    if (prediction.userTradedRelease) Icons.Default.CheckCircle else Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (prediction.userTradedRelease) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (prediction.userTradedRelease) "Traded in Journal" else "Mark as Traded",
                    fontSize = 12.sp,
                    color = if (prediction.userTradedRelease) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun MacroIndicatorRow(indicator: MacroIndicator) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.5f)) {
            Text(
                text = indicator.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = indicator.impactOnGold,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${indicator.currentValue} ${indicator.unit}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Prev: ${indicator.previousValue} ${indicator.unit}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = when (indicator.trend) {
                    TrendDirection.UP -> Icons.Default.TrendingUp
                    TrendDirection.DOWN -> Icons.Default.TrendingDown
                    TrendDirection.FLAT -> Icons.Default.TrendingFlat
                },
                contentDescription = null,
                tint = if (indicator.isBullishForGold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun HistoricalReleaseCard(release: NewsRelease) {
    LiquidGlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = release.dateString,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Act: ${release.actual ?: "-"} ${release.unit} | Exp: ${release.forecast} ${release.unit}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (release.surprise != null) {
                    val sign = if (release.surprise > 0) "+" else ""
                    Text(
                        text = "Surprise: $sign${release.surprise} ${release.unit}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Bold,
                        color = if (release.surprise > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                if (release.gold15mDelta != null) {
                    val sign = if (release.gold15mDelta > 0) "+$" else "-$"
                    Text(
                        text = "15m: $sign${String.format(Locale.US, "%.1f", kotlin.math.abs(release.gold15mDelta))}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (release.gold15mDelta >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                if (release.gold60mDelta != null) {
                    val sign = if (release.gold60mDelta > 0) "+$" else "-$"
                    Text(
                        text = "60m: $sign${String.format(Locale.US, "%.1f", kotlin.math.abs(release.gold60mDelta))}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = if (release.gold60mDelta >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
