package com.example.presentation.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import com.example.domain.model.BottomNavStyle
import com.example.domain.model.CurrencyCode
import com.example.presentation.TradingViewModel
import com.example.presentation.components.CurrencyFormatter
import com.example.presentation.components.LiquidGlassCard
import com.example.presentation.components.LiquidGlassPill
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

@Composable
fun SettingsScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()
    val allTrades by viewModel.trades.collectAsState()
    val allStrategies by viewModel.strategies.collectAsState()
    val allBots by viewModel.bots.collectAsState()

    val sdfDay = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val todayStr = remember { sdfDay.format(Calendar.getInstance().time) }
    val currentMonthPrefix = remember { todayStr.take(7) }

    // Monthly Target & Daily Loss progress calculations
    val monthTrades = remember(allTrades, currentMonthPrefix) {
        allTrades.filter { it.date.startsWith(currentMonthPrefix) && it.result != com.example.domain.model.TradeResult.NO_TRADE }
    }
    val currentMonthPnL = remember(monthTrades) { monthTrades.sumOf { it.pnl } }
    val monthlyTargetProgress = remember(currentMonthPnL, settings.monthlyProfitTarget) {
        if (settings.monthlyProfitTarget > 0) {
            (currentMonthPnL / settings.monthlyProfitTarget).toFloat().coerceIn(0f, 1f)
        } else 0f
    }

    val todayTrades = remember(allTrades, todayStr) {
        allTrades.filter { it.date == todayStr && it.result != com.example.domain.model.TradeResult.NO_TRADE }
    }
    val todayNetPnL = remember(todayTrades) { todayTrades.sumOf { it.pnl } }
    val isDailyLossLimitHit = remember(todayNetPnL, settings.maxDailyLossLimit) {
        todayNetPnL < 0 && abs(todayNetPnL) >= settings.maxDailyLossLimit
    }

    var monthlyTargetInput by remember(settings.monthlyProfitTarget) {
        mutableStateOf(settings.monthlyProfitTarget.toString())
    }
    var maxDailyLossInput by remember(settings.maxDailyLossLimit) {
        mutableStateOf(settings.maxDailyLossLimit.toString())
    }
    var maxTrailingDrawdownInput by remember(settings.maxTrailingDrawdown) {
        mutableStateOf(settings.maxTrailingDrawdown.toString())
    }

    val dailyLossUsed = remember(todayNetPnL) { if (todayNetPnL < 0) abs(todayNetPnL) else 0.0 }
    val dailyLossProgress = remember(dailyLossUsed, settings.maxDailyLossLimit) {
        if (settings.maxDailyLossLimit > 0) (dailyLossUsed / settings.maxDailyLossLimit).toFloat().coerceIn(0f, 1f) else 0f
    }
    val trailingDrawdownStats = remember(allTrades) { computeTrailingDrawdown(allTrades) }
    val currentDrawdownFromPeak = trailingDrawdownStats.first
    val maxHistoricalDrawdown = trailingDrawdownStats.second
    val trailingDrawdownProgress = remember(currentDrawdownFromPeak, settings.maxTrailingDrawdown) {
        if (settings.maxTrailingDrawdown > 0) (currentDrawdownFromPeak / settings.maxTrailingDrawdown).toFloat().coerceIn(0f, 1f) else 0f
    }

    var fredApiKeyInput by remember(settings.fredApiKey) { mutableStateOf(settings.fredApiKey) }
    var geminiApiKeyInput by remember(settings.geminiApiKey) { mutableStateOf(settings.geminiApiKey) }

    var isOutlookEnabled by remember(settings.isOutlookEnabled) { mutableStateOf(settings.isOutlookEnabled) }
    var newsAlertEnabled by remember(settings.newsAlertEnabled) { mutableStateOf(settings.newsAlertEnabled) }

    var pinLockEnabled by remember(settings.pinLockEnabled) { mutableStateOf(settings.pinLockEnabled) }
    var pinCodeInput by remember(settings.pinCode) { mutableStateOf(settings.pinCode) }

    var eveningReminderEnabled by remember(settings.eveningReminderEnabled) { mutableStateOf(settings.eveningReminderEnabled) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            eveningReminderEnabled = true
            viewModel.updateEveningReminder(true)
            Toast.makeText(context, "Evening journal reminder scheduled for 20:00", Toast.LENGTH_SHORT).show()
        } else {
            eveningReminderEnabled = false
            viewModel.updateEveningReminder(false)
            Toast.makeText(context, "Notification permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Dialogs
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreJsonInput by remember { mutableStateOf("") }
    var showClearDataConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Preferences & Goals",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Daily Loss Limit Warning Banner if limit breached today
        if (isDailyLossLimitHit) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "DAILY LOSS LIMIT HIT!",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Today's loss (${CurrencyFormatter.format(todayNetPnL, settings.currency)}) has reached your risk cap (${CurrencyFormatter.format(settings.maxDailyLossLimit, settings.currency, false)}). Stop trading for today.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // 1. Currency Selector
        item {
            LiquidGlassCard {
                Text(
                    text = "Account Currency",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(CurrencyCode.values()) { currency ->
                        LiquidGlassPill(
                            text = currency.displayName,
                            isSelected = settings.currency == currency,
                            onClick = { viewModel.updateCurrency(currency) },
                            activeColor = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // 1a. App Color Theme Preset Selector
        item {
            ThemePresetSelectorCard(
                selectedPreset = settings.themePreset,
                onSelectPreset = { preset -> viewModel.updateThemePreset(preset) }
            )
        }

        // 1b. Bottom Navigation Tab Style (Playful switcher)
        item {
            LiquidGlassCard(testTag = "settings_bottom_nav_style_card") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Bottom Nav Tab Style",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Switch the look of the bottom bar anytime",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            val others = BottomNavStyle.values().filter { it != settings.bottomNavStyle }
                            if (others.isNotEmpty()) {
                                viewModel.updateBottomNavStyle(others.random())
                            }
                        },
                        modifier = Modifier.testTag("randomize_bottom_nav_style_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Randomize style",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Shuffle", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BottomNavStyle.values().forEach { style ->
                        val isSelected = settings.bottomNavStyle == style
                        val primary = MaterialTheme.colorScheme.primary
                        val rowBg = if (isSelected) {
                            primary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        }
                        val rowShape = if (style == BottomNavStyle.ARCADE_RETRO) {
                            RoundedCornerShape(6.dp)
                        } else {
                            RoundedCornerShape(12.dp)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clip(rowShape)
                                .background(rowBg)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(1.dp, primary.copy(alpha = 0.5f), rowShape)
                                    } else Modifier
                                )
                                .clickable { viewModel.updateBottomNavStyle(style) }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .testTag("bottom_nav_style_${style.name}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BottomNavStyleMiniSwatch(style = style, primary = primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = style.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = if (style == BottomNavStyle.ARCADE_RETRO) FontFamily.Monospace else FontFamily.Default,
                                    color = if (isSelected) primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = style.description,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Surface(
                                    color = primary,
                                    shape = CircleShape,
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Goals & Risk Limits
        item {
            LiquidGlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TrackChanges, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Trading Goals & Risk Limits",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Monthly Profit Target
                Text(
                    text = "Monthly Profit Target: ${CurrencyFormatter.format(currentMonthPnL, settings.currency)} / ${CurrencyFormatter.format(settings.monthlyProfitTarget, settings.currency, false)} (${(monthlyTargetProgress * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { monthlyTargetProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Daily Loss Limit Meter
                    Text(
                        text = "Daily Loss Used: ${CurrencyFormatter.format(dailyLossUsed, settings.currency, false)} / ${CurrencyFormatter.format(settings.maxDailyLossLimit, settings.currency, false)} (${(dailyLossProgress * 100).toInt()}%)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { dailyLossProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (dailyLossProgress >= 0.85f) MaterialTheme.colorScheme.error else Color(0xFFF59E0B),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Trailing Drawdown Meter
                    Text(
                        text = "Trailing Drawdown: ${CurrencyFormatter.format(currentDrawdownFromPeak, settings.currency, false)} / ${CurrencyFormatter.format(settings.maxTrailingDrawdown, settings.currency, false)} (Max ${CurrencyFormatter.format(maxHistoricalDrawdown, settings.currency, false)})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { trailingDrawdownProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (trailingDrawdownProgress >= 0.8f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = monthlyTargetInput,
                            onValueChange = { monthlyTargetInput = it },
                            label = { Text("Target") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = maxDailyLossInput,
                            onValueChange = { maxDailyLossInput = it },
                            label = { Text("Daily Loss") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = maxTrailingDrawdownInput,
                            onValueChange = { maxTrailingDrawdownInput = it },
                            label = { Text("Max DD") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val target = monthlyTargetInput.toDoubleOrNull() ?: 2500.0
                            val lossLimit = maxDailyLossInput.toDoubleOrNull() ?: 300.0
                            val trailingDd = maxTrailingDrawdownInput.toDoubleOrNull() ?: 2000.0
                            viewModel.updateGoals(target, lossLimit, trailingDd)
                            Toast.makeText(context, "Goals & risk limits updated", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save Goals & Risk Limits")
                    }
            }
        }

        // 3. News Outlook & API Keys
        item {
            LiquidGlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "News Outlook & Macro Feeds",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Switch(
                            checked = isOutlookEnabled,
                            onCheckedChange = {
                                isOutlookEnabled = it
                                viewModel.updateSettings(settings.copy(isOutlookEnabled = it))
                            }
                        )
                    }

                    if (isOutlookEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Release Alert notification
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Alert 30 mins before NFP / CPI",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Switch(
                                checked = newsAlertEnabled,
                                onCheckedChange = {
                                    newsAlertEnabled = it
                                    viewModel.updateSettings(settings.copy(newsAlertEnabled = it))
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = fredApiKeyInput,
                            onValueChange = { fredApiKeyInput = it },
                            label = { Text("FRED API Key (Federal Reserve)") },
                            placeholder = { Text("Optional for live macro data") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = geminiApiKeyInput,
                            onValueChange = { geminiApiKeyInput = it },
                            label = { Text("Gemini API Key (AI Summaries)") },
                            placeholder = { Text("Configured via Secrets panel") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                viewModel.updateSettings(
                                    settings.copy(
                                        fredApiKey = fredApiKeyInput.trim(),
                                        geminiApiKey = geminiApiKeyInput.trim(),
                                        isOutlookEnabled = isOutlookEnabled,
                                        newsAlertEnabled = newsAlertEnabled
                                    )
                                )
                                Toast.makeText(context, "API credentials stored securely", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save API Credentials")
                        }
                    }
            }
        }

        // 4. Evening Reminder & PIN Security
        item {
            LiquidGlassCard {
                    // Evening Reminder
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(text = "Evening Journal Reminder", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(text = "Prompt at 20:00 to log daily review", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = eveningReminderEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!granted) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        eveningReminderEnabled = true
                                        viewModel.updateEveningReminder(true)
                                    }
                                } else {
                                    eveningReminderEnabled = enabled
                                    viewModel.updateEveningReminder(enabled)
                                }
                            },
                            modifier = Modifier.testTag("evening_reminder_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // PIN Security Lock
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(text = "PIN Lock", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(text = "Require 4-digit code on open", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = pinLockEnabled,
                            onCheckedChange = { enabled ->
                                pinLockEnabled = enabled
                                viewModel.updateSettings(
                                    settings.copy(
                                        pinLockEnabled = enabled,
                                        pinCode = pinCodeInput
                                    )
                                )
                            },
                            modifier = Modifier.testTag("pin_lock_switch")
                        )
                    }

                    if (pinLockEnabled) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = pinCodeInput,
                            onValueChange = { raw ->
                                val filtered = raw.filter(Char::isDigit).take(4)
                                pinCodeInput = filtered
                                if (filtered.length == 4) {
                                    viewModel.updateSettings(
                                        settings.copy(
                                            pinLockEnabled = true,
                                            pinCode = filtered
                                        )
                                    )
                                    Toast.makeText(context, "4-digit PIN saved", Toast.LENGTH_SHORT).show()
                                }
                            },
                            label = { Text("4-Digit PIN") },
                            supportingText = {
                                Text(
                                    text = if (pinCodeInput.length == 4) {
                                        "PIN active — required on next app launch"
                                    } else {
                                        "Enter 4 digits to activate lock"
                                    }
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pin_code_input"),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
            }
        }

        // 5. Data Management: CSV Export, Backup & Restore JSON, Reset
        item {
            LiquidGlassCard {
                    Text(
                        text = "Data Backup & Export",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Export CSV
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val csv = viewModel.repository.exportTradesToCsv(allTrades)
                                val sendIntent: Intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, csv)
                                    putExtra(Intent.EXTRA_TITLE, "TradePulse_Trades.csv")
                                    type = "text/csv"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Export Trades to CSV"))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_csv_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export All Trades to CSV")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Full JSON Backup
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val json = viewModel.repository.exportFullBackupJson(allTrades, allStrategies, allBots)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("TradePulse Backup", json)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Full JSON Backup copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backup_json_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Backup All Data to JSON (Clipboard)")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Restore from JSON
                    OutlinedButton(
                        onClick = { showRestoreDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("restore_json_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore from JSON Backup")
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Clear All Data
                    Button(
                        onClick = { showClearDataConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset & Clear All Data")
                    }
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // Restore Dialog
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Paste JSON Backup") },
            text = {
                OutlinedTextField(
                    value = restoreJsonInput,
                    onValueChange = { restoreJsonInput = it },
                    placeholder = { Text("Paste your full JSON backup string here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    shape = RoundedCornerShape(10.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val success = viewModel.repository.restoreFullBackupJson(restoreJsonInput)
                            showRestoreDialog = false
                            if (success) {
                                Toast.makeText(context, "Data restored successfully", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Invalid JSON backup format", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear Data Confirmation Dialog
    if (showClearDataConfirm) {
        AlertDialog(
            onDismissRequest = { showClearDataConfirm = false },
            title = { Text("Clear All Records?") },
            text = { Text("Are you sure you want to delete all trades, strategies, bots, and reviews? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData()
                        showClearDataConfirm = false
                        Toast.makeText(context, "All records wiped", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Yes, Delete All")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearDataConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun BottomNavStyleMiniSwatch(
    style: BottomNavStyle,
    primary: Color
) {
    val swatchBg = MaterialTheme.colorScheme.surface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(
                when (style) {
                    BottomNavStyle.ARCADE_RETRO -> RoundedCornerShape(3.dp)
                    BottomNavStyle.MINIMAL_RAIL -> RoundedCornerShape(4.dp)
                    else -> RoundedCornerShape(12.dp)
                }
            )
            .background(swatchBg)
            .then(
                if (style == BottomNavStyle.ARCADE_RETRO) {
                    Modifier.border(1.dp, primary.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                } else Modifier
            )
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        when (style) {
            BottomNavStyle.LIQUID_GLASS -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 14.dp, height = 14.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(primary.copy(alpha = 0.3f))
                            .border(0.75.dp, primary, RoundedCornerShape(6.dp))
                    )
                    repeat(2) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(muted))
                    }
                }
            }
            BottomNavStyle.NEON_ORBIT -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(primary))
                    repeat(2) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(muted))
                    }
                }
            }
            BottomNavStyle.ARCADE_RETRO -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(width = 12.dp, height = 14.dp).background(primary))
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .size(width = 10.dp, height = 14.dp)
                                .border(0.75.dp, muted)
                        )
                    }
                }
            }
            BottomNavStyle.MINIMAL_RAIL -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 14.dp, height = 3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(primary)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(primary))
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(muted))
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(muted))
                    }
                }
            }
            BottomNavStyle.BUBBLE_BOUNCE -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 20.dp, height = 14.dp)
                            .clip(CircleShape)
                            .background(primary.copy(alpha = 0.28f))
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 4.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(primary)
                        )
                    }
                    repeat(2) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(muted))
                    }
                }
            }
        }
    }
}
