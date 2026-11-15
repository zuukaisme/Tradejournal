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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.EconomicCalendarEvent
import com.example.presentation.TradingViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun EconomicCalendarScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    val events by viewModel.economicCalendarEvents.collectAsState()
    val isLoading by viewModel.isEconomicCalendarLoading.collectAsState()
    val status by viewModel.economicCalendarStatus.collectAsState()
    var selectedImpact by remember { mutableStateOf("All") }
    var selectedCurrency by remember { mutableStateOf("All") }
    var selectedRange by remember { mutableStateOf("This week") }
    var searchQuery by remember { mutableStateOf("") }
    var currencyMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshEconomicCalendar()
    }

    val currencies = remember(events) {
        listOf("All") + events.map { it.currency }.distinct().sorted()
    }
    val filteredEvents = remember(events, selectedImpact, selectedCurrency, selectedRange, searchQuery) {
        val now = Calendar.getInstance()
        val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        events.filter { event ->
            val eventDate = Calendar.getInstance().apply { timeInMillis = event.timestamp }
            val impactMatches = selectedImpact == "All" || event.impact.equals(selectedImpact, true)
            val currencyMatches = selectedCurrency == "All" || event.currency == selectedCurrency
            val dateMatches = when (selectedRange) {
                "Today" -> eventDate.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                    eventDate.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
                "Tomorrow" -> eventDate.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR) &&
                    eventDate.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR)
                else -> eventDate.get(Calendar.WEEK_OF_YEAR) == now.get(Calendar.WEEK_OF_YEAR) &&
                    eventDate.get(Calendar.YEAR) == now.get(Calendar.YEAR)
            }
            val searchMatches = searchQuery.isBlank() ||
                event.title.contains(searchQuery, true) ||
                event.currency.contains(searchQuery, true)
            impactMatches && currencyMatches && dateMatches && searchMatches
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Economic Calendar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${filteredEvents.size} matching events · Forex Factory", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { viewModel.refreshEconomicCalendar() }, enabled = !isLoading) {
                    if (isLoading) CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Refresh, contentDescription = "Refresh calendar", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search events or currency") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp)
            )
        }

        item {
            Text("Importance", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(listOf("All", "High", "Medium", "Low", "Holiday")) { impact ->
                    FilterChip(
                        selected = selectedImpact == impact,
                        onClick = { selectedImpact = impact },
                        label = { Text(impact) }
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    TextButton(onClick = { currencyMenuExpanded = true }) {
                        Text("Currency: $selectedCurrency")
                    }
                    DropdownMenu(
                        expanded = currencyMenuExpanded,
                        onDismissRequest = { currencyMenuExpanded = false }
                    ) {
                        currencies.forEach { currency ->
                            DropdownMenuItem(
                                text = { Text(currency) },
                                onClick = {
                                    selectedCurrency = currency
                                    currencyMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.width(4.dp))
                listOf("Today", "Tomorrow", "This week").forEach { range ->
                    FilterChip(
                        selected = selectedRange == range,
                        onClick = { selectedRange = range },
                        label = { Text(range, fontSize = 11.sp) }
                    )
                }
            }
        }

        if (status != null) {
            item {
                Text(status!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        if (isLoading && events.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (filteredEvents.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Text(
                        if (events.isEmpty()) "No calendar data loaded. Check your connection and try again."
                        else "No events match these filters.",
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filteredEvents, key = { it.id }) { event ->
                EconomicEventCard(event)
            }
        }

        item {
            Text(
                "Times are shown in your device's local timezone. Calendar data covers the current week. Source: Forex Factory.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun EconomicEventCard(event: EconomicCalendarEvent) {
    val dateFormat = remember { SimpleDateFormat("EEE, dd MMM · HH:mm", Locale.getDefault()) }
    val impactColor = when (event.impact.lowercase()) {
        "high" -> MaterialTheme.colorScheme.error
        "medium" -> androidx.compose.ui.graphics.Color(0xFFFFB74D)
        "holiday" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dateFormat.format(Date(event.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(event.currency, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(event.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(event.impact, color = impactColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                EventValue("Actual", event.actual)
                EventValue("Forecast", event.forecast)
                EventValue("Previous", event.previous)
            }
        }
    }
}

@Composable
private fun EventValue(label: String, value: String?) {
    Column(Modifier.width(88.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value?.ifBlank { "—" } ?: "—", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
