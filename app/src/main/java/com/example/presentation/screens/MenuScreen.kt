package com.example.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.presentation.AppScreen
import com.example.presentation.TradingViewModel
import com.example.presentation.components.LiquidGlassCard
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonEmerald

@Composable
fun MenuScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    val strategies by viewModel.strategies.collectAsState()
    val bots by viewModel.bots.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val predictions by viewModel.outlookPredictions.collectAsState()
    val allTrades by viewModel.trades.collectAsState()

    val nextNfpPred = predictions.firstOrNull { it.eventType == com.example.domain.model.NewsEventType.NFP }

    val accentColor = MaterialTheme.colorScheme.primary

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Gold (XAUUSD) Featured Hero Card
        item {
            LiquidGlassCard(
                onClick = { viewModel.navigateTo(AppScreen.OUTLOOK) },
                testTag = "menu_gold_outlook_hero"
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
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFFB300).copy(alpha = 0.2f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB300),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Gold (XAUUSD) Outlook",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFFFFB300).copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "LIVE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFB300),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (nextNfpPred != null) "NFP Gold Model: ${(nextNfpPred.probGoldRise * 100).toInt()}% Buy vs ${(nextNfpPred.probGoldFall * 100).toInt()}% Sell" else "Macro news countdown & statistical prediction",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Open Outlook",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Section 1: Execution & Tools
        item {
            Text(
                text = "TRADING TOOLS",
        item {
            MenuNavigationCard(
                icon = Icons.Default.CalendarMonth,
                iconTint = Color(0xFF42A5F5),
                title = "Economic Calendar",
                subtitle = "Filter global news by impact, currency and date",
                badgeText = "NEWS",
                testTag = "menu_item_economic_calendar",
                onClick = { viewModel.navigateTo(AppScreen.ECONOMIC_CALENDAR) }
            )
        }

                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }

        item {
            MenuNavigationCard(
                icon = Icons.Default.ShowChart,
                iconTint = Color(0xFF00D4AA),
                title = "TradingView Charts",
                subtitle = "Advanced charts for Deriv synthetics, forex, metals & oil",
                badgeText = "LIVE",
                testTag = "menu_item_charts",
                onClick = { viewModel.navigateTo(AppScreen.CHARTS) }
            )
        }

        item {
            MenuNavigationCard(
                icon = Icons.Default.Analytics,
                iconTint = accentColor,
                title = "WLTrade SyntX Indices",
                subtitle = "FX Vol, SFX Vol, PainX, Boom/Crash & Step Index analytics",
                badgeText = "SyntX",
                testTag = "menu_item_syntx",
                onClick = { viewModel.navigateTo(AppScreen.STATS) }
            )
        }

        item {
            MenuNavigationCard(
                icon = Icons.Default.Psychology,
                iconTint = accentColor,
                title = "Strategies & Checklists",
                subtitle = "Manage entry criteria, execution rules, and win rates",
                badgeText = "${strategies.size} Active",
                testTag = "menu_item_strategies",
                onClick = { viewModel.navigateTo(AppScreen.STRATEGIES) }
            )
        }

        item {
            MenuNavigationCard(
                icon = Icons.Default.Code,
                iconTint = Color(0xFF2979FF),
                title = "MQL5 Bot Library",
                subtitle = "Offline Expert Advisors, scripts & syntax viewer",
                badgeText = "${bots.size} Bots",
                testTag = "menu_item_bots",
                onClick = { viewModel.navigateTo(AppScreen.BOTS) }
            )
        }

        item {
            MenuNavigationCard(
                icon = Icons.Default.QueryStats,
                iconTint = accentColor,
                title = "Strategy Backtester",
                subtitle = "Simulate CSV & MT5 candle exports with risk rules",
                badgeText = "Engine",
                testTag = "menu_item_backtest",
                onClick = { viewModel.navigateTo(AppScreen.BACKTEST) }
            )
        }

        item {
            MenuNavigationCard(
                icon = Icons.Default.Calculate,
                iconTint = Color(0xFF00D4AA),
                title = "Lot Size & Risk Calculator",
                subtitle = "Position sizing for SyntX indices, XAUUSD & FX pairs",
                badgeText = "1R-3R",
                testTag = "menu_item_risk_calc",
                onClick = { viewModel.navigateTo(AppScreen.RISK_CALC) }
            )
        }

        // Section 2: Discipline & Risk
        item {
            Text(
                text = "DISCIPLINE & RISK",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
        }

        item {
            MenuNavigationCard(
                icon = Icons.Default.Psychology,
                iconTint = Color(0xFF7C4DFF),
                title = "Trading Psychology",
                subtitle = "Emotional patterns, rule adherence and daily reflections",
                badgeText = "INSIGHTS",
                testTag = "menu_item_psychology",
                onClick = { viewModel.navigateTo(AppScreen.PSYCHOLOGY) }
            )
        }

        item {
            MenuNavigationCard(
                icon = Icons.Default.RateReview,
                iconTint = Color(0xFFF59E0B),
                title = "Daily Journal Review",
                subtitle = "End-of-day discipline rating and reflection",
                badgeText = "Review",
                testTag = "menu_item_review",
                onClick = { viewModel.openDailyReview() }
            )
        }

        item {
            MenuNavigationCard(
                icon = Icons.Default.TrackChanges,
                iconTint = Color(0xFF7C4DFF),
                title = "Goals & Risk Limits",
                subtitle = "Monthly profit target and max daily loss limits",
                badgeText = "${settings.currency.symbol}${settings.monthlyProfitTarget.toInt()}",
                testTag = "menu_item_goals",
                onClick = { viewModel.navigateTo(AppScreen.SETTINGS) }
            )
        }

        // Section 3: App Configuration & Data
        item {
            Text(
                text = "SYSTEM & DATA",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
        }

        item {
            MenuNavigationCard(
                icon = Icons.Default.Settings,
                iconTint = MaterialTheme.colorScheme.onSurface,
                title = "Settings & Backup",
                subtitle = "Currency, API keys, PIN lock, CSV & JSON export",
                badgeText = settings.currency.symbol,
                testTag = "menu_item_settings",
                onClick = { viewModel.navigateTo(AppScreen.SETTINGS) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun MenuNavigationCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    badgeText: String,
    testTag: String,
    onClick: () -> Unit
) {
    LiquidGlassCard(
        onClick = onClick,
        testTag = testTag
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
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = iconTint.copy(alpha = 0.16f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
