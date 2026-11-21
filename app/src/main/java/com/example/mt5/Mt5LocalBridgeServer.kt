package com.example.mt5

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

object Mt5LocalBridgeServer {
    const val PORT = 8765
    private const val PREFS = "tradepulse_mt5_bridge"
    private const val TOKEN_KEY = "local_auth_token"

    private val running = AtomicBoolean(false)
    private val lock = Any()
    private val mutableStatus = MutableStateFlow("Stopped")
    private val mutableEvents = MutableSharedFlow<String>(extraBufferCapacity = 256)
    private val mutableToken = MutableStateFlow("")

    private var server: ServerSocket? = null
    private var client: Socket? = null
    private var writer: BufferedWriter? = null
    private var worker: Thread? = null

    val status = mutableStatus.asStateFlow()
    val events = mutableEvents.asSharedFlow()
    val token = mutableToken.asStateFlow()

    @Synchronized
    fun start(context: Context) {
        if (running.get()) return
        mutableToken.value = loadOrCreateToken(context.applicationContext)
        val socket = ServerSocket()
        socket.reuseAddress = false
        socket.bind(java.net.InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT))
        server = socket
        running.set(true)
        mutableStatus.value = "Waiting for authenticated MT5 Expert Advisor"

        worker = Thread({ acceptLoop(socket, mutableToken.value) }, "TradePulse-MT5-Bridge").apply {
            isDaemon = true
            start()
        }
    }

    fun sendCommand(command: String): Boolean {
        if (command.isBlank() || command.contains('\n') || command.contains('\r')) return false
        return synchronized(lock) {
            val activeWriter = writer ?: return@synchronized false
            runCatching {
                activeWriter.write(command)
                activeWriter.newLine()
                activeWriter.flush()
            }.onFailure {
                mutableStatus.value = "MT5 bridge disconnected"
            }.isSuccess
        }
    }

    @Synchronized
    fun stop() {
        running.set(false)
        synchronized(lock) {
            runCatching { writer?.close() }
            runCatching { client?.close() }
            writer = null
            client = null
        }
        runCatching { server?.close() }
        server = null
        worker?.interrupt()
        worker = null
        mutableStatus.value = "Stopped"
    }

    private fun acceptLoop(serverSocket: ServerSocket, expectedToken: String) {
        while (running.get()) {
            val connection = runCatching { serverSocket.accept() }.getOrNull() ?: break
            connection.tcpNoDelay = true
            connection.soTimeout = 5000

            val reader = runCatching {
                BufferedReader(InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))
            }.getOrNull()
            val connectionWriter = runCatching {
                BufferedWriter(OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8))
            }.getOrNull()

            if (reader == null || connectionWriter == null) {
                runCatching { connection.close() }
                continue
            }

            val authentication = runCatching { reader.readLine() }.getOrNull()
            if (authentication != "AUTH|$expectedToken") {
                runCatching {
                    connectionWriter.write("AUTH_FAILED")
                    connectionWriter.newLine()
                    connectionWriter.flush()
                }
                runCatching { connection.close() }
                continue
            }

            connection.soTimeout = 0
            synchronized(lock) {
                runCatching { client?.close() }
                client = connection
                writer = connectionWriter
            }
            runCatching {
                connectionWriter.write("AUTH_OK")
                connectionWriter.newLine()
                connectionWriter.flush()
            }
            mutableStatus.value = "MT5 Expert Advisor authenticated"

            runCatching {
                while (running.get() && !connection.isClosed) {
                    val line = reader.readLine() ?: break
                    if (line.length <= 65536) mutableEvents.tryEmit(line)
                }
            }

            synchronized(lock) {
                if (client === connection) {
                    writer = null
                    client = null
                }
            }
            runCatching { connection.close() }
            if (running.get()) mutableStatus.value = "Waiting for authenticated MT5 Expert Advisor"
        }

        if (running.get()) {
            running.set(false)
            mutableStatus.value = "Bridge server stopped"
        }
    }

    private fun loadOrCreateToken(context: Context): String {
        val preferences: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        preferences.getString(TOKEN_KEY, null)?.let { return it }

        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        val value = bytes.joinToString("") { "%02x".format(it) }
        preferences.edit().putString(TOKEN_KEY, value).apply()
        return value
    }
}