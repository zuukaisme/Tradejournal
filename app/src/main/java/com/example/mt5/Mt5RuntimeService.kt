package com.example.mt5

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class Mt5RuntimeService : Service() {
    private val stopping = AtomicBoolean(false)
    private val starting = AtomicBoolean(false)
    private val nativeLauncher by lazy { NativeProcessLauncher() }

    @Volatile
    private var terminalPid: Int? = null

    @Volatile
    private var probePid: Int? = null

    @Volatile
    private var termuxDisplayActive = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRuntime()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> startRuntime()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRuntime() {
        if (!starting.compareAndSet(false, true)) return
        var handedOffToProbe = false
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification("Checking Winlator Mali runtime"))
            val inspection = Mt5RuntimeManager.winlatorMaliInspection(this)
            val launchConfiguration = WinlatorMaliLaunchConfigurationBuilder.build(
                File(filesDir, "imagefs"),
                inspection
            )
            val details = buildList {
                addAll(launchConfiguration.blockers)
                addAll(inspection.launchBlockers)
                if (!TermuxHeadlessX11Bridge.isInstalled(this@Mt5RuntimeService)) {
                    add("Install Termux and its xorg-server-xvfb package to provide the headless display.")
                } else if (!TermuxHeadlessX11Bridge.hasRunCommandPermission(this@Mt5RuntimeService)) {
                    add("Grant TradePulse the Termux Run commands permission and enable allow-external-apps in Termux.")
                }
                add("Terminal launch remains disabled until the X11 display and Wine runtime probes pass.")
            }.distinct()
            Mt5RuntimeManager.logFile(this).writeText(
                "Embedded MT5 launch preflight\n" +
                    "ImageFS version: ${inspection.imageFsVersion ?: "not found"}\n" +
                    "Runtime files ready: ${inspection.runtimeFilesReady}\n" +
                    "Wine executable: ${inspection.wine?.absolutePath ?: "not found"}\n" +
                    "Selected Wine prefix: ${inspection.winePrefix?.absolutePath ?: "not found"}\n" +
                    "MT5 terminal: ${inspection.terminal?.absolutePath ?: "not found"}\n" +
                    "Wine architecture validated: ${launchConfiguration.blockers.none { it.contains("ARM64 ELF") }}\n" +
                    "Termux available: ${TermuxHeadlessX11Bridge.isInstalled(this)}\n" +
                    "Termux command permission: ${TermuxHeadlessX11Bridge.hasRunCommandPermission(this)}\n" +
                    "Headless display endpoint: authenticated 127.0.0.1:0\n" +
                    "Terminal launch enabled: false\n" +
                    "Launch arguments: ${launchConfiguration.arguments.joinToString(" ")}\n" +
                    "Launch environment keys: ${launchConfiguration.environment.keys.sorted().joinToString(", ")}\n" +
                    details.joinToString("\n") { "- $it" }
            )
            val hasTermuxControl = TermuxHeadlessX11Bridge.isInstalled(this) &&
                TermuxHeadlessX11Bridge.hasRunCommandPermission(this)
            val canProbeWine = hasTermuxControl &&
                launchConfiguration.executable != null &&
                launchConfiguration.blockers.all { it.startsWith("A live headless X11 server") }
            if (canProbeWine) {
                handedOffToProbe = true
                runWineVersionProbe()
            } else {
                appendRuntimeLog("Wine probe not started. Check Termux permission, allow-external-apps, and the Winlator Mali runtime files.")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } finally {
            if (!handedOffToProbe) starting.set(false)
        }
    }

    private fun runWineVersionProbe() {
        Thread({
            var pid: Int? = null
            try {
                check(TermuxHeadlessX11Bridge.isInstalled(this)) { "Termux is not installed." }
                check(TermuxHeadlessX11Bridge.hasRunCommandPermission(this)) {
                    "Grant TradePulse the Termux Run commands permission."
                }
                val authorization = TermuxHeadlessX11Bridge.createAuthorization(this)
                TermuxHeadlessX11Bridge.start(this, authorization.cookieHex)
                termuxDisplayActive = true

                val displayDeadline = System.currentTimeMillis() + DISPLAY_START_TIMEOUT_MS
                var displayReady = false
                while (System.currentTimeMillis() < displayDeadline) {
                    check(!stopping.get()) { "Runtime startup was cancelled." }
                    if (TermuxHeadlessX11Bridge.isDisplayReady(authorization.cookieHex)) {
                        displayReady = true
                        break
                    }
                    Thread.sleep(150)
                }
                check(displayReady) {
                    "Termux Xvfb did not complete an authenticated X11 handshake. Check the Xvfb package and allow-external-apps setting."
                }

                val imageFs = File(filesDir, "imagefs")
                val inspection = Mt5RuntimeManager.winlatorMaliInspection(this)
                val resolvedConfiguration = WinlatorMaliLaunchConfigurationBuilder.build(
                    imageFs,
                    inspection,
                    File(imageFs, "usr/tmp/.X11-unix/X0"),
                    "127.0.0.1:0",
                    authorization.file,
                    true
                )
                check(resolvedConfiguration.blockers.isEmpty()) {
                    resolvedConfiguration.blockers.joinToString()
                }
                val executable = resolvedConfiguration.executable ?: error("Wine executable is unavailable.")
                val processId = nativeLauncher.start(
                    executable,
                    listOf("--version"),
                    resolvedConfiguration.environment,
                    resolvedConfiguration.workingDirectory,
                    File(filesDir, "mt5-wine-probe.log")
                )
                pid = processId
                probePid = processId
                if (stopping.get()) nativeLauncher.stop(processId)
                val deadline = System.currentTimeMillis() + WINE_PROBE_TIMEOUT_MS
                while (nativeLauncher.isAlive(processId) && System.currentTimeMillis() < deadline) {
                    Thread.sleep(100)
                }
                if (nativeLauncher.isAlive(processId)) {
                    nativeLauncher.stop(processId)
                    Thread.sleep(1500)
                    if (nativeLauncher.isAlive(processId)) nativeLauncher.forceStop(processId)
                }
                val exitCode = nativeLauncher.waitFor(processId)
                appendRuntimeLog("Wine version probe exit code: $exitCode")
            } catch (error: Throwable) {
                appendRuntimeLog("Wine version probe failed: ${error.message ?: error.javaClass.simpleName}")
                pid?.let { runCatching { nativeLauncher.forceStop(it) } }
            } finally {
                probePid = null
                stopDisplay()
                starting.set(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }, "TradePulse-Wine-Probe").start()
    }

    private fun appendRuntimeLog(message: String) {
        runCatching {
            Mt5RuntimeManager.logFile(this).appendText("\n$message")
        }
    }

    private fun stopRuntime() {
        stopping.set(true)
        Mt5LocalBridgeServer.stop()
        stopProcesses()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun stopProcesses() {
        probePid?.let { runCatching { nativeLauncher.stop(it) } }
        probePid = null
        terminalPid?.let { runCatching { nativeLauncher.stop(it) } }
        terminalPid = null
        stopDisplay()
    }

    @Synchronized
    private fun stopDisplay() {
        if (termuxDisplayActive) {
            TermuxHeadlessX11Bridge.stop(this)
            termuxDisplayActive = false
        }
    }

    override fun onDestroy() {
        stopRuntime()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MT5 runtime",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("TradePulse MT5")
            .setContentText(message)
            .setOngoing(message.contains("running", ignoreCase = true))
            .setOnlyAlertOnce(true)
            .build()

    companion object {
        const val ACTION_START = "com.example.mt5.START"
        const val ACTION_STOP = "com.example.mt5.STOP"
        private const val CHANNEL_ID = "tradepulse_mt5_runtime"
        private const val NOTIFICATION_ID = 7315
        private const val WINE_PROBE_TIMEOUT_MS = 20_000L
        private const val DISPLAY_START_TIMEOUT_MS = 10_000L
    }
}
