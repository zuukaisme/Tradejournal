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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.CurrencyCode
import com.example.domain.model.SyntheticIndexCatalog
import com.example.domain.model.Trade
import com.example.domain.model.TradeDirection
import com.example.domain.model.TradeResult
import com.example.presentation.TradingViewModel
import com.example.presentation.components.CurrencyFormatter
import com.example.presentation.components.EmotionBadge
import com.example.presentation.components.LiquidGlassCard
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonEmerald
import java.util.Locale

@Composable
fun TradesScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    val filteredTrades by viewModel.filteredTrades.collectAsState()
    val allTrades by viewModel.trades.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val searchQuery by viewModel.tradeSearchQuery.collectAsState()
    val selectedResultFilter by viewModel.selectedResultFilter.collectAsState()

    var showNewsOnly by remember { mutableStateOf(false) }
    var showIndicesOnly by remember { mutableStateOf(false) }

    val displayedTrades = remember(filteredTrades, showNewsOnly, showIndicesOnly) {
        var list = filteredTrades
        if (showNewsOnly) {
            list = list.filter { it.isNewsTrade }
        }
        if (showIndicesOnly) {
            list = list.filter { SyntheticIndexCatalog.isSyntheticIndex(it.instrument) }
        }
        list
    }

    val totalPnL = remember(displayedTrades) {
        displayedTrades.filter { it.result != TradeResult.NO_TRADE }.sumOf { it.pnl }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val errorColor = MaterialTheme.colorScheme.error

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // Search Input with subtle styling
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.tradeSearchQuery.value = it },
                placeholder = { Text("Search instrument, synthetics, notes...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.tradeSearchQuery.value = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("trades_search_field"),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = primaryColor.copy(alpha = 0.6f),
                    unfocusedBorderColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Chips Row
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = selectedResultFilter == null && !showNewsOnly && !showIndicesOnly,
                        onClick = {
                            viewModel.selectedResultFilter.value = null
                            showNewsOnly = false
                            showIndicesOnly = false
                        },
                        label = { Text("All (${allTrades.size})") },
                        modifier = Modifier.testTag("filter_chip_all")
                    )
                }
                item {
                    FilterChip(
                        selected = showIndicesOnly,
                        onClick = {
                            showIndicesOnly = !showIndicesOnly
                            if (showIndicesOnly) {
                                showNewsOnly = false
                            }
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Analytics,
                                contentDescription = null,
                                tint = if (showIndicesOnly) onPrimaryColor else primaryColor,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        label = { Text("WLTrade SyntX", color = if (showIndicesOnly) onPrimaryColor else primaryColor) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = primaryColor),
                        modifier = Modifier.testTag("filter_chip_indices")
                    )
                }
                item {
                    FilterChip(
                        selected = selectedResultFilter == TradeResult.WIN,
                        onClick = {
                            viewModel.selectedResultFilter.value = if (selectedResultFilter == TradeResult.WIN) null else TradeResult.WIN
                            showNewsOnly = false
                        },
                        label = { Text("Wins", color = if (selectedResultFilter == TradeResult.WIN) onPrimaryColor else primaryColor) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = primaryColor),
                        modifier = Modifier.testTag("filter_chip_wins")
                    )
                }
                item {
                    FilterChip(
                        selected = selectedResultFilter == TradeResult.LOSS,
                        onClick = {
                            viewModel.selectedResultFilter.value = if (selectedResultFilter == TradeResult.LOSS) null else TradeResult.LOSS
                            showNewsOnly = false
                        },
                        label = { Text("Losses", color = if (selectedResultFilter == TradeResult.LOSS) Color.White else errorColor) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = errorColor),
                        modifier = Modifier.testTag("filter_chip_losses")
                    )
                }
                item {
                    FilterChip(
                        selected = selectedResultFilter == TradeResult.BREAK_EVEN,
                        onClick = {
                            viewModel.selectedResultFilter.value = if (selectedResultFilter == TradeResult.BREAK_EVEN) null else TradeResult.BREAK_EVEN
                            showNewsOnly = false
                        },
                        label = { Text("Break Even") },
                        modifier = Modifier.testTag("filter_chip_be")
                    )
                }
                item {
                    FilterChip(
                        selected = showNewsOnly,
                        onClick = {
                            showNewsOnly = !showNewsOnly
                            if (showNewsOnly) {
                                viewModel.selectedResultFilter.value = null
                                showIndicesOnly = false
                            }
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                        },
                        label = { Text("News Only") },
                        modifier = Modifier.testTag("filter_chip_news")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sub-header: Count and Total P&L
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${displayedTrades.size} trades shown",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Net: ${CurrencyFormatter.format(totalPnL, settings.currency)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (totalPnL >= 0) primaryColor else errorColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Trades List
            if (displayedTrades.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            color = primaryColor.copy(alpha = 0.12f),
                            shape = CircleShape,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.FormatListBulleted,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (allTrades.isEmpty()) "No Trades Recorded Yet" else "No trades match selected filters",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (allTrades.isEmpty()) "Your journal is completely clean. Tap below to log your first trade." else "Try adjusting your search query or reset filter chips.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (allTrades.isEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.openNewTradeEntry() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = primaryColor,
                                    contentColor = onPrimaryColor
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Log First Trade Execution", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayedTrades, key = { it.id }) { trade ->
                        TradeListItemCard(
                            trade = trade,
                            currency = settings.currency,
                            onClick = { viewModel.openEditTrade(trade) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }

        // Floating Action Button to Add Trade
        FloatingActionButton(
            onClick = { viewModel.openNewTradeEntry() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("fab_add_trade"),
            containerColor = primaryColor,
            contentColor = onPrimaryColor
        ) {
            Icon(Icons.Default.Add, contentDescription = "Log Trade")
        }
    }
}

@Composable
fun TradeListItemCard(
    trade: Trade,
    currency: CurrencyCode,
    onClick: () -> Unit
) {
    val isIndex = SyntheticIndexCatalog.isSyntheticIndex(trade.instrument)
    val positiveColor = MaterialTheme.colorScheme.primary
    val negativeColor = MaterialTheme.colorScheme.error

    LiquidGlassCard(
        onClick = onClick,
        testTag = "trade_item_${trade.id}"
    ) {
        // Top Row: Instrument, Direction, Tags, P&L
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = trade.instrument,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = if (trade.direction == TradeDirection.BUY) positiveColor.copy(alpha = 0.16f) else negativeColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (trade.direction == TradeDirection.BUY) "BUY" else "SELL",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (trade.direction == TradeDirection.BUY) positiveColor else negativeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (isIndex) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = positiveColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "SyntX",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = positiveColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                if (trade.isNewsTrade) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFFF59E0B).copy(alpha = 0.18f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = "News",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF59E0B)
                            )
                        }
                    }
                }
            }

            // P&L Amount
            Text(
                text = if (trade.result == TradeResult.NO_TRADE) "No Trade" else CurrencyFormatter.format(trade.pnl, currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = when (trade.result) {
                    TradeResult.WIN -> positiveColor
                    TradeResult.LOSS -> negativeColor
                    TradeResult.BREAK_EVEN -> MaterialTheme.colorScheme.onSurfaceVariant
                    TradeResult.NO_TRADE -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Middle Row: Strategy Name, Date & Time, Lot Size
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (trade.strategyName.isNotBlank()) trade.strategyName else "Discretionary",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${trade.date} ${trade.time}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                if (trade.lotSize != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${trade.lotSize} lots",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Bottom Row: Emotion Badge, Rule Discipline, Auto R:R
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EmotionBadge(emotion = trade.emotion)

                Surface(
                    color = if (trade.followedRules) positiveColor.copy(alpha = 0.12f) else negativeColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (trade.followedRules) "Rules Followed" else "Rules Broken",
                        color = if (trade.followedRules) positiveColor else negativeColor,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (trade.riskRewardRatio != null) {
                Text(
                    text = "1:${String.format(Locale.US, "%.1f", trade.riskRewardRatio)} R:R",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.Bold,
                    color = positiveColor
                )
            }
        }

        // Notes snippet if present
        if (trade.notes.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = trade.notes,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                maxLines = 2
            )
        }
    }
}
