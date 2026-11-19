package com.example.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.backtest.BacktestCodec
import com.example.backtest.CandleTimeframeResampler
import com.example.backtest.SavedCsvStore
import com.example.data.mt5.Mt5ConnectionStore
import com.example.data.mt5.Mt5ConnectorClient
import com.example.data.mt5.Mt5Symbol
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun Mt5ConnectorSettingsCard() {
    val context = LocalContext.current
    val store = remember { Mt5ConnectionStore(context) }
    val saved = remember { runCatching { store.load() }.getOrDefault("" to "") }
    var url by remember { mutableStateOf(saved.first) }
    var token by remember { mutableStateOf(saved.second) }
    var symbols by remember { mutableStateOf(emptyList<Mt5Symbol>()) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var selectedSymbol by remember { mutableStateOf("") }
    var symbolMenuExpanded by remember { mutableStateOf(false) }
    var timeframe by remember { mutableStateOf("M1") }
    var timeframeMenuExpanded by remember { mutableStateOf(false) }
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Not connected") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun runConnection(loadSymbols: Boolean) {
        if (busy) return
        busy = true
        status = if (loadSymbols) "Loading broker symbols…" else "Testing connector…"
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val client = Mt5ConnectorClient(url, token)
                    val info = client.account()
                    val loadedSymbols = if (loadSymbols) client.symbols() else emptyList()
                    info to loadedSymbols
                }
                val info = result.first
                account = listOf(info.company, info.server, "Login ${info.login}", info.currency)
                    .filter { it.isNotBlank() }
                    .joinToString(" • ")
                if (loadSymbols) {
                    symbols = result.second
                    selected = result.second.filter { it.visible }.map { it.name }.toSet()
                    selectedSymbol = selected.firstOrNull() ?: result.second.firstOrNull()?.name.orEmpty()
                    status = "Connected. Loaded ${symbols.size} symbols from the broker."
                } else {
                    status = if (info.terminalConnected) {
                        "Connected to MT5 successfully."
                    } else {
                        "Connector reached, but MT5 terminal is disconnected."
                    }
                }
                store.save(url, token)
            } catch (error: Exception) {
                status = error.message ?: "Unable to connect to the MT5 connector."
            } finally {
                busy = false
            }
        }
    }

    fun downloadHistory() {
        if (busy) return
        val datePattern = Regex("\\d{4}-\\d{2}-\\d{2}")
        if (selectedSymbol.isBlank() || selectedSymbol !in selected) {
            status = "Select a broker symbol before downloading."
            return
        }
        if (!datePattern.matches(fromDate) || !datePattern.matches(toDate)) {
            status = "Enter both dates in YYYY-MM-DD format."
            return
        }
        if (fromDate > toDate) {
            status = "The start date must be before the end date."
            return
        }
        busy = true
        status = "Downloading $selectedSymbol $timeframe history…"
        scope.launch {
            val rawFile = File(context.cacheDir, "mt5_raw_${System.currentTimeMillis()}.csv")
            val normalizedFile = File(context.cacheDir, "mt5_normalized_${System.currentTimeMillis()}.csv")
            try {
                val imported = withContext(Dispatchers.IO) {
                    val client = Mt5ConnectorClient(url, token)
                    client.downloadRatesToFile(
                        symbol = selectedSymbol,
                        timeframe = timeframe,
                        from = "${fromDate}T00:00:00Z",
                        to = "${toDate}T23:59:59Z",
                        destination = rawFile
                    )
                    val inspection = rawFile.inputStream().use { input ->
                        BacktestCodec.streamCsvToCacheFile(input, normalizedFile)
                    }
                    val detected = CandleTimeframeResampler.detectTimeframe(
                        inspection.sampleLines.joinToString("\n")
                    )
                    if (inspection.estimatedRows <= 0) {
                        normalizedFile.delete()
                        throw IllegalStateException("The selected range contains no candle rows.")
                    }
                    val root = SavedCsvStore.defaultRootDir(context)
                    val dataset = SavedCsvStore.saveImportedFile(
                        rootDir = root,
                        tempCsvFile = normalizedFile,
                        displayName = "MT5_${selectedSymbol}_${timeframe}_${fromDate}_${toDate}.csv",
                        inspection = inspection,
                        detectedTimeframe = detected,
                        marketName = selectedSymbol
                    )
                    SavedCsvStore.setLastSelectedId(context, dataset.id)
                    dataset
                }
                status = "Imported ${imported.estimatedRows} candles. Saved as ${imported.displayName} in Backtester Saved CSVs."
                store.save(url, token)
            } catch (error: Exception) {
                rawFile.delete()
                normalizedFile.delete()
                status = error.message ?: "Unable to download MT5 history."
            } finally {
                rawFile.delete()
                busy = false
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("MT5 Historical Data Connection", style = MaterialTheme.typography.titleMedium)
            Text(
                "Connect to the MT5 read-only data service. Downloaded candles are added directly to Backtester Saved CSVs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Connector URL") },
                placeholder = { Text("http://host:8765") }
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                label = { Text("Connector access token") }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { runConnection(false) },
                    enabled = !busy && url.isNotBlank() && token.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Test connection")
                }
                OutlinedButton(
                    onClick = { runConnection(true) },
                    enabled = !busy && url.isNotBlank() && token.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Load symbols")
                }
            }
            if (busy) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator()
                    Text(status, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (account.isNotBlank()) {
                Text(account, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (symbols.isNotEmpty()) {
                Text("Broker symbols (${selected.size} selected)", style = MaterialTheme.typography.titleSmall)
                LazyColumn(modifier = Modifier.height(180.dp)) {
                    items(symbols, key = { it.name }) { symbol ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = symbol.name in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + symbol.name else selected - symbol.name
                                    if (checked && selectedSymbol.isBlank()) selectedSymbol = symbol.name
                                    if (!checked && selectedSymbol == symbol.name) {
                                        selectedSymbol = selected.firstOrNull { it != symbol.name }.orEmpty()
                                    }
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(symbol.name, style = MaterialTheme.typography.bodyMedium)
                                if (symbol.description.isNotBlank()) {
                                    Text(
                                        symbol.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                Text("Download history for", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { symbolMenuExpanded = true }) {
                        Text(if (selectedSymbol.isBlank()) "Choose symbol" else selectedSymbol)
                    }
                    DropdownMenu(
                        expanded = symbolMenuExpanded,
                        onDismissRequest = { symbolMenuExpanded = false }
                    ) {
                        selected.sorted().forEach { symbol ->
                            DropdownMenuItem(
                                text = { Text(symbol) },
                                onClick = {
                                    selectedSymbol = symbol
                                    symbolMenuExpanded = false
                                }
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { timeframeMenuExpanded = true }) {
                        Text(timeframe)
                    }
                    DropdownMenu(
                        expanded = timeframeMenuExpanded,
                        onDismissRequest = { timeframeMenuExpanded = false }
                    ) {
                        listOf("M1", "M2", "M3", "M4", "M5", "M6", "M10", "M12", "M15", "M20", "M30", "H1", "H2", "H3", "H4", "H6", "H8", "H12", "D1", "W1", "MN1").forEach { value ->
                            DropdownMenuItem(
                                text = { Text(value) },
                                onClick = {
                                    timeframe = value
                                    timeframeMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fromDate,
                        onValueChange = { fromDate = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("From (YYYY-MM-DD)") }
                    )
                    OutlinedTextField(
                        value = toDate,
                        onValueChange = { toDate = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("To (YYYY-MM-DD)") }
                    )
                }
                Button(
                    onClick = { downloadHistory() },
                    enabled = !busy && selectedSymbol.isNotBlank() && selectedSymbol in selected &&
                        fromDate.isNotBlank() && toDate.isNotBlank() && url.isNotBlank() && token.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Download candles to Backtester")
                }
            }
        }
    }
}
