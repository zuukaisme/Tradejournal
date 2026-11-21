package com.example.presentation.screens

import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.backtest.BacktestCodec
import com.example.domain.model.Bot
import com.example.domain.model.BotStatus
import com.example.domain.model.BotType
import com.example.presentation.TradingViewModel
import com.example.mt5.Mt5LocalBridgeServer
import com.example.mt5.Mt5BotArtifactManager
import com.example.presentation.components.CurrencyFormatter
import com.example.presentation.components.LiquidGlassCard
import com.example.presentation.components.LiquidGlassPill
import com.example.presentation.components.LiquidGlassTabRow
import com.example.presentation.components.Mql5CodeViewer
import com.example.ui.theme.AmberGold
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.SlateBreakEven
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BotsScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    val bots by viewModel.bots.collectAsState()
    val allTrades by viewModel.trades.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val selectedBot by viewModel.selectedBotForView.collectAsState()
    val isBotEditorOpen by viewModel.isBotEditorOpen.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf<BotType?>(null) }
    var selectedStatusFilter by remember { mutableStateOf<BotStatus?>(null) }
    var runTarget by remember { mutableStateOf<Bot?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mt5BridgeStatus by Mt5LocalBridgeServer.status.collectAsState()

    val botArtifactImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = runTarget
        if (uri != null && target != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val displayName = context.contentResolver.query(
                            uri,
                            arrayOf(OpenableColumns.DISPLAY_NAME),
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        }.orEmpty()
                        Mt5BotArtifactManager.importArtifact(context, target.id, displayName, uri).getOrThrow()
                    }
                }
                Toast.makeText(
                    context,
                    if (result.isSuccess) "Compiled bot stored locally. MT5 deployment is still pending runtime integration." else result.exceptionOrNull()?.message ?: "Import failed",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val botExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val target = runTarget
        if (uri != null && target != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(target.code.toByteArray(Charsets.UTF_8))
                        } ?: error("Unable to create the bot source file.")
                    }
                }
                Toast.makeText(
                    context,
                    if (result.isSuccess) "${target.name} exported" else result.exceptionOrNull()?.message ?: "Export failed",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val filteredBots = remember(bots, searchQuery, selectedTypeFilter, selectedStatusFilter) {
        bots.filter { bot ->
            val matchesQuery = searchQuery.isBlank() ||
                    bot.name.contains(searchQuery, ignoreCase = true) ||
                    bot.instrument.contains(searchQuery, ignoreCase = true) ||
                    bot.tags.any { it.contains(searchQuery, ignoreCase = true) }
            val matchesType = selectedTypeFilter == null || bot.type == selectedTypeFilter
            val matchesStatus = selectedStatusFilter == null || bot.status == selectedStatusFilter
            matchesQuery && matchesType && matchesStatus
        }
    }

    // If viewing a single bot's code & details
    val currentBot = selectedBot
    if (currentBot != null && !isBotEditorOpen) {
        BotDetailScreen(
            bot = currentBot,
            viewModel = viewModel,
            onBack = { viewModel.closeBotDetails() },
            onEditCode = { viewModel.openEditBotCode(currentBot) }
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MQL5 Bot Library",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${bots.size} EAs, Indicators & Scripts stored offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { viewModel.openNewBotEditor() },
                    modifier = Modifier.testTag("add_bot_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Bot")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search bot name, pair, tags...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Liquid Glass Filter Pills
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    LiquidGlassPill(
                        text = "All Types",
                        isSelected = selectedTypeFilter == null,
                        onClick = { selectedTypeFilter = null },
                        activeColor = MaterialTheme.colorScheme.primary
                    )
                }
                items(BotType.values()) { type ->
                    LiquidGlassPill(
                        text = type.label.take(18),
                        isSelected = selectedTypeFilter == type,
                        onClick = {
                            selectedTypeFilter = if (selectedTypeFilter == type) null else type
                        },
                        activeColor = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (filteredBots.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No bots match criteria. Tap '+ Add Bot' to import or paste MQL5 code.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredBots, key = { it.id }) { bot ->
                        val botTrades = remember(allTrades, bot.id) {
                            allTrades.filter { it.botId == bot.id }
                        }
                        val botPnL = botTrades.sumOf { it.pnl }
                        val botWins = botTrades.count { it.pnl > 0 }
                        val botWinRate = if (botTrades.isNotEmpty()) (botWins.toDouble() / botTrades.size) * 100.0 else 0.0

                        BotItemCard(
                            bot = bot,
                            tradesCount = botTrades.size,
                            winRate = botWinRate,
                            totalPnL = botPnL,
                            currency = settings.currency,
                            onClick = { viewModel.openBotDetails(bot) },
                            onRun = { runTarget = bot }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { viewModel.openNewBotEditor() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("fab_add_bot"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Bot")
        }
    }

    runTarget?.let { bot ->
        val isLibrary = bot.type == BotType.LIBRARY
        val sourceReady = bot.code.isNotBlank()
        AlertDialog(
            onDismissRequest = { runTarget = null },
            title = { Text("Run ${bot.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("MT5 bridge: $mt5BridgeStatus", style = MaterialTheme.typography.bodySmall)
                    Text(
                        when {
                            isLibrary -> "MQL5 libraries cannot run by themselves. An Expert Advisor, indicator or script must load or call the library."
                            !sourceReady -> "This bot has no source code to prepare."
                            mt5BridgeStatus == "MT5 Expert Advisor authenticated" -> "The terminal bridge is connected, but automatic bot deployment and launch are not implemented yet."
                            else -> "The MT5 terminal is not connected. You can still prepare and export this bot's source while the Wine runtime is being completed."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        if (Mt5BotArtifactManager.hasArtifact(context, bot.id)) {
                            "A compiled .ex5 artifact is stored locally for this bot. Automatic deployment is not available until the embedded MT5 runtime is integrated."
                        } else {
                            "You can import a compiled .ex5 artifact or export the editable source. Files remain stored locally until MT5 deployment is implemented."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = { botArtifactImportLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                        enabled = bot.id > 0L && !isLibrary
                    ) { Text("Import .ex5") }
                    TextButton(
                        onClick = {
                            val extension = if (isLibrary) "mqh" else "mq5"
                            val safeName = bot.name.replace(Regex("[^A-Za-z0-9_-]"), "_").take(48).ifBlank { "TradePulseBot" }
                            botExportLauncher.launch("$safeName.$extension")
                        },
                        enabled = sourceReady
                    ) { Text("Export source") }
                }
            },
            dismissButton = {
                TextButton(onClick = { runTarget = null }) { Text("Close") }
            }
        )
    }

    if (isBotEditorOpen) {
        BotEditorBottomSheet(
            bot = selectedBot,
            viewModel = viewModel,
            onDismiss = { viewModel.closeBotEditor() }
        )
    }
}

@Composable
fun BotItemCard(
    bot: Bot,
    tradesCount: Int,
    winRate: Double,
    totalPnL: Double,
    currency: com.example.domain.model.CurrencyCode,
    onClick: () -> Unit,
    onRun: () -> Unit
) {
    LiquidGlassCard(
        onClick = onClick,
        testTag = "bot_card_${bot.id}"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = bot.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                color = when (bot.status) {
                    BotStatus.LIVE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    BotStatus.TESTING -> AmberGold.copy(alpha = 0.2f)
                    BotStatus.RETIRED -> SlateBreakEven.copy(alpha = 0.2f)
                },
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = bot.status.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when (bot.status) {
                        BotStatus.LIVE -> MaterialTheme.colorScheme.primary
                        BotStatus.TESTING -> AmberGold
                        BotStatus.RETIRED -> SlateBreakEven
                    },
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${bot.type.label} • v${bot.version} • ${bot.timeframe}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = bot.instrument,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (bot.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = bot.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$tradesCount Linked Trades",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Win Rate: ${String.format(Locale.US, "%.0f%%", winRate)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = CurrencyFormatter.format(totalPnL, currency),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.Bold,
                    color = if (totalPnL >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }

        OutlinedButton(
            onClick = onRun,
            modifier = Modifier.fillMaxWidth().testTag("run_bot_${bot.id}"),
            shape = RoundedCornerShape(9.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Run")
        }
    }
}

@Composable
fun BotDetailScreen(
    bot: Bot,
    viewModel: TradingViewModel,
    onBack: () -> Unit,
    onEditCode: () -> Unit
) {
    var selectedTab by remember(bot.id) { mutableStateOf(0) }
    var showRunDialog by remember(bot.id) { mutableStateOf(false) }
    val bridgeStatus by Mt5LocalBridgeServer.status.collectAsState()
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(bot.code.toByteArray(Charsets.UTF_8))
                } ?: error("Unable to create the bot source file.")
            }.onSuccess {
                Toast.makeText(context, "${bot.name} exported", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(context, it.message ?: "Export failed", Toast.LENGTH_LONG).show()
            }
        }
    }
    val versions by viewModel.repository.getBotVersions(bot.id).collectAsState(initial = emptyList())
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

    BackHandler(onBack = onBack)

    if (showRunDialog) {
        val isLibrary = bot.type == BotType.LIBRARY
        AlertDialog(
            onDismissRequest = { showRunDialog = false },
            title = { Text("Prepare ${bot.name} for MT5") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bridge: $bridgeStatus", style = MaterialTheme.typography.bodySmall)
                    Text(
                        when {
                            isLibrary -> "MQL5 libraries cannot run by themselves. An EA, indicator or script must load or call this library."
                            bot.code.isBlank() -> "This bot has no source code to export."
                            bridgeStatus == "MT5 Expert Advisor authenticated" -> "The bridge is connected. Automatic deployment, chart attachment and bot start/stop are not available yet."
                            else -> "The MT5 terminal is not connected. Export the source and compile it in MetaEditor after MT5 is installed."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Exporting does not start the bot or place trades.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val extension = if (isLibrary) "mqh" else "mq5"
                        val safeName = bot.name.replace(Regex("[^A-Za-z0-9_-]"), "_").take(48).ifBlank { "TradePulseBot" }
                        exportLauncher.launch("$safeName.$extension")
                        showRunDialog = false
                    },
                    enabled = bot.code.isNotBlank()
                ) { Text("Export source") }
            },
            dismissButton = {
                TextButton(onClick = { showRunDialog = false }) { Text("Close") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column {
                        Text(
                            text = bot.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${bot.type.label} • v${bot.version}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    IconButton(onClick = { showRunDialog = true }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Prepare for MT5")
                    }
                    IconButton(onClick = onEditCode) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Code")
                    }
                    IconButton(onClick = { viewModel.deleteBot(bot.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            LiquidGlassTabRow(
                tabs = listOf("Code Viewer", "Backtest", "Versions (${versions.size})"),
                selectedIndex = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }

        when (selectedTab) {
            0 -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    Mql5CodeViewer(
                        code = bot.code,
                        botName = bot.name,
                        isDarkTheme = LocalIsDarkTheme.current
                    )
                }
            }
            1 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    LiquidGlassCard {
                        Text(
                            text = "Backtest Results & Parameters",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (bot.backtestNotes.isNotBlank()) bot.backtestNotes else "No backtest parameters recorded yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LiquidGlassCard {
                        Text(
                            text = "General Notes & Setup Guide",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (bot.notes.isNotBlank()) bot.notes else "No notes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            2 -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (versions.isEmpty()) {
                        item {
                            Text(
                                text = "Current version: ${bot.version}. Updates will create version snapshots.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        items(versions) { ver ->
                            LiquidGlassCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(text = "v${ver.version}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                        Text(text = ver.changeNote, style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            text = sdf.format(Date(ver.timestamp)),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.restoreBotVersion(bot.id, ver) },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Restore", fontSize = 11.sp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotEditorBottomSheet(
    bot: Bot?,
    viewModel: TradingViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val strategies by viewModel.strategies.collectAsState()
    val context = LocalContext.current

    var name by remember(bot?.id) { mutableStateOf(bot?.name ?: "") }
    var description by remember(bot?.id) { mutableStateOf(bot?.description ?: "") }
    var version by remember(bot?.id) { mutableStateOf(bot?.version ?: "1.0.0") }
    var type by remember(bot?.id) { mutableStateOf(bot?.type ?: BotType.EXPERT_ADVISOR) }
    var instrument by remember(bot?.id) { mutableStateOf(bot?.instrument ?: "XAUUSD") }
    var timeframe by remember(bot?.id) { mutableStateOf(bot?.timeframe ?: "H1") }
    var status by remember(bot?.id) { mutableStateOf(bot?.status ?: BotStatus.TESTING) }
    var selectedStrategyId by remember(bot?.id) { mutableStateOf(bot?.strategyId) }
    var code by remember(bot?.id) { mutableStateOf(bot?.code ?: "// Paste your MQL5 source code here\n") }
    var backtestNotes by remember(bot?.id) { mutableStateOf(bot?.backtestNotes ?: "") }
    var changeNote by remember(bot?.id) { mutableStateOf("") }
    var importGeneration by remember(bot?.id) { mutableIntStateOf(0) }
    var isImportingFile by remember(bot?.id) { mutableStateOf(false) }
    var formError by remember(bot?.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isImportingFile = true
                formError = null
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        var fileName: String? = null
                        var reportedSize = -1L
                        context.contentResolver.query(
                            uri,
                            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                                    fileName = cursor.getString(nameIdx)
                                }
                                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                                    reportedSize = cursor.getLong(sizeIdx)
                                }
                            }
                        }
                        val resolvedName = fileName?.takeIf { it.isNotBlank() }
                            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                            ?: "ImportedBot.mq5"
                        val extension = resolvedName.substringAfterLast('.', "").lowercase(Locale.US)
                        val allowedExtensions = setOf("mq5", "mq4", "mqh", "txt")
                        if (extension.isNotEmpty() && extension !in allowedExtensions) {
                            throw IllegalArgumentException(
                                "Choose an MQL source file: .mq5, .mq4, .mqh or .txt (selected .$extension)."
                            )
                        }
                        val maxSourceBytes = 16L * 1024L * 1024L
                        if (reportedSize > maxSourceBytes) {
                            throw IllegalArgumentException("The selected file exceeds the 16 MB import limit.")
                        }
                        val importedCode = context.contentResolver.openInputStream(uri)?.use { stream ->
                            BacktestCodec.readTextStreamWithBom(stream, maxSourceBytes)
                        } ?: error("The selected file could not be read.")
                        require(importedCode.isNotBlank()) { "The selected file is empty." }
                        Triple(resolvedName, extension.ifEmpty { "mq5" }, importedCode)
                    }
                }
                isImportingFile = false
                result.onSuccess { (fileName, extension, importedCode) ->
                    code = importedCode
                    importGeneration++
                    if (bot == null || name.isBlank()) {
                        name = fileName.substringBeforeLast('.').ifBlank { "ImportedBot" }
                    }
                    if (bot == null) {
                        type = if (extension == "mqh") BotType.LIBRARY else BotType.EXPERT_ADVISOR
                    }
                    Toast.makeText(
                        context,
                        "Imported $fileName (${importedCode.lineSequence().count()} lines)",
                        Toast.LENGTH_SHORT
                    ).show()
                }.onFailure { error ->
                    formError = error.message ?: "Unable to import this file."
                }
            }
        }
    }

    var typeDropdownExpanded by remember { mutableStateOf(false) }
    var statusDropdownExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .heightIn(min = 420.dp, max = 640.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (bot == null) "Add MQL5 Bot" else "Edit ${bot.name}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Bot / Script Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it },
                    label = { Text("Version") },
                    singleLine = true,
                    modifier = Modifier.weight(0.8f),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Type dropdown
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { typeDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = type.label.take(16), fontSize = 11.sp, maxLines = 1)
                    }
                    DropdownMenu(
                        expanded = typeDropdownExpanded,
                        onDismissRequest = { typeDropdownExpanded = false }
                    ) {
                        BotType.values().forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.label) },
                                onClick = {
                                    type = t
                                    typeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Status dropdown
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { statusDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = status.label, fontSize = 11.sp)
                    }
                    DropdownMenu(
                        expanded = statusDropdownExpanded,
                        onDismissRequest = { statusDropdownExpanded = false }
                    ) {
                        BotStatus.values().forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.label) },
                                onClick = {
                                    status = s
                                    statusDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Source Code File",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                enabled = !isImportingFile,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isImportingFile) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Importing large file...")
                } else {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import .mq5, .mq4, .mqh or .txt")
                }
            }
            if (formError != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Editable MQL5 Code Area
            val isDark = LocalIsDarkTheme.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Mql5CodeViewer(
                    code = code,
                    botName = name.ifBlank { "Expert" },
                    isDarkTheme = isDark,
                    isEditable = true,
                    onCodeChange = { code = it },
                    sourceKey = importGeneration
                )
            }

            if (bot != null) {
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = changeNote,
                    onValueChange = { changeNote = it },
                    placeholder = { Text("Changelog note for version history...", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isEmpty()) {
                        formError = "Enter a bot or script name."
                        return@Button
                    }
                    if (code.isBlank()) {
                        formError = "Bot source code cannot be empty."
                        return@Button
                    }
                    formError = null
                    val strategy = strategies.firstOrNull { it.id == selectedStrategyId }
                    val toSave = Bot(
                        id = bot?.id ?: 0L,
                        name = trimmedName,
                        description = description.trim(),
                        version = version.trim(),
                        type = type,
                        instrument = instrument.trim(),
                        timeframe = timeframe.trim(),
                        strategyId = selectedStrategyId,
                        strategyName = strategy?.name ?: "",
                        status = status,
                        tags = listOf(type.name, instrument),
                        notes = "",
                        code = code,
                        backtestNotes = backtestNotes,
                        createdAt = bot?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )

                    if (bot != null) {
                        viewModel.saveBotWithVersion(toSave, changeNote)
                    } else {
                        viewModel.saveBot(toSave)
                    }
                },
                enabled = !isImportingFile,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_bot_code_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (bot == null) "Save Bot" else "Save Changes & Record Version")
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
