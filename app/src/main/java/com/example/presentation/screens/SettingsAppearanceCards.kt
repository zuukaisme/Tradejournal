package com.example.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AppThemePreset
import com.example.domain.model.Trade
import com.example.domain.model.TradeResult
import com.example.presentation.components.LiquidGlassCard
import com.example.ui.theme.colorSchemeForPreset
import kotlin.math.max

@Composable
fun ThemePresetSelectorCard(
    selectedPreset: AppThemePreset,
    onSelectPreset: (AppThemePreset) -> Unit,
    modifier: Modifier = Modifier
) {
    LiquidGlassCard(
        modifier = modifier,
        testTag = "settings_theme_preset_card"
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = "App Color Theme",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Switch between 6 custom trading terminal palettes",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppThemePreset.values().forEach { preset ->
                val isSelected = selectedPreset == preset
                val previewScheme = colorSchemeForPreset(preset, darkTheme = !preset.forcesLight)
                val primary = MaterialTheme.colorScheme.primary
                val shape = RoundedCornerShape(12.dp)
                val rowBg = if (isSelected) {
                    primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clip(shape)
                        .background(rowBg)
                        .then(
                            if (isSelected) {
                                Modifier.border(1.dp, primary.copy(alpha = 0.55f), shape)
                            } else Modifier
                        )
                        .clickable { onSelectPreset(preset) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("theme_preset_${preset.name}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 3-circle palette swatch
                    Box(modifier = Modifier.width(44.dp).height(24.dp)) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(previewScheme.background)
                                .border(1.dp, previewScheme.outline, CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .offset(x = 12.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(previewScheme.surfaceContainer)
                                .border(1.dp, previewScheme.outline, CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .offset(x = 24.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(previewScheme.primary)
                                .border(1.dp, previewScheme.background, CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = preset.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = preset.description,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isSelected) {
                        Surface(
                            color = primary,
                            shape = CircleShape,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Returns Pair(currentDrawdownFromPeak, maxHistoricalDrawdown) across closed trades chronologically.
 */
fun computeTrailingDrawdown(trades: List<Trade>): Pair<Double, Double> {
    val ordered = trades
        .filter { it.result != TradeResult.NO_TRADE }
        .sortedWith(compareBy({ it.date }, { it.time }, { it.timestamp }))
    if (ordered.isEmpty()) return 0.0 to 0.0

    var equity = 0.0
    var peak = 0.0
    var maxDd = 0.0
    for (trade in ordered) {
        equity += trade.pnl
        if (equity > peak) {
            peak = equity
        }
        val dd = peak - equity
        maxDd = max(maxDd, dd)
    }
    val currentDd = (peak - equity).coerceAtLeast(0.0)
    return currentDd to maxDd
}
