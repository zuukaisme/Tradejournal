package com.example.presentation.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Emotion
import com.example.domain.model.SyntheticIndexCatalog
import com.example.domain.model.Trade
import com.example.domain.model.TradeDirection
import com.example.domain.model.TradeResult
import com.example.presentation.TradingViewModel
import com.example.presentation.components.getEmotionColor
import com.example.presentation.components.getEmotionIcon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SlateBreakEven
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeEntryBottomSheet(
    viewModel: TradingViewModel,
    tradeToEdit: Trade?,
    preselectedDate: String?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val strategies by viewModel.strategies.collectAsState()
    val bots by viewModel.bots.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val sdfDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val sdfTime = remember { SimpleDateFormat("HH:mm", Locale.US) }
    val now = remember { Calendar.getInstance().time }

    // Form states
    var result by remember { mutableStateOf(tradeToEdit?.result ?: TradeResult.WIN) }
    var pnlInput by remember {
        mutableStateOf(if (tradeToEdit != null) abs(tradeToEdit.pnl).toString() else "")
    }
    var dateInput by remember {
        mutableStateOf(tradeToEdit?.date ?: preselectedDate ?: sdfDate.format(now))
    }
    var timeInput by remember {
        mutableStateOf(tradeToEdit?.time ?: sdfTime.format(now))
    }
    var instrumentInput by remember {
        mutableStateOf(tradeToEdit?.instrument ?: "XAUUSD")
    }
    var direction by remember {
        mutableStateOf(tradeToEdit?.direction ?: TradeDirection.BUY)
    }
    var entryPriceInput by remember {
        mutableStateOf(tradeToEdit?.entryPrice?.toString() ?: "")
    }
    var exitPriceInput by remember {
        mutableStateOf(tradeToEdit?.exitPrice?.toString() ?: "")
    }
    var stopLossInput by remember {
        mutableStateOf(tradeToEdit?.stopLoss?.toString() ?: "")
    }
    var takeProfitInput by remember {
        mutableStateOf(tradeToEdit?.takeProfit?.toString() ?: "")
    }
    var lotSizeInput by remember {
        mutableStateOf(tradeToEdit?.lotSize?.toString() ?: "0.1")
    }

    var selectedStrategyId by remember {
        mutableStateOf<Long?>(tradeToEdit?.strategyId ?: strategies.firstOrNull()?.id)
    }
    var selectedBotId by remember {
        mutableStateOf<Long?>(tradeToEdit?.botId)
    }

    var followedRules by remember { mutableStateOf(tradeToEdit?.followedRules ?: true) }
    var isNewsTrade by remember { mutableStateOf(tradeToEdit?.isNewsTrade ?: false) }
    var selectedEmotion by remember { mutableStateOf(tradeToEdit?.emotion ?: Emotion.CONFIDENT) }
    var notesInput by remember { mutableStateOf(tradeToEdit?.notes ?: "") }
    var screenshotUri by remember { mutableStateOf(tradeToEdit?.screenshotUri) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            screenshotUri = uri.toString()
        }
    }

    var strategyDropdownExpanded by remember { mutableStateOf(false) }
    var botDropdownExpanded by remember { mutableStateOf(false) }

    // Automatic Risk-to-Reward calculation
    val autoRiskReward by remember(entryPriceInput, stopLossInput, takeProfitInput) {
        derivedStateOf {
            val entry = entryPriceInput.toDoubleOrNull()
            val sl = stopLossInput.toDoubleOrNull()
            val tp = takeProfitInput.toDoubleOrNull()

            if (entry != null && sl != null && tp != null) {
                val risk = abs(entry - sl)
                val reward = abs(tp - entry)
                if (risk > 0.0) reward / risk else null
            } else null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Title & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (tradeToEdit == null) "Log New Trade" else "Edit Trade #${tradeToEdit.id}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Big Result Buttons: Winning, Losing, Break Even, Didn't Trade
            Text(
                text = "Trade Result",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ResultButton(
                    label = "WINNING",
                    isSelected = result == TradeResult.WIN,
                    activeColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("result_btn_win"),
                    onClick = { result = TradeResult.WIN }
                )
                ResultButton(
                    label = "LOSING",
                    isSelected = result == TradeResult.LOSS,
                    activeColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("result_btn_loss"),
                    onClick = { result = TradeResult.LOSS }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ResultButton(
                    label = "BREAK EVEN",
                    isSelected = result == TradeResult.BREAK_EVEN,
                    activeColor = SlateBreakEven,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("result_btn_be"),
                    onClick = {
                        result = TradeResult.BREAK_EVEN
                        pnlInput = "0.0"
                    }
                )
                ResultButton(
                    label = "DIDN'T TRADE",
                    isSelected = result == TradeResult.NO_TRADE,
                    activeColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("result_btn_no_trade"),
                    onClick = {
                        result = TradeResult.NO_TRADE
                        pnlInput = "0.0"
                    }
                )
            }

            // 2. Profit / Loss Amount
            if (result != TradeResult.NO_TRADE) {
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = pnlInput,
                    onValueChange = { pnlInput = it },
                    label = { Text("P&L Amount (${settings.currency.symbol})") },
                    placeholder = { Text("e.g. 450.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pnl_amount_field"),
                    shape = RoundedCornerShape(12.dp),
                    prefix = {
                        Text(
                            text = if (result == TradeResult.WIN) "+${settings.currency.symbol}" else if (result == TradeResult.LOSS) "-${settings.currency.symbol}" else settings.currency.symbol,
                            fontWeight = FontWeight.Bold,
                            color = if (result == TradeResult.WIN) MaterialTheme.colorScheme.primary else if (result == TradeResult.LOSS) MaterialTheme.colorScheme.error else Color.Gray
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Instrument and Direction
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Instrument & Direction",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                if (SyntheticIndexCatalog.isSyntheticIndex(instrumentInput)) {
                    Text(
                        text = "WLTrade SyntX Index",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            // WLTrade SyntX Synthetic Indices Chips
            Text(
                text = "WLTrade SyntX Indices",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            val syntxChips = listOf("FX Vol 20", "FX Vol 40", "PainX 400", "PainX 999", "SFX Vol 40", "Boom 500", "Crash 500", "Step Index", "V75")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(syntxChips) { symbol ->
                    FilterChip(
                        selected = instrumentInput.equals(symbol, ignoreCase = true),
                        onClick = { instrumentInput = symbol },
                        label = { Text(symbol, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Gold, Metals & Forex Markets
            Text(
                text = "Gold & Forex Markets",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            val quickPairs = listOf("XAUUSD", "EURUSD", "GBPUSD", "US30", "NAS100", "BTCUSD")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(quickPairs) { pair ->
                    FilterChip(
                        selected = instrumentInput.equals(pair, ignoreCase = true),
                        onClick = { instrumentInput = pair },
                        label = { Text(pair, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = instrumentInput,
                    onValueChange = { instrumentInput = it.uppercase() },
                    label = { Text("Pair / Symbol") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("instrument_input_field"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Direction Toggle (BUY vs SELL)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(
                                if (direction == TradeDirection.BUY) MaterialTheme.colorScheme.primary else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { direction = TradeDirection.BUY },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "BUY",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (direction == TradeDirection.BUY) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(
                                if (direction == TradeDirection.SELL) MaterialTheme.colorScheme.error else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { direction = TradeDirection.SELL },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SELL",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (direction == TradeDirection.SELL) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Date and Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = dateInput,
                    onValueChange = { dateInput = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("trade_date_field"),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = timeInput,
                    onValueChange = { timeInput = it },
                    label = { Text("Time (HH:mm)") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(0.8f)
                        .testTag("trade_time_field"),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 5. Strategy Picker
            Text(
                text = "Strategy Used",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            val currentStrategy = strategies.firstOrNull { it.id == selectedStrategyId }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { strategyDropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = currentStrategy?.name ?: "No Strategy Selected",
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }

                DropdownMenu(
                    expanded = strategyDropdownExpanded,
                    onDismissRequest = { strategyDropdownExpanded = false }
                ) {
                    strategies.forEach { strategy ->
                        DropdownMenuItem(
                            text = { Text(strategy.name) },
                            onClick = {
                                selectedStrategyId = strategy.id
                                strategyDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Optional Bot Link
            if (bots.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                val currentBot = bots.firstOrNull { it.id == selectedBotId }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { botDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (currentBot != null) "Linked Bot: ${currentBot.name}" else "Link MQL5 Bot (Optional)",
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }

                    DropdownMenu(
                        expanded = botDropdownExpanded,
                        onDismissRequest = { botDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None (Discretionary Trade)") },
                            onClick = {
                                selectedBotId = null
                                botDropdownExpanded = false
                            }
                        )
                        bots.forEach { bot ->
                            DropdownMenuItem(
                                text = { Text("${bot.name} (${bot.version})") },
                                onClick = {
                                    selectedBotId = bot.id
                                    botDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 6. Optional Prices, Stop Loss, Take Profit, Lot Size
            Text(
                text = "Execution Prices & Sizing (Optional)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = entryPriceInput,
                    onValueChange = { entryPriceInput = it },
                    label = { Text("Entry Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = exitPriceInput,
                    onValueChange = { exitPriceInput = it },
                    label = { Text("Exit Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = stopLossInput,
                    onValueChange = { stopLossInput = it },
                    label = { Text("Stop Loss") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = takeProfitInput,
                    onValueChange = { takeProfitInput = it },
                    label = { Text("Take Profit") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = lotSizeInput,
                    onValueChange = { lotSizeInput = it },
                    label = { Text("Lot Size") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                if (autoRiskReward != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Auto R:R Ratio",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "1 : ${String.format(Locale.US, "%.2f", autoRiskReward)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 7. Discipline & News Day Toggles
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Followed Rules Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Followed Strategy Rules?",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (followedRules) "Yes, stuck to checklist" else "No, violated rules / FOMO",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = if (followedRules) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        Switch(
                            checked = followedRules,
                            onCheckedChange = { followedRules = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // News Day Release Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "High-Impact News Release Trade",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Taken within 60 mins of NFP / CPI release",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isNewsTrade,
                            onCheckedChange = { isNewsTrade = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFF59E0B))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 8. Emotion Selector
            Text(
                text = "Emotional State",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            val emotions = listOf(
                Emotion.CALM,
                Emotion.CONFIDENT,
                Emotion.FEARFUL,
                Emotion.GREEDY,
                Emotion.REVENGE,
                Emotion.NEUTRAL
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(emotions) { emotion ->
                    val isSelected = selectedEmotion == emotion
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedEmotion = emotion },
                        leadingIcon = {
                            Icon(
                                imageVector = getEmotionIcon(emotion),
                                contentDescription = emotion.label,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else getEmotionColor(emotion),
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(emotion.label, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 9. Notes: Why taken & What learned
            OutlinedTextField(
                value = notesInput,
                onValueChange = { notesInput = it },
                label = { Text("Trade Reflection & Notes") },
                placeholder = { Text("Why did I enter? What worked well? What could be improved?") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .testTag("trade_notes_field"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 10. Screenshot Attachment Button (Android Photo Picker)
            OutlinedButton(
                onClick = {
                    if (screenshotUri == null) {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    } else {
                        screenshotUri = null
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = if (screenshotUri == null) Icons.Default.Image else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (screenshotUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (screenshotUri == null) "Attach Chart Screenshot" else "Screenshot Attached (Tap to remove)",
                    fontSize = 12.sp,
                    color = if (screenshotUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 11. Save and Delete Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (tradeToEdit != null) {
                    OutlinedButton(
                        onClick = { viewModel.deleteTrade(tradeToEdit.id) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("delete_trade_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete")
                    }
                }

                Button(
                    onClick = {
                        val parsedPnl = pnlInput.toDoubleOrNull() ?: 0.0
                        val signedPnl = when (result) {
                            TradeResult.WIN -> abs(parsedPnl)
                            TradeResult.LOSS -> -abs(parsedPnl)
                            TradeResult.BREAK_EVEN -> 0.0
                            TradeResult.NO_TRADE -> 0.0
                        }

                        val strategy = strategies.firstOrNull { it.id == selectedStrategyId }
                        val bot = bots.firstOrNull { it.id == selectedBotId }

                        val tradeToSave = Trade(
                            id = tradeToEdit?.id ?: 0L,
                            date = dateInput.ifBlank { sdfDate.format(now) },
                            time = timeInput.ifBlank { sdfTime.format(now) },
                            timestamp = tradeToEdit?.timestamp ?: System.currentTimeMillis(),
                            result = result,
                            pnl = signedPnl,
                            instrument = instrumentInput.ifBlank { "XAUUSD" },
                            direction = direction,
                            entryPrice = entryPriceInput.toDoubleOrNull(),
                            exitPrice = exitPriceInput.toDoubleOrNull(),
                            stopLoss = stopLossInput.toDoubleOrNull(),
                            takeProfit = takeProfitInput.toDoubleOrNull(),
                            lotSize = lotSizeInput.toDoubleOrNull(),
                            riskRewardRatio = autoRiskReward,
                            strategyId = selectedStrategyId,
                            strategyName = strategy?.name ?: "",
                            botId = selectedBotId,
                            botName = bot?.name ?: "",
                            notes = notesInput,
                            emotion = selectedEmotion,
                            followedRules = followedRules,
                            screenshotUri = screenshotUri,
                            isNewsTrade = isNewsTrade,
                            tags = listOf(instrumentInput, if (isNewsTrade) "NewsTrade" else "Standard")
                        )

                        viewModel.saveTrade(tradeToSave)
                    },
                    modifier = Modifier
                        .weight(2f)
                        .testTag("save_trade_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (tradeToEdit == null) "Log Trade" else "Save Changes")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ResultButton(
    label: String,
    isSelected: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val winColor = MaterialTheme.colorScheme.primary
    val lossColor = MaterialTheme.colorScheme.error
    val contentColor = when {
        !isSelected -> MaterialTheme.colorScheme.onSurface
        activeColor == lossColor -> MaterialTheme.colorScheme.onError
        activeColor == winColor -> MaterialTheme.colorScheme.onPrimary
        else -> Color.White
    }
    Surface(
        color = if (isSelected) activeColor else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .height(50.dp)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = contentColor
            )
        }
    }
}
