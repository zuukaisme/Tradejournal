package com.example.presentation.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.backtest.BacktestCodec
import com.example.domain.model.PythonStrategy
import com.example.domain.model.Strategy
import com.example.presentation.TradingViewModel
import com.example.presentation.components.CurrencyFormatter
import com.example.presentation.components.LiquidGlassCard
import com.example.presentation.components.LiquidGlassTabRow
import com.example.ui.theme.CrimsonRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private data class PendingPythonUpload(
    val suggestedName: String,
    val code: String
)

private data class BuiltInPythonPreset(
    val title: String,
    val subtitle: String,
    val className: String
)

private val BUILT_IN_TEXTBOOK_PRESETS = listOf(
    BuiltInPythonPreset(
        title = "Textbook Tri-Horizon (Auto)",
        subtitle = "Scalp + Hold Hours + Hold Days",
        className = "InstitutionalTriHorizonStrategy"
    ),
    BuiltInPythonPreset(
        title = "Tri-Horizon: Scalp Kill-Zone",
        subtitle = "Minutes–2h sweeps & ModMACD",
        className = "ScalpKillZoneStrategy"
    ),
    BuiltInPythonPreset(
        title = "Tri-Horizon: Hold for Hours",
        subtitle = "London/NY H1 OB + 25/240 MA",
        className = "IntradayLondonExpressStrategy"
    ),
    BuiltInPythonPreset(
        title = "Tri-Horizon: Hold for Days",
        subtitle = "Multi-day HTF OB + 18/40 EMA",
        className = "SwingMultiDayOrderBlockStrategy"
    )
)

@Composable
fun StrategiesScreen(
    viewModel: TradingViewModel,
    onOpenStrategyGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val strategies by viewModel.strategies.collectAsState()
    val pythonStrategies by viewModel.pythonStrategies.collectAsState()
    val allTrades by viewModel.trades.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val isEditorOpen by viewModel.isStrategyEditorOpen.collectAsState()
    val strategyForEdit by viewModel.selectedStrategyForEdit.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    // Python strategy dialog states
    var pendingUpload by remember { mutableStateOf<PendingPythonUpload?>(null) }
    var uploadReadError by remember { mutableStateOf<String?>(null) }
    var strategyToView by remember { mutableStateOf<PythonStrategy?>(null) }
    var strategyToRename by remember { mutableStateOf<PythonStrategy?>(null) }
    var strategyToEdit by remember { mutableStateOf<PythonStrategy?>(null) }
    var strategyToDelete by remember { mutableStateOf<PythonStrategy?>(null) }

    val pyFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val readResult = withContext(Dispatchers.IO) {
                    runCatching {
                        val fileName = resolveFileDisplayName(context, uri)
                        val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                            BacktestCodec.readTextStreamWithBom(stream, 64L * 1024L * 1024L)
                        } ?: error("Unable to open selected .py file.")
                        if (text.isBlank()) error("Selected .py file is empty.")
                        val baseName = fileName.removeSuffix(".py").ifBlank { "Custom Strategy" }
                        PendingPythonUpload(suggestedName = baseName, code = text)
                    }
                }
                readResult.onSuccess { upload ->
                    uploadReadError = null
                    pendingUpload = upload
                }.onFailure { err ->
                    uploadReadError = err.message ?: "Failed to read .py file."
                }
            }
        }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Strategies",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${pythonStrategies.size} Python • ${strategies.size} Checklists",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = onOpenStrategyGuide,
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("open_strategy_guide_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Strategy Guide", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LiquidGlassTabRow(
                tabs = listOf("Python (.py)", "Checklists"),
                selectedIndex = selectedTab,
                onTabSelected = { selectedTab = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTab == 0) {
                // Python Backtest Strategies Tab
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { pyFilePicker.launch(arrayOf("*/*")) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("upload_python_strategy_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload .py", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val loadRes = withContext(Dispatchers.IO) {
                                    runCatching {
                                        context.assets.open("strategies/institutional_tri_horizon.py")
                                            .bufferedReader(Charsets.UTF_8)
                                            .use { it.readText() }
                                    }
                                }
                                loadRes.onSuccess { bundledCode ->
                                    val baseTitle = "Textbook Tri-Horizon"
                                    val existingNames = pythonStrategies.map { it.name.trim().lowercase() }.toSet()
                                    val uniqueName = if (baseTitle.lowercase() !in existingNames) {
                                        baseTitle
                                    } else {
                                        var idx = 2
                                        while ("$baseTitle ($idx)".lowercase() in existingNames) idx++
                                        "$baseTitle ($idx)"
                                    }
                                    viewModel.savePythonStrategy(
                                        name = uniqueName,
                                        className = "InstitutionalTriHorizonStrategy",
                                        code = bundledCode
                                    )
                                    uploadReadError = null
                                    android.widget.Toast.makeText(
                                        context,
                                        "Added \"$uniqueName\" (.py) to Strategies",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }.onFailure { err ->
                                    uploadReadError = err.message ?: "Failed to load bundled .py strategy."
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("install_textbook_tri_horizon_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Tri-Horizon", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val quickPresetModes = remember {
                    BUILT_IN_TEXTBOOK_PRESETS.drop(1)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickPresetModes.forEach { preset ->
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val loadRes = withContext(Dispatchers.IO) {
                                        runCatching {
                                            context.assets.open("strategies/institutional_tri_horizon.py")
                                                .bufferedReader(Charsets.UTF_8)
                                                .use { it.readText() }
                                        }
                                    }
                                    loadRes.onSuccess { bundledCode ->
                                        val existingNames = pythonStrategies.map { it.name.trim().lowercase() }.toSet()
                                        val uniqueName = if (preset.title.lowercase() !in existingNames) {
                                            preset.title
                                        } else {
                                            var idx = 2
                                            while ("${preset.title} ($idx)".lowercase() in existingNames) idx++
                                            "${preset.title} ($idx)"
                                        }
                                        viewModel.savePythonStrategy(
                                            name = uniqueName,
                                            className = preset.className,
                                            code = bundledCode
                                        )
                                        uploadReadError = null
                                        android.widget.Toast.makeText(
                                            context,
                                            "Added \"${preset.title}\" (${preset.subtitle})",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }.onFailure { err ->
                                        uploadReadError = err.message ?: "Failed to load bundled preset."
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(preset.title.removePrefix("Tri-Horizon: "), fontSize = 11.sp)
                        }
                    }
                }

                Text(
                    text = "Auto-import folder: Downloads/Tradepulse/strategies",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )

                if (uploadReadError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uploadReadError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (pythonStrategies.isEmpty()) {
                    LiquidGlassCard(testTag = "empty_python_strategies_card") {
                        Text(
                            text = "No Python Strategies Saved",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap Upload .py Strategy to pick any Python strategy file from your device, or read the Strategy Guide for the 11-rule contract and an AI prompt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(pythonStrategies, key = { it.id }) { strat ->
                            PythonStrategyCard(
                                strategy = strat,
                                onView = { strategyToView = strat },
                                onRename = { strategyToRename = strat },
                                onEdit = { strategyToEdit = strat },
                                onDelete = { strategyToDelete = strat }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            } else {
                // Journal Checklists Tab
                if (strategies.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No journal checklist strategies yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.openNewStrategyEditor() },
                                modifier = Modifier.testTag("add_strategy_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New Checklist")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(strategies, key = { it.id }) { strategy ->
                            val stratTrades = remember(allTrades, strategy.id) {
                                allTrades.filter { it.strategyId == strategy.id }
                            }
                            val tradesCount = stratTrades.size
                            val wins = stratTrades.count { it.pnl > 0 }
                            val winRate = if (tradesCount > 0) (wins.toDouble() / tradesCount) * 100.0 else 0.0
                            val totalPnL = stratTrades.sumOf { it.pnl }

                            StrategyCard(
                                strategy = strategy,
                                tradesCount = tradesCount,
                                winRate = winRate,
                                totalPnL = totalPnL,
                                currency = settings.currency,
                                onEdit = { viewModel.openEditStrategy(strategy) }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(72.dp))
                        }
                    }
                }
            }
        }

        if (selectedTab == 1) {
            FloatingActionButton(
                onClick = { viewModel.openNewStrategyEditor() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .testTag("fab_add_strategy"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Checklist Strategy")
            }
        }
    }

    // Dialog: Name + optional Class Name for uploaded .py file
    pendingUpload?.let { upload ->
        SavePythonStrategyDialog(
            suggestedName = upload.suggestedName,
            code = upload.code,
            existingNames = pythonStrategies.map { it.name },
            onDismiss = { pendingUpload = null },
            onSave = { name, className ->
                viewModel.savePythonStrategy(name = name, className = className, code = upload.code)
                pendingUpload = null
            }
        )
    }

    // Dialog: Rename / Edit Class Name
    strategyToRename?.let { strat ->
        RenamePythonStrategyDialog(
            strategy = strat,
            existingOtherNames = pythonStrategies.filter { it.id != strat.id }.map { it.name },
            onDismiss = { strategyToRename = null },
            onSave = { newName, newClassName ->
                viewModel.renamePythonStrategy(strat, newName, newClassName)
                strategyToRename = null
            }
        )
    }

    strategyToEdit?.let { strat ->
        EditPythonStrategyDialog(
            strategy = strat,
            existingOtherNames = pythonStrategies.filter { it.id != strat.id }.map { it.name },
            onDismiss = { strategyToEdit = null },
            onSave = { newName, newClassName, newCode ->
                viewModel.editPythonStrategy(strat, newName, newClassName, newCode)
                strategyToEdit = null
            }
        )
    }

    // Dialog: Confirm Delete
    strategyToDelete?.let { strat ->
        AlertDialog(
            onDismissRequest = { strategyToDelete = null },
            title = { Text("Delete Python Strategy") },
            text = { Text("Remove \"${strat.name}\" from saved strategies? Existing journal trades will not be affected.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePythonStrategy(strat.id)
                        strategyToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_python_strategy")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { strategyToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Sheet: View Strategy Code
    strategyToView?.let { strat ->
        PythonCodeViewerSheet(
            strategy = strat,
            onDismiss = { strategyToView = null }
        )
    }

    if (isEditorOpen) {
        StrategyEditorBottomSheet(
            strategy = strategyForEdit,
            onDismiss = { viewModel.closeStrategyEditor() },
            onSave = { viewModel.saveStrategy(it) },
            onDelete = { if (strategyForEdit != null) viewModel.deleteStrategy(strategyForEdit!!.id) }
        )
    }
}

@Composable
private fun PythonStrategyCard(
    strategy: PythonStrategy,
    onView: () -> Unit,
    onRename: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val lineCount = remember(strategy.code) { strategy.code.lineSequence().count() }

    LiquidGlassCard(
        onClick = onView,
        testTag = "python_strategy_card_${strategy.id}"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strategy.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val classLabel = if (strategy.className.isBlank()) {
                        "Auto-detect class"
                    } else {
                        "Class: ${strategy.className}"
                    }
                    Text(
                        text = "$classLabel • $lineCount lines",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onView,
                modifier = Modifier.testTag("view_python_strategy_${strategy.id}")
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("View", fontSize = 12.sp)
            }
            TextButton(
                onClick = onRename,
                modifier = Modifier.testTag("rename_python_strategy_${strategy.id}")
            ) {
                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Rename", fontSize = 12.sp)
            }
            TextButton(
                onClick = onEdit,
                modifier = Modifier.testTag("edit_python_strategy_${strategy.id}")
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Edit code", fontSize = 12.sp)
            }
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("delete_python_strategy_${strategy.id}")
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Delete", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SavePythonStrategyDialog(
    suggestedName: String,
    code: String,
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(suggestedName) }
    var className by remember { mutableStateOf("") }
    val lineCount = remember(code) { code.lineSequence().count() }
    val trimmed = name.trim()
    val isDuplicate = remember(trimmed, existingNames) {
        trimmed.isNotEmpty() && existingNames.any { it.trim().equals(trimmed, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Python Strategy", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Loaded $lineCount lines (${code.length / 1024} KB) from .py file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Strategy Name") },
                    placeholder = { Text("e.g. Opening Range Breakout") },
                    singleLine = true,
                    isError = isDuplicate,
                    supportingText = if (isDuplicate) {
                        { Text("Name already taken — choose a unique strategy name.") }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("python_strategy_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = className,
                    onValueChange = { className = it },
                    label = { Text("Class Name (optional)") },
                    placeholder = { Text("Leave blank to auto-detect") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("python_strategy_class_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (trimmed.isNotEmpty() && !isDuplicate) {
                        onSave(trimmed, className.trim())
                    }
                },
                enabled = trimmed.isNotEmpty() && !isDuplicate,
                modifier = Modifier.testTag("confirm_save_python_strategy")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RenamePythonStrategyDialog(
    strategy: PythonStrategy,
    existingOtherNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(strategy.name) }
    var className by remember { mutableStateOf(strategy.className) }
    val trimmed = name.trim()
    val isDuplicate = remember(trimmed, existingOtherNames) {
        trimmed.isNotEmpty() && existingOtherNames.any { it.trim().equals(trimmed, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Strategy", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Strategy Name") },
                    singleLine = true,
                    isError = isDuplicate,
                    supportingText = if (isDuplicate) {
                        { Text("Name already taken — choose a unique strategy name.") }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_python_strategy_input"),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = className,
                    onValueChange = { className = it },
                    label = { Text("Class Name (optional)") },
                    placeholder = { Text("Leave blank to auto-detect") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (trimmed.isNotEmpty() && !isDuplicate) {
                        onSave(trimmed, className.trim())
                    }
                },
                enabled = trimmed.isNotEmpty() && !isDuplicate,
                modifier = Modifier.testTag("confirm_rename_python_strategy")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EditPythonStrategyDialog(
    strategy: PythonStrategy,
    existingOtherNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember(strategy.id) { mutableStateOf(strategy.name) }
    var className by remember(strategy.id) { mutableStateOf(strategy.className) }
    var code by remember(strategy.id) { mutableStateOf(strategy.code) }
    var unlockLargeEditor by remember(strategy.id) { mutableStateOf(false) }

    val trimmed = name.trim()
    val isDuplicate = remember(trimmed, existingOtherNames) {
        trimmed.isNotEmpty() && existingOtherNames.any { it.trim().equals(trimmed, ignoreCase = true) }
    }
    val lineCount = remember(code) { code.lineSequence().count() }
    val isLargeCode = code.length > 24_000 || lineCount > 350
    val lines = remember(code, isLargeCode, unlockLargeEditor) {
        if (isLargeCode && !unlockLargeEditor) code.lines() else emptyList()
    }
    val horizontalScroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Python Strategy", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Strategy Name") },
                    singleLine = true,
                    isError = isDuplicate,
                    supportingText = if (isDuplicate) {
                        { Text("Name already taken — choose a unique strategy name.") }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = className,
                    onValueChange = { className = it },
                    label = { Text("Class Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$lineCount lines • ${code.length / 1024} KB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isLargeCode && !unlockLargeEditor) {
                        TextButton(onClick = { unlockLargeEditor = true }) {
                            Text("Unlock Editor", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (isLargeCode && !unlockLargeEditor) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        ) {
                            items(count = lines.size, key = { it }) { idx ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(horizontalScroll)
                                        .padding(vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "${idx + 1}  ",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = lines[idx],
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        softWrap = false,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Python source code") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        minLines = 12,
                        maxLines = 24,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (trimmed.isNotEmpty() && !isDuplicate && code.isNotBlank()) {
                        onSave(trimmed, className.trim(), code)
                    }
                },
                enabled = trimmed.isNotEmpty() && !isDuplicate && code.isNotBlank(),
                modifier = Modifier.testTag("save_edited_python_strategy")
            ) {
                Text("Save changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PythonCodeViewerSheet(
    strategy: PythonStrategy,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    val lines = remember(strategy.code) { strategy.code.lines() }
    val horizontalScroll = rememberScrollState()

    val exportPyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-python")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(strategy.code.toByteArray(Charsets.UTF_8))
                        } ?: error("Cannot open destination file.")
                    }
                }
                res.onSuccess {
                    android.widget.Toast.makeText(
                        context,
                        "Saved .py strategy file.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }.onFailure { err ->
                    android.widget.Toast.makeText(
                        context,
                        err.message ?: "Failed to export .py file.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strategy.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = (if (strategy.className.isBlank()) "Class: Auto-detect" else "Class: ${strategy.className}") +
                            " • ${lines.size} lines",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = {
                        val safeName = strategy.name
                            .lowercase()
                            .replace(Regex("[^a-z0-9]+"), "_")
                            .trim('_')
                            .ifBlank { "strategy" }
                        exportPyLauncher.launch("$safeName.py")
                    },
                    modifier = Modifier.testTag("export_python_strategy_button")
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Export .py",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(".py", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedButton(
                    onClick = {
                        if (strategy.code.length > 250_000) {
                            android.widget.Toast.makeText(
                                context,
                                "Strategy file exceeds Android clipboard limit (>250 KB).",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            runCatching {
                                clipboardManager.setText(AnnotatedString(strategy.code))
                                copied = true
                            }.onFailure {
                                android.widget.Toast.makeText(
                                    context,
                                    "Could not copy full file to clipboard.",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                ) {
                    Icon(
                        if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy Code",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (copied) "Copied" else "Copy", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                ) {
                    items(count = lines.size, key = { it }) { idx ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(horizontalScroll)
                                .padding(vertical = 1.dp)
                        ) {
                            Text(
                                text = "${idx + 1}  ",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = lines[idx],
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                softWrap = false,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

private fun resolveFileDisplayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
    return uri.lastPathSegment ?: "strategy.py"
}

@Composable
fun StrategyCard(
    strategy: Strategy,
    tradesCount: Int,
    winRate: Double,
    totalPnL: Double,
    currency: com.example.domain.model.CurrencyCode,
    onEdit: () -> Unit
) {
    val positiveColor = MaterialTheme.colorScheme.primary
    val negativeColor = MaterialTheme.colorScheme.error

    LiquidGlassCard(
        onClick = onEdit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val color = try {
                    Color(android.graphics.Color.parseColor(strategy.colorHex))
                } catch (e: Exception) {
                    positiveColor
                }
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(color, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strategy.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Strategy", modifier = Modifier.size(18.dp))
            }
        }

        if (strategy.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = strategy.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Trades", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "$tradesCount", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Win Rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = String.format(Locale.US, "%.1f%%", winRate), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = positiveColor)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Net P&L", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = CurrencyFormatter.format(totalPnL, currency),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (totalPnL >= 0) positiveColor else negativeColor
                    )
                }
            }
        }

        if (strategy.checklist.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Rules / Checklist (${strategy.checklist.size}):",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            strategy.checklist.take(3).forEach { rule ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = positiveColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = rule,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (strategy.checklist.size > 3) {
                Text(
                    text = "+${strategy.checklist.size - 3} more rules...",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = positiveColor
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrategyEditorBottomSheet(
    strategy: Strategy?,
    onDismiss: () -> Unit,
    onSave: (Strategy) -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(strategy?.name ?: "") }
    var description by remember { mutableStateOf(strategy?.description ?: "") }
    var colorHex by remember { mutableStateOf(strategy?.colorHex ?: "#10B981") }
    val checklist = remember {
        mutableStateListOf<String>().apply {
            if (strategy != null) addAll(strategy.checklist)
        }
    }
    var newRuleInput by remember { mutableStateOf("") }
    var tagsInput by remember { mutableStateOf(strategy?.tags?.joinToString(", ") ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                Text(
                    text = if (strategy == null) "Create Strategy" else "Edit Strategy",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Strategy Name") },
                placeholder = { Text("e.g. ICT Silver Bullet / FVG") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("strategy_name_field"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description & Edge") },
                placeholder = { Text("Explain setup conditions, session times, invalidation...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Checklist Rules Builder
            Text(
                text = "Entry Checklist Rules (${checklist.size})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            checklist.forEachIndexed { index, rule ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(
                        text = rule,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { checklist.removeAt(index) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove rule", modifier = Modifier.size(16.dp))
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newRuleInput,
                    onValueChange = { newRuleInput = it },
                    placeholder = { Text("Add rule condition...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = {
                        if (newRuleInput.isNotBlank()) {
                            checklist.add(newRuleInput.trim())
                            newRuleInput = ""
                        }
                    },
                    modifier = Modifier.testTag("add_rule_item_button")
                ) {
                    Text("Add")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = tagsInput,
                onValueChange = { tagsInput = it },
                label = { Text("Tags (comma-separated)") },
                placeholder = { Text("SMC, Scalping, Gold") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (strategy != null) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonRed),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }

                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            val tagsList = tagsInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            val newStrategy = Strategy(
                                id = strategy?.id ?: 0L,
                                name = name.trim(),
                                description = description.trim(),
                                checklist = checklist.toList(),
                                tags = tagsList,
                                colorHex = colorHex
                            )
                            onSave(newStrategy)
                        }
                    },
                    modifier = Modifier
                        .weight(2f)
                        .testTag("save_strategy_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (strategy == null) "Create Strategy" else "Save Strategy")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
