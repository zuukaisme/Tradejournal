package com.example.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.backtest.AccountBacktestSimulator
import com.example.backtest.AccountSimulationResult
import com.example.backtest.ExitReason
import com.example.backtest.Side
import com.example.backtest.SimulatedAccountTrade
import com.example.domain.model.CurrencyCode
import com.example.presentation.AppScreen
import com.example.presentation.BacktestViewModel
import com.example.presentation.TradingViewModel
import com.example.presentation.components.CurrencyFormatter
import com.example.presentation.components.LiquidGlassCard
import com.example.ui.theme.AmberGold
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AccountSimulatorScreen(
    tradingViewModel: TradingViewModel,
    backtestViewModel: BacktestViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by tradingViewModel.settings.collectAsState()
    val profiles by tradingViewModel.accountProfiles.collectAsState()
    val activeProfile by tradingViewModel.activeAccountProfile.collectAsState()
    val backtestState by backtestViewModel.uiState.collectAsState()

    val baseProfile = activeProfile ?: AccountBacktestSimulator.fallbackProfile(
        testerLot = settings.testerLotSize,
        testerLeverage = settings.testerLeverage
    )

    // Local editable inputs synced smoothly from saved profile & settings
    var balanceInput by remember(baseProfile.id, baseProfile.startingBalance) {
        mutableStateOf(baseProfile.startingBalance.toString())
    }
    var lotInput by remember(settings.testerLotSize) {
        mutableStateOf(String.format(Locale.US, "%.2f", settings.testerLotSize))
    }
    var leverageInput by remember(settings.testerLeverage) {
        mutableStateOf(settings.testerLeverage.toString())
    }
    var valuePerPtInput by remember(baseProfile.id, baseProfile.valuePerPointPerLot) {
        mutableStateOf(baseProfile.valuePerPointPerLot.toString())
    }
    var contractSizeInput by remember(baseProfile.id, baseProfile.contractSizePerLot) {
        mutableStateOf(baseProfile.contractSizePerLot.toString())
    }
    var commissionInput by remember(baseProfile.id, baseProfile.commissionPerLot) {
        mutableStateOf(baseProfile.commissionPerLot.toString())
    }
    var selectedInstrumentLabel by remember(baseProfile.id, baseProfile.instrumentLabel) {
        mutableStateOf(baseProfile.instrumentLabel)
    }

    var itemFilter by remember { mutableIntStateOf(0) } // 0=All, 1=Profit, 2=Loss, 3=Margin Alert
    var showAllItems by remember { mutableStateOf(false) }

    val primary = MaterialTheme.colorScheme.primary
    val negative = MaterialTheme.colorScheme.error

    val activeResult = backtestState.result
    if (activeResult == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            LiquidGlassCard(testTag = "account_simulator_empty_card") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = primary.copy(alpha = 0.14f),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = primary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    Text(
                        text = "Run a Backtest to Simulate Real Account Numbers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Once your strategy completes, this screen calculates actual balance growth, fixed-lot profits, leverage margin used, commissions, and itemized cashflows.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { tradingViewModel.navigateTo(AppScreen.PROFILE) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Profiles", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { tradingViewModel.navigateTo(AppScreen.BACKTEST) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Backtester", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        return
    }

    val startingBalance = (balanceInput.toDoubleOrNull() ?: baseProfile.startingBalance).coerceAtLeast(1.0)
    val fixedLot = (lotInput.toDoubleOrNull() ?: settings.testerLotSize).coerceAtLeast(0.001)
    val leverage = (leverageInput.toIntOrNull() ?: settings.testerLeverage).coerceIn(1, 5000)
    val valuePerPt = (valuePerPtInput.toDoubleOrNull() ?: baseProfile.valuePerPointPerLot).coerceAtLeast(0.0001)
    val contractSize = (contractSizeInput.toDoubleOrNull() ?: baseProfile.contractSizePerLot).coerceAtLeast(0.0)
    val commissionPerLot = (commissionInput.toDoubleOrNull() ?: baseProfile.commissionPerLot).coerceAtLeast(0.0)

    val simulation = remember(
        activeResult.trades,
        startingBalance,
        fixedLot,
        leverage,
        valuePerPt,
        contractSize,
        commissionPerLot,
        baseProfile.stopOutLevelPct
    ) {
        AccountBacktestSimulator.simulateFixedLot(
            trades = activeResult.trades,
            startingBalance = startingBalance,
            fixedLotSize = fixedLot,
            leverage = leverage,
            valuePerPointPerLot = valuePerPt,
            contractSizePerLot = contractSize,
            commissionPerLot = commissionPerLot,
            stopOutLevelPct = baseProfile.stopOutLevelPct
        )
    }

    val filteredItems = remember(simulation.items, itemFilter) {
        when (itemFilter) {
            1 -> simulation.items.filter { it.netPnL > 0.0 }
            2 -> simulation.items.filter { it.netPnL < 0.0 }
            3 -> simulation.items.filter { it.hasMarginAlert }
            else -> simulation.items
        }
    }
    val displayedItems = remember(filteredItems, showAllItems) {
        if (showAllItems) filteredItems else filteredItems.take(120)
    }

    val activeStrategyLabel = remember(backtestState.batchResults, backtestState.activeBatchStrategyId, backtestState.selectedStrategy) {
        backtestState.batchResults.firstOrNull { it.strategyId == backtestState.activeBatchStrategyId }?.strategyName
            ?: backtestState.selectedStrategy?.name
            ?: "Strategy Run"
    }

    val quickLots = listOf(0.01, 0.05, 0.10, 0.25, 0.50, 1.00, 2.00)
    val quickLeverages = listOf(30, 50, 100, 200, 500, 1000)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Batch Strategy Selector if multiple strategies ran
        if (backtestState.batchResults.count { it.result != null } > 1) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "COMPARE STRATEGY ACCOUNT OUTCOMES",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(backtestState.batchResults.filter { it.result != null }) { run ->
                            val isSelected = run.strategyId == backtestState.activeBatchStrategyId
                            val runSim = remember(
                                run.result,
                                startingBalance,
                                fixedLot,
                                leverage,
                                valuePerPt,
                                contractSize,
                                commissionPerLot,
                                baseProfile.stopOutLevelPct
                            ) {
                                AccountBacktestSimulator.simulateFixedLot(
                                    trades = run.result?.trades.orEmpty(),
                                    startingBalance = startingBalance,
                                    fixedLotSize = fixedLot,
                                    leverage = leverage,
                                    valuePerPointPerLot = valuePerPt,
                                    contractSizePerLot = contractSize,
                                    commissionPerLot = commissionPerLot,
                                    stopOutLevelPct = baseProfile.stopOutLevelPct
                                )
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = { backtestViewModel.selectBatchResult(run.strategyId) },
                                label = {
                                    Text(
                                        text = "${run.strategyName} (${CurrencyFormatter.format(runSim.netProfit, settings.currency)})",
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }
            }
        }

        // 2. Account Performance Hero Summary
        item {
            AccountHeroSummaryCard(
                strategyName = activeStrategyLabel,
                profileName = baseProfile.name,
                category = baseProfile.accountCategory,
                simulation = simulation,
                currency = settings.currency,
                fileName = backtestState.fileName
            )
        }

        // 3. Margin & Leverage Health Warning / Stats Card
        item {
            AccountMarginHealthCard(
                simulation = simulation,
                currency = settings.currency
            )
        }

        // 4. Interactive Fixed-Lot, Leverage & Profile Parameter Controls
        item {
            LiquidGlassCard(testTag = "account_sim_controls_card") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Account & Trade Tester Controls",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Fixed Lot Size • Auto-saved to Vault",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(
                        onClick = { tradingViewModel.navigateTo(AppScreen.PROFILE) },
                        modifier = Modifier.testTag("account_sim_manage_profiles_button")
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Profiles", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (profiles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(profiles, key = { it.id }) { prof ->
                            val selected = prof.id == baseProfile.id
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    tradingViewModel.selectActiveAccountProfile(prof, syncDefaultsToTester = true)
                                },
                                label = {
                                    Text(
                                        text = "${prof.name} (1:${prof.leverage})",
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Fixed Lot Size Quick Bar + Custom Input
                Text(
                    text = "Fixed Lot Size (All Trades)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(quickLots) { lot ->
                        val label = String.format(Locale.US, "%.2f", lot)
                        val selected = lotInput.toDoubleOrNull() == lot
                        FilterChip(
                            selected = selected,
                            onClick = {
                                lotInput = label
                                tradingViewModel.updateTesterLotSize(lot)
                            },
                            label = {
                                Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            },
                            modifier = Modifier.testTag("sim_lot_chip_$label")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Leverage Quick Bar
                Text(
                    text = "Account Leverage",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(quickLeverages) { lev ->
                        val selected = leverageInput.toIntOrNull() == lev
                        FilterChip(
                            selected = selected,
                            onClick = {
                                leverageInput = lev.toString()
                                tradingViewModel.updateTesterLeverage(lev)
                            },
                            label = {
                                Text("1:$lev", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            },
                            modifier = Modifier.testTag("sim_leverage_chip_$lev")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lotInput,
                        onValueChange = { raw ->
                            lotInput = raw
                            raw.toDoubleOrNull()?.takeIf { it > 0.0 }?.let {
                                tradingViewModel.updateTesterLotSize(it)
                            }
                        },
                        label = { Text("Lot Size") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sim_lot_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = leverageInput,
                        onValueChange = { raw ->
                            val clean = raw.filter(Char::isDigit)
                            leverageInput = clean
                            clean.toIntOrNull()?.takeIf { it >= 1 }?.let {
                                tradingViewModel.updateTesterLeverage(it)
                            }
                        },
                        label = { Text("Leverage 1:X") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sim_leverage_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = balanceInput,
                        onValueChange = { balanceInput = it },
                        label = { Text("Balance") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("sim_balance_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Instrument Contract Spec",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Backtest ptSize: ${backtestState.pointSizeInput.ifBlank { "1.0" }}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = AmberGold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(AccountBacktestSimulator.instrumentPresets) { spec ->
                        FilterChip(
                            selected = selectedInstrumentLabel == spec.label,
                            onClick = {
                                selectedInstrumentLabel = spec.label
                                valuePerPtInput = spec.valuePerPointPerLot.toString()
                                contractSizeInput = spec.contractSizePerLot.toString()
                            },
                            label = { Text(spec.label, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = valuePerPtInput,
                        onValueChange = { valuePerPtInput = it },
                        label = { Text("${settings.currency.symbol}/Pt @1 Lot") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = contractSizeInput,
                        onValueChange = { contractSizeInput = it },
                        label = { Text("Contract Units") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = commissionInput,
                        onValueChange = { commissionInput = it },
                        label = { Text("Comm/Lot") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        tradingViewModel.applyTesterSpecsToActiveProfile(
                            lotSize = fixedLot,
                            leverage = leverage,
                            startingBalance = startingBalance,
                            commissionPerLot = commissionPerLot,
                            valuePerPointPerLot = valuePerPt,
                            contractSizePerLot = contractSize,
                            instrumentLabel = selectedInstrumentLabel
                        )
                        Toast.makeText(
                            context,
                            "Saved specs to active profile & Trading Vault",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sim_save_to_profile_vault_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Specs to Profile & Trading Vault", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 5. Real-Money Balance Curve Canvas
        item {
            LiquidGlassCard(testTag = "account_balance_curve_card") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Account Balance Curve",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Drag chart to inspect dollar balance at any trade",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = CurrencyFormatter.format(simulation.netProfit, settings.currency),
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (simulation.netProfit >= 0.0) primary else negative
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                AccountBalanceCurveCanvas(
                    balanceCurve = simulation.balanceCurve,
                    startingBalance = simulation.startingBalance,
                    currency = settings.currency,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 6. Itemized Trade Cashflow Ledger Header & Filters
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Trade Cashflow Items (${filteredItems.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format(Locale.US, "Fixed %.2f lots", simulation.fixedLotSize),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = primary
                    )
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val filters = listOf(
                        "All (${simulation.items.size})",
                        "Profit (${simulation.winningTrades})",
                        "Loss (${simulation.losingTrades})",
                        "Margin Alert (${simulation.items.count { it.hasMarginAlert }})"
                    )
                    items(filters.indices.toList()) { idx ->
                        FilterChip(
                            selected = itemFilter == idx,
                            onClick = { itemFilter = idx },
                            label = { Text(filters[idx], fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        if (displayedItems.isEmpty()) {
            item {
                LiquidGlassCard {
                    Text(
                        text = "No trades match this filter.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(displayedItems, key = { it.index }) { item ->
                AccountTradeLedgerRow(
                    item = item,
                    currency = settings.currency
                )
            }

            if (!showAllItems && filteredItems.size > displayedItems.size) {
                item {
                    OutlinedButton(
                        onClick = { showAllItems = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Show All ${filteredItems.size} Trades")
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

@Composable
private fun AccountHeroSummaryCard(
    strategyName: String,
    profileName: String,
    category: String,
    simulation: AccountSimulationResult,
    currency: CurrencyCode,
    fileName: String?
) {
    val primary = MaterialTheme.colorScheme.primary
    val negative = MaterialTheme.colorScheme.error
    val profitColor = if (simulation.netProfit >= 0.0) primary else negative
    val roiSign = if (simulation.roiPct > 0.0) "+" else ""

    LiquidGlassCard(testTag = "account_sim_hero_card") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strategyName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$profileName ($category) • ${fileName ?: "Dataset"}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                color = profitColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, profitColor.copy(alpha = 0.45f))
            ) {
                Text(
                    text = String.format(Locale.US, "%s%.1f%% ROI", roiSign, simulation.roiPct),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = profitColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "FINAL ACCOUNT BALANCE",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyFormatter.format(simulation.endingBalance, currency, includeSign = false),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (simulation.endingBalance >= simulation.startingBalance) MaterialTheme.colorScheme.onSurface else negative
                )
                Text(
                    text = "Start: ${CurrencyFormatter.format(simulation.startingBalance, currency, includeSign = false)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "PROFIT MADE",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyFormatter.format(simulation.netProfit, currency, includeSign = true),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = profitColor
                )
                Text(
                    text = String.format(Locale.US, "%.2f lots • 1:%d", simulation.fixedLotSize, simulation.leverage),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = AmberGold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallStatTile(
                label = "Gross Profit",
                value = CurrencyFormatter.format(simulation.grossProfit, currency, includeSign = false),
                valueColor = primary,
                modifier = Modifier.weight(1f)
            )
            SmallStatTile(
                label = "Gross Loss",
                value = CurrencyFormatter.format(simulation.grossLoss, currency, includeSign = true),
                valueColor = negative,
                modifier = Modifier.weight(1f)
            )
            SmallStatTile(
                label = "Max DD",
                value = String.format(
                    Locale.US,
                    "%s (%.1f%%)",
                    CurrencyFormatter.formatCompact(-simulation.maxDrawdownCurrency, currency),
                    simulation.maxDrawdownPct
                ),
                valueColor = negative,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallStatTile(
                label = "Total Comm.",
                value = CurrencyFormatter.format(-simulation.totalCommission, currency, includeSign = true),
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            SmallStatTile(
                label = "Profit Factor",
                value = simulation.profitFactor?.let { String.format(Locale.US, "%.2f", it) } ?: "INF",
                valueColor = primary,
                modifier = Modifier.weight(1f)
            )
            SmallStatTile(
                label = "Peak Balance",
                value = CurrencyFormatter.format(simulation.peakBalance, currency, includeSign = false),
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AccountMarginHealthCard(
    simulation: AccountSimulationResult,
    currency: CurrencyCode
) {
    val primary = MaterialTheme.colorScheme.primary
    val negative = MaterialTheme.colorScheme.error
    val hasAlert = simulation.isAccountBlown || simulation.stopOutCount > 0 || simulation.insufficientMarginCount > 0

    LiquidGlassCard(testTag = "account_margin_health_card") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Leverage & Margin Utilization",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Surface(
                color = if (hasAlert) negative.copy(alpha = 0.16f) else primary.copy(alpha = 0.14f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = when {
                        simulation.isAccountBlown -> "BLOWN"
                        simulation.stopOutCount > 0 -> "STOP-OUT RISK"
                        simulation.insufficientMarginCount > 0 -> "LOW MARGIN"
                        else -> "HEALTHY"
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (hasAlert) negative else primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (hasAlert) {
            Surface(
                color = negative.copy(alpha = 0.14f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = negative, modifier = Modifier.size(18.dp))
                    Text(
                        text = buildString {
                            if (simulation.isAccountBlown) append("Balance dropped to zero or below. ")
                            if (simulation.insufficientMarginCount > 0) {
                                append("${simulation.insufficientMarginCount} trade(s) exceeded available free margin. ")
                            }
                            simulation.firstStopOutTradeIndex?.let {
                                append("First stop-out breach at Trade #$it (<= ${simulation.stopOutLevelPct.roundToInt()}%).")
                            }
                        }.trim(),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallStatTile(
                label = "Avg Margin/Trade",
                value = CurrencyFormatter.format(simulation.avgMarginPerTrade, currency, includeSign = false),
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            SmallStatTile(
                label = "Peak Margin",
                value = CurrencyFormatter.format(simulation.peakMarginUsed, currency, includeSign = false),
                valueColor = AmberGold,
                modifier = Modifier.weight(1f)
            )
            SmallStatTile(
                label = "Lowest Free Margin",
                value = CurrencyFormatter.format(simulation.lowestFreeMargin, currency, includeSign = true),
                valueColor = if (simulation.lowestFreeMargin < 0) negative else primary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SmallStatTile(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AccountBalanceCurveCanvas(
    balanceCurve: List<Double>,
    startingBalance: Double,
    currency: CurrencyCode,
    modifier: Modifier = Modifier
) {
    val positive = MaterialTheme.colorScheme.primary
    val negative = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val zeroLineColor = AmberGold.copy(alpha = 0.75f)
    var inspectedIndex by remember(balanceCurve) { mutableStateOf<Int?>(null) }

    val lineColor = if ((balanceCurve.lastOrNull() ?: startingBalance) >= startingBalance) positive else negative

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        inspectedIndex?.let { idx ->
            val clamped = idx.coerceIn(0, (balanceCurve.size - 1).coerceAtLeast(0))
            val bal = balanceCurve.getOrElse(clamped) { startingBalance }
            val diff = bal - startingBalance
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (clamped == 0) "Start Balance" else "After Trade #$clamped",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${CurrencyFormatter.format(bal, currency, false)} (${CurrencyFormatter.format(diff, currency, true)})",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (diff >= 0.0) positive else negative
                    )
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .pointerInput(balanceCurve) {
                    detectTapGestures { offset ->
                        if (balanceCurve.size >= 2) {
                            val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            inspectedIndex = (fraction * (balanceCurve.size - 1)).roundToInt()
                        }
                    }
                }
                .pointerInput(balanceCurve) {
                    detectDragGestures(
                        onDragEnd = { inspectedIndex = null },
                        onDragCancel = { inspectedIndex = null }
                    ) { change, _ ->
                        change.consume()
                        if (balanceCurve.size >= 2) {
                            val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            inspectedIndex = (fraction * (balanceCurve.size - 1)).roundToInt()
                        }
                    }
                }
        ) {
            if (balanceCurve.size < 2) return@Canvas
            val minVal = balanceCurve.minOrNull() ?: startingBalance
            val maxVal = balanceCurve.maxOrNull() ?: startingBalance
            val span = (maxVal - minVal).takeIf { it > 0.0 } ?: (startingBalance * 0.1).coerceAtLeast(1.0)
            val topPad = 12.dp.toPx()
            val botPad = 12.dp.toPx()
            val usableH = (size.height - topPad - botPad).coerceAtLeast(1f)

            fun yFor(value: Double): Float {
                val norm = ((value - minVal) / span).toFloat().coerceIn(0f, 1f)
                return size.height - botPad - norm * usableH
            }

            // Grid lines
            for (g in 0..3) {
                val y = topPad + (usableH / 3f) * g
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }

            // Starting balance baseline
            val baseY = yFor(startingBalance)
            drawLine(
                color = zeroLineColor,
                start = Offset(0f, baseY),
                end = Offset(size.width, baseY),
                strokeWidth = 1.6f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
            )

            val path = Path()
            val fillPath = Path()
            val stepX = size.width / (balanceCurve.size - 1).toFloat()
            balanceCurve.forEachIndexed { i, v ->
                val x = i * stepX
                val y = yFor(v)
                if (i == 0) {
                    path.moveTo(x, y)
                    fillPath.moveTo(x, size.height)
                    fillPath.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
                if (i == balanceCurve.lastIndex) {
                    fillPath.lineTo(x, size.height)
                    fillPath.close()
                }
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.0f))
                )
            )
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            inspectedIndex?.let { idx ->
                val clamped = idx.coerceIn(0, balanceCurve.lastIndex)
                val ix = clamped * stepX
                val iy = yFor(balanceCurve[clamped])
                drawLine(
                    color = lineColor.copy(alpha = 0.45f),
                    start = Offset(ix, 0f),
                    end = Offset(ix, size.height),
                    strokeWidth = 1.5f
                )
                drawCircle(color = lineColor, radius = 5.dp.toPx(), center = Offset(ix, iy))
            }
        }
    }
}

@Composable
private fun AccountTradeLedgerRow(
    item: SimulatedAccountTrade,
    currency: CurrencyCode
) {
    val positive = MaterialTheme.colorScheme.primary
    val negative = MaterialTheme.colorScheme.error
    val pnlColor = when {
        item.netPnL > 0.0 -> positive
        item.netPnL < 0.0 -> negative
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val sideColor = if (item.side == Side.BUY) positive else negative

    LiquidGlassCard(testTag = "account_ledger_item_${item.index}") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "#${item.index}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = sideColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(5.dp)
                ) {
                    Text(
                        text = "${item.side.name} • ${item.reason.name}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = sideColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (item.hasMarginAlert) {
                    Surface(
                        color = negative.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(5.dp)
                    ) {
                        Text(
                            text = if (item.hasStopOut) "STOP-OUT" else "LOW MARGIN",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = negative,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Text(
                text = CurrencyFormatter.format(item.netPnL, currency, includeSign = true),
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                color = pnlColor
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = String.format(
                        Locale.US,
                        "%.2f -> %.2f (%+.1f pts)",
                        item.entryPrice,
                        item.exitPrice,
                        item.points
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = String.format(
                        Locale.US,
                        "%.2f lot • Margin %s • Comm %s",
                        item.lotSize,
                        CurrencyFormatter.format(item.requiredMargin, currency, false),
                        CurrencyFormatter.format(item.commission, currency, false)
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Balance",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyFormatter.format(item.runningBalanceAfter, currency, false),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (item.runningBalanceAfter <= 0.0) negative else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
