package com.example.backtest.worker

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.example.backtest.BacktestProgressListener
import com.example.backtest.PythonBacktestRunner
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Isolated Android service executing Python strategy backtests in its own OS process
 * and independent CPython GIL for true multi-core parallelism.
 */
abstract class BacktestWorkerService(private val workerLabel: String) : Service() {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "BacktestWorker-$workerLabel").apply { isDaemon = true }
    }

    private val incomingHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != MSG_RUN_STRATEGY) return
            val data = Bundle(msg.data)
            val replyTo = msg.replyTo ?: return
            executor.execute {
                executeStrategyTask(data, replyTo)
            }
        }
    }

    private val messenger = Messenger(incomingHandler)

    override fun onCreate() {
        super.onCreate()
        executor.execute {
            runCatching { PythonBacktestRunner.ensureStarted(applicationContext) }
        }
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun executeStrategyTask(data: Bundle, replyTo: Messenger) {
        val requestId = data.getString(EXTRA_REQUEST_ID).orEmpty()
        val strategyId = data.getLong(EXTRA_STRATEGY_ID, 0L)
        val className = data.getString(EXTRA_CLASS_NAME).orEmpty()
        val csvPath = data.getString(EXTRA_CSV_PATH).orEmpty()
        val codeFilePath = data.getString(EXTRA_CODE_FILE_PATH).orEmpty()
        val resultFilePath = data.getString(EXTRA_RESULT_FILE_PATH).orEmpty()
        val maxStopPoints = data.getDouble(EXTRA_MAX_STOP_POINTS, 0.0)
        val pointSize = data.getDouble(EXTRA_POINT_SIZE, 1.0)
        val loadStart = data.getInt(EXTRA_LOAD_START, 0)
        val loadEnd = data.getInt(EXTRA_LOAD_END, -1)
        val entryStart = data.getInt(EXTRA_ENTRY_START, 0)
        val entryEnd = data.getInt(EXTRA_ENTRY_END, -1)

        try {
            val code = File(codeFilePath).readText(Charsets.UTF_8)
            var lastSentAt = 0L
            val listener = BacktestProgressListener { progressJson ->
                val now = System.currentTimeMillis()
                if (now - lastSentAt >= 120L) {
                    lastSentAt = now
                    sendProgress(replyTo, requestId, strategyId, progressJson)
                }
            }

            val rawJson = if (entryEnd > entryStart && entryEnd > 0) {
                PythonBacktestRunner.runWithStrategyFileSlice(
                    context = applicationContext,
                    csvPath = csvPath,
                    strategyCode = code,
                    className = className,
                    maxStopPoints = maxStopPoints,
                    pointSize = pointSize,
                    progressListener = listener,
                    loadStart = loadStart,
                    loadEnd = loadEnd,
                    entryStart = entryStart,
                    entryEnd = entryEnd
                )
            } else {
                PythonBacktestRunner.runWithStrategyFile(
                    context = applicationContext,
                    csvPath = csvPath,
                    strategyCode = code,
                    className = className,
                    maxStopPoints = maxStopPoints,
                    pointSize = pointSize,
                    progressListener = listener
                )
            }

            val outFile = File(resultFilePath)
            outFile.parentFile?.mkdirs()
            val tmpFile = File(outFile.parentFile, "${outFile.name}.tmp")
            tmpFile.writeText(rawJson, Charsets.UTF_8)
            if (outFile.exists()) outFile.delete()
            if (!tmpFile.renameTo(outFile)) {
                tmpFile.copyTo(outFile, overwrite = true)
                tmpFile.delete()
            }

            val msg = Message.obtain(null, MSG_RESULT).apply {
                this.data = Bundle().apply {
                    putString(EXTRA_REQUEST_ID, requestId)
                    putLong(EXTRA_STRATEGY_ID, strategyId)
                    putString(EXTRA_WORKER_LABEL, workerLabel)
                    putString(EXTRA_RESULT_FILE_PATH, outFile.absolutePath)
                }
            }
            runCatching { replyTo.send(msg) }
        } catch (t: Throwable) {
            val errJson = JSONObject().apply {
                put("error", t.message ?: t::class.simpleName ?: "Worker execution failed")
                put("details", t.stackTraceToString().take(1500))
            }.toString()
            runCatching {
                val outFile = File(resultFilePath)
                outFile.parentFile?.mkdirs()
                outFile.writeText(errJson, Charsets.UTF_8)
            }
            val msg = Message.obtain(null, MSG_RESULT).apply {
                this.data = Bundle().apply {
                    putString(EXTRA_REQUEST_ID, requestId)
                    putLong(EXTRA_STRATEGY_ID, strategyId)
                    putString(EXTRA_WORKER_LABEL, workerLabel)
                    putString(EXTRA_RESULT_FILE_PATH, resultFilePath)
                    putString(EXTRA_ERROR_MSG, t.message ?: "Worker execution failed")
                }
            }
            runCatching { replyTo.send(msg) }
        }
    }

    private fun sendProgress(
        replyTo: Messenger,
        requestId: String,
        strategyId: Long,
        progressJson: String
    ) {
        val msg = Message.obtain(null, MSG_PROGRESS).apply {
            this.data = Bundle().apply {
                putString(EXTRA_REQUEST_ID, requestId)
                putLong(EXTRA_STRATEGY_ID, strategyId)
                putString(EXTRA_WORKER_LABEL, workerLabel)
                putString(EXTRA_PROGRESS_JSON, progressJson)
            }
        }
        runCatching { replyTo.send(msg) }
    }

    companion object {
        const val MSG_RUN_STRATEGY = 1
        const val MSG_PROGRESS = 2
        const val MSG_RESULT = 3

        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_STRATEGY_ID = "strategy_id"
        const val EXTRA_STRATEGY_NAME = "strategy_name"
        const val EXTRA_CLASS_NAME = "class_name"
        const val EXTRA_CODE_FILE_PATH = "code_file_path"
        const val EXTRA_CSV_PATH = "csv_path"
        const val EXTRA_RESULT_FILE_PATH = "result_file_path"
        const val EXTRA_MAX_STOP_POINTS = "max_stop_points"
        const val EXTRA_POINT_SIZE = "point_size"
        const val EXTRA_WORKER_LABEL = "worker_label"
        const val EXTRA_PROGRESS_JSON = "progress_json"
        const val EXTRA_ERROR_MSG = "error_msg"
        const val EXTRA_LOAD_START = "load_start"
        const val EXTRA_LOAD_END = "load_end"
        const val EXTRA_ENTRY_START = "entry_start"
        const val EXTRA_ENTRY_END = "entry_end"
    }
}

class BacktestWorkerService1 : BacktestWorkerService("W1")
class BacktestWorkerService2 : BacktestWorkerService("W2")
class BacktestWorkerService3 : BacktestWorkerService("W3")
class BacktestWorkerService4 : BacktestWorkerService("W4")
class BacktestWorkerService5 : BacktestWorkerService("W5")
class BacktestWorkerService6 : BacktestWorkerService("W6")
class BacktestWorkerService7 : BacktestWorkerService("W7")
class BacktestWorkerService8 : BacktestWorkerService("W8")
class BacktestWorkerService9 : BacktestWorkerService("W9")
class BacktestWorkerService10 : BacktestWorkerService("W10")
class BacktestWorkerService11 : BacktestWorkerService("W11")
class BacktestWorkerService12 : BacktestWorkerService("W12")
class BacktestWorkerService13 : BacktestWorkerService("W13")
class BacktestWorkerService14 : BacktestWorkerService("W14")
class BacktestWorkerService15 : BacktestWorkerService("W15")
class BacktestWorkerService16 : BacktestWorkerService("W16")
class BacktestWorkerService17 : BacktestWorkerService("W17")
class BacktestWorkerService18 : BacktestWorkerService("W18")
class BacktestWorkerService19 : BacktestWorkerService("W19")
class BacktestWorkerService20 : BacktestWorkerService("W20")
class BacktestWorkerService21 : BacktestWorkerService("W21")
class BacktestWorkerService22 : BacktestWorkerService("W22")
class BacktestWorkerService23 : BacktestWorkerService("W23")
class BacktestWorkerService24 : BacktestWorkerService("W24")
class BacktestWorkerService25 : BacktestWorkerService("W25")
class BacktestWorkerService26 : BacktestWorkerService("W26")
class BacktestWorkerService27 : BacktestWorkerService("W27")
class BacktestWorkerService28 : BacktestWorkerService("W28")
class BacktestWorkerService29 : BacktestWorkerService("W29")
class BacktestWorkerService30 : BacktestWorkerService("W30")
class BacktestWorkerService31 : BacktestWorkerService("W31")
class BacktestWorkerService32 : BacktestWorkerService("W32")
