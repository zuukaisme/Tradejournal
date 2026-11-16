package com.example.presentation.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.backtest.AiProvider
import com.example.presentation.BacktestUiState
import com.example.presentation.BacktestViewModel
import com.example.presentation.components.LiquidGlassCard
import com.example.presentation.components.LiquidGlassPill
import kotlinx.coroutines.delay

@Composable
fun AiStrategyBuilderContent(
    state: BacktestUiState,
    backtestViewModel: BacktestViewModel,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }
    var isEditingGeneratedCode by remember { mutableStateOf(false) }
    var copiedCode by remember { mutableStateOf(false) }

    LaunchedEffect(copiedCode) {
        if (copiedCode) {
            delay(2000)
            copiedCode = false
        }
    }

    val selectedModelOption = remember(state.availableModels, state.selectedModelId) {
        state.availableModels.firstOrNull { it.id == state.selectedModelId }
    }

    val quickTemplates = remember {
        listOf(
            "EMA Cross + ATR SL" to "Buy when 9-period EMA crosses above 21-period EMA and close is above 50-period EMA. Sell on reverse cross. Stop loss is 1.5x 14-candle ATR, take profit is 2R.",
            "3-Bar Momentum 2R" to "Enter buy after 3 consecutive bullish candles whose bodies each exceed 30% of their range. Stop loss 1x 14-candle average range below close, take profit 2R.",
            "RSI Pullback" to "Compute 14-period Wilder RSI using pure Python. Buy when RSI crosses back above 30 while close is above 50-period SMA. Stop at 14-bar swing low minus 0.5 ATR, take profit 2R."
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Provider, Model & API Key Card
        item {
            LiquidGlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AI Provider & Model",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Backtester only — builds Python strategies compatible with the embedded engine.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Provider Selector: Claude / Gemini / OpenAI
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiProvider.entries.forEach { provider ->
                        LiquidGlassPill(
                            text = provider.displayName,
                            isSelected = state.selectedProvider == provider,
                            onClick = { backtestViewModel.selectProvider(provider) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ai_provider_${provider.name}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Model Selector + Live Fetch Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { modelMenuExpanded = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("ai_model_dropdown_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = when {
                                            state.useCustomModel -> "Custom Model ID"
                                            selectedModelOption != null -> selectedModelOption.displayName
                                            else -> state.selectedModelId
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select AI Model")
                            }
                        }

                        DropdownMenu(
                            expanded = modelMenuExpanded,
                            onDismissRequest = { modelMenuExpanded = false }
                        ) {
                            state.availableModels.forEach { model ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(model.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(
                                                text = "${model.id} • ${model.tierSummary}",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        backtestViewModel.selectModel(model.id)
                                        modelMenuExpanded = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Custom Model ID...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(
                                            text = "Enter any exact snapshot ID manually",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    backtestViewModel.enableCustomModel(state.customModelInput.ifBlank { state.selectedModelId })
                                    modelMenuExpanded = false
                                }
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { backtestViewModel.fetchLiveModels() },
                        enabled = !state.isFetchingModels && state.apiKeyInput.isNotBlank(),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("ai_fetch_models_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (state.isFetchingModels) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Fetch Live Models", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                if (selectedModelOption != null && !state.useCustomModel) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${selectedModelOption.id} — ${selectedModelOption.tierSummary}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_model_cost_guidance")
                ) {
                    Text(
                        text = "Model tip: Smarter reasoning models (such as Opus, Fable, Gemini 4 Argon/Carbon, or GPT-6 Astra) are more effective for complex multi-rule strategies, but can cost more per request than faster models like Haiku, Hydrogen, Flash, or Luna.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                if (state.useCustomModel) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.customModelInput,
                        onValueChange = { backtestViewModel.updateCustomModelInput(it) },
                        label = { Text("Custom Model ID") },
                        placeholder = { Text("e.g. claude-fable-5-2 or gpt-6.1-sol") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_custom_model_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // API Key Input
                OutlinedTextField(
                    value = state.apiKeyInput,
                    onValueChange = { backtestViewModel.updateApiKeyInput(it) },
                    label = { Text(state.selectedProvider.keyLabel) },
                    placeholder = { Text(state.selectedProvider.keyPlaceholder) },
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { showApiKey = !showApiKey },
                                modifier = Modifier.testTag("ai_toggle_key_visibility")
                            ) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showApiKey) "Hide key" else "Show key"
                                )
                            }
                            TextButton(
                                onClick = { backtestViewModel.saveCurrentApiKey() },
                                enabled = state.apiKeyInput.isNotBlank(),
                                modifier = Modifier.testTag("ai_save_key_button")
                            ) {
                                Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_api_key_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Strategy Description Prompt Card
        item {
            LiquidGlassCard {
                Text(
                    text = "Describe Your Strategy",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(quickTemplates) { (label, templateText) ->
                        LiquidGlassPill(
                            text = label,
                            isSelected = state.strategyPromptInput == templateText,
                            onClick = { backtestViewModel.updateStrategyPrompt(templateText) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = state.strategyPromptInput,
                    onValueChange = { backtestViewModel.updateStrategyPrompt(it) },
                    label = { Text("Entry, Stop Loss & Take Profit Rules") },
                    placeholder = { Text("Explain your rules in plain English. AI will generate a pure-Python backtest.py Strategy subclass...") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_strategy_prompt_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { backtestViewModel.generateStrategyWithAi() },
                    enabled = !state.isGeneratingStrategy &&
                        state.apiKeyInput.isNotBlank() &&
                        state.strategyPromptInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("ai_build_strategy_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.isGeneratingStrategy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Building with ${state.selectedProvider.displayName}...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Build Strategy with AI", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Status or Error Banner
        state.aiStatusMessage?.let { msg ->
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        state.aiError?.let { err ->
            item {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_error_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Contract Validation Warnings Banner
        if (state.aiContractWarnings.isNotEmpty() && state.generatedCode != null) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_contract_warnings_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Contract Warning",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "Contract Check (${state.aiContractWarnings.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            state.aiContractWarnings.forEach { warning ->
                                Text(
                                    text = "• $warning",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        // Generated Strategy Code & Save Actions
        state.generatedCode?.let { generatedCode ->
            item {
                LiquidGlassCard(testTag = "ai_generated_strategy_card") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Generated Python Strategy",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row {
                            IconButton(
                                onClick = { isEditingGeneratedCode = !isEditingGeneratedCode },
                                modifier = Modifier.testTag("ai_toggle_edit_code")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = if (isEditingGeneratedCode) "Preview Code" else "Edit Code",
                                    tint = if (isEditingGeneratedCode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(generatedCode))
                                    copiedCode = true
                                },
                                modifier = Modifier.testTag("ai_copy_generated_code")
                            ) {
                                Icon(
                                    imageVector = if (copiedCode) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = "Copy Generated Code",
                                    tint = if (copiedCode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = state.generatedStrategyName,
                            onValueChange = { backtestViewModel.updateGeneratedStrategyName(it) },
                            label = { Text("Strategy Name", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ai_generated_name_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = state.generatedClassName,
                            onValueChange = { backtestViewModel.updateGeneratedClassName(it) },
                            label = { Text("Class (Optional)", fontSize = 12.sp) },
                            placeholder = { Text("Auto") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ai_generated_class_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isEditingGeneratedCode) {
                        OutlinedTextField(
                            value = generatedCode,
                            onValueChange = { backtestViewModel.updateGeneratedCode(it) },
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            ),
                            minLines = 10,
                            maxLines = 18,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 300.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = generatedCode,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { backtestViewModel.saveGeneratedStrategy(switchToRunner = false) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("ai_save_strategy_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save")
                        }

                        Button(
                            onClick = { backtestViewModel.saveGeneratedStrategy(switchToRunner = true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1.4f)
                                .height(48.dp)
                                .testTag("ai_save_and_backtest_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save & Backtest", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
