package com.example.presentation.screens

import android.widget.Toast
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
import androidx.compose.material3.OutlinedTextField
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
import com.example.mt5.Mt5LocalBridgeServer
import com.example.mt5.Mt5InstallerManager
import com.example.mt5.Mt5RuntimeManager
import com.example.mt5.TermuxHeadlessX11Bridge
import com.example.mt5.WinlatorImageFsImporter
import com.example.mt5.ProotRuntimeLayout
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
    val bars by Mt5LocalBridgeServer.bars.collectAsState()
    val lastBridgeResult by Mt5LocalBridgeServer.lastResult.collectAsState()
    var requestedSymbol by remember { mutableStateOf("EURUSD") }
    var requestedTimeframe by remember { mutableStateOf("M5") }
    var installed by remember { mutableStateOf(Mt5RuntimeManager.isInstalled(context)) }
    var installerStaged by remember { mutableStateOf(Mt5InstallerManager.isInstallerStaged(context)) }
    var termuxInstalled by remember { mutableStateOf(TermuxHeadlessX11Bridge.isInstalled(context)) }
    var termuxPermissionGranted by remember { mutableStateOf(TermuxHeadlessX11Bridge.hasRunCommandPermission(context)) }
    var runtimeRevision by remember { mutableStateOf(0) }
    val winlatorAssetAvailable = remember { WinlatorImageFsImporter.bundledAssetAvailable(context) }
    val winlatorInspection = remember(installed, runtimeRevision) { Mt5RuntimeManager.winlatorMaliInspection(context) }
    val prootInspection = remember(installed) { ProotRuntimeLayout.inspect(context) }
    var busy by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf(
            if (installed) "Generic prototype bundle detected. It is not yet a runnable MT5 desktop runtime."
            else "No runnable MT5 desktop runtime is installed."
        )
    }

    val installerPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            status = "Validating and importing mt5setup.exe…"
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    Mt5InstallerManager.importInstaller(context, uri)
                }
                installerStaged = result.isSuccess
                status = result.fold(
                    { "MT5 installer imported successfully. It is staged but cannot be launched until the compatible Wine/Box64 runtime is integrated." },
                    { it.message ?: "MT5 installer import failed." }
                )
                busy = false
            }
        }
    }

    val termuxPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        termuxPermissionGranted = granted
        status = if (granted) {
            "Termux command permission granted. Enable allow-external-apps in Termux settings and install Xvfb."
        } else {
            "Termux command permission was not granted."
        }
    }

    val eaExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.assets.open("mt5/TradePulseBridge.mq5").use { input ->
                            context.contentResolver.openOutputStream(uri)?.use { output ->
                                input.copyTo(output)
                            } ?: error("Unable to create the EA source file.")
                        }
                    }
                }
                Toast.makeText(
                    context,
                    if (result.isSuccess) "TradePulseBridge.mq5 exported" else result.exceptionOrNull()?.message ?: "Export failed",
                    Toast.LENGTH_LONG
                ).show()
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
                    Text("Embedded MT5 runtime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Winlator Mali integration in progress", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            OutlinedButton(
                onClick = {
                    busy = true
                    status = "Importing Winlator Mali ImageFS from the installed app. This may take several minutes…"
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            WinlatorImageFsImporter.importBundledImageFs(context)
                        }
                        result.onSuccess { imported ->
                            runtimeRevision++
                            status = "Winlator Mali ImageFS imported: ${imported.filesWritten} archive entries, ${imported.bytesWritten / (1024L * 1024L)} MiB expanded. Proton/Wine, prefix and MT5 still need to be supplied."
                        }.onFailure {
                            status = it.message ?: "Winlator Mali ImageFS import failed."
                        }
                        busy = false
                    }
                },
                enabled = !busy && winlatorAssetAvailable,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import Winlator Mali ImageFS")
            }
            Text(
                if (winlatorAssetAvailable) "Uses the imagefs.tzst asset already bundled in the installed Winlator Mali app; no runtime download is started."
                else "No bundled imagefs.tzst asset was found. Install the compatible Winlator Mali build with its ImageFS asset included.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                "Winlator Mali ImageFS: " + if (winlatorInspection.runtimeFilesReady) "runtime files detected" else "not ready (${winlatorInspection.missingComponents.size} components missing)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Gemini-style PRoot runtime: " + if (prootInspection.runtimeFilesReady) "files detected" else "${prootInspection.missingComponents.size} required components missing",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (prootInspection.missingComponents.isNotEmpty()) {
                Text(
                    "Missing: ${prootInspection.missingComponents.joinToString()}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "The Gemini repository provides a useful PRoot/Box64/Wine setup design. TradePulse is targeting headless MT5 execution through the local API, so Android desktop rendering and touch input are not required. TradePulse can start Termux's Android ARM64 Xvfb in the background and verify an authenticated X11 connection before probing Wine. Full MT5 terminal launch and bot deployment still need validation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "TradePulse can now import the base Winlator Mali ImageFS asset from the installed app, including its symbolic links. The Proton ARM64EC runtime, WOWBox64/FEX components, Wine prefix and MT5 terminal are separate packages or setup steps and are not copied from Winlator's private app storage.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "The current ZIP format is a temporary prototype format. Do not use it for Winlator Mali imagefs, Proton or WOWBox64 archives.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                when {
                    !termuxInstalled -> "Termux is not installed. Install Termux to provide a headless Xvfb process without downloading another runtime bundle."
                    termuxPermissionGranted -> "Termux command access is enabled. In Termux run: pkg install x11-repo, then pkg install xorg-server-xvfb xorg-fonts-misc. Set allow-external-apps=true in ~/.termux/termux.properties and run termux-reload-settings."
                    else -> "Termux is installed but TradePulse does not have permission to start its background Xvfb process. Grant the permission below, then enable allow-external-apps in Termux."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (termuxInstalled && !termuxPermissionGranted) {
                OutlinedButton(
                    onClick = { termuxPermissionLauncher.launch("com.termux.permission.RUN_COMMAND") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Termux command permission")
                }
            }

            OutlinedButton(
                onClick = { installerPicker.launch(arrayOf("application/vnd.microsoft.portable-executable", "application/octet-stream", "application/x-msdownload")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (installerStaged) "Replace MT5 installer (.exe)" else "Import MT5 installer (.exe)")
            }
            Text(
                if (installerStaged) "mt5setup.exe is safely stored in TradePulse. Installation is waiting for a compatible Wine runtime."
                else "Import the official mt5setup.exe installer here. This is separate from the MQL5 bot importer.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = { eaExporter.launch("TradePulseBridge.mq5") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export MT5 bridge Expert Advisor")
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            ) {
                Text(
                    "MT5 bot execution is not enabled yet. Termux Xvfb only supplies the headless display; Winlator Mali ImageFS, Proton ARM64EC, MT5 terminal startup, bridge EA deployment, and bot lifecycle must all pass validation first.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (busy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())

            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Text("Local bridge: $bridgeStatus", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        runCatching { Mt5LocalBridgeServer.start(context) }
                            .onSuccess { status = "Local MT5 API bridge started on 127.0.0.1:8765." }
                            .onFailure { status = it.message ?: "Unable to start the local MT5 bridge." }
                    },
                    enabled = bridgeStatus == "Stopped" || bridgeStatus == "Bridge server stopped",
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Start local API")
                }
                OutlinedButton(
                    onClick = {
                        Mt5LocalBridgeServer.stop()
                        status = "Local MT5 API bridge stopped."
                    },
                    enabled = bridgeStatus != "Stopped" && bridgeStatus != "Bridge server stopped",
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stop API")
                }
            }
            lastBridgeResult?.let { result ->
                Text("Latest MT5 response: $result", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
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
            OutlinedTextField(
                value = requestedSymbol,
                onValueChange = { requestedSymbol = it.uppercase().take(32) },
                label = { Text("MT5 symbol") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = requestedTimeframe,
                onValueChange = { requestedTimeframe = it.uppercase().take(4) },
                label = { Text("Timeframe (M1, M5, M15, H1…)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = {
                    runCatching { Mt5LocalBridgeServer.requestBars(requestedSymbol, requestedTimeframe, 500) }
                        .onSuccess { sent -> status = if (sent) "Candle request sent to MT5." else "Connect the MT5 Expert Advisor first." }
                        .onFailure { status = it.message ?: "Unable to request candles." }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Request 500 MT5 candles")
            }
            if (bars.isNotEmpty()) {
                val latest = bars.last()
                Text("${bars.size} candles · ${latest.symbol} ${latest.timeframe} · Latest close ${latest.close}", style = MaterialTheme.typography.bodySmall)
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
                "Only import runtime binaries from a source you trust. They run with TradePulse's app permissions. A successful import does not mean MT5 can launch. The current generic runtime is still a prototype, not Winlator Mali compatible. In the exported EA, AllowLiveTrading defaults to false; leave it disabled until the demo-account execution path has been tested. Keep the foreground notification enabled while MT5 is running.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}