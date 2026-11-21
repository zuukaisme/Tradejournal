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
    private val mutableEvents = MutableSharedFlow<String>(replay = 32, extraBufferCapacity = 256)
    private val mutableAccount = MutableStateFlow<Mt5AccountSnapshot?>(null)
    private val mutablePositions = MutableStateFlow<List<Mt5PositionSnapshot>>(emptyList())
    private val mutableBars = MutableStateFlow<List<Mt5CandleSnapshot>>(emptyList())
    private val mutableLastResult = MutableStateFlow<String?>(null)
    private val pendingPositions = mutableListOf<Mt5PositionSnapshot>()
    private val pendingBars = mutableListOf<Mt5CandleSnapshot>()
    private val mutableToken = MutableStateFlow("")

    private var server: ServerSocket? = null
    private var client: Socket? = null
    private var writer: BufferedWriter? = null
    private var worker: Thread? = null

    val status = mutableStatus.asStateFlow()
    val events = mutableEvents.asSharedFlow()
    val account = mutableAccount.asStateFlow()
    val positions = mutablePositions.asStateFlow()
    val bars = mutableBars.asStateFlow()
    val lastResult = mutableLastResult.asStateFlow()
    val token = mutableToken.asStateFlow()

    @Synchronized
    fun start(context: Context) {
        if (running.get()) return
        mutableToken.value = loadOrCreateToken(context.applicationContext)
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(java.net.InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT))
        server = socket
        running.set(true)
        mutableStatus.value = "Waiting for authenticated MT5 Expert Advisor"

        worker = Thread({ acceptLoop(socket, mutableToken.value) }, "TradePulse-MT5-Bridge").apply {
            isDaemon = true
            start()
        }
    }

    fun requestBars(symbol: String, timeframe: String, count: Int = 500): Boolean {
        require(symbol.matches(Regex("[A-Za-z0-9._-]{1,32}"))) { "Invalid MT5 symbol." }
        require(timeframe in setOf("M1", "M2", "M3", "M4", "M5", "M6", "M10", "M12", "M15", "M20", "M30", "H1", "H2", "H3", "H4", "H6", "H8", "H12", "D1", "W1", "MN1")) { "Unsupported timeframe." }
        require(count in 1..1000) { "Candle count must be between 1 and 1000." }
        return sendCommand("BARS|$symbol|$timeframe|$count")
    }

    fun placeMarketOrder(side: String, symbol: String, volume: Double, liveTradingConfirmed: Boolean): Boolean {
        require(liveTradingConfirmed) { "Explicit live-trading confirmation is required." }
        require(side == "BUY" || side == "SELL") { "Order side must be BUY or SELL." }
        require(symbol.matches(Regex("[A-Za-z0-9._-]{1,32}"))) { "Invalid MT5 symbol." }
        require(volume.isFinite() && volume > 0.0) { "Volume must be greater than zero." }
        return sendCommand("$side|$symbol|$volume")
    }

    fun closePosition(ticket: Long, liveTradingConfirmed: Boolean): Boolean {
        require(liveTradingConfirmed) { "Explicit live-trading confirmation is required." }
        require(ticket > 0L) { "Invalid MT5 position ticket." }
        return sendCommand("CLOSE|$ticket")
    }

    fun closeAllPositions(liveTradingConfirmed: Boolean): Boolean {
        require(liveTradingConfirmed) { "Explicit live-trading confirmation is required." }
        return sendCommand("CLOSE_ALL")
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
        mutableAccount.value = null
        mutablePositions.value = emptyList()
        mutableBars.value = emptyList()
        mutableLastResult.value = null
        synchronized(pendingPositions) { pendingPositions.clear() }
        synchronized(pendingBars) { pendingBars.clear() }
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
            mutableAccount.value = null
            mutablePositions.value = emptyList()
            mutableBars.value = emptyList()
            mutableLastResult.value = null
            synchronized(pendingPositions) { pendingPositions.clear() }
            synchronized(pendingBars) { pendingBars.clear() }
            runCatching {
                connectionWriter.write("AUTH_OK")
                connectionWriter.newLine()
                connectionWriter.flush()
            }
            mutableStatus.value = "MT5 Expert Advisor authenticated"

            runCatching {
                while (running.get() && !connection.isClosed) {
                    val line = reader.readLine() ?: break
                    if (line.length <= 65536) handleEvent(line)
                }
            }

            synchronized(lock) {
                if (client === connection) {
                    writer = null
                    client = null
                }
            }
            runCatching { connection.close() }
            mutableAccount.value = null
            mutablePositions.value = emptyList()
            mutableBars.value = emptyList()
            synchronized(pendingPositions) { pendingPositions.clear() }
            synchronized(pendingBars) { pendingBars.clear() }
            if (running.get()) mutableStatus.value = "Waiting for authenticated MT5 Expert Advisor"
        }

        synchronized(this) {
            if (running.get() && server === serverSocket) {
                running.set(false)
                server = null
                mutableStatus.value = "Bridge server stopped"
            }
        }
    }

    private fun handleEvent(line: String) {
        mutableEvents.tryEmit(line)
        val fields = line.split('|')
        when (fields.firstOrNull()) {
            "HELLO" -> if (fields.size >= 4) {
                mutableLastResult.value = "Connected: ${fields[1]} · ${fields[2]} · ${fields[3]}"
            }
            "PONG" -> mutableLastResult.value = "MT5 bridge heartbeat received"
            "ORDER_RESULT" -> if (fields.size >= 6) {
                mutableLastResult.value = "${fields[1]} ${fields[2]}: ${fields[3]} · ${fields[4]} · ${fields[5]}"
            }
            "CLOSE_RESULT" -> if (fields.size >= 4) {
                mutableLastResult.value = "Close ${fields[1]}: ${fields[2]} · ${fields[3]}"
            }
            "CLOSE_ALL_RESULT" -> if (fields.size >= 3) {
                mutableLastResult.value = "Close all: ${fields[1]} closed · ${fields[2]} failed"
            }
            "ERROR" -> mutableLastResult.value = fields.drop(1).joinToString(" · ")
            "ACCOUNT" -> if (fields.size >= 8) {
                mutableAccount.value = Mt5AccountSnapshot(
                    login = fields[1].toLongOrNull() ?: 0L,
                    server = fields[2],
                    currency = fields[3],
                    balance = fields[4].toDoubleOrNull() ?: 0.0,
                    equity = fields[5].toDoubleOrNull() ?: 0.0,
                    margin = fields[6].toDoubleOrNull() ?: 0.0,
                    freeMargin = fields[7].toDoubleOrNull() ?: 0.0
                )
            }
            "BAR" -> if (fields.size >= 9) {
                val candle = Mt5CandleSnapshot(
                    symbol = fields[1],
                    timeframe = fields[2],
                    timestamp = fields[3].toLongOrNull() ?: 0L,
                    open = fields[4].toDoubleOrNull() ?: 0.0,
                    high = fields[5].toDoubleOrNull() ?: 0.0,
                    low = fields[6].toDoubleOrNull() ?: 0.0,
                    close = fields[7].toDoubleOrNull() ?: 0.0,
                    volume = fields[8].toLongOrNull() ?: 0L
                )
                synchronized(pendingBars) { pendingBars += candle }
            }
            "BARS_END" -> synchronized(pendingBars) {
                mutableBars.value = pendingBars.toList()
                pendingBars.clear()
            }
            "POSITION" -> if (fields.size >= 7) {
                val position = Mt5PositionSnapshot(
                    ticket = fields[1].toLongOrNull() ?: 0L,
                    symbol = fields[2],
                    type = fields[3].toIntOrNull() ?: 0,
                    volume = fields[4].toDoubleOrNull() ?: 0.0,
                    openPrice = fields[5].toDoubleOrNull() ?: 0.0,
                    profit = fields[6].toDoubleOrNull() ?: 0.0
                )
                synchronized(pendingPositions) { pendingPositions += position }
            }
            "SNAPSHOT_END" -> synchronized(pendingPositions) {
                mutablePositions.value = pendingPositions.toList()
                pendingPositions.clear()
            }
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

data class Mt5AccountSnapshot(
    val login: Long,
    val server: String,
    val currency: String,
    val balance: Double,
    val equity: Double,
    val margin: Double,
    val freeMargin: Double
)

data class Mt5PositionSnapshot(
    val ticket: Long,
    val symbol: String,
    val type: Int,
    val volume: Double,
    val openPrice: Double,
    val profit: Double
)


data class Mt5CandleSnapshot(
    val symbol: String,
    val timeframe: String,
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)
