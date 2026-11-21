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
            val details = buildList {
                addAll(inspection.missingComponents)
                addAll(inspection.launchBlockers)
                add("The legacy generic Xvfb launcher is not compatible with the Winlator Mali ImageFS runtime.")
                add("MT5 launch remains disabled until the Winlator process environment and embedded X11 Surface/input bridge are connected.")
            }.distinct()
            Mt5RuntimeManager.logFile(this).writeText(
                "Embedded MT5 launch preflight\n" +
                    "ImageFS version: ${inspection.imageFsVersion ?: "not found"}\n" +
                    "Runtime files ready: ${inspection.runtimeFilesReady}\n" +
                    "Terminal launch enabled: false\n" +
                    details.joinToString("\n") { "- $it" }
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } finally {
            starting.set(false)
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
    }
}
