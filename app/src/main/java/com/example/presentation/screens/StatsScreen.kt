package com.example.presentation.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.PerformanceSummary
import com.example.domain.model.StrategyStat
import com.example.domain.model.SyntheticFamily
import com.example.domain.model.SyntheticIndexCatalog
import com.example.domain.model.TimePeriod
import com.example.domain.model.TradeDirection
import com.example.domain.model.TradeResult
import com.example.domain.usecase.StatsCalculator
import com.example.presentation.TradingViewModel
import com.example.presentation.components.CurrencyFormatter
import com.example.presentation.components.DailyPnLBarChart
import com.example.presentation.components.EquityCurveChart
import com.example.presentation.components.LiquidGlassCard
import com.example.presentation.components.LiquidGlassPill
import com.example.presentation.components.LiquidGlassTabRow
import com.example.presentation.components.StrategyPerformanceBarChart
import com.example.presentation.components.StrategyWinRateComparisonChart
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LocalIsDarkTheme
import java.util.Locale

@Composable
fun StatsScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    val stats by viewModel.performanceSummary.collectAsState()
    val allTrades by viewModel.trades.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val currentPeriod by viewModel.statsPeriod.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Overview", "Indices", "Strategies", "Breakdown")
    val tabIcons = listOf(
        Icons.Default.ShowChart,
        Icons.Default.Analytics,
        Icons.Default.Psychology,
        Icons.Default.PieChart
    )

    val periodTrades = remember(allTrades, currentPeriod) {
        StatsCalculator.filterTradesByPeriod(allTrades, currentPeriod)
    }

    // Synthetic Indices trades & calculations (WLTrade SyntX / MT5)
    val indexTrades = remember(periodTrades) {
        periodTrades.filter { SyntheticIndexCatalog.isSyntheticIndex(it.instrument) }
    }
    val indexStats = remember(indexTrades, currentPeriod) {
        StatsCalculator.calculate(indexTrades, currentPeriod)
    }

    val indexTradesByFamily = remember(indexTrades) {
        indexTrades.groupBy { trade ->
            val info = SyntheticIndexCatalog.getIndexInfo(trade.instrument)
            info?.family ?: when {
                trade.instrument.contains("SFX", ignoreCase = true) -> SyntheticFamily.SFX_VOL
                trade.instrument.contains("FX", ignoreCase = true) -> SyntheticFamily.FX_VOL
                trade.instrument.contains("PAIN", ignoreCase = true) -> SyntheticFamily.PAIN_X
                trade.instrument.contains("CRASH", ignoreCase = true) || trade.instrument.contains("BOOM", ignoreCase = true) -> SyntheticFamily.CRASH_BOOM
                trade.instrument.contains("STEP", ignoreCase = true) || trade.instrument.contains("JUMP", ignoreCase = true) -> SyntheticFamily.STEP_JUMP
                else -> SyntheticFamily.DERIV_VOL
            }
        }
    }

    // News trades performance comparison
    val newsTrades = remember(periodTrades) { periodTrades.filter { it.isNewsTrade } }
    val normalTrades = remember(periodTrades) { periodTrades.filter { !it.isNewsTrade && it.result != TradeResult.NO_TRADE } }

    val newsPnL = remember(newsTrades) { newsTrades.sumOf { it.pnl } }
    val newsWins = remember(newsTrades) { newsTrades.count { it.pnl > 0 } }
    val newsWinRate = remember(newsTrades) {
        if (newsTrades.isNotEmpty()) (newsWins.toDouble() / newsTrades.size) * 100.0 else 0.0
    }

    val normalPnL = remember(normalTrades) { normalTrades.sumOf { it.pnl } }
    val normalWins = remember(normalTrades) { normalTrades.count { it.pnl > 0 } }
    val normalWinRate = remember(normalTrades) {
        if (normalTrades.isNotEmpty()) (normalWins.toDouble() / normalTrades.size) * 100.0 else 0.0
    }

    val rankedStrategies = remember(stats.strategyBreakdown) {
        stats.strategyBreakdown.values.sortedByDescending { it.totalPnL }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Liquid Glass Tab Row (Overview, Indices, Strategies, Breakdown)
        item {
            LiquidGlassTabRow(
                tabs = tabTitles,
                selectedIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it },
                icons = tabIcons
            )
        }

        // 2. Period Selector Filter Pills
        item {
            val periods = listOf(
                TimePeriod.TODAY,
                TimePeriod.THIS_WEEK,
                TimePeriod.THIS_MONTH,
                TimePeriod.THIS_YEAR,
                TimePeriod.ALL_TIME
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(periods) { period ->
                    LiquidGlassPill(
                        text = period.label,
                        isSelected = currentPeriod == period,
                        onClick = { viewModel.statsPeriod.value = period }
                    )
                }
            }
        }

        // 3. Tab Content
        when (selectedTabIndex) {
            0 -> {
                // OVERVIEW TAB
                item {
                    OverviewHeroCard(
                        stats = stats,
                        currency = settings.currency
                    )
                }

                item {
                    OverviewMetricsGrid(
                        stats = stats,
                        currency = settings.currency
                    )
                }

                if (stats.dailyPnLList.isNotEmpty()) {
                    item {
                        LiquidGlassCard {
                            Text(
                                text = "Equity Growth Trajectory",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            EquityCurveChart(
                                equityPoints = stats.equityCurve,
                                currency = settings.currency,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    item {
                        LiquidGlassCard {
                            Text(
                                text = "Daily Net Profit / Loss",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            DailyPnLBarChart(
                                dailyPnL = stats.dailyPnLList,
                                currency = settings.currency,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    item {
                        EmptyStatsCard(
                            title = "No Trades Recorded Yet",
                            subtitle = "Log your first trade execution to generate equity curves, strategy graphs, and win-rate analytics.",
                            onLogClick = { viewModel.openNewTradeEntry() }
                        )
                    }
                }

                // Quick Strategy Winner Preview on Overview
                if (rankedStrategies.isNotEmpty()) {
                    item {
                        LiquidGlassCard(
                            onClick = { selectedTabIndex = 2 }
                        ) {
                            StrategyPerformanceBarChart(
                                strategyStats = rankedStrategies,
                                currency = settings.currency
                            )
                        }
                    }
                }

                // Rule Discipline Impact
                item {
                    val adherence = stats.ruleAdherenceBreakdown
                    LiquidGlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Checklist Discipline Impact",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val followedWinRate = if (adherence.followedTrades > 0) (adherence.followedWins.toDouble() / adherence.followedTrades) * 100.0 else 0.0
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Followed Rules", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text(text = "${adherence.followedTrades} Trades (${String.format(Locale.US, "%.0f%%", followedWinRate)} Win)", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = CurrencyFormatter.format(adherence.followedPnL, settings.currency),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (adherence.followedPnL >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                            val violatedWinRate = if (adherence.violatedTrades > 0) (adherence.violatedWins.toDouble() / adherence.violatedTrades) * 100.0 else 0.0
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(text = "Violated Rules", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                Text(text = "${adherence.violatedTrades} Trades (${String.format(Locale.US, "%.0f%%", violatedWinRate)} Win)", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = CurrencyFormatter.format(adherence.violatedPnL, settings.currency),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (adherence.violatedPnL >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            1 -> {
                // DEDICATED INDICES TAB (WLTrade SyntX / MT5 Synthetic Indices)
                item {
                    val isDark = LocalIsDarkTheme.current
                    LiquidGlassCard(
                        backgroundColor = if (isDark) Color(0xFF0A141A) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Analytics,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "WLTrade SyntX & Indices",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "24/7 algorithmic volatility, FX Vol, PainX & Boom/Crash performance",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Indices Net P&L",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = CurrencyFormatter.format(indexStats.netPnL, settings.currency),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (indexStats.netPnL >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Indices Win Rate",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f%%", indexStats.winRate),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (indexStats.winRate >= 50.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${indexStats.totalTrades} Executions (${indexStats.winningTrades}W / ${indexStats.losingTrades}L)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Profit Factor: ${String.format(Locale.US, "%.2f", indexStats.profitFactor)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (indexTrades.isNotEmpty()) {
                    // Index Equity Curve
                    item {
                        LiquidGlassCard {
                            Text(
                                text = "Synthetic Indices Equity Curve",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            EquityCurveChart(
                                equityPoints = indexStats.equityCurve,
                                currency = settings.currency,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    item {
                        Text(
                            text = "Performance by SyntX Family",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }

                    items(indexTradesByFamily.entries.toList()) { (family, familyTrades) ->
                        val familyNet = familyTrades.sumOf { it.pnl }
                        val familyWins = familyTrades.count { it.pnl > 0 }
                        val familyWinRate = (familyWins.toDouble() / familyTrades.size) * 100.0

                        LiquidGlassCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = family.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = family.description,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${familyTrades.size} trades • ${String.format(Locale.US, "%.0f%%", familyWinRate)} win rate",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = CurrencyFormatter.format(familyNet, settings.currency),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (familyNet >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Individual Index Statistics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 2.dp, top = 4.dp)
                        )
                    }

                    val indexBySymbol = indexTrades.groupBy { it.instrument }
                    items(indexBySymbol.entries.toList()) { (symbol, symTrades) ->
                        val symNet = symTrades.sumOf { it.pnl }
                        val symWins = symTrades.count { it.pnl > 0 }
                        val symWinRate = (symWins.toDouble() / symTrades.size) * 100.0
                        val buyCount = symTrades.count { it.direction == TradeDirection.BUY }
                        val sellCount = symTrades.count { it.direction == TradeDirection.SELL }

                        LiquidGlassCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = symbol,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${symTrades.size} trades ($buyCount Buys, $sellCount Sells) • ${String.format(Locale.US, "%.0f%%", symWinRate)} win rate",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = CurrencyFormatter.format(symNet, settings.currency),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (symNet >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                } else {
                    item {
                        EmptyStatsCard(
                            title = "No Synthetic Indices Logged",
                            subtitle = "Trade WLTrade SyntX (FX Vol 20, PainX 400, Boom/Crash, Step Index, V75)? Log an execution to view dedicated index analytics.",
                            onLogClick = { viewModel.openNewTradeEntry() }
                        )
                    }

                    item {
                        LiquidGlassCard {
                            Text(
                                text = "Weltrade SyntX Supported Instruments",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "SyntX algorithms operate 24/7 on MT5 independent of macroeconomic releases:",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            val syntxFamilies = listOf(
                                "FX Vol (10, 20, 40, 60, 80, 100)" to "Continuous percentage volatility",
                                "SFX Vol (20, 40, 80, 100)" to "Algorithmic spike jumps every 30m",
                                "PainX (400, 600, 800, 999, 1200)" to "Upward trend with periodic drop ticks",
                                "Crash & Boom (300, 500, 1000)" to "Interval spike explosions and crashes",
                                "Step & Jump (Step Index, Jump 10-100)" to "Equal tick steps and jump volatility",
                                "V-Series (V75, V100, V50, V25)" to "Continuous benchmark volatility"
                            )

                            syntxFamilies.forEach { (name, desc) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = name, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    Text(text = desc, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // STRATEGIES TAB WITH GRAPHS SHOWING WHICH STRATEGY WORKS BEST
                if (rankedStrategies.isNotEmpty()) {
                    val topByPnL = rankedStrategies.first()
                    val topByWinRate = rankedStrategies.maxByOrNull { it.winRate } ?: topByPnL
                    val topByPf = rankedStrategies.maxByOrNull { it.profitFactor } ?: topByPnL

                    // Best Strategy Spotlight Card
                    item {
                        BestStrategySpotlightCard(
                            bestByPnL = topByPnL,
                            bestByWinRate = topByWinRate,
                            bestByProfitFactor = topByPf,
                            currency = settings.currency
                        )
                    }

                    // Graph 1: Net P&L Ranking Bar Graph
                    item {
                        LiquidGlassCard {
                            StrategyPerformanceBarChart(
                                strategyStats = rankedStrategies,
                                currency = settings.currency
                            )
                        }
                    }

                    // Graph 2: Win Rate & Profit Factor Grouped Comparison Chart
                    item {
                        LiquidGlassCard {
                            StrategyWinRateComparisonChart(
                                strategyStats = rankedStrategies,
                                currency = settings.currency
                            )
                        }
                    }

                    item {
                        Text(
                            text = "Strategy Breakdown Details",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 2.dp, top = 2.dp)
                        )
                    }

                    items(rankedStrategies) { strat ->
                        LiquidGlassCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = strat.strategyName,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "${strat.trades} trades (${strat.wins}W / ${strat.losses}L) • ${String.format(Locale.US, "%.1f%%", strat.winRate)} win",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Profit Factor: ${String.format(Locale.US, "%.2f", strat.profitFactor)} • Avg/Trade: ${CurrencyFormatter.format(strat.averagePnL, settings.currency)}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        color = ElectricBlue
                                    )
                                }
                                Text(
                                    text = CurrencyFormatter.format(strat.totalPnL, settings.currency),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (strat.totalPnL >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                } else {
                    item {
                        EmptyStatsCard(
                            title = "No Strategy Trades Yet",
                            subtitle = "Create a strategy and assign trades to it to unlock comparative strategy graphs.",
                            onLogClick = { viewModel.openNewTradeEntry() }
                        )
                    }
                }
            }

            3 -> {
                // BREAKDOWN TAB (Instruments, Day of Week, News vs Standard)
                if (stats.instrumentBreakdown.isNotEmpty()) {
                    item {
                        Text(
                            text = "Performance by Instrument",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(stats.instrumentBreakdown.values.sortedByDescending { it.totalPnL }) { inst ->
                        val isIndex = SyntheticIndexCatalog.isSyntheticIndex(inst.instrument)
                        LiquidGlassCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isIndex) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.padding(end = 8.dp)
                                        ) {
                                            Text(
                                                text = "SyntX",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(text = inst.instrument, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = "${inst.trades} trades • ${String.format(Locale.US, "%.0f%%", inst.winRate)} win",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = CurrencyFormatter.format(inst.totalPnL, settings.currency),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (inst.totalPnL >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // News Releases vs Normal Days
                item {
                    LiquidGlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "News Releases vs Standard Sessions",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "News Release Trades", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFB300), fontWeight = FontWeight.Bold)
                                Text(text = "${newsTrades.size} Trades (${String.format(Locale.US, "%.0f%%", newsWinRate)} Win)", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = CurrencyFormatter.format(newsPnL, settings.currency),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (newsPnL >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(text = "Standard Sessions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                Text(text = "${normalTrades.size} Trades (${String.format(Locale.US, "%.0f%%", normalWinRate)} Win)", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = CurrencyFormatter.format(normalPnL, settings.currency),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (normalPnL >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // Day of Week Analysis
                val activeDays = stats.dayOfWeekBreakdown.values.filter { it.trades > 0 }
                if (activeDays.isNotEmpty()) {
                    item {
                        Text(
                            text = "Performance by Day of Week",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(activeDays) { day ->
                        LiquidGlassCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = day.dayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = "${day.trades} trades • ${String.format(Locale.US, "%.0f%%", day.winRate)} win rate",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = CurrencyFormatter.format(day.netPnL, settings.currency),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (day.netPnL >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                } else if (stats.instrumentBreakdown.isEmpty()) {
                    item {
                        EmptyStatsCard(
                            title = "No Breakdown Available",
                            subtitle = "Log trades across instruments and days to inspect session and asset distributions.",
                            onLogClick = { viewModel.openNewTradeEntry() }
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BestStrategySpotlightCard(
    bestByPnL: StrategyStat,
    bestByWinRate: StrategyStat,
    bestByProfitFactor: StrategyStat,
    currency: com.example.domain.model.CurrencyCode
) {
    val isDark = LocalIsDarkTheme.current
    LiquidGlassCard(
        backgroundColor = if (isDark) Color(0xFF091712) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        testTag = "best_strategy_spotlight"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                shape = CircleShape,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "BEST PERFORMING STRATEGY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = bestByPnL.strategyName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = CurrencyFormatter.format(bestByPnL.totalPnL, currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (bestByPnL.totalPnL >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Highest Win Rate",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = bestByWinRate.strategyName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = String.format(Locale.US, "%.1f%% (%d/%d)", bestByWinRate.winRate, bestByWinRate.wins, bestByWinRate.trades),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "Best Profit Factor",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = bestByProfitFactor.strategyName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "PF ${String.format(Locale.US, "%.2f", bestByProfitFactor.profitFactor)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ElectricBlue
                )
            }
        }
    }
}

@Composable
private fun OverviewHeroCard(
    stats: PerformanceSummary,
    currency: com.example.domain.model.CurrencyCode
) {
    LiquidGlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = "Net Realized Profit / Loss",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyFormatter.format(stats.netPnL, currency),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (stats.netPnL >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            Surface(
                color = if (stats.netPnL >= 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (stats.netPnL >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = if (stats.netPnL >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = String.format(Locale.US, "%.1f%% Win", stats.winRate),
                        color = if (stats.netPnL >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = "Total Trades", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "${stats.totalTrades}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Column {
                Text(text = "Profit Factor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = String.format(Locale.US, "%.2f", stats.profitFactor),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "Max Drawdown", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = CurrencyFormatter.format(-stats.maxDrawdown, currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun OverviewMetricsGrid(
    stats: PerformanceSummary,
    currency: com.example.domain.model.CurrencyCode
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                label = "Average Win",
                value = CurrencyFormatter.format(stats.averageWin, currency),
                valueColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "Average Loss",
                value = CurrencyFormatter.format(-stats.averageLoss, currency),
                valueColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                label = "Best Day",
                value = CurrencyFormatter.format(stats.bestDay, currency),
                valueColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "Worst Day",
                value = CurrencyFormatter.format(stats.worstDay, currency),
                valueColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                label = "Winning Streak",
                value = "${stats.longestWinningStreak} Trades",
                valueColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "Losing Streak",
                value = "${stats.longestLosingStreak} Trades",
                valueColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    LiquidGlassCard(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
private fun EmptyStatsCard(
    title: String,
    subtitle: String,
    onLogClick: () -> Unit
) {
    LiquidGlassCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onLogClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Log Trade Execution", fontWeight = FontWeight.Bold)
            }
        }
    }
}
