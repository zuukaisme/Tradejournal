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
    private var displayPid: Int? = null

    @Volatile
    private var terminalPid: Int? = null

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
                add("Terminal launch remains disabled until a headless X11 server is integrated and validated.")
            }.distinct()
            Mt5RuntimeManager.logFile(this).writeText(
                "Embedded MT5 launch preflight\n" +
                    "ImageFS version: ${inspection.imageFsVersion ?: "not found"}\n" +
                    "Runtime files ready: ${inspection.runtimeFilesReady}\n" +
                    "Wine executable: ${inspection.wine?.absolutePath ?: "not found"}\n" +
                    "Selected Wine prefix: ${inspection.winePrefix?.absolutePath ?: "not found"}\n" +
                    "MT5 terminal: ${inspection.terminal?.absolutePath ?: "not found"}\n" +
                    "Wine architecture validated: ${launchConfiguration.blockers.none { it.contains("ARM64 ELF") }}\n" +
                    "Display socket present: ${File(filesDir, "imagefs/usr/tmp/.X11-unix/X0").exists()}\n" +
                    "Terminal launch enabled: false\n" +
                    "Launch arguments: ${launchConfiguration.arguments.joinToString(" ")}\n" +
                    "Launch environment keys: ${launchConfiguration.environment.keys.sorted().joinToString(", ")}\n" +
                    details.joinToString("\n") { "- $it" }
            )
            val canProbeWine = launchConfiguration.executable != null &&
                launchConfiguration.blockers.all { it.startsWith("A live headless X11 server") }
            if (canProbeWine) {
                runWineVersionProbe(launchConfiguration)
            } else {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } finally {
            starting.set(false)
        }
    }

    private fun runWineVersionProbe(configuration: WinlatorMaliLaunchConfiguration) {
        Thread({
            var pid: Int? = null
            try {
                val executable = configuration.executable ?: error("Wine executable is unavailable.")
                pid = nativeLauncher.start(
                    executable,
                    listOf("--version"),
                    configuration.environment,
                    configuration.workingDirectory,
                    File(filesDir, "mt5-wine-probe.log")
                )
                val deadline = System.currentTimeMillis() + WINE_PROBE_TIMEOUT_MS
                while (nativeLauncher.isAlive(pid) && System.currentTimeMillis() < deadline) {
                    Thread.sleep(100)
                }
                if (nativeLauncher.isAlive(pid)) {
                    nativeLauncher.stop(pid)
                    Thread.sleep(1500)
                    if (nativeLauncher.isAlive(pid)) nativeLauncher.forceStop(pid)
                }
                val exitCode = nativeLauncher.waitFor(pid)
                appendRuntimeLog("Wine version probe exit code: $exitCode")
            } catch (error: Throwable) {
                appendRuntimeLog("Wine version probe failed: ${error.message ?: error.javaClass.simpleName}")
                pid?.let { runCatching { nativeLauncher.forceStop(it) } }
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }, "TradePulse-Wine-Probe").start()
    }

    private fun appendRuntimeLog(message: String) {
        runCatching {
            Mt5RuntimeManager.logFile(this).appendText("\\n$message")
        }
    }

    private fun stopRuntime() {
        stopping.set(true)
        Mt5LocalBridgeServer.stop()
        stopProcesses()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun stopProcesses() {
        terminalPid?.let { runCatching { nativeLauncher.stop(it) } }
        terminalPid = null
        stopDisplay()
    }

    private fun stopDisplay() {
        displayPid?.let { runCatching { nativeLauncher.stop(it) } }
        displayPid = null
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
    }
}
