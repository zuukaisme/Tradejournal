package com.example.presentation.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.backtest.BacktestError
import com.example.backtest.BacktestProgressSnapshot
import com.example.backtest.BacktestResult
import com.example.backtest.BacktestTimeframe
import com.example.backtest.BatchStrategyRunResult
import com.example.backtest.ExitReason
import com.example.backtest.SavedCsvDataset
import com.example.backtest.Side
import com.example.backtest.StrategyLiveProgress
import com.example.backtest.Trade
import com.example.backtest.worker.WorkerPoolBacktestCoordinator
import com.example.presentation.BacktestViewModel
import com.example.presentation.TradingViewModel
import com.example.presentation.components.LiquidGlassCard
import com.example.presentation.components.LiquidGlassTabRow
import com.example.ui.theme.AmberGold
import com.example.ui.theme.SlateBreakEven
import java.util.Locale
import kotlin.math.abs
import org.json.JSONObject

@Composable
fun BacktestScreen(
    onOpenStrategies: () -> Unit,
    modifier: Modifier = Modifier,
    backtestViewModel: BacktestViewModel = viewModel(),
    tradingViewModel: TradingViewModel? = null,
    onOpenAccountSimulator: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    val state by backtestViewModel.uiState.collectAsState()
    val settings = tradingViewModel?.settings?.collectAsState()?.value
    val activeProfile = tradingViewModel?.activeAccountProfile?.collectAsState()?.value
    val currentLotSize = settings?.testerLotSize ?: 0.10
    val currentLeverage = settings?.testerLeverage ?: 100
    var showAccountChipEditor by remember { mutableStateOf(false) }

    var strategyMenuExpanded by remember { mutableStateOf(false) }
    var tradeFilter by remember(state.result) { mutableIntStateOf(0) }
    val filteredTrades = remember(state.result?.trades, tradeFilter) {
        state.result?.trades.orEmpty().withIndex().filter { (_, trade) ->
            when (tradeFilter) {
                1 -> trade.r > 1e-6 || (abs(trade.r) <= 1e-6 && trade.points > 1e-6)
                2 -> trade.r < -1e-6 || (abs(trade.r) <= 1e-6 && trade.points < -1e-6)
                3 -> abs(trade.points) <= 1e-6 && abs(trade.r) <= 1e-6
                else -> true
            }
        }
    }

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            backtestViewModel.loadCsvUri(uri)
        }
    }

    val positiveColor = MaterialTheme.colorScheme.primary
    val negativeColor = MaterialTheme.colorScheme.error
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

    Box(modifier = modifier.fillMaxSize()) {
    val isReplayTab = state.selectedTab == 1
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(
                if (isReplayTab) Modifier else Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
    ) {
        if (!isReplayTab) {
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                backgroundColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f),
                testTag = "backtest_screen_hero"
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = positiveColor.copy(alpha = 0.16f),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CandlestickChart,
                                contentDescription = null,
                                tint = positiveColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "BACKTEST LAB",
                            style = MaterialTheme.typography.labelSmall,
                            color = positiveColor,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Strategy testing",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Run, replay and compare strategies",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        color = positiveColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "ENGINE",
                            color = positiveColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val replayLabel = if (state.replaySourceTrades.isEmpty()) "Replay" else "Replay (${state.replaySourceTrades.size})"
            val compareLabel = if (state.savedRuns.isEmpty()) "Compare" else "Compare (${state.savedRuns.size})"
            LiquidGlassTabRow(
                tabs = listOf("Run", replayLabel, "Builder", compareLabel),
                icons = listOf(
                    Icons.Default.PlayArrow,
                    Icons.Default.CandlestickChart,
                    Icons.Default.AutoAwesome,
                    Icons.Default.CompareArrows
                ),
                selectedIndex = state.selectedTab,
                onTabSelected = { backtestViewModel.selectTab(it) }
            )

            Spacer(modifier = Modifier.height(10.dp))
        }

        when (state.selectedTab) {
            1 -> TradeReplayTabContent(
                strategyName = state.replayStrategyName,
                trades = state.replaySourceTrades,
                currentIndex = state.replayTradeIndex,
                cursorGlobalBar = state.replayCursorBar,
                totalDatasetCandles = state.replayTotalCandles,
                skipEmptyGaps = state.replaySkipEmptyGaps,
                window = state.replayWindow,
                isLoading = state.isReplayLoading,
                error = state.replayError,
                outcomeFilter = state.replayOutcomeFilter,
                playbackSpeed = state.replayPlaybackSpeed,
                batchResults = state.batchResults,
                activeBatchStrategyId = state.activeBatchStrategyId,
                onSelectBatchStrategy = { stratId ->
                    backtestViewModel.selectBatchResult(stratId)
                    backtestViewModel.selectTab(1)
                },
                onSelectFilter = backtestViewModel::selectReplayFilter,
                onCycleSpeed = backtestViewModel::cycleReplaySpeed,
                onToggleSkipEmptyGaps = backtestViewModel::toggleReplaySkipEmptyGaps,
                onUpdateCursorBar = backtestViewModel::updateReplayCursorBar,
                onEnsureSliceCovers = backtestViewModel::ensureReplaySliceCovers,
                onSelectTradeIndex = backtestViewModel::selectReplayTradeAt,
                onGoToBacktest = { backtestViewModel.selectTab(0) },
                modifier = Modifier.weight(1f)
            )
            2 -> AiStrategyBuilderContent(
                state = state,
                backtestViewModel = backtestViewModel,
                modifier = Modifier.weight(1f)
            )
            3 -> BacktestCompareContent(
                savedRuns = state.savedRuns,
                runAId = state.compareRunAId,
                runBId = state.compareRunBId,
                onSelectRun = backtestViewModel::selectCompareSlot,
                onSwapRuns = backtestViewModel::swapCompareSlots,
                onDeleteRun = backtestViewModel::deleteSavedRun,
                modifier = Modifier.weight(1f)
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
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

                        if (state.isImportingCsv) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Importing large CSV", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (state.importTotalBytes > 0L) {
                                            "${(state.importProgress * 100).toInt()}%"
                                        } else {
                                            "Reading..."
                                        },
                                        color = positiveColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { state.importProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                val readMb = state.importBytesRead / (1024.0 * 1024.0)
                                val totalMb = state.importTotalBytes / (1024.0 * 1024.0)
                                Text(
                                    if (state.importTotalBytes > 0L) {
                                        String.format(Locale.US, "%.1f MB of %.1f MB", readMb, totalMb)
                                    } else {
                                        String.format(Locale.US, "%.1f MB processed", readMb)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = backtestViewModel::cancelCsvImport,
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Cancel import")
                                }
                            }
                        }

                        if (state.fileName != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val tfSummary = state.detectedTimeframeLabel.ifBlank { "Unknown" }
                            Text(
                                text = "${state.estimatedRows} rows • TF: $tfSummary • ${state.detectedEncoding} • ${state.detectedDelimiter}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = positiveColor,
                                modifier = Modifier.testTag("detected_timeframe_label")
                            )
                        }

                        if (state.savedCsvs.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            SavedCsvLibraryStrip(
                                datasets = state.savedCsvs,
                                activeId = state.activeSavedCsvId,
                                selectedIds = state.selectedMarketIds,
                                enabled = !state.isLoading && !state.isImportingCsv && !state.isMultiMarketRunning,
                                onSelect = backtestViewModel::selectSavedCsv,
                                onToggleMarket = backtestViewModel::toggleMarketSelection,
                                onRenameMarket = backtestViewModel::updateSavedCsvMarketName,
                                onDelete = backtestViewModel::deleteSavedCsv
                            )
                        }

                        if (state.fileName != null) {

                            if (state.hasCsvBytes && state.availableTimeframes.size > 1) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Candle Timeframe",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(state.availableTimeframes) { tf ->
                                        val isSelected = tf == state.selectedTimeframe
                                        val chipText = if (tf == BacktestTimeframe.NATIVE) {
                                            val shortSrc = state.detectedTimeframeLabel.substringBefore(" ")
                                                .ifBlank { "Native" }
                                            "$shortSrc (Auto)"
                                        } else {
                                            tf.label
                                        }
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { backtestViewModel.selectTimeframe(tf) },
                                            label = {
                                                Text(
                                                    text = chipText,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = positiveColor,
                                                selectedLabelColor = onPrimaryColor
                                            ),
                                            modifier = Modifier.testTag("timeframe_chip_${tf.shortLabel}")
                                        )
                                    }
                                }
                                if (state.selectedTimeframe != BacktestTimeframe.NATIVE) {
                                    val srcMin = state.detectedTimeframeMinutes ?: 0
                                    val estBars = if (srcMin > 0 && state.selectedTimeframe.minutes > 0) {
                                        ((state.estimatedRows.toLong() * srcMin) / state.selectedTimeframe.minutes)
                                            .coerceAtLeast(1L)
                                    } else {
                                        state.estimatedRows.toLong()
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Resampling ${state.detectedTimeframeLabel} → ${state.selectedTimeframe.label} (~$estBars OHLCV bars)",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = AmberGold,
                                        modifier = Modifier.testTag("resampling_preview_label")
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Saved Python Strategy Dropdown
                        if (state.availableStrategies.isEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "No saved .py strategies yet",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = onOpenStrategies,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("backtest_go_to_strategies_button"),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Upload .py", fontSize = 12.sp)
                                        }
                                        Button(
                                            onClick = { backtestViewModel.selectTab(2) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("backtest_open_ai_builder_button"),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Build with AI", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Strategy Mode",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = !state.isMultiStrategyMode,
                                        onClick = { backtestViewModel.toggleMultiStrategyMode(false) },
                                        label = {
                                            Text(
                                                text = "Single",
                                                fontSize = 11.sp,
                                                fontWeight = if (!state.isMultiStrategyMode) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = positiveColor,
                                            selectedLabelColor = onPrimaryColor
                                        ),
                                        modifier = Modifier.testTag("strategy_mode_single")
                                    )
                                    FilterChip(
                                        selected = state.isMultiStrategyMode,
                                        onClick = { backtestViewModel.toggleMultiStrategyMode(true) },
                                        label = {
                                            Text(
                                                text = "Multi (${state.selectedStrategyIds.size})",
                                                fontSize = 11.sp,
                                                fontWeight = if (state.isMultiStrategyMode) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = positiveColor,
                                            selectedLabelColor = onPrimaryColor
                                        ),
                                        modifier = Modifier.testTag("strategy_mode_multi")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (!state.isMultiStrategyMode) {
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
                                                text = state.selectedStrategy?.name ?: "Select Strategy",
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
                                                            text = if (strat.className.isBlank()) {
                                                                "Auto-detect Strategy subclass"
                                                            } else {
                                                                "Class: ${strat.className}"
                                                            },
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
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${state.selectedStrategyIds.size} of ${state.availableStrategies.size} selected",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = positiveColor,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                TextButton(
                                                    onClick = backtestViewModel::selectAllStrategies,
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.testTag("multi_strategy_select_all")
                                                ) {
                                                    Text("All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                                TextButton(
                                                    onClick = backtestViewModel::clearStrategySelection,
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.testTag("multi_strategy_clear")
                                                ) {
                                                    Text("Clear", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 200.dp)
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            state.availableStrategies.forEach { strat ->
                                                val checked = strat.id in state.selectedStrategyIds
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 48.dp)
                                                        .clickable { backtestViewModel.toggleStrategySelection(strat.id) }
                                                        .padding(vertical = 2.dp)
                                                        .testTag("multi_strategy_checkbox_${strat.id}"),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Checkbox(
                                                        checked = checked,
                                                        onCheckedChange = { backtestViewModel.toggleStrategySelection(strat.id) }
                                                    )
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = strat.name,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = strat.className.ifBlank { "Auto-detect Strategy subclass" },
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (!state.isMultiStrategyMode && state.hasCsvBytes) {
                            Spacer(modifier = Modifier.height(10.dp))
                            val isLargeCsv = state.csvFileSizeBytes >= WorkerPoolBacktestCoordinator.LARGE_DATASET_TIME_SPLIT_BYTES
                            val sizeMb = (state.csvFileSizeBytes / (1024L * 1024L)).coerceAtLeast(1L)
                            Surface(
                                color = if (isLargeCsv && state.timeSplitLargeCsv) {
                                    positiveColor.copy(alpha = 0.10f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Time-Split Workers (≥50 MB)",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Surface(
                                                color = if (isLargeCsv) positiveColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(5.dp)
                                            ) {
                                                Text(
                                                    text = if (isLargeCsv) "${sizeMb} MB • Active" else "Standby (<50 MB)",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isLargeCsv) positiveColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Splits 2015→2026 across cores (3,000-bar indicator warm-up per chunk)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Switch(
                                        checked = state.timeSplitLargeCsv,
                                        onCheckedChange = backtestViewModel::setTimeSplitLargeCsv,
                                        modifier = Modifier.testTag("time_split_workers_switch")
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = state.workerCountInput,
                                onValueChange = backtestViewModel::updateWorkerCount,
                                label = { Text("Workers / strategy", fontSize = 12.sp) },
                                placeholder = { Text("4") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("backtest_worker_count_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Column(modifier = Modifier.weight(2f)) {
                                Text(
                                    text = if (state.isMultiStrategyMode && state.selectedStrategyIds.size > 1) {
                                        "${state.selectedStrategyIds.size} strategies × ${state.workerCountInput.ifBlank { "4" }} = ${runCatching { (state.selectedStrategyIds.size * (state.workerCountInput.toIntOrNull() ?: 4)).coerceAtMost(WorkerPoolBacktestCoordinator.MAX_WORKER_PROCESSES) }.getOrDefault(0)} worker processes"
                                    } else {
                                        "Large CSVs: each strategy gets this many workers; smaller runs use one per strategy"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Up to ${WorkerPoolBacktestCoordinator.MAX_WORKER_PROCESSES} concurrent worker processes",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Config inputs: maxStopPoints & pointSize
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = state.maxStopPointsInput,
                                onValueChange = { backtestViewModel.updateMaxStopPoints(it) },
                                label = { Text("Max Stop (pts)", fontSize = 12.sp) },
                                placeholder = { Text("0 = No cap") },
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

                        if (tradingViewModel != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                item {
                                    FilterChip(
                                        selected = true,
                                        onClick = { showAccountChipEditor = true },
                                        label = {
                                            Text(
                                                text = String.format(Locale.US, "Lot: %.2f", currentLotSize),
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                            selectedLabelColor = positiveColor
                                        ),
                                        modifier = Modifier.testTag("tester_chip_lot_size")
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = true,
                                        onClick = { showAccountChipEditor = true },
                                        label = {
                                            Text(
                                                text = "Lev: 1:$currentLeverage",
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                            selectedLabelColor = AmberGold
                                        ),
                                        modifier = Modifier.testTag("tester_chip_leverage")
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = false,
                                        onClick = onOpenProfile,
                                        label = {
                                            Text(
                                                text = activeProfile?.name ?: "Default Profile",
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        modifier = Modifier.testTag("tester_chip_profile")
                                    )
                                }
                                if (state.result != null) {
                                    item {
                                        FilterChip(
                                            selected = true,
                                            onClick = onOpenAccountSimulator,
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.AccountBalanceWallet,
                                                    contentDescription = "Open Account Profit Simulator",
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            },
                                            label = {
                                                Text("Account $", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = positiveColor,
                                                selectedLabelColor = onPrimaryColor,
                                                selectedLeadingIconColor = onPrimaryColor
                                            ),
                                            modifier = Modifier.testTag("tester_chip_account_results")
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val canRun = !state.isLoading && !state.isQueueRunning && !state.isImportingCsv && state.hasCsvBytes &&
                            (if (state.isMultiStrategyMode) state.selectedStrategyIds.isNotEmpty() else state.selectedStrategy != null)
                        val multiCount = state.selectedStrategyIds.size
                        val totalLiveStrategies = state.liveProgress?.totalStrategies ?: 0
                        val runButtonText = when {
                            state.isLoading && totalLiveStrategies > 1 ->
                                "Running $totalLiveStrategies Strategies..."
                            state.isLoading -> "Running Python Engine..."
                            state.isMultiStrategyMode && multiCount > 1 -> "Run $multiCount Strategies Together"
                            else -> "Run Backtest"
                        }

                        // Run Button + Loading state
                        Button(
                            onClick = { backtestViewModel.run() },
                            enabled = canRun,
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
                                Text(runButtonText, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(runButtonText, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        var showOptimizer by remember { mutableStateOf(state.isOptimizing || state.optimizerCombinations > 0) }
                        var showQueue by remember { mutableStateOf(state.isQueueRunning || state.backtestQueue.isNotEmpty()) }

                        // Collapsible Parameter Optimizer Card
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showOptimizer = !showOptimizer }
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Tune, contentDescription = null, tint = positiveColor, modifier = Modifier.size(18.dp))
                                        Column {
                                            Text("Parameter Optimizer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = if (state.optimizerCombinations > 0) "${state.optimizerCombinations} combos tested" else "Sweep one public parameter",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = if (showOptimizer) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (showOptimizer) "Collapse Optimizer" else "Expand Optimizer"
                                    )
                                }

                                AnimatedVisibility(visible = showOptimizer) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                        OutlinedTextField(
                                            value = state.optimizerParameterInput,
                                            onValueChange = backtestViewModel::updateOptimizerParameter,
                                            label = { Text("Parameter name") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = state.optimizerMinInput,
                                                onValueChange = backtestViewModel::updateOptimizerMin,
                                                label = { Text("Min") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            OutlinedTextField(
                                                value = state.optimizerMaxInput,
                                                onValueChange = backtestViewModel::updateOptimizerMax,
                                                label = { Text("Max") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            OutlinedTextField(
                                                value = state.optimizerStepInput,
                                                onValueChange = backtestViewModel::updateOptimizerStep,
                                                label = { Text("Step") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                        }
                                        Button(
                                            onClick = backtestViewModel::runOptimizer,
                                            enabled = state.hasCsvBytes && state.selectedStrategy != null && !state.isOptimizing && !state.isLoading && !state.isQueueRunning,
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            if (state.isOptimizing) {
                                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Optimizing parameter grid...")
                                            } else {
                                                Text("Run Optimizer", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        state.optimizerError?.let {
                                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                        }
                                        if (state.optimizerCombinations > 0) {
                                            Text(
                                                "${state.optimizerCombinations} combinations tested • ranked by Net R, PF & DD",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            state.optimizerResults.take(10).forEach { result ->
                                                val params = result.optJSONObject("parameters")
                                                Surface(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text("#${result.optInt("rank")} • ${params?.toString() ?: ""}", fontWeight = FontWeight.SemiBold)
                                                            Text("${result.optInt("totalTrades")} trades • PF ${result.optString("profitFactor")} • DD ${result.optString("maxDrawdownR")}R", style = MaterialTheme.typography.bodySmall)
                                                        }
                                                        Text("${result.optString("netR")}R", fontWeight = FontWeight.Bold, color = positiveColor)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Collapsible Batch Queue Card
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showQueue = !showQueue }
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Queue, contentDescription = null, tint = AmberGold, modifier = Modifier.size(18.dp))
                                        Column {
                                            Text("Backtest Queue", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = if (state.backtestQueue.isEmpty()) "Sequential job runner" else "${state.backtestQueue.size} jobs queued",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = if (showQueue) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (showQueue) "Collapse Queue" else "Expand Queue"
                                    )
                                }

                                AnimatedVisibility(visible = showQueue) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                        Text(
                                            "Queue strategies and process them one at a time. Pause prevents the next job from starting.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            OutlinedButton(
                                                onClick = backtestViewModel::enqueueSelectedStrategies,
                                                enabled = state.hasCsvBytes && !state.isLoading,
                                                modifier = Modifier.weight(1f).height(48.dp)
                                            ) { Text("Add selected") }
                                            Button(
                                                onClick = backtestViewModel::startQueue,
                                                enabled = state.backtestQueue.any { it.status == "Queued" } && !state.isQueueRunning && !state.isLoading,
                                                modifier = Modifier.weight(1f).height(48.dp)
                                            ) { Text("Start queue") }
                                        }
                                        if (state.isQueueRunning) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = if (state.isQueuePaused) backtestViewModel::resumeQueue else backtestViewModel::pauseQueue,
                                                    modifier = Modifier.weight(1f).height(48.dp)
                                                ) { Text(if (state.isQueuePaused) "Resume" else "Pause") }
                                                OutlinedButton(
                                                    onClick = backtestViewModel::clearFinishedQueueItems,
                                                    modifier = Modifier.weight(1f).height(48.dp)
                                                ) { Text("Clear finished") }
                                            }
                                        } else if (state.backtestQueue.any { it.status != "Queued" }) {
                                            TextButton(onClick = backtestViewModel::clearFinishedQueueItems) { Text("Clear finished jobs") }
                                        }
                                        state.backtestQueue.forEach { queued ->
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(queued.strategyName, fontWeight = FontWeight.SemiBold)
                                                        Text(queued.status, style = MaterialTheme.typography.bodySmall, color = when {
                                                            queued.status == "Completed" -> positiveColor
                                                            queued.status == "Failed" -> negativeColor
                                                            queued.status == "Running" -> AmberGold
                                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                        })
                                                    }
                                                    TextButton(onClick = { backtestViewModel.removeQueuedItem(queued.id) }) {
                                                        Text(if (queued.status == "Running") "Cancel" else "Remove")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Live Running Progress Card
                if (state.isLoading) {
                    item {
                        BacktestLiveProgressCard(
                            progress = state.liveProgress ?: BacktestProgressSnapshot(totalCandles = state.estimatedRows)
                        )
                    }
                }

                // Expandable Python / CSV Error Card
                state.backtestError?.let { err ->
                    item {
                        BacktestErrorCard(error = err)
                    }
                }

                if (state.marketPerformances.isNotEmpty()) {
                    item { MarketPerformanceCard(performances = state.marketPerformances) }
                }

                if (state.batchResults.isNotEmpty()) {
                    item {
                        BatchLeaderboardCard(
                            batchResults = state.batchResults,
                            activeStrategyId = state.activeBatchStrategyId,
                            onSelectStrategy = backtestViewModel::selectBatchResult
                        )
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

            state.lastRunTimeframeLabel?.let { tfLabel ->
                item {
                    val barText = state.lastRunCandleCount?.let { " • $it bars" } ?: ""
                    Surface(
                        color = positiveColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = positiveColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Tested on $tfLabel$barText",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = positiveColor,
                                modifier = Modifier.testTag("tested_timeframe_banner")
                            )
                        }
                    }
                }
            }

            // Row of 4 Summary Stat Cards
            item {
                BacktestSummaryCardsRow(result = result)
            }

            item {
                BacktestTradeProgressCard(result = result)
            }

            item {
                BacktestResearchCard(result = result)
            }

            item {
                state.runSavedFeedback?.let { feedback ->
                    Surface(
                        color = positiveColor.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("backtest_auto_saved_feedback")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = feedback,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (feedback.startsWith("Could not")) negativeColor else positiveColor,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { backtestViewModel.selectTab(3) }) {
                                Text("View Compare", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Interactive Equity Growth & Drawdown Curve Card
            item {
                LiquidGlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Equity Growth Curve",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Drag chart to inspect trade progression",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val netPts = result.summary.netPoints
                        Text(
                            text = "${formatSignedPoints(netPts)} pts (${formatSignedR(result.summary.netR)})",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (netPts >= 0.0) positiveColor else negativeColor
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    BacktestEquityCurveCanvas(
                        equityCurve = result.equityCurve,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Points Section (avgLossPoints and largestLossPoints are already signed in backtest.py)
            item {
                LiquidGlassCard {
                    Text(
                        text = "Points Breakdown",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MetricRow(
                        "Entered / Won / Lost / BE",
                        "${result.summary.enteredTrades} / ${result.summary.wins} / ${result.summary.losses} / ${result.summary.breakEvens}"
                    )
                    MetricRow("Avg Win", formatSignedPoints(result.points.avgWinPoints), positiveColor)
                    MetricRow("Avg Loss", formatSignedPoints(result.points.avgLossPoints), negativeColor)
                    MetricRow("Largest Win", formatSignedPoints(result.points.largestWinPoints), positiveColor)
                    MetricRow("Largest Loss", formatSignedPoints(result.points.largestLossPoints), negativeColor)
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

            // Trades Header & Outcome Filter Chips
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Trades (${result.trades.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (result.trades.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (tradingViewModel != null) {
                                    OutlinedButton(
                                        onClick = onOpenAccountSimulator,
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("open_account_simulator_button"),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccountBalanceWallet,
                                            contentDescription = "Open Account & Profit Screen",
                                            tint = positiveColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text("Account $", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        val firstIdx = filteredTrades.firstOrNull()?.index ?: 0
                                        backtestViewModel.openTradeReplay(firstIdx)
                                    },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("open_trade_replay_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CandlestickChart,
                                        contentDescription = "Replay trades on chart",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Replay", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    if (result.trades.isNotEmpty()) {
                        val filterOptions = listOf(
                            "All (${result.trades.size})",
                            "Won (${result.summary.wins})",
                            "Lost (${result.summary.losses})",
                            "BE (${result.summary.breakEvens})"
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(filterOptions) { idx, label ->
                                val selected = tradeFilter == idx
                                FilterChip(
                                    selected = selected,
                                    onClick = { tradeFilter = idx },
                                    label = {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    modifier = Modifier.testTag("backtest_trade_filter_$idx")
                                )
                            }
                        }
                    }

                    if (result.trades.isEmpty()) {
                        LiquidGlassCard {
                            Text(
                                text = "No closed trades in this backtest run.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (filteredTrades.isEmpty()) {
                        LiquidGlassCard {
                            Text(
                                text = "No trades match this outcome filter.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(items = filteredTrades, key = { it.index }) { indexedTrade ->
                Column(modifier = Modifier.padding(bottom = 10.dp)) {
                    BacktestTradeCard(
                        index = indexedTrade.index + 1,
                        trade = indexedTrade.value,
                        onReplay = { backtestViewModel.openTradeReplay(indexedTrade.index) }
                    )
                }
            }
        } else if (!state.isLoading && state.backtestError == null && state.batchResults.isEmpty()) {
            item {
                LiquidGlassCard {
                    Text(
                        text = "Ready to Backtest",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pick an MT5 tab-separated export or standard comma/semicolon CSV, choose one or more Python (.py) strategies, optionally set maxStopPoints and pointSize, and tap Run Backtest.",
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
    }

    if (state.isReplayOpen && state.replaySourceTrades.isNotEmpty()) {
        BacktestReplayScreen(
            strategyName = state.replayStrategyName,
            trades = state.replaySourceTrades,
            currentIndex = state.replayTradeIndex,
            window = state.replayWindow,
            isLoading = state.isReplayLoading,
            error = state.replayError,
            onSelectTradeIndex = backtestViewModel::selectReplayTradeAt,
            onClose = backtestViewModel::closeTradeReplay
        )
    }

    if (showAccountChipEditor && tradingViewModel != null) {
        var lotDraft by remember(currentLotSize) {
            mutableStateOf(String.format(Locale.US, "%.2f", currentLotSize))
        }
        var leverageDraft by remember(currentLeverage) {
            mutableStateOf(currentLeverage.toString())
        }
        val quickLots = listOf(0.01, 0.05, 0.10, 0.25, 0.50, 1.00)
        val quickLevs = listOf(30, 50, 100, 200, 500, 1000)

        AlertDialog(
            onDismissRequest = { showAccountChipEditor = false },
            title = {
                Text("Trade Tester Fixed Lot & Leverage", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Every change automatically syncs into your Trading Vault.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = lotDraft,
                        onValueChange = { lotDraft = it },
                        label = { Text("Fixed Lot Size") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_tester_lot_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(quickLots) { lot ->
                            val lbl = String.format(Locale.US, "%.2f", lot)
                            FilterChip(
                                selected = lotDraft.toDoubleOrNull() == lot,
                                onClick = { lotDraft = lbl },
                                label = { Text(lbl, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = leverageDraft,
                        onValueChange = { leverageDraft = it.filter(Char::isDigit) },
                        label = { Text("Leverage (1:X)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_tester_leverage_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(quickLevs) { lev ->
                            FilterChip(
                                selected = leverageDraft.toIntOrNull() == lev,
                                onClick = { leverageDraft = lev.toString() },
                                label = { Text("1:$lev", fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            showAccountChipEditor = false
                            onOpenProfile()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Edit Custom Account Profiles →", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        lotDraft.toDoubleOrNull()?.takeIf { it > 0.0 }?.let {
                            tradingViewModel.updateTesterLotSize(it)
                        }
                        leverageDraft.toIntOrNull()?.takeIf { it >= 1 }?.let {
                            tradingViewModel.updateTesterLeverage(it)
                        }
                        showAccountChipEditor = false
                    },
                    modifier = Modifier.testTag("dialog_tester_save_button")
                ) {
                    Text("Save to Vault")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAccountChipEditor = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    }
}

@Composable
private fun BacktestErrorCard(error: BacktestError) {
    val negativeColor = MaterialTheme.colorScheme.error
    var detailsExpanded by remember(error) { mutableStateOf(false) }

    Surface(
        color = negativeColor.copy(alpha = 0.14f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("backtest_error_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Backtest Error",
                    tint = negativeColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = error.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = negativeColor,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }

            if (error.details.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = { detailsExpanded = !detailsExpanded },
                    colors = ButtonDefaults.textButtonColors(contentColor = negativeColor),
                    modifier = Modifier.testTag("backtest_error_details_toggle")
                ) {
                    Icon(
                        imageVector = if (detailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (detailsExpanded) "Hide details" else "Show details",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                AnimatedVisibility(visible = detailsExpanded) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backtest_error_details_text")
                    ) {
                        Text(
                            text = error.details,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BacktestLiveProgressCard(progress: BacktestProgressSnapshot) {
    val positiveColor = MaterialTheme.colorScheme.primary
    val negativeColor = MaterialTheme.colorScheme.error
    val neutralColor = SlateBreakEven
    val pctComplete = (progress.candleProgressFraction * 100f).toInt().coerceIn(0, 100)

    LiquidGlassCard(testTag = "backtest_live_progress_card") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Live Backtest Progress",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (progress.totalStrategies > 1) {
                    Text(
                        text = "Parallel workers active (${progress.strategyIndex}/${progress.totalStrategies} running)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = positiveColor,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val barLabel = if (progress.totalCandles > 0) {
                    "${progress.candlesProcessed} / ${progress.totalCandles} candles"
                } else {
                    "Preparing market candles..."
                }
                Text(
                    text = if (progress.currentTime.isNotBlank()) "$barLabel • ${progress.currentTime}" else barLabel,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$pctComplete%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = positiveColor,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (progress.totalCandles > 0) {
            LinearProgressIndicator(
                progress = { progress.candleProgressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = positiveColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = positiveColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutcomeStackedBar(
            wins = progress.wins,
            breakEvens = progress.breakEvens,
            losses = progress.losses
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProgressTile(
                label = "Entered",
                value = progress.enteredTrades.toString(),
                subValue = "${progress.closedTrades} closed",
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            ProgressTile(
                label = "Won",
                value = progress.wins.toString(),
                subValue = "${formatOneDec(progress.winRatePct)}%",
                color = positiveColor,
                modifier = Modifier.weight(1f)
            )
            ProgressTile(
                label = "Lost",
                value = progress.losses.toString(),
                subValue = "${formatOneDec(progress.lossRatePct)}%",
                color = negativeColor,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProgressTile(
                label = "Broke Even",
                value = progress.breakEvens.toString(),
                subValue = "${formatOneDec(progress.breakEvenRatePct)}%",
                color = neutralColor,
                modifier = Modifier.weight(1f)
            )
            ProgressTile(
                label = "Open Now",
                value = progress.openTrades.toString(),
                subValue = if (progress.openTrades > 0) "Active" else "Flat",
                color = if (progress.openTrades > 0) AmberGold else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            ProgressTile(
                label = "Running Net",
                value = "${formatSignedPoints(progress.netPoints)}p",
                subValue = formatSignedR(progress.netR),
                color = if (progress.netPoints >= 0.0) positiveColor else negativeColor,
                modifier = Modifier.weight(1f)
            )
        }

        val failedTasks = progress.strategyStatuses.filter { it.status == "error" }
        if (failedTasks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("live_strategy_statuses_list")
            ) {
                failedTasks.forEach { failedTask ->
                    LiveStrategyStatusRow(item = failedTask)
                }
            }
        }
    }
}

@Composable
private fun LiveStrategyStatusRow(item: StrategyLiveProgress) {
    val positiveColor = MaterialTheme.colorScheme.primary
    val negativeColor = MaterialTheme.colorScheme.error
    val isError = item.status == "error"
    val isQueued = item.status == "queued"
    val isDone = item.status == "done"
    val pnlColor = if (item.netPoints >= 0.0) positiveColor else negativeColor
    val pct = (item.progressFraction * 100f).toInt().coerceIn(0, 100)

    Surface(
        color = if (isError) {
            negativeColor.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_strategy_status_${item.strategyId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (item.workerLabel.isNotBlank()) {
                            Surface(
                                color = if (isDone) {
                                    positiveColor.copy(alpha = 0.2f)
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                },
                                shape = RoundedCornerShape(5.dp)
                            ) {
                                Text(
                                    text = item.workerLabel,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isDone) positiveColor else MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        } else if (isQueued) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(5.dp)
                            ) {
                                Text(
                                    text = "QUEUE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    when {
                        isError -> {
                            Text(
                                text = item.error.ifBlank { "Execution failed" },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = negativeColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        isQueued -> {
                            Text(
                                text = "Waiting for next free worker...",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            val openNote = if (item.openTrades > 0) " • ${item.openTrades} open" else ""
                            val pctNote = if (item.totalCandles > 0) " • $pct%" else ""
                            Text(
                                text = "${item.closedTrades}t • ${item.wins}W/${item.losses}L/${item.breakEvens}BE$openNote$pctNote",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                when {
                    isError -> {
                        Text(
                            text = "ERROR",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = negativeColor,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    isQueued -> {
                        Text(
                            text = "QUEUED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    else -> {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = formatSignedR(item.netR),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = pnlColor,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${formatSignedPoints(item.netPoints)}p",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = pnlColor,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
            if (!isError && !isQueued && item.totalCandles > 0) {
                LinearProgressIndicator(
                    progress = { item.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = if (isDone) positiveColor else MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}


@Composable
private fun BacktestResearchCard(result: BacktestResult) {
    val research = result.research
    val confidence = research.confidence
    val monteCarlo = research.monteCarlo
    val breakdown = research.performanceBreakdown
    val positiveColor = MaterialTheme.colorScheme.primary
    val negativeColor = MaterialTheme.colorScheme.error

    LiquidGlassCard(testTag = "backtest_research_card") {
        Text("Research & Reliability", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Confidence score", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${confidence.score}/100 • ${confidence.label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${confidence.trades} trades", style = MaterialTheme.typography.labelSmall)
                Text("Avg ${formatSignedR(confidence.averageR)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(confidence.note.ifBlank { "Reliability score is unavailable for this result." }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
        Text("Monte Carlo • ${monteCarlo.simulations} simulations", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        val ending = monteCarlo.endingNetR
        val drawdown = monteCarlo.maxDrawdownR
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ResearchMetric("Ending R P05", ending["p05"] ?: 0.0, negativeColor)
            ResearchMetric("Median", ending["p50"] ?: 0.0, positiveColor)
            ResearchMetric("Ending R P95", ending["p95"] ?: 0.0, positiveColor)
        }
        Text("Max drawdown R • P05 ${formatTwoDec(drawdown["p05"] ?: 0.0)}  |  Median ${formatTwoDec(drawdown["p50"] ?: 0.0)}  |  P95 ${formatTwoDec(drawdown["p95"] ?: 0.0)}", style = MaterialTheme.typography.bodySmall)
        Text("Chance of negative ending: ${formatOneDec(monteCarlo.probabilityOfNegativeEndingPct)}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
        Text("Performance breakdown", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        ResearchBucketGroup("Sessions", breakdown.bySession)
        ResearchBucketGroup("Weekdays", breakdown.byWeekday)
        ResearchBucketGroup("Months", breakdown.byMonth)
        if (breakdown.unclassifiedTrades > 0) {
            Text("${breakdown.unclassifiedTrades} trades had unrecognized timestamps.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ResearchMetric(label: String, value: Double, valueColor: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatSignedR(value), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun ResearchBucketGroup(title: String, rows: List<com.example.backtest.ResearchBucket>) {
    if (rows.isEmpty()) return
    var expanded by remember(title, rows) { mutableStateOf(title == "Sessions") }
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)) {
            Text("$title (${rows.size})", fontWeight = FontWeight.SemiBold)
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
        }
        if (expanded) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${row.label} • ${row.trades} trades", style = MaterialTheme.typography.bodySmall)
                    Text("${formatSignedR(row.netR)} • WR ${formatOneDec(row.winRatePct)}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun BacktestTradeProgressCard(result: BacktestResult) {
    val summary = result.summary
    val totalClosed = summary.totalTrades
    val positiveColor = MaterialTheme.colorScheme.primary
    val negativeColor = MaterialTheme.colorScheme.error
    val neutralColor = SlateBreakEven

    LiquidGlassCard(testTag = "backtest_trade_progress_card") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Trade Progress",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                val openSuffix = if (summary.openTrades > 0) " • ${summary.openTrades} open" else ""
                val candlePrefix = if (summary.totalCandles > 0) "${summary.totalCandles} bars • " else ""
                Text(
                    text = "$candlePrefix${summary.enteredTrades} entered • $totalClosed closed$openSuffix",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${formatOneDec(summary.winRatePct)}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (summary.winRatePct >= 50.0) positiveColor else negativeColor
                )
                Text(
                    text = "${formatSignedPoints(summary.expectancyPoints)} pts/trade",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutcomeStackedBar(
            wins = summary.wins,
            breakEvens = summary.breakEvens,
            losses = summary.losses
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Won ${formatOneDec(summary.winRatePct)}%",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = positiveColor,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Broke Even ${formatOneDec(summary.breakEvenRatePct)}%",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = neutralColor,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Lost ${formatOneDec(summary.lossRatePct)}%",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = negativeColor,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProgressTile(
                label = "Entered",
                value = summary.enteredTrades.toString(),
                subValue = "$totalClosed closed",
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .testTag("progress_entered_tile")
            )
            ProgressTile(
                label = "Won",
                value = summary.wins.toString(),
                subValue = "${formatOneDec(summary.winRatePct)}%",
                color = positiveColor,
                modifier = Modifier
                    .weight(1f)
                    .testTag("progress_won_tile")
            )
            ProgressTile(
                label = "Lost",
                value = summary.losses.toString(),
                subValue = "${formatOneDec(summary.lossRatePct)}%",
                color = negativeColor,
                modifier = Modifier
                    .weight(1f)
                    .testTag("progress_lost_tile")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProgressTile(
                label = "Broke Even",
                value = summary.breakEvens.toString(),
                subValue = "${formatOneDec(summary.breakEvenRatePct)}%",
                color = neutralColor,
                modifier = Modifier
                    .weight(1f)
                    .testTag("progress_be_tile")
            )
            ProgressTile(
                label = "Open at End",
                value = summary.openTrades.toString(),
                subValue = if (summary.openTrades > 0) "Unclosed" else "All settled",
                color = if (summary.openTrades > 0) AmberGold else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .testTag("progress_open_tile")
            )
            ProgressTile(
                label = "Skipped",
                value = summary.skippedByStopCap.toString(),
                subValue = "Max stop cap",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .testTag("progress_skipped_tile")
            )
        }

        if (totalClosed > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
            Spacer(modifier = Modifier.height(8.dp))

            val currentStreakLabel = when {
                summary.currentStreak > 0 -> "${summary.currentStreak}W"
                summary.currentStreak < 0 -> "${abs(summary.currentStreak)}L"
                else -> "0"
            }
            val currentStreakColor = when {
                summary.currentStreak > 0 -> positiveColor
                summary.currentStreak < 0 -> negativeColor
                else -> neutralColor
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Exits: ${summary.tpExits} TP • ${summary.slExits} SL • ${summary.breakEvens} BE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Streak: $currentStreakLabel (Best ${result.risk.maxConsecutiveWins}W / ${result.risk.maxConsecutiveLosses}L)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = currentStreakColor
                )
            }

            if (result.trades.isNotEmpty()) {
                val recentTrades = remember(result.trades) { result.trades.takeLast(32) }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (result.trades.size > recentTrades.size) {
                        "Recent Outcome Sequence (Last ${recentTrades.size} of ${result.trades.size})"
                    } else {
                        "Trade Outcome Sequence (${recentTrades.size})"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    recentTrades.forEach { t ->
                        val isBe = abs(t.points) <= 1e-6 && abs(t.r) <= 1e-6
                        val isWin = !isBe && (t.r > 1e-6 || t.points > 1e-6)
                        val pillColor = when {
                            isWin -> positiveColor
                            isBe -> neutralColor
                            else -> negativeColor
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .background(pillColor, RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OutcomeStackedBar(
    wins: Int,
    breakEvens: Int,
    losses: Int
) {
    val total = wins + breakEvens + losses
    val positiveColor = MaterialTheme.colorScheme.primary
    val negativeColor = MaterialTheme.colorScheme.error
    val neutralColor = SlateBreakEven

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (total <= 0) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(10.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            )
        } else {
            if (wins > 0) {
                Box(
                    modifier = Modifier
                        .weight(wins.toFloat() / total)
                        .height(10.dp)
                        .background(positiveColor, RoundedCornerShape(8.dp))
                )
            }
            if (breakEvens > 0) {
                Box(
                    modifier = Modifier
                        .weight(breakEvens.toFloat() / total)
                        .height(10.dp)
                        .background(neutralColor, RoundedCornerShape(8.dp))
                )
            }
            if (losses > 0) {
                Box(
                    modifier = Modifier
                        .weight(losses.toFloat() / total)
                        .height(10.dp)
                        .background(negativeColor, RoundedCornerShape(8.dp))
                )
            }
        }
    }
}

@Composable
private fun ProgressTile(
    label: String,
    value: String,
    subValue: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            Text(
                text = subValue,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BacktestSummaryCardsRow(result: BacktestResult) {
    val summary = result.summary
    val positiveColor = MaterialTheme.colorScheme.primary
    val negativeColor = MaterialTheme.colorScheme.error
    val pf = summary.profitFactor
    val pfText = when {
        pf != null -> formatTwoDec(pf)
        summary.wins > 0 -> "INF"
        else -> "--"
    }
    val pfColor = when {
        pf == null && summary.wins > 0 -> positiveColor
        pf != null && pf >= 1.0 -> positiveColor
        else -> negativeColor
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
            valueColor = pfColor,
            modifier = Modifier.weight(1f)
        )
        CompactStatCard(
            label = "Max DD",
            value = "${formatTwoDec(result.risk.maxDrawdownR)}R",
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
    var showDrawdown by remember { mutableStateOf(false) }
    var scrubFrac by remember(equityCurve, showDrawdown) { mutableStateOf<Float?>(null) }

    val growthSeries = remember(equityCurve) { listOf(0.0) + equityCurve }
    val drawdownSeries = remember(growthSeries) {
        var peak = 0.0
        growthSeries.map { v ->
            if (v > peak) peak = v
            v - peak
        }
    }
    val activeSeries = if (showDrawdown) drawdownSeries else growthSeries
    val finalVal = activeSeries.lastOrNull() ?: 0.0
    val lineColor = if (showDrawdown) MaterialTheme.colorScheme.error else if (finalVal >= 0.0) Color(0xFF089981) else Color(0xFFF23645)

    if (equityCurve.isEmpty()) {
        Box(modifier = modifier.height(140.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "No closed trades to plot",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val minVal = activeSeries.minOrNull() ?: 0.0
    val maxVal = activeSeries.maxOrNull() ?: 0.0
    val inspectIdx = scrubFrac?.let { f ->
        (f * (activeSeries.size - 1).coerceAtLeast(0)).roundToInt().coerceIn(0, activeSeries.lastIndex)
    }
    val axisPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Growth vs Underwater Drawdown Toggle Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    onClick = { showDrawdown = false },
                    color = if (!showDrawdown) Color(0xFF089981).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (!showDrawdown) Color(0xFF089981) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = "Cumulative",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (!showDrawdown) Color(0xFF089981) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
                Surface(
                    onClick = { showDrawdown = true },
                    color = if (showDrawdown) Color(0xFFF23645).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (showDrawdown) Color(0xFFF23645) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = "Drawdown",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (showDrawdown) Color(0xFFF23645) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }
            val displayVal = if (inspectIdx != null) activeSeries[inspectIdx] else finalVal
            val prefix = if (inspectIdx != null) "T#$inspectIdx: " else if (showDrawdown) "End DD: " else "End: "
            Text(
                text = "$prefix${formatSignedPoints(displayVal)}p",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = lineColor
            )
        }

        Surface(
            color = Color(0xFF0A111A),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(185.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .pointerInput(activeSeries) {
                        detectTapGestures { offset ->
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            scrubFrac = if (scrubFrac != null) null else (offset.x / w).coerceIn(0f, 1f)
                        }
                    }
                    .pointerInput(activeSeries) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            scrubFrac = (change.position.x / w).coerceIn(0f, 1f)
                        }
                    }
            ) {
                val axisLabelW = 46.dp.toPx()
                val plotW = (size.width - axisLabelW).coerceAtLeast(40f)
                val rawRange = (maxVal - minVal).takeIf { it > 1e-6 } ?: 1.0
                val pMin = minVal - rawRange * 0.08
                val pMax = maxVal + rawRange * 0.08
                val range = (pMax - pMin).coerceAtLeast(1e-6)
                val padTop = 8.dp.toPx()
                val padBottom = 8.dp.toPx()
                val usableH = (size.height - padTop - padBottom).coerceAtLeast(20f)

                fun yFor(value: Double): Float {
                    val ratio = ((value - pMin) / range).toFloat()
                    return padTop + usableH * (1f - ratio)
                }

                axisPaint.textSize = 9.5.sp.toPx()
                axisPaint.color = android.graphics.Color.argb(180, 148, 163, 184)
                for (step in 0..3) {
                    val f = step / 3f
                    val y = padTop + usableH * f
                    val v = pMax - range * f
                    drawLine(
                        color = Color(0xFF162131),
                        start = Offset(0f, y),
                        end = Offset(plotW, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        String.format(Locale.US, "%.1f", v),
                        plotW + 4.dp.toPx(),
                        y + 3.dp.toPx(),
                        axisPaint
                    )
                }

                if (0.0 in pMin..pMax) {
                    val zeroY = yFor(0.0)
                    drawLine(
                        color = Color(0xFF475569),
                        start = Offset(0f, zeroY),
                        end = Offset(plotW, zeroY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )
                }

                val linePath = Path()
                val fillPath = Path()
                val stepX = if (activeSeries.size > 1) plotW / (activeSeries.size - 1) else plotW

                activeSeries.forEachIndexed { idx, value ->
                    val x = idx * stepX
                    val y = yFor(value)
                    if (idx == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, padTop + usableH)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }

                fillPath.lineTo((activeSeries.size - 1) * stepX, padTop + usableH)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent)
                    )
                )

                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                val lastX = (activeSeries.size - 1) * stepX
                val lastY = yFor(finalVal)
                drawCircle(color = lineColor.copy(alpha = 0.35f), radius = 6.dp.toPx(), center = Offset(lastX, lastY))
                drawCircle(color = lineColor, radius = 3.5.dp.toPx(), center = Offset(lastX, lastY))

                inspectIdx?.let { idx ->
                    val cx = idx * stepX
                    val cy = yFor(activeSeries[idx])
                    drawLine(
                        color = Color(0xFF38BDF8).copy(alpha = 0.7f),
                        start = Offset(cx, padTop),
                        end = Offset(cx, padTop + usableH),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f)
                    )
                    drawCircle(color = Color(0xFF38BDF8), radius = 4.5.dp.toPx(), center = Offset(cx, cy))
                }
            }
        }
    }
}

@Composable
@Composable
private fun SavedCsvLibraryStrip(
    datasets: List<SavedCsvDataset>,
    activeId: String?,
    selectedIds: Set<String>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onToggleMarket: (String) -> Unit,
    onRenameMarket: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    val positiveColor = MaterialTheme.colorScheme.primary
    var editingDatasetId by remember { mutableStateOf<String?>(null) }
    var marketDraft by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Markets (${selectedIds.size} selected)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { datasets.forEach { if (it.id !in selectedIds) onToggleMarket(it.id) } }, enabled = enabled) { Text("All", fontSize = 11.sp) }
                TextButton(onClick = { selectedIds.toList().forEach(onToggleMarket) }, enabled = enabled) { Text("Clear", fontSize = 11.sp) }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            items(datasets, key = { it.id }) { dataset ->
                val isActive = dataset.id == activeId
                val isIncluded = dataset.id in selectedIds
                val tfShort = dataset.detectedTimeframeLabel.substringBefore(" ").ifBlank { "?" }
                Surface(
                    color = if (isIncluded) positiveColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                    border = if (isActive) BorderStroke(1.dp, positiveColor) else null,
                    modifier = Modifier.widthIn(min = 190.dp, max = 235.dp).testTag("saved_csv_item_${dataset.id}")
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isIncluded, onCheckedChange = { onToggleMarket(dataset.id) }, enabled = enabled, modifier = Modifier.size(34.dp))
                            Column(modifier = Modifier.weight(1f).clickable(enabled = enabled) { onSelect(dataset.id) }) {
                                Text(text = dataset.marketName.ifBlank { "CUSTOM" }, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(text = dataset.displayName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { onDelete(dataset.id) }, enabled = enabled, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete ${dataset.displayName}", modifier = Modifier.size(15.dp))
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "${formatBytesCompact(dataset.fileSizeBytes)} • ${dataset.estimatedRows} rows • $tfShort", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            TextButton(onClick = { editingDatasetId = dataset.id; marketDraft = dataset.marketName.ifBlank { "CUSTOM" } }, enabled = enabled, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text("Rename", fontSize = 10.sp) }
                        }
                    }
                }
            }
        }
    }
    val editing = datasets.firstOrNull { it.id == editingDatasetId }
    if (editing != null) {
        AlertDialog(
            onDismissRequest = { editingDatasetId = null },
            title = { Text("Name market") },
            text = {
                OutlinedTextField(value = marketDraft, onValueChange = { marketDraft = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' }.take(20) }, singleLine = true, label = { Text("Symbol (e.g. EURUSD)") })
            },
            confirmButton = { TextButton(onClick = { onRenameMarket(editing.id, marketDraft.ifBlank { "CUSTOM" }); editingDatasetId = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editingDatasetId = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun BatchLeaderboardCard(
    batchResults: List<BatchStrategyRunResult>,
    activeStrategyId: Long?,
    onSelectStrategy: (Long) -> Unit
) {
    val positiveColor = MaterialTheme.colorScheme.primary
    val negativeColor = MaterialTheme.colorScheme.error
    val sorted = remember(batchResults) {
        batchResults.sortedWith(
            compareByDescending<BatchStrategyRunResult> { it.result != null }
                .thenByDescending { it.result?.summary?.netR ?: Double.NEGATIVE_INFINITY }
        )
    }

    LiquidGlassCard(testTag = "batch_leaderboard_card") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Multi-Strategy Comparison (${batchResults.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tap to inspect",
                style = MaterialTheme.typography.labelSmall,
                color = positiveColor
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sorted.forEachIndexed { rankIdx, item ->
                val isSelected = item.strategyId == activeStrategyId
                val res = item.result
                Surface(
                    color = if (isSelected) {
                        positiveColor.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = if (isSelected) BorderStroke(1.dp, positiveColor) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = res != null) { onSelectStrategy(item.strategyId) }
                        .testTag("batch_result_row_${item.strategyId}")
                ) {
                    if (res != null) {
                        val netPts = res.summary.netPoints
                        val pnlColor = if (netPts >= 0.0) positiveColor else negativeColor
                        val pfText = res.summary.profitFactor?.let { formatTwoDec(it) }
                            ?: if (res.summary.wins > 0) "INF" else "--"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "#${rankIdx + 1} ${item.strategyName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${res.summary.totalTrades}t • WR ${formatOneDec(res.summary.winRatePct)}% • PF $pfText • DD ${formatTwoDec(res.risk.maxDrawdownR)}R",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatSignedR(res.summary.netR),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = pnlColor,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "${formatSignedPoints(netPts)}p",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = pnlColor,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Strategy failed",
                                tint = negativeColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.strategyName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = item.error?.error ?: "Strategy error",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = negativeColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytesCompact(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        val kb = (bytes / 1024.0).coerceAtLeast(1.0)
        String.format(Locale.US, "%.0f KB", kb)
    }
}

@Composable
private fun BacktestTradeCard(
    index: Int,
    trade: Trade,
    onReplay: () -> Unit
) {
    val isBreakEven = trade.reason == ExitReason.BE || (abs(trade.points) <= 1e-6 && abs(trade.r) <= 1e-6)
    val isWin = !isBreakEven && (trade.points > 1e-6 || trade.r > 1e-6)
    val positiveColor = MaterialTheme.colorScheme.primary
    val negativeColor = MaterialTheme.colorScheme.error
    val pnlColor = when {
        isWin -> positiveColor
        isBreakEven -> SlateBreakEven
        else -> negativeColor
    }
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SL: ${formatPrice(trade.sl)} | TP: ${formatPrice(trade.tp)}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onReplay,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("replay_trade_button_${index - 1}")
            ) {
                Icon(
                    imageVector = Icons.Default.CandlestickChart,
                    contentDescription = "Replay trade #$index",
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Replay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

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
    val sign = if (value > 0.0) "+" else ""
    return "$sign${String.format(Locale.US, "%.1f", value)}"
}

private fun formatSignedR(value: Double): String {
    val sign = if (value > 0.0) "+" else ""
    return "$sign${String.format(Locale.US, "%.2f", value)}R"
}

private fun formatPrice(value: Double): String =
    String.format(Locale.US, "%.2f", value)
