package com.example.mt5

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom

data class TermuxX11Authorization(
    val file: File,
    val cookieHex: String
)

object TermuxHeadlessX11Bridge {
    private const val TERMUX_PACKAGE = "com.termux"
    private const val RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
    private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
    private const val TERMUX_HOME = "/data/data/com.termux/files/home"
    private const val TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash"

    fun isInstalled(context: Context): Boolean = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(
                TERMUX_PACKAGE,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        }
        true
    }.getOrDefault(false)

    fun hasRunCommandPermission(context: Context): Boolean =
        context.checkSelfPermission("com.termux.permission.RUN_COMMAND") == PackageManager.PERMISSION_GRANTED

    fun createAuthorization(context: Context): TermuxX11Authorization {
        val cookie = ByteArray(16).also(SecureRandom()::nextBytes)
        val file = File(context.filesDir, "mt5-x11.Xauthority")

        writeAuthorizationFile(file, cookie)

        val cookieHex = cookie.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return TermuxX11Authorization(file, cookieHex)
    }

    internal fun writeAuthorizationFile(file: File, cookie: ByteArray) {
        require(cookie.size == 16) { "MIT-MAGIC-COOKIE-1 requires a 16-byte cookie." }
        val authName = "MIT-MAGIC-COOKIE-1".toByteArray(Charsets.US_ASCII)
        val displayNumber = "0".toByteArray(Charsets.US_ASCII)
        DataOutputStream(FileOutputStream(file)).use { output ->
            output.writeShort(0)
            output.writeShort(4)
            output.write(byteArrayOf(127, 0, 0, 1))
            output.writeShort(displayNumber.size)
            output.write(displayNumber)
            output.writeShort(authName.size)
            output.write(authName)
            output.writeShort(cookie.size)
            output.write(cookie)
        }
    }

    fun start(context: Context, cookieHex: String) {
        require(cookieHex.matches(Regex("[0-9a-f]{32}"))) { "The X11 authorization cookie is invalid." }
        val cleanup = "if [ -f \"\$HOME/.tradepulse-xvfb.pid\" ]; then " +
            "pid=\"\$(cat \"\$HOME/.tradepulse-xvfb.pid\")\"; " +
            "case \"\$pid\" in ''|*[!0-9]*) ;; *) " +
            "if [ -r \"/proc/\$pid/cmdline\" ] && grep -aq 'Xvfb' \"/proc/\$pid/cmdline\"; then kill \"\$pid\" 2>/dev/null || true; fi ;; esac; " +
            "rm -f \"\$HOME/.tradepulse-xvfb.pid\"; fi; "
        val command = cleanup +
            "xauth -f \"\$HOME/.tradepulse.Xauthority\" add 127.0.0.1:0 MIT-MAGIC-COOKIE-1 $cookieHex && " +
            "echo \$\$ > \"\$HOME/.tradepulse-xvfb.pid\" && " +
            "exec \"\$PREFIX/bin/Xvfb\" :0 -screen 0 1280x720x24 -listen tcp -auth \"\$HOME/.tradepulse.Xauthority\" -noreset"
        runTermuxCommand(context, command)
    }

    fun stop(context: Context) {
        val command = "if [ -f \"\$HOME/.tradepulse-xvfb.pid\" ]; then " +
            "pid=\"\$(cat \"\$HOME/.tradepulse-xvfb.pid\")\"; " +
            "case \"\$pid\" in ''|*[!0-9]*) ;; *) " +
            "if [ -r \"/proc/\$pid/cmdline\" ] && grep -aq 'Xvfb' \"/proc/\$pid/cmdline\"; then kill \"\$pid\" 2>/dev/null || true; fi ;; esac; " +
            "rm -f \"\$HOME/.tradepulse-xvfb.pid\"; fi"
        runCatching { runTermuxCommand(context, command) }
    }

    fun isDisplayReady(cookieHex: String, timeoutMs: Int = 1000): Boolean = runCatching {
        require(cookieHex.matches(Regex("[0-9a-f]{32}")))
        val authName = "MIT-MAGIC-COOKIE-1".toByteArray(Charsets.US_ASCII)
        val cookie = cookieHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", 6000), timeoutMs)
            socket.soTimeout = timeoutMs
            val packet = byteArrayOf(
                'l'.code.toByte(), 0,
                11, 0,
                0, 0,
                authName.size.toByte(), 0,
                cookie.size.toByte(), 0,
                0, 0
            ) + authName + byteArrayOf(0, 0) + cookie
            socket.getOutputStream().write(packet)
            socket.getOutputStream().flush()
            socket.getInputStream().read() == 1
        }
    }.getOrDefault(false)

    private fun runTermuxCommand(context: Context, command: String) {
        check(isInstalled(context)) { "Termux is not installed." }
        check(hasRunCommandPermission(context)) {
            "Grant TradePulse the 'Run commands in Termux environment' permission in Android app settings."
        }

        val intent = Intent(RUN_COMMAND_ACTION).apply {
            setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            putExtra(EXTRA_COMMAND_PATH, TERMUX_BASH)
            putExtra(EXTRA_ARGUMENTS, arrayOf("-lc", command))
            putExtra(EXTRA_WORKDIR, TERMUX_HOME)
            putExtra(EXTRA_BACKGROUND, true)
            putExtra(EXTRA_SESSION_ACTION, "0")
        }
        context.startService(intent)
    }
}
