package com.example.presentation.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.mt5.Mt5LocalBridgeServer
import com.example.mt5.Mt5RuntimeManager
import com.example.mt5.Mt5RuntimeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun Mt5EmbeddedRuntimeCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val bridgeStatus by Mt5LocalBridgeServer.status.collectAsState()
    val bridgeToken by Mt5LocalBridgeServer.token.collectAsState()
    val account by Mt5LocalBridgeServer.account.collectAsState()
    val positions by Mt5LocalBridgeServer.positions.collectAsState()
    var installed by remember { mutableStateOf(Mt5RuntimeManager.isInstalled(context)) }
    var busy by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf(
            if (installed) "Runtime bundle installed. MT5 terminal files are ready to launch."
            else "Runtime not installed. Import a compatible Wine + Box64 bundle to continue."
        )
    }

    val bundlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            status = "Installing runtime bundle into TradePulse private storage…"
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    Mt5RuntimeManager.installBundle(context, uri)
                }
                installed = result.isSuccess
                status = result.fold(
                    { "Runtime bundle installed and validated." },
                    { it.message ?: "Runtime installation failed." }
                )
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text("Experimental MT5 runtime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Generic runtime loader — Winlator Mali adapter still pending", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Text(
                "This experimental loader stores files inside TradePulse. It is not yet compatible with Winlator Mali's real imagefs and ARM64EC Wine packages. The device-specific Wine/X server integration is still being developed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "The current ZIP format is a temporary prototype format. Do not use it for Winlator Mali imagefs, Proton or WOWBox64 archives.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { bundlePicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (installed) "Replace runtime" else "Import runtime")
                }
                OutlinedButton(
                    onClick = {
                        val intent = Intent(context, Mt5RuntimeService::class.java).setAction(Mt5RuntimeService.ACTION_START)
                        ContextCompat.startForegroundService(context, intent)
                        status = "MT5 runtime start requested. Check the runtime log if startup fails."
                    },
                    enabled = installed && !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Start MT5")
                }
            }

            OutlinedButton(
                onClick = {
                    context.startService(Intent(context, Mt5RuntimeService::class.java).setAction(Mt5RuntimeService.ACTION_STOP))
                    status = "MT5 runtime stop requested."
                },
                enabled = installed && !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop MT5")
            }

            if (busy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())

            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Text("Local bridge: $bridgeStatus", style = MaterialTheme.typography.bodySmall)
            if (bridgeToken.isNotBlank()) {
                Text("Bridge token: $bridgeToken", style = MaterialTheme.typography.labelSmall)
                OutlinedButton(
                    onClick = { clipboardManager.setText(AnnotatedString(bridgeToken)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Copy bridge token")
                }
                Text(
                    "In MT5, add 127.0.0.1 to the allowed socket addresses, attach TradePulseBridge.mq5 to a chart, and enter this token in its BridgeToken input.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (account != null) {
                Text(
                    "Account ${account!!.login} · ${account!!.server}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Balance ${account!!.balance} ${account!!.currency} · Equity ${account!!.equity} · Free margin ${account!!.freeMargin}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Open positions: ${positions.size}", style = MaterialTheme.typography.bodySmall)
                positions.take(5).forEach { position ->
                    Text(
                        "${position.symbol} · ${if (position.type == 0) "Buy" else "Sell"} · ${position.volume} · P/L ${position.profit}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (position.profit < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                "Only import runtime binaries from a source you trust. They run with TradePulse's app permissions. A successful import does not mean MT5 can launch. The package must be built for Android ARM64 and include an x86_64 Wine environment. Keep the foreground notification enabled while MT5 is running.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}