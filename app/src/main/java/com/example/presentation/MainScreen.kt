package com.example.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.presentation.components.LiquidGlassNavItem
import com.example.presentation.components.ThemedBottomNavBar
import com.example.presentation.screens.BacktestScreen
import com.example.presentation.screens.BotsScreen
import com.example.presentation.screens.CalendarScreen
import com.example.presentation.screens.DailyReviewBottomSheet
import com.example.presentation.screens.MenuScreen
import com.example.presentation.screens.OutlookScreen
import com.example.presentation.screens.RiskCalculatorScreen
import com.example.presentation.screens.SettingsScreen
import com.example.presentation.screens.StatsScreen
import com.example.presentation.screens.StrategiesScreen
import com.example.presentation.screens.StrategyGuideScreen
import com.example.presentation.screens.TradeEntryBottomSheet
import com.example.presentation.screens.TradesScreen
import com.example.presentation.screens.TradingViewChartsScreen
import com.example.presentation.screens.EconomicCalendarScreen
import com.example.presentation.screens.PsychologyScreen
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: TradingViewModel,
    onToggleTheme: () -> Unit,
    isDarkTheme: Boolean
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val allTrades by viewModel.trades.collectAsState()

    // Modals
    val isTradeEntryOpen by viewModel.isTradeEntryOpen.collectAsState()
    val tradeForEdit by viewModel.selectedTradeForEdit.collectAsState()
    val preselectedDate by viewModel.tradeEntryPreselectedDate.collectAsState()

    val isDailyReviewOpen by viewModel.isDailyReviewOpen.collectAsState()
    val reviewDate by viewModel.reviewDate.collectAsState()

    // Check risk limit status for top bar warning
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val todayStr = remember { sdf.format(Calendar.getInstance().time) }
    val todayTrades = remember(allTrades, todayStr) {
        allTrades.filter { it.date == todayStr && it.result != com.example.domain.model.TradeResult.NO_TRADE }
    }
    val todayNet = remember(todayTrades) { todayTrades.sumOf { it.pnl } }
    val isDailyLossLimitHit = remember(todayNet, settings.maxDailyLossLimit) {
        todayNet < 0 && abs(todayNet) >= settings.maxDailyLossLimit
    }

    val backtestViewModel: BacktestViewModel = viewModel()

    val isSecondaryScreen = currentScreen in listOf(
        AppScreen.STRATEGIES,
        AppScreen.STRATEGY_GUIDE,
        AppScreen.BOTS,
        AppScreen.OUTLOOK,
        AppScreen.BACKTEST,
        AppScreen.RISK_CALC,
        AppScreen.CHARTS,
        AppScreen.ECONOMIC_CALENDAR,
        AppScreen.PSYCHOLOGY,
        AppScreen.SETTINGS
    )

    // Android BackHandler: Strategy Guide returns to Strategies, other sub-screens return to Menu, main tabs return to Calendar
    BackHandler(enabled = currentScreen != AppScreen.CALENDAR) {
        when {
            currentScreen == AppScreen.STRATEGY_GUIDE -> viewModel.navigateTo(AppScreen.STRATEGIES)
            isSecondaryScreen -> viewModel.navigateTo(AppScreen.MENU)
            else -> viewModel.navigateTo(AppScreen.CALENDAR)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (isSecondaryScreen) {
                        IconButton(
                            onClick = {
                                if (currentScreen == AppScreen.STRATEGY_GUIDE) {
                                    viewModel.navigateTo(AppScreen.STRATEGIES)
                                } else {
                                    viewModel.navigateTo(AppScreen.MENU)
                                }
                            },
                            modifier = Modifier.testTag("top_bar_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (currentScreen == AppScreen.STRATEGY_GUIDE) "Back to Strategies" else "Back to Menu",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isSecondaryScreen) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = settings.currency.symbol,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Column {
                            Text(
                                text = when (currentScreen) {
                                    AppScreen.CALENDAR -> "TradePulse"
                                    AppScreen.TRADES -> "Trade Journal"
                                    AppScreen.STATS -> "Performance Analytics"
                                    AppScreen.MENU -> "Menu & Tools"
                                    AppScreen.STRATEGIES -> "Strategies & Rules"
                                    AppScreen.STRATEGY_GUIDE -> "Strategy Guide"
                                    AppScreen.BOTS -> "MQL5 Bot Library"
                                    AppScreen.OUTLOOK -> "Gold News Outlook"
                                    AppScreen.BACKTEST -> "Strategy Backtester"
                                    AppScreen.RISK_CALC -> "Risk & Lot Calculator"
                                    AppScreen.CHARTS -> "Trading Charts"
                                    AppScreen.ECONOMIC_CALENDAR -> "Economic Calendar"
                                    AppScreen.PSYCHOLOGY -> "Trading Psychology"
                                    AppScreen.SETTINGS -> "Settings & Storage"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when (currentScreen) {
                                    AppScreen.CALENDAR -> "Institutional Trading Journal"
                                    AppScreen.TRADES -> "${allTrades.size} logged executions"
                                    AppScreen.STATS -> "Indices, strategy graphs & P&L"
                                    AppScreen.MENU -> "Bots, strategies, gold outlook & tools"
                                    AppScreen.STRATEGIES -> "Python scripts & entry checklists"
                                    AppScreen.STRATEGY_GUIDE -> "Python contract, example & AI prompt"
                                    AppScreen.BOTS -> "Offline expert advisors & scripts"
                                    AppScreen.OUTLOOK -> "XAUUSD macro event probabilities"
                                    AppScreen.BACKTEST -> "Embedded Python & MT5 simulator"
                                    AppScreen.RISK_CALC -> "WLTrade SyntX, Gold & FX sizing"
                                    AppScreen.CHARTS -> "TradingView charts for live markets"
                                    AppScreen.ECONOMIC_CALENDAR -> "News by impact, currency and date"
                                    AppScreen.PSYCHOLOGY -> "Emotions, discipline and execution patterns"
                                    AppScreen.SETTINGS -> "Risk limits, backup & API keys"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (isDailyLossLimitHit) {
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Risk Cap Hit",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier.testTag("theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            ThemedBottomNavBar(
                style = settings.bottomNavStyle,
                isDarkTheme = isDarkTheme,
                items = listOf(
                    LiquidGlassNavItem(
                        label = "Calendar",
                        selectedIcon = Icons.Filled.CalendarMonth,
                        unselectedIcon = Icons.Outlined.CalendarMonth,
                        selected = currentScreen == AppScreen.CALENDAR,
                        testTag = "nav_calendar",
                        onClick = { viewModel.navigateTo(AppScreen.CALENDAR) }
                    ),
                    LiquidGlassNavItem(
                        label = "Trades",
                        selectedIcon = Icons.AutoMirrored.Filled.FormatListBulleted,
                        unselectedIcon = Icons.AutoMirrored.Outlined.FormatListBulleted,
                        selected = currentScreen == AppScreen.TRADES,
                        badgeCount = allTrades.size,
                        testTag = "nav_trades",
                        onClick = { viewModel.navigateTo(AppScreen.TRADES) }
                    ),
                    LiquidGlassNavItem(
                        label = "Stats",
                        selectedIcon = Icons.Filled.Analytics,
                        unselectedIcon = Icons.Outlined.Analytics,
                        selected = currentScreen == AppScreen.STATS,
                        testTag = "nav_stats",
                        onClick = { viewModel.navigateTo(AppScreen.STATS) }
                    ),
                    LiquidGlassNavItem(
                        label = "Menu",
                        selectedIcon = Icons.Filled.GridView,
                        unselectedIcon = Icons.Outlined.GridView,
                        selected = currentScreen == AppScreen.MENU || isSecondaryScreen,
                        testTag = "nav_menu",
                        onClick = { viewModel.navigateTo(AppScreen.MENU) }
                    )
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    AppScreen.CALENDAR -> CalendarScreen(viewModel = viewModel)
                    AppScreen.TRADES -> TradesScreen(viewModel = viewModel)
                    AppScreen.STATS -> StatsScreen(viewModel = viewModel)
                    AppScreen.MENU -> MenuScreen(viewModel = viewModel)
                    AppScreen.STRATEGIES -> StrategiesScreen(
                        viewModel = viewModel,
                        onOpenStrategyGuide = { viewModel.navigateTo(AppScreen.STRATEGY_GUIDE) }
                    )
                    AppScreen.STRATEGY_GUIDE -> StrategyGuideScreen(
                        onOpenStrategies = { viewModel.navigateTo(AppScreen.STRATEGIES) }
                    )
                    AppScreen.BOTS -> BotsScreen(viewModel = viewModel)
                    AppScreen.OUTLOOK -> OutlookScreen(viewModel = viewModel)
                    AppScreen.BACKTEST -> BacktestScreen(
                        onOpenStrategies = { viewModel.navigateTo(AppScreen.STRATEGIES) },
                        backtestViewModel = backtestViewModel
                    )
                    AppScreen.RISK_CALC -> RiskCalculatorScreen(viewModel = viewModel)
                    AppScreen.CHARTS -> TradingViewChartsScreen()
                    AppScreen.ECONOMIC_CALENDAR -> EconomicCalendarScreen(viewModel = viewModel)
                    AppScreen.PSYCHOLOGY -> PsychologyScreen(viewModel = viewModel)
                    AppScreen.SETTINGS -> SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }

    // Trade Entry / Edit Modal Bottom Sheet
    if (isTradeEntryOpen) {
        TradeEntryBottomSheet(
            viewModel = viewModel,
            tradeToEdit = tradeForEdit,
            preselectedDate = preselectedDate,
            onDismiss = { viewModel.closeTradeEntry() }
        )
    }

    // Daily Review Modal Bottom Sheet
    if (isDailyReviewOpen) {
        DailyReviewBottomSheet(
            viewModel = viewModel,
            reviewDate = reviewDate,
            onDismiss = { viewModel.closeDailyReview() }
        )
    }
}
