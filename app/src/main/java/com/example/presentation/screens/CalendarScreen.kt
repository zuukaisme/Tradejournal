package com.example.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.example.ui.theme.SlateBreakEven
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    val trades by viewModel.trades.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val dailyReviews by viewModel.dailyReviews.collectAsState()
    val selectedDate by viewModel.selectedDateForDaySheet.collectAsState()

    var currentCalendar by remember {
        mutableStateOf(Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
        })
    }

    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.US) }
    val dayFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val todayStr = remember { dayFormat.format(Calendar.getInstance().time) }

    // Aggregate trades by day
    val tradesByDate = remember(trades) { trades.groupBy { it.date } }
    val reviewsByDate = remember(dailyReviews) { dailyReviews.associateBy { it.date } }

    // Current month prefix (YYYY-MM)
    val currentYearMonthPrefix = remember(currentCalendar) {
        val y = currentCalendar.get(Calendar.YEAR)
        val m = currentCalendar.get(Calendar.MONTH) + 1
        String.format(Locale.US, "%04d-%02d", y, m)
    }

    // Monthly Summary Calculations
    val monthTrades = remember(trades, currentYearMonthPrefix) {
        trades.filter { it.date.startsWith(currentYearMonthPrefix) }
    }
    val validMonthTrades = remember(monthTrades) {
        monthTrades.filter { it.result != TradeResult.NO_TRADE }
    }
    val monthNetPnL = remember(validMonthTrades) {
        validMonthTrades.sumOf { it.pnl }
    }
    val monthWins = remember(validMonthTrades) {
        validMonthTrades.count { it.pnl > 0 || it.result == TradeResult.WIN }
    }
    val monthWinRate = remember(validMonthTrades, monthWins) {
        if (validMonthTrades.isNotEmpty()) (monthWins.toDouble() / validMonthTrades.size) * 100.0 else 0.0
    }

    // Days in current month grid
    val daysInMonth = currentCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = (currentCalendar.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7 // Monday = 0

    // Count green and red days
    val monthDays = remember(tradesByDate, currentYearMonthPrefix) {
        tradesByDate.filterKeys { it.startsWith(currentYearMonthPrefix) }
    }
    val greenDays = monthDays.count { (_, list) -> list.sumOf { it.pnl } > 0 }
    val redDays = monthDays.count { (_, list) -> list.sumOf { it.pnl } < 0 }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Month Selector Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val prev = currentCalendar.clone() as Calendar
                    prev.add(Calendar.MONTH, -1)
                    currentCalendar = prev
                },
                modifier = Modifier.testTag("calendar_prev_month")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous Month",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = monthYearFormat.format(currentCalendar.time),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            IconButton(
                onClick = {
                    val next = currentCalendar.clone() as Calendar
                    next.add(Calendar.MONTH, 1)
                    currentCalendar = next
                },
                modifier = Modifier.testTag("calendar_next_month")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next Month",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Month Summary KPI Card (Liquid Glass style, borderless)
        LiquidGlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Net PnL
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Net P&L",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.format(monthNetPnL, settings.currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (monthNetPnL >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                // Win Rate
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Win Rate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f%%", monthWinRate),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Green / Red Days
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Days W / L",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$greenDays",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = " / ",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$redDays",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Weekday Headers (Mon - Sun)
        val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            weekdays.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Calendar Days Grid
        val totalCells = firstDayOfWeek + daysInMonth
        val rows = (totalCells + 6) / 7

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (row in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (col in 0..6) {
                        val cellIndex = row * 7 + col
                        val dayNumber = cellIndex - firstDayOfWeek + 1

                        if (dayNumber in 1..daysInMonth) {
                            val year = currentCalendar.get(Calendar.YEAR)
                            val month = currentCalendar.get(Calendar.MONTH) + 1
                            val dateStr = String.format(Locale.US, "%04d-%02d-%02d", year, month, dayNumber)

                            val dayTrades = tradesByDate[dateStr] ?: emptyList()
                            val validDayTrades = dayTrades.filter { it.result != TradeResult.NO_TRADE }
                            val isNoTradeDay = dayTrades.any { it.result == TradeResult.NO_TRADE } && validDayTrades.isEmpty()
                            val dayNet = validDayTrades.sumOf { it.pnl }
                            val isToday = (dateStr == todayStr)
                            val hasReview = reviewsByDate.containsKey(dateStr)

                            CalendarDayCell(
                                dayNumber = dayNumber,
                                dateStr = dateStr,
                                netPnL = dayNet,
                                hasTrades = dayTrades.isNotEmpty(),
                                isNoTradeDay = isNoTradeDay,
                                isToday = isToday,
                                hasReview = hasReview,
                                currency = settings.currency,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .testTag("calendar_day_$dayNumber"),
                                onClick = { viewModel.openDayDetails(dateStr) }
                            )
                        } else {
                            // Blank cell for padding
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                            )
                        }
                    }
                }
            }
        }
    }

    // Day Details Bottom Sheet
    if (selectedDate != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val dayTrades = tradesByDate[selectedDate] ?: emptyList()
        val review = reviewsByDate[selectedDate]
        val dayNet = dayTrades.filter { it.result != TradeResult.NO_TRADE }.sumOf { it.pnl }

        ModalBottomSheet(
            onDismissRequest = { viewModel.closeDayDetails() },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Trades for $selectedDate",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Day Net: ${CurrencyFormatter.format(dayNet, settings.currency)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (dayNet >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row {
                        IconButton(
                            onClick = {
                                viewModel.closeDayDetails()
                                viewModel.openDailyReview(selectedDate)
                            },
                            modifier = Modifier.testTag("day_review_button")
                        ) {
                            Icon(
                                Icons.Default.RateReview,
                                contentDescription = "Daily Review",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { viewModel.closeDayDetails() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (review != null) {
                    LiquidGlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "End of Day Review",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                repeat(review.disciplineRating) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                        if (review.reflection.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = review.reflection,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (dayTrades.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No trades logged on this day",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(dayTrades) { trade ->
                            DayTradeItem(
                                trade = trade,
                                currency = settings.currency,
                                onClick = {
                                    viewModel.closeDayDetails()
                                    viewModel.openEditTrade(trade)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        viewModel.closeDayDetails()
                        viewModel.openNewTradeEntry(selectedDate)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_trade_for_day_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Log Trade on $selectedDate", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun CalendarDayCell(
    dayNumber: Int,
    dateStr: String,
    netPnL: Double,
    hasTrades: Boolean,
    isNoTradeDay: Boolean,
    isToday: Boolean,
    hasReview: Boolean,
    currency: CurrencyCode,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val positiveColor = MaterialTheme.colorScheme.primary
    val negativeColor = MaterialTheme.colorScheme.error
    val (bgColor, textColor, pnlColor) = when {
        !hasTrades -> Triple(
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            Color.Transparent
        )
        isNoTradeDay -> Triple(
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            MaterialTheme.colorScheme.onSurfaceVariant,
            SlateBreakEven
        )
        netPnL > 0 -> Triple(
            positiveColor.copy(alpha = 0.16f),
            MaterialTheme.colorScheme.onSurface,
            positiveColor
        )
        netPnL < 0 -> Triple(
            negativeColor.copy(alpha = 0.16f),
            MaterialTheme.colorScheme.onSurface,
            negativeColor
        )
        else -> Triple(
            SlateBreakEven.copy(alpha = 0.16f),
            MaterialTheme.colorScheme.onSurface,
            SlateBreakEven
        )
    }

    // Less borders: only subtle highlight for today, otherwise borderless liquid glass
    val borderModifier = if (isToday) {
        Modifier.border(1.5.dp, positiveColor, RoundedCornerShape(8.dp))
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .then(borderModifier)
            .clickable { onClick() }
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$dayNumber",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isToday) positiveColor else textColor
                )
                if (hasReview) {
                    Box(
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .size(4.dp)
                            .background(Color(0xFFFFB300), CircleShape)
                    )
                }
            }

            if (hasTrades) {
                if (isNoTradeDay) {
                    Text(
                        text = "—",
                        fontSize = 8.sp,
                        color = SlateBreakEven,
                        maxLines = 1
                    )
                } else {
                    val formatted = when {
                        kotlin.math.abs(netPnL) >= 1000 -> String.format(Locale.US, "%s%.0fk", currency.symbol, netPnL / 1000.0)
                        else -> String.format(Locale.US, "%s%.0f", currency.symbol, netPnL)
                    }
                    Text(
                        text = formatted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = pnlColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun DayTradeItem(
    trade: Trade,
    currency: CurrencyCode,
    onClick: () -> Unit
) {
    val isIndex = SyntheticIndexCatalog.isSyntheticIndex(trade.instrument)
    val positiveColor = MaterialTheme.colorScheme.primary
    val negativeColor = MaterialTheme.colorScheme.error

    LiquidGlassCard(
        onClick = onClick,
        cornerRadius = 10.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = trade.instrument,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = if (trade.direction == TradeDirection.BUY) positiveColor.copy(alpha = 0.16f) else negativeColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (trade.direction == TradeDirection.BUY) "BUY" else "SELL",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (trade.direction == TradeDirection.BUY) positiveColor else negativeColor,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                if (isIndex) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        color = positiveColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "SyntX",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = positiveColor,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Text(
                text = if (trade.result == TradeResult.NO_TRADE) "Didn't Trade" else CurrencyFormatter.format(trade.pnl, currency),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = when (trade.result) {
                    TradeResult.WIN -> positiveColor
                    TradeResult.LOSS -> negativeColor
                    TradeResult.BREAK_EVEN -> MaterialTheme.colorScheme.onSurfaceVariant
                    TradeResult.NO_TRADE -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (trade.strategyName.isNotBlank()) trade.strategyName else "Discretionary",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            EmotionBadge(emotion = trade.emotion)
        }
    }
}
