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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.data.mt5.Mt5ConnectionStore
import com.example.data.mt5.Mt5ConnectorClient
import com.example.data.mt5.Mt5Symbol
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
                account = listOf(info.company, info.server, "Login ${info.login}", info.currency).filter { it.isNotBlank() }.joinToString(" • ")
                if (loadSymbols) {
                    symbols = result.second
                    selected = result.second.filter { it.visible }.map { it.name }.toSet()
                    status = "Connected. Loaded ${symbols.size} symbols from the broker."
                } else {
                    status = if (info.terminalConnected) "Connected to MT5 successfully." else "Connector reached, but MT5 terminal is disconnected."
                }
                store.save(url, token)
            } catch (error: Exception) {
                status = error.message ?: "Unable to connect to the MT5 connector."
            } finally {
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
                "Connect to the free Windows companion service beside your signed-in MT5 terminal. This connection is for historical market data only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Connector URL") },
                placeholder = { Text("https://your-private-connector") }
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                LazyColumn(modifier = Modifier.height(220.dp)) {
                    items(symbols, key = { it.name }) { symbol ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = symbol.name in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + symbol.name else selected - symbol.name
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(symbol.name, style = MaterialTheme.typography.bodyMedium)
                                if (symbol.description.isNotBlank()) {
                                    Text(symbol.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Text(
                    "Symbol selection is loaded live from your broker. Historical download controls are being connected to the Backtester next.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
