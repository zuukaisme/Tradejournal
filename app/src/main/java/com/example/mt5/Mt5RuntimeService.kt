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
        return START_STICKY
    }

    private fun startRuntime() {
        if (terminalPid?.let(nativeLauncher::isAlive) == true) return
        if (!starting.compareAndSet(false, true)) return
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Preparing embedded MT5 runtime"))
        stopping.set(false)

        Thread({
            try {
                runRuntime()
            } finally {
                starting.set(false)
            }
        }, "TradePulse-MT5-Launcher").apply {
            isDaemon = true
            start()
        }
    }

    private fun runRuntime() {
        val runtime = Mt5RuntimeManager.runtimeDirectory(this)
        val rootfs = File(runtime, "rootfs")
        val box64 = File(runtime, "bin/box64")
        val wine64 = File(rootfs, "usr/bin/wine64")
        val xvfb = File(rootfs, "usr/bin/Xvfb")
        val mt5Directory = File(runtime, "mt5")
        val terminal = File(mt5Directory, "terminal64.exe")
        val prefix = File(runtime, "wineprefix")
        val home = File(runtime, "home")
        val temp = File(runtime, "tmp")
        val log = Mt5RuntimeManager.logFile(this)

        try {
            check(Mt5RuntimeManager.isInstalled(this)) {
                "The embedded MT5 runtime bundle is incomplete: ${Mt5RuntimeManager.missingFiles(this).joinToString()}"
            }
            listOf(prefix, home, temp).forEach {
                check(it.mkdirs() || it.isDirectory) { "Unable to create runtime directory: ${it.name}" }
            }

            val libraryPaths = listOf(
                File(rootfs, "usr/lib/x86_64-linux-gnu"),
                File(rootfs, "lib/x86_64-linux-gnu"),
                File(rootfs, "usr/lib"),
                File(rootfs, "lib")
            ).filter { it.isDirectory }.joinToString(File.pathSeparator) { it.absolutePath }

            val environment = mapOf(
                "HOME" to home.absolutePath,
                "WINEPREFIX" to prefix.absolutePath,
                "WINEARCH" to "win64",
                "DISPLAY" to ":0",
                "WINEDEBUG" to "-all",
                "TMPDIR" to temp.absolutePath,
                "BOX64_LD_LIBRARY_PATH" to libraryPaths,
                "BOX64_PATH" to File(rootfs, "usr/bin").absolutePath,
                "BOX64_LOG" to "0"
            )

            displayPid = nativeLauncher.start(
                executable = box64,
                arguments = listOf(
                    box64.absolutePath,
                    xvfb.absolutePath,
                    ":0",
                    "-screen",
                    "0",
                    "1024x768x24",
                    "-nolisten",
                    "tcp"
                ),
                environment = environment,
                workingDirectory = runtime,
                logFile = log
            )

            Thread.sleep(1500)
            check(!stopping.get()) { "MT5 runtime startup was cancelled." }
            check(displayPid?.let(nativeLauncher::isAlive) == true) {
                "The virtual display stopped during startup. Check the MT5 runtime log."
            }

            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, buildNotification("Starting MetaTrader 5"))

            terminalPid = nativeLauncher.start(
                executable = box64,
                arguments = listOf(
                    box64.absolutePath,
                    wine64.absolutePath,
                    terminal.absolutePath
                ),
                environment = environment,
                workingDirectory = mt5Directory,
                logFile = log
            )

            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, buildNotification("Embedded MT5 terminal is running"))

            val exitCode = terminalPid?.let(nativeLauncher::waitFor) ?: -1
            terminalPid = null
            stopDisplay()

            if (!stopping.get()) {
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(NOTIFICATION_ID, buildNotification("MT5 terminal stopped (exit $exitCode)"))
                stopSelf()
            }
        } catch (error: Throwable) {
            log.appendText("\n${error.stackTraceToString()}")
            stopProcesses()
            if (!stopping.get()) {
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(NOTIFICATION_ID, buildNotification("MT5 failed to start. Check runtime log."))
                stopSelf()
            }
        }
    }

    private fun stopRuntime() {
        stopping.set(true)
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