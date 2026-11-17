package com.example.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.backtest.AccountBacktestSimulator
import com.example.domain.model.TradingAccountProfile
import com.example.presentation.AppScreen
import com.example.presentation.TradingViewModel
import com.example.presentation.components.CurrencyFormatter
import com.example.presentation.components.LiquidGlassCard
import com.example.ui.theme.AmberGold
import java.util.Locale

@Composable
fun ProfileScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val profiles by viewModel.accountProfiles.collectAsState()
    val activeProfile by viewModel.activeAccountProfile.collectAsState()
    val autoVaultEnabled by viewModel.autoVaultEnabled.collectAsState()

    var editingProfile by remember { mutableStateOf<TradingAccountProfile?>(null) }
    var isEditorOpen by remember { mutableStateOf(false) }
    var profileToDelete by remember { mutableStateOf<TradingAccountProfile?>(null) }

    val primary = MaterialTheme.colorScheme.primary
    val effectiveActive = activeProfile ?: AccountBacktestSimulator.fallbackProfile(
        testerLot = settings.testerLotSize,
        testerLeverage = settings.testerLeverage
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header + Vault Sync Badge + New Profile Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Trading Profile & Accounts",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Vault Sync Status",
                            tint = if (autoVaultEnabled) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (autoVaultEnabled) {
                                "Every account edit auto-syncs to Trading Vault"
                            } else {
                                "Auto-Vault paused (enable in Settings)"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Button(
                    onClick = {
                        editingProfile = TradingAccountProfile(
                            name = "",
                            accountCategory = "Custom",
                            startingBalance = 10_000.0,
                            defaultLotSize = settings.testerLotSize,
                            leverage = settings.testerLeverage
                        )
                        isEditorOpen = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("create_account_profile_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New account profile", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Account", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // Active Account Hero Card
        item {
            LiquidGlassCard(testTag = "active_account_hero_card") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = primary.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, primary.copy(alpha = 0.45f)),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = effectiveActive.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    color = primary.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (activeProfile != null) "ACTIVE" else "DEFAULT",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${effectiveActive.accountCategory} • ${effectiveActive.instrumentLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            editingProfile = effectiveActive
                            isEditorOpen = true
                        },
                        modifier = Modifier.testTag("edit_active_profile_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit active account profile",
                            tint = primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileHeroMetricBox(
                        label = "Starting Balance",
                        value = CurrencyFormatter.format(effectiveActive.startingBalance, settings.currency, includeSign = false),
                        modifier = Modifier.weight(1f)
                    )
                    ProfileHeroMetricBox(
                        label = "Default Fixed Lot",
                        value = String.format(Locale.US, "%.2f lots", effectiveActive.defaultLotSize),
                        modifier = Modifier.weight(1f)
                    )
                    ProfileHeroMetricBox(
                        label = "Leverage",
                        value = "1:${effectiveActive.leverage}",
                        accentColor = AmberGold,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Current Trade Tester Config",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "Fixed %.2f lots • 1:%d leverage • Comm %s/lot",
                                    settings.testerLotSize,
                                    settings.testerLeverage,
                                    CurrencyFormatter.format(effectiveActive.commissionPerLot, settings.currency, false)
                                ),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (activeProfile != null &&
                            (settings.testerLotSize != effectiveActive.defaultLotSize || settings.testerLeverage != effectiveActive.leverage)
                        ) {
                            TextButton(
                                onClick = {
                                    viewModel.selectActiveAccountProfile(effectiveActive, syncDefaultsToTester = true)
                                    Toast.makeText(context, "Synced ${effectiveActive.name} defaults to Trade Tester", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.navigateTo(AppScreen.BACKTEST) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("profile_open_backtester_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.QueryStats, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Backtester", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.navigateTo(AppScreen.BACKTEST_ACCOUNT) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("profile_open_account_sim_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Account Results", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Custom Account Profiles Section Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CUSTOM ACCOUNT PROFILES (${profiles.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = primary
                )
                Text(
                    text = "Tap to set active",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (profiles.isEmpty()) {
            item {
                LiquidGlassCard(testTag = "profile_empty_state_card") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = primary.copy(alpha = 0.14f),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Text(
                            text = "No Custom Accounts Saved Yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Create custom broker or prop profiles (balance, default fixed lot, leverage, commission, contract size) or start from a quick template below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(AccountBacktestSimulator.starterTemplates) { tpl ->
                                OutlinedButton(
                                    onClick = {
                                        val created = TradingAccountProfile(
                                            name = tpl.title,
                                            accountCategory = tpl.category,
                                            startingBalance = tpl.startingBalance,
                                            defaultLotSize = tpl.defaultLotSize,
                                            leverage = tpl.leverage,
                                            commissionPerLot = tpl.commissionPerLot,
                                            valuePerPointPerLot = tpl.valuePerPointPerLot,
                                            contractSizePerLot = tpl.contractSizePerLot,
                                            stopOutLevelPct = tpl.stopOutLevelPct,
                                            instrumentLabel = tpl.instrumentLabel
                                        )
                                        viewModel.saveAccountProfile(created, makeActive = true, syncToTester = true)
                                        Toast.makeText(context, "Created ${tpl.title} & synced to Vault", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("+ ${tpl.title}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            items(profiles, key = { it.id }) { profile ->
                val isSelected = profile.id == effectiveActive.id
                LiquidGlassCard(
                    onClick = {
                        viewModel.selectActiveAccountProfile(profile, syncDefaultsToTester = true)
                        Toast.makeText(
                            context,
                            "Switched to ${profile.name} (${profile.defaultLotSize} lots • 1:${profile.leverage})",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    testTag = "profile_card_${profile.id}"
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
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Active profile",
                                        tint = primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) primary else MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = profile.accountCategory,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%s • %.2f lots • 1:%d • $/pt %.2f • Comm %s",
                                    CurrencyFormatter.format(profile.startingBalance, settings.currency, false),
                                    profile.defaultLotSize,
                                    profile.leverage,
                                    profile.valuePerPointPerLot,
                                    CurrencyFormatter.format(profile.commissionPerLot, settings.currency, false)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (profile.notes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = profile.notes,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    editingProfile = profile
                                    isEditorOpen = true
                                },
                                modifier = Modifier.testTag("edit_profile_${profile.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit ${profile.name}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { profileToDelete = profile },
                                modifier = Modifier.testTag("delete_profile_${profile.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete ${profile.name}",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(36.dp))
        }
    }

    if (isEditorOpen && editingProfile != null) {
        AccountProfileEditorDialog(
            initialProfile = editingProfile!!,
            currencySymbol = settings.currency.symbol,
            onDismiss = {
                isEditorOpen = false
                editingProfile = null
            },
            onSave = { saved, makeActive ->
                viewModel.saveAccountProfile(saved, makeActive = makeActive, syncToTester = true)
                Toast.makeText(context, "Saved \"${saved.name}\" to Profile & Vault", Toast.LENGTH_SHORT).show()
                isEditorOpen = false
                editingProfile = null
            }
        )
    }

    profileToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text("Delete Account Profile?") },
            text = { Text("Remove \"${target.name}\" from your Trading Profile and Vault?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAccountProfile(target.id)
                        Toast.makeText(context, "Removed \"${target.name}\"", Toast.LENGTH_SHORT).show()
                        profileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { profileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ProfileHeroMetricBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
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
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AccountProfileEditorDialog(
    initialProfile: TradingAccountProfile,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (TradingAccountProfile, Boolean) -> Unit
) {
    var nameInput by remember { mutableStateOf(initialProfile.name) }
    var categoryInput by remember { mutableStateOf(initialProfile.accountCategory) }
    var balanceInput by remember { mutableStateOf(initialProfile.startingBalance.toString()) }
    var defaultLotInput by remember { mutableStateOf(String.format(Locale.US, "%.2f", initialProfile.defaultLotSize)) }
    var leverageInput by remember { mutableStateOf(initialProfile.leverage.toString()) }
    var commissionInput by remember { mutableStateOf(initialProfile.commissionPerLot.toString()) }
    var valuePerPtInput by remember { mutableStateOf(initialProfile.valuePerPointPerLot.toString()) }
    var contractSizeInput by remember { mutableStateOf(initialProfile.contractSizePerLot.toString()) }
    var stopOutInput by remember { mutableStateOf(initialProfile.stopOutLevelPct.toString()) }
    var instrumentLabel by remember { mutableStateOf(initialProfile.instrumentLabel) }
    var notesInput by remember { mutableStateOf(initialProfile.notes) }
    var setAsActive by remember { mutableStateOf(true) }

    val quickCategories = listOf("Standard", "Raw ECN", "Pro", "Funded Prop", "Cent / Micro")
    val quickLots = listOf(0.01, 0.05, 0.10, 0.25, 0.50, 1.00)
    val quickLeverages = listOf(30, 50, 100, 200, 500, 1000)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialProfile.id == 0L) "Create Custom Account Profile" else "Edit Account Profile",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (initialProfile.id == 0L) {
                    Text(
                        text = "Quick Starter Fill (optional)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(AccountBacktestSimulator.starterTemplates) { tpl ->
                            FilterChip(
                                selected = nameInput == tpl.title,
                                onClick = {
                                    nameInput = tpl.title
                                    categoryInput = tpl.category
                                    balanceInput = tpl.startingBalance.toString()
                                    defaultLotInput = String.format(Locale.US, "%.2f", tpl.defaultLotSize)
                                    leverageInput = tpl.leverage.toString()
                                    commissionInput = tpl.commissionPerLot.toString()
                                    valuePerPtInput = tpl.valuePerPointPerLot.toString()
                                    contractSizeInput = tpl.contractSizePerLot.toString()
                                    stopOutInput = tpl.stopOutLevelPct.toString()
                                    instrumentLabel = tpl.instrumentLabel
                                },
                                label = { Text(tpl.title, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Profile Name") },
                    placeholder = { Text("e.g. IC Markets Raw 1:500") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = categoryInput,
                    onValueChange = { categoryInput = it },
                    label = { Text("Account Type Label") },
                    placeholder = { Text("Custom / Raw ECN / Funded / Cent") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_category_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(quickCategories) { cat ->
                        FilterChip(
                            selected = categoryInput.equals(cat, ignoreCase = true),
                            onClick = { categoryInput = cat },
                            label = { Text(cat, fontSize = 11.sp) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = balanceInput,
                        onValueChange = { balanceInput = it },
                        label = { Text("Balance ($currencySymbol)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("profile_balance_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = defaultLotInput,
                        onValueChange = { defaultLotInput = it },
                        label = { Text("Default Lot") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("profile_default_lot_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(quickLots) { lot ->
                        val label = String.format(Locale.US, "%.2f", lot)
                        FilterChip(
                            selected = defaultLotInput.toDoubleOrNull() == lot,
                            onClick = { defaultLotInput = label },
                            label = { Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = leverageInput,
                        onValueChange = { leverageInput = it.filter(Char::isDigit) },
                        label = { Text("Leverage (1:X)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("profile_leverage_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = commissionInput,
                        onValueChange = { commissionInput = it },
                        label = { Text("Comm/Lot ($currencySymbol)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("profile_commission_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(quickLeverages) { lev ->
                        FilterChip(
                            selected = leverageInput.toIntOrNull() == lev,
                            onClick = { leverageInput = lev.toString() },
                            label = { Text("1:$lev", fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Text(
                    text = "Instrument Contract & Margin Spec",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(AccountBacktestSimulator.instrumentPresets) { spec ->
                        FilterChip(
                            selected = instrumentLabel == spec.label,
                            onClick = {
                                instrumentLabel = spec.label
                                valuePerPtInput = spec.valuePerPointPerLot.toString()
                                contractSizeInput = spec.contractSizePerLot.toString()
                            },
                            label = { Text(spec.label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = valuePerPtInput,
                        onValueChange = { valuePerPtInput = it },
                        label = { Text("$currencySymbol/Pt @1 Lot") },
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
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stopOutInput,
                        onValueChange = { stopOutInput = it },
                        label = { Text("Stop-Out %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        label = { Text("Notes") },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { setAsActive = !setAsActive }
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Set as Active Profile & Sync Defaults",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (setAsActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .size(22.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                    ) {
                        if (setAsActive) {
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
        },
        confirmButton = {
            Button(
                onClick = {
                    val saved = initialProfile.copy(
                        name = nameInput.trim().ifBlank { "Custom Account" },
                        accountCategory = categoryInput.trim().ifBlank { "Custom" },
                        startingBalance = (balanceInput.toDoubleOrNull() ?: initialProfile.startingBalance).coerceAtLeast(1.0),
                        defaultLotSize = (defaultLotInput.toDoubleOrNull() ?: initialProfile.defaultLotSize).coerceAtLeast(0.001),
                        leverage = (leverageInput.toIntOrNull() ?: initialProfile.leverage).coerceIn(1, 5000),
                        commissionPerLot = (commissionInput.toDoubleOrNull() ?: initialProfile.commissionPerLot).coerceAtLeast(0.0),
                        valuePerPointPerLot = (valuePerPtInput.toDoubleOrNull() ?: initialProfile.valuePerPointPerLot).coerceAtLeast(0.0001),
                        contractSizePerLot = (contractSizeInput.toDoubleOrNull() ?: initialProfile.contractSizePerLot).coerceAtLeast(0.01),
                        stopOutLevelPct = (stopOutInput.toDoubleOrNull() ?: initialProfile.stopOutLevelPct).coerceIn(0.0, 200.0),
                        instrumentLabel = instrumentLabel,
                        notes = notesInput.trim()
                    )
                    onSave(saved, setAsActive)
                },
                modifier = Modifier.testTag("save_account_profile_confirm_button")
            ) {
                Text("Save to Vault")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
