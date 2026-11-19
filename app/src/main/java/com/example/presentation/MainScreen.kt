package com.example.presentation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.presentation.components.LiquidGlassNavItem
import com.example.presentation.components.ThemedBottomNavBar
import com.example.presentation.screens.AccountSimulatorScreen
import com.example.presentation.screens.BacktestScreen
import com.example.presentation.screens.BotsScreen
import com.example.presentation.screens.CalendarScreen
import com.example.presentation.screens.DailyReviewBottomSheet
import com.example.presentation.screens.MenuScreen
import com.example.presentation.screens.OutlookScreen
import com.example.presentation.screens.ProfileScreen
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
    val context = LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val allTrades by viewModel.trades.collectAsState()
    val showFreshInstallRestore by viewModel.showFreshInstallRestorePrompt.collectAsState()
    val vaultNotice by viewModel.vaultNoticeMessage.collectAsState()
    val isVaultLoading by viewModel.isVaultLoading.collectAsState()
    val vaultLoadingMessage by viewModel.vaultLoadingMessage.collectAsState()

    val vaultFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreVaultFromUri(uri) { summary ->
                Toast.makeText(
                    context,
                    summary.toUserMessage(),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

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
    val backtestState by backtestViewModel.uiState.collectAsState()

    val isSecondaryScreen = currentScreen in listOf(
        AppScreen.STRATEGIES,
        AppScreen.STRATEGY_GUIDE,
        AppScreen.BOTS,
        AppScreen.OUTLOOK,
        AppScreen.BACKTEST,
        AppScreen.BACKTEST_ACCOUNT,
        AppScreen.PROFILE,
        AppScreen.RISK_CALC,
        AppScreen.CHARTS,
        AppScreen.ECONOMIC_CALENDAR,
        AppScreen.PSYCHOLOGY,
        AppScreen.SETTINGS
    )

    val isReplayImmersive = !isVaultLoading && currentScreen == AppScreen.BACKTEST && backtestState.selectedTab == 1

    if (isVaultLoading) {
        VaultLoadingScreen(message = vaultLoadingMessage)
        return
    }

    // Android BackHandler: Exit immersive Replay tab back to Run tab first, otherwise follow screen hierarchy
    BackHandler(enabled = currentScreen != AppScreen.CALENDAR) {
        when {
            currentScreen == AppScreen.BACKTEST && backtestState.selectedTab == 1 -> backtestViewModel.selectTab(0)
            currentScreen == AppScreen.BACKTEST_ACCOUNT -> viewModel.navigateTo(AppScreen.BACKTEST)
            currentScreen == AppScreen.STRATEGY_GUIDE -> viewModel.navigateTo(AppScreen.STRATEGIES)
            isSecondaryScreen -> viewModel.navigateTo(AppScreen.MENU)
            else -> viewModel.navigateTo(AppScreen.CALENDAR)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (isReplayImmersive) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            if (!isReplayImmersive) {
            TopAppBar(
                expandedHeight = 76.dp,
                navigationIcon = {
                    if (isSecondaryScreen) {
                        IconButton(
                            onClick = {
                                when (currentScreen) {
                                    AppScreen.BACKTEST_ACCOUNT -> viewModel.navigateTo(AppScreen.BACKTEST)
                                    AppScreen.STRATEGY_GUIDE -> viewModel.navigateTo(AppScreen.STRATEGIES)
                                    else -> viewModel.navigateTo(AppScreen.MENU)
                                }
                            },
                            modifier = Modifier.testTag("top_bar_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = when (currentScreen) {
                                    AppScreen.BACKTEST_ACCOUNT -> "Back to Backtester"
                                    AppScreen.STRATEGY_GUIDE -> "Back to Strategies"
                                    else -> "Back to Menu"
                                },
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
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = when (currentScreen) {
                                    AppScreen.CALENDAR -> "TradePulse"
                                    AppScreen.TRADES -> "Trade Journal"
                                    AppScreen.STATS -> "Performance Analytics"
                                    AppScreen.MENU -> "Menu & Tools"
                                    AppScreen.STRATEGIES -> "Strategies & Rules"
                                    AppScreen.STRATEGY_GUIDE -> "Strategy Guide"
                                    AppScreen.BOTS -> "MQL5 Bot Library"
                                    AppScreen.OUTLOOK -> "Gold Outlook"
                                    AppScreen.BACKTEST -> "Backtester"
                                    AppScreen.BACKTEST_ACCOUNT -> "Account Profit Simulator"
                                    AppScreen.PROFILE -> "Trading Profile"
                                    AppScreen.RISK_CALC -> "Risk & Lot Calculator"
                                    AppScreen.CHARTS -> "Trading Charts"
                                    AppScreen.ECONOMIC_CALENDAR -> "Economic Calendar"
                                    AppScreen.PSYCHOLOGY -> "Trading Psychology"
                                    AppScreen.SETTINGS -> "Settings & Storage"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = 16.sp,
                                lineHeight = 19.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                                    AppScreen.OUTLOOK -> "XAUUSD news & market outlook"
                                    AppScreen.BACKTEST -> "Python strategy lab"
                                    AppScreen.BACKTEST_ACCOUNT -> "Fixed-lot balance, leverage & cashflows"
                                    AppScreen.PROFILE -> "Custom accounts, default lot & Vault sync"
                                    AppScreen.RISK_CALC -> "WLTrade SyntX, Gold & FX sizing"
                                    AppScreen.CHARTS -> "TradingView charts for live markets"
                                    AppScreen.ECONOMIC_CALENDAR -> "News by impact, currency and date"
                                    AppScreen.PSYCHOLOGY -> "Emotions, discipline and execution patterns"
                                    AppScreen.SETTINGS -> "Risk limits, backup & API keys"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 9.sp,
                                    lineHeight = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    val isBacktestActive = currentScreen == AppScreen.BACKTEST
                    val isOutlookActive = currentScreen == AppScreen.OUTLOOK
                    val goldColor = com.example.ui.theme.AmberGold

                    // 1-Tap Quick Shortcut: BigTester
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .testTag("topbar_shortcut_backtest")
                    ) {
                        Surface(
                            onClick = { viewModel.navigateTo(AppScreen.BACKTEST) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isBacktestActive) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isBacktestActive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QueryStats,
                                    contentDescription = "Open Strategy Backtester",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Tester",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isBacktestActive) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // 1-Tap Quick Shortcut: Gold Outlook
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .testTag("topbar_shortcut_outlook")
                    ) {
                        Surface(
                            onClick = { viewModel.navigateTo(AppScreen.OUTLOOK) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isOutlookActive) {
                                goldColor.copy(alpha = 0.24f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isOutlookActive) {
                                    goldColor
                                } else {
                                    goldColor.copy(alpha = 0.35f)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = "Open Gold News Outlook",
                                    tint = goldColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Gold",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOutlookActive) goldColor else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (isDailyLossLimitHit) {
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Cap",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    val isProfileActive = currentScreen == AppScreen.PROFILE
                    IconButton(
                        onClick = { viewModel.navigateTo(AppScreen.PROFILE) },
                        modifier = Modifier.testTag("topbar_shortcut_profile")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Open Trading Profile",
                            tint = if (isProfileActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            }
                        )
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
            }
        },
        bottomBar = {
            if (!isReplayImmersive) {
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!isReplayImmersive) {
            vaultNotice?.let { notice ->
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = notice,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearVaultNotice() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss restore notice",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (showFreshInstallRestore && !isSecondaryScreen) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("fresh_install_restore_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Reinstalled? Restore Your Local Vault",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Pick your saved .json Vault file (e.g. Downloads/TradePulse/tradepulse_vault.json) to restore API keys, settings, trades, strategies, and bots.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    vaultFilePickerLauncher.launch(arrayOf("application/json", "*/*"))
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("banner_restore_vault_button")
                            ) {
                                Text("Restore .json Vault", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = { viewModel.dismissFreshInstallRestorePrompt() },
                                modifier = Modifier.testTag("banner_dismiss_restore_button")
                            ) {
                                Text("Not now", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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
                        backtestViewModel = backtestViewModel,
                        tradingViewModel = viewModel,
                        onOpenAccountSimulator = { viewModel.navigateTo(AppScreen.BACKTEST_ACCOUNT) },
                        onOpenProfile = { viewModel.navigateTo(AppScreen.PROFILE) }
                    )
                    AppScreen.BACKTEST_ACCOUNT -> AccountSimulatorScreen(
                        tradingViewModel = viewModel,
                        backtestViewModel = backtestViewModel
                    )
                    AppScreen.PROFILE -> ProfileScreen(viewModel = viewModel)
                    AppScreen.RISK_CALC -> RiskCalculatorScreen(viewModel = viewModel)
                    AppScreen.CHARTS -> TradingViewChartsScreen()
                    AppScreen.ECONOMIC_CALENDAR -> EconomicCalendarScreen(viewModel = viewModel)
                    AppScreen.PSYCHOLOGY -> PsychologyScreen(viewModel = viewModel)
                    AppScreen.SETTINGS -> SettingsScreen(viewModel = viewModel)
                }
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

@Composable
private fun VaultLoadingScreen(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("vault_loading_screen")
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    modifier = Modifier.size(84.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = "Loading TradePulse Vault",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
                Text(
                    text = "TradePulse Vault",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 3.5.dp,
                    modifier = Modifier.size(40.dp)
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Loading vault.json snapshot & encrypted settings...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
