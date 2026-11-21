package com.example.mt5

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.LocalSocket
import android.net.LocalSocketAddress
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
                if (!TermuxHeadlessX11Bridge.isInstalled(this)) {
                    add("Install Termux and its xorg-server-xvfb package to provide the headless display.")
                } else if (!TermuxHeadlessX11Bridge.hasRunCommandPermission(this)) {
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
                    "Display socket present: ${File(filesDir, "imagefs/usr/tmp/.X11-unix/X0").exists()}\n" +
                    "Terminal launch enabled: false\n" +
                    "Launch arguments: ${launchConfiguration.arguments.joinToString(" ")}\n" +
                    "Launch environment keys: ${launchConfiguration.environment.keys.sorted().joinToString(", ")}\n" +
                    details.joinToString("\n") { "- $it" }
            )
            val hasTermuxControl = TermuxHeadlessX11Bridge.isInstalled(this) &&
                TermuxHeadlessX11Bridge.hasRunCommandPermission(this)
            val canProbeWine = (hasTermuxControl || HeadlessX11RuntimeManager.isInstalled(this)) &&
                launchConfiguration.executable != null &&
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
                val imageFs = File(filesDir, "imagefs")
                val displaySocket = File(imageFs, "usr/tmp/.X11-unix/X0")
                var authorization: TermuxX11Authorization? = null
                var usingTermuxDisplay = false

                if (TermuxHeadlessX11Bridge.isInstalled(this) &&
                    TermuxHeadlessX11Bridge.hasRunCommandPermission(this)
                ) {
                    try {
                        authorization = TermuxHeadlessX11Bridge.createAuthorization(this)
                        TermuxHeadlessX11Bridge.start(this, authorization.cookieHex)
                        termuxDisplayActive = true
                        val displayDeadline = System.currentTimeMillis() + DISPLAY_START_TIMEOUT_MS
                        while (System.currentTimeMillis() < displayDeadline) {
                            check(!stopping.get()) { "Runtime startup was cancelled." }
                            if (TermuxHeadlessX11Bridge.isDisplayReady()) {
                                usingTermuxDisplay = true
                                break
                            }
                            Thread.sleep(100)
                        }
                        if (!usingTermuxDisplay) {
                            TermuxHeadlessX11Bridge.stop(this)
                            termuxDisplayActive = false
                            appendRuntimeLog("Termux Xvfb did not open TCP display :0.")
                        }
                    } catch (error: Throwable) {
                        TermuxHeadlessX11Bridge.stop(this)
                        termuxDisplayActive = false
                        if (stopping.get()) throw error
                        appendRuntimeLog("Termux Xvfb startup failed: ${error.message ?: error.javaClass.simpleName}")
                    }
                }

                if (!usingTermuxDisplay) startHeadlessDisplay(imageFs, displaySocket)
                check(!stopping.get()) { "Runtime startup was cancelled." }
                val inspection = Mt5RuntimeManager.winlatorMaliInspection(this)
                val resolvedConfiguration = WinlatorMaliLaunchConfigurationBuilder.build(
                    imageFs,
                    inspection,
                    displaySocket,
                    if (usingTermuxDisplay) "127.0.0.1:0" else ":0",
                    authorization?.file,
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
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }, "TradePulse-Wine-Probe").start()
    }

    private fun startHeadlessDisplay(imageFs: File, socket: File) {
        val runtime = HeadlessX11RuntimeManager.runtimeDirectory(this)
        check(HeadlessX11RuntimeManager.isInstalled(this)) {
            "Import the Android ARM64 headless X11 runtime before starting the Wine check."
        }
        val temporaryDirectory = File(imageFs, "usr/tmp")
        check(temporaryDirectory.mkdirs() || temporaryDirectory.isDirectory) {
            "Unable to create the Winlator temporary directory."
        }
        val socketDirectory = socket.parentFile
            ?: error("The X11 socket directory is invalid.")
        check(socketDirectory.mkdirs() || socketDirectory.isDirectory) {
            "Unable to create the X11 socket directory."
        }
        socket.delete()

        val xvfb = File(runtime, "bin/Xvfb")
        val environment = buildMap {
            put("HOME", runtime.absolutePath)
            put("PREFIX", runtime.absolutePath)
            put("PATH", "${File(runtime, "bin").absolutePath}:/system/bin")
            put("TMPDIR", temporaryDirectory.absolutePath)
            put("DISPLAY", ":0")
            put("XKB_CONFIG_ROOT", File(runtime, "share/X11/xkb").absolutePath)
            put("FONTCONFIG_PATH", File(runtime, "etc/fonts").absolutePath)
            put("LD_LIBRARY_PATH", "${File(runtime, "lib").absolutePath}:/system/lib64")
            val sharedMemory = File(runtime, "lib/libandroid-shmem.so")
            if (sharedMemory.isFile) put("LD_PRELOAD", sharedMemory.absolutePath)
        }
        val processId = nativeLauncher.start(
            xvfb,
            listOf(
                ":0",
                "-screen", "0", "1280x720x24",
                "-fp", File(runtime, "share/fonts").absolutePath,
                "-nolisten", "tcp",
                "-ac",
                "-noreset"
            ),
            environment,
            runtime,
            File(filesDir, "mt5-xvfb.log")
        )
        displayPid = processId

        val deadline = System.currentTimeMillis() + DISPLAY_START_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            check(!stopping.get()) { "Runtime startup was cancelled." }
            if (!nativeLauncher.isAlive(processId)) {
                val exitCode = nativeLauncher.waitFor(processId)
                displayPid = null
                error("Xvfb exited before creating its display socket. Exit code: $exitCode")
            }
            if (canConnectToDisplaySocket(socket)) return
            Thread.sleep(100)
        }

        stopAndReap(processId)
        displayPid = null
        error("Xvfb did not create a working :0 display socket within the startup timeout.")
    }

    private fun canConnectToDisplaySocket(socketFile: File): Boolean = runCatching {
        if (!socketFile.exists()) return@runCatching false
        LocalSocket().use { client ->
            client.connect(
                LocalSocketAddress(
                    socketFile.absolutePath,
                    LocalSocketAddress.Namespace.FILESYSTEM
                )
            )
        }
        true
    }.getOrDefault(false)

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
        displayPid?.let(::stopAndReap)
        displayPid = null
    }

    private fun stopAndReap(pid: Int) {
        runCatching { nativeLauncher.stop(pid) }
        val deadline = System.currentTimeMillis() + 1500
        while (runCatching { nativeLauncher.isAlive(pid) }.getOrDefault(false) &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(50)
        }
        if (runCatching { nativeLauncher.isAlive(pid) }.getOrDefault(false)) {
            runCatching { nativeLauncher.forceStop(pid) }
        }
        runCatching { nativeLauncher.waitFor(pid) }
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
