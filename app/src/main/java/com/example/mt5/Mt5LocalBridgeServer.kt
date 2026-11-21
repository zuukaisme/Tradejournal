package com.example.mt5

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
import java.util.concurrent.atomic.AtomicBoolean

object Mt5LocalBridgeServer {
    const val PORT = 8765

    private val running = AtomicBoolean(false)
    private val lock = Any()
    private val mutableStatus = MutableStateFlow("Stopped")
    private val mutableEvents = MutableSharedFlow<String>(extraBufferCapacity = 256)

    private var server: ServerSocket? = null
    private var client: Socket? = null
    private var writer: BufferedWriter? = null
    private var worker: Thread? = null

    val status = mutableStatus.asStateFlow()
    val events = mutableEvents.asSharedFlow()

    @Synchronized
    fun start() {
        if (running.get()) return
        val socket = ServerSocket()
        socket.reuseAddress = false
        socket.bind(java.net.InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT))
        server = socket
        running.set(true)
        mutableStatus.value = "Waiting for MT5 Expert Advisor"

        worker = Thread({ acceptLoop(socket) }, "TradePulse-MT5-Bridge").apply {
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

    private fun acceptLoop(serverSocket: ServerSocket) {
        while (running.get()) {
            val connection = runCatching { serverSocket.accept() }.getOrNull() ?: break
            connection.tcpNoDelay = true
            synchronized(lock) {
                runCatching { client?.close() }
                client = connection
                writer = BufferedWriter(OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8))
            }
            mutableStatus.value = "MT5 Expert Advisor connected"

            runCatching {
                BufferedReader(InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)).use { reader ->
                    while (running.get() && !connection.isClosed) {
                        val line = reader.readLine() ?: break
                        if (line.length <= 65536) mutableEvents.tryEmit(line)
                    }
                }
            }

            synchronized(lock) {
                if (client === connection) {
                    writer = null
                    client = null
                }
            }
            runCatching { connection.close() }
            if (running.get()) mutableStatus.value = "Waiting for MT5 Expert Advisor"
        }
        if (running.get()) {
            running.set(false)
            mutableStatus.value = "Bridge server stopped"
        }
    }
}