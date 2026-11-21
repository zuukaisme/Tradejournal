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
    private var runtimeProcess: Process? = null
    private val stopping = AtomicBoolean(false)

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
        return START_STICKY
    }

    private fun startRuntime() {
        if (runtimeProcess?.isAlive == true) return
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Starting embedded MT5 runtime"))

        if (!Mt5RuntimeManager.isInstalled(this)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        stopping.set(false)
        val runtime = Mt5RuntimeManager.runtimeDirectory(this)
        val launcher = File(runtime, "launch-mt5.sh")
        val log = Mt5RuntimeManager.logFile(this)

        try {
            val process = ProcessBuilder("/system/bin/sh", launcher.absolutePath)
                .directory(runtime)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
                .apply {
                    environment()["TRADEPULSE_RUNTIME_DIR"] = runtime.absolutePath
                    environment()["TRADEPULSE_MT5_DIR"] = File(runtime, "mt5").absolutePath
                    environment()["TRADEPULSE_WINEPREFIX"] = File(runtime, "wineprefix").absolutePath
                    environment()["TRADEPULSE_LOG_FILE"] = log.absolutePath
                }
                .start()
            runtimeProcess = process

            Thread {
                val exitCode = runCatching { process.waitFor() }.getOrNull()
                if (!stopping.get()) {
                    val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(
                        NOTIFICATION_ID,
                        buildNotification("MT5 runtime stopped${exitCode?.let { " (exit $it)" } ?: ""}")
                    )
                    stopSelf()
                }
            }.apply {
                name = "TradePulse-MT5-Watcher"
                isDaemon = true
                start()
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, buildNotification("Embedded MT5 runtime is running"))
        } catch (error: Exception) {
            log.appendText("\n${error.stackTraceToString()}")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopRuntime() {
        stopping.set(true)
        runtimeProcess?.let { process ->
            process.destroy()
            if (process.isAlive) process.destroyForcibly()
        }
        runtimeProcess = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopRuntime()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "MT5 runtime", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
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