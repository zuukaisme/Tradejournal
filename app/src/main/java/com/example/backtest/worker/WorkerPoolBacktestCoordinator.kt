package com.example.backtest.worker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import com.example.backtest.BacktestCodec
import com.example.backtest.BacktestError
import com.example.backtest.BacktestParseOutcome
import com.example.backtest.BacktestProgressListener
import com.example.backtest.BacktestProgressSnapshot
import com.example.backtest.BatchBacktestParseOutcome
import com.example.backtest.BatchStrategyRunResult
import com.example.backtest.BatchStrategySpec
import com.example.backtest.BinaryCandleCache
import com.example.backtest.PythonBacktestRunner
import com.example.backtest.StrategyLiveProgress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pure Kotlin progress aggregator shared by parallel worker execution so the UI
 * receives smooth, monotonic combined progress and live per-strategy rows.
 */
class BatchProgressAggregator(
    specs: List<BatchStrategySpec>,
    private val defaultTotalCandles: Int,
    private val sumCandleProgress: Boolean = false,
    perSlotTotalCandles: List<Int>? = null,
    private val workerPoolSize: Int? = null
) {
    private val orderedIds = specs.map { it.id }
    private val states = LinkedHashMap<Long, StrategyLiveProgress>(specs.size).apply {
        specs.forEachIndexed { idx, s ->
            val slotTotal = perSlotTotalCandles?.getOrNull(idx) ?: defaultTotalCandles
            put(
                s.id,
                StrategyLiveProgress(
                    strategyId = s.id,
                    name = s.name,
                    totalCandles = slotTotal,
                    status = "queued"
                )
            )
        }
    }

    @Synchronized
    fun markAssigned(strategyId: Long, workerLabel: String) {
        val prev = states[strategyId] ?: return
        states[strategyId] = prev.copy(workerLabel = workerLabel, status = "running")
    }

    @Synchronized
    fun onWorkerProgress(strategyId: Long, workerLabel: String, snap: BacktestProgressSnapshot) {
        val prev = states[strategyId] ?: return
        val effectiveTotal = if (snap.totalCandles > 0) snap.totalCandles else prev.totalCandles
        states[strategyId] = prev.copy(
            workerLabel = workerLabel.ifBlank { prev.workerLabel },
            candlesProcessed = snap.candlesProcessed.coerceAtLeast(prev.candlesProcessed),
            totalCandles = effectiveTotal,
            closedTrades = snap.closedTrades,
            openTrades = snap.openTrades,
            wins = snap.wins,
            losses = snap.losses,
            breakEvens = snap.breakEvens,
            netPoints = snap.netPoints,
            netR = snap.netR,
            status = "running",
            error = ""
        )
    }

    @Synchronized
    fun markFinished(strategyId: Long, outcome: BacktestParseOutcome) {
        val prev = states[strategyId] ?: return
        states[strategyId] = when (outcome) {
            is BacktestParseOutcome.Success -> {
                val sum = outcome.result.summary
                val tc = if (sum.totalCandles > 0) sum.totalCandles else prev.totalCandles
                prev.copy(
                    candlesProcessed = tc.coerceAtLeast(prev.candlesProcessed),
                    totalCandles = tc,
                    closedTrades = sum.totalTrades,
                    openTrades = sum.openTrades,
                    wins = sum.wins,
                    losses = sum.losses,
                    breakEvens = sum.breakEvens,
                    netPoints = sum.netPoints,
                    netR = sum.netR,
                    status = "done",
                    error = ""
                )
            }
            is BacktestParseOutcome.Failure -> {
                prev.copy(
                    status = "error",
                    error = outcome.error.error
                )
            }
        }
    }

    @Synchronized
    fun snapshot(currentTimeHint: String = ""): BacktestProgressSnapshot {
        val list = orderedIds.mapNotNull { states[it] }
        val totalStrats = list.size.coerceAtLeast(1)
        val combinedTotalCandles = if (sumCandleProgress) {
            defaultTotalCandles.coerceAtLeast(list.sumOf { it.totalCandles.coerceAtLeast(0) }).coerceAtLeast(1)
        } else {
            (list.maxOfOrNull { it.totalCandles } ?: defaultTotalCandles).coerceAtLeast(1)
        }
        val combinedProcessed = if (sumCandleProgress) {
            list.sumOf { s ->
                val cap = if (s.totalCandles > 0) s.totalCandles else combinedTotalCandles
                when (s.status) {
                    "done", "error" -> cap
                    else -> s.candlesProcessed.coerceIn(0, cap)
                }
            }.coerceIn(0, combinedTotalCandles)
        } else {
            list.sumOf { s ->
                when (s.status) {
                    "done", "error" -> if (s.totalCandles > 0) s.totalCandles else combinedTotalCandles
                    else -> s.candlesProcessed.coerceIn(0, combinedTotalCandles)
                }
            } / totalStrats
        }

        val aggClosed = list.sumOf { it.closedTrades }
        val aggOpen = list.sumOf { it.openTrades }
        val aggWins = list.sumOf { it.wins }
        val aggLosses = list.sumOf { it.losses }
        val aggBe = list.sumOf { it.breakEvens }
        val aggPts = list.sumOf { it.netPoints }
        val aggR = list.sumOf { it.netR }
        val activeCount = list.count { it.status == "running" }
        val displayedWorkerCount = workerPoolSize ?: list.size
        val displayedActiveWorkers = activeCount.coerceAtMost(displayedWorkerCount)

        return BacktestProgressSnapshot(
            candlesProcessed = combinedProcessed,
            totalCandles = combinedTotalCandles,
            enteredTrades = aggClosed + aggOpen,
            closedTrades = aggClosed,
            openTrades = aggOpen,
            wins = aggWins,
            losses = aggLosses,
            breakEvens = aggBe,
            netPoints = aggPts,
            netR = aggR,
            currentTime = currentTimeHint,
            strategyIndex = displayedActiveWorkers,
            totalStrategies = displayedWorkerCount,
            currentStrategyName = "$displayedActiveWorkers/$displayedWorkerCount workers active",
            strategyStatuses = list
        )
    }
}

/**
 * Coordinates parallel execution across isolated multi-process Android Services
 * (`BacktestWorkerService1..4`) with file-backed IPC and automatic in-process fallback.
 */
object WorkerPoolBacktestCoordinator {

    const val LARGE_DATASET_TIME_SPLIT_BYTES = 50L * 1024L * 1024L
    const val MIN_CANDLES_FOR_TIME_SPLIT = 200_000

    const val MAX_WORKER_PROCESSES = 32

    private val WORKER_CLASSES: List<Pair<String, Class<out BacktestWorkerService>>> = listOf(
        "W1" to BacktestWorkerService1::class.java,
        "W2" to BacktestWorkerService2::class.java,
        "W3" to BacktestWorkerService3::class.java,
        "W4" to BacktestWorkerService4::class.java,
        "W5" to BacktestWorkerService5::class.java,
        "W6" to BacktestWorkerService6::class.java,
        "W7" to BacktestWorkerService7::class.java,
        "W8" to BacktestWorkerService8::class.java,
        "W9" to BacktestWorkerService9::class.java,
        "W10" to BacktestWorkerService10::class.java,
        "W11" to BacktestWorkerService11::class.java,
        "W12" to BacktestWorkerService12::class.java,
        "W13" to BacktestWorkerService13::class.java,
        "W14" to BacktestWorkerService14::class.java,
        "W15" to BacktestWorkerService15::class.java,
        "W16" to BacktestWorkerService16::class.java,
        "W17" to BacktestWorkerService17::class.java,
        "W18" to BacktestWorkerService18::class.java,
        "W19" to BacktestWorkerService19::class.java,
        "W20" to BacktestWorkerService20::class.java,
        "W21" to BacktestWorkerService21::class.java,
        "W22" to BacktestWorkerService22::class.java,
        "W23" to BacktestWorkerService23::class.java,
        "W24" to BacktestWorkerService24::class.java,
        "W25" to BacktestWorkerService25::class.java,
        "W26" to BacktestWorkerService26::class.java,
        "W27" to BacktestWorkerService27::class.java,
        "W28" to BacktestWorkerService28::class.java,
        "W29" to BacktestWorkerService29::class.java,
        "W30" to BacktestWorkerService30::class.java,
        "W31" to BacktestWorkerService31::class.java,
        "W32" to BacktestWorkerService32::class.java
    )

    private class BoundWorker(
        val label: String,
        val connection: ServiceConnection,
        val messenger: Messenger,
        val disconnected: CompletableDeferred<Unit>
    )

    private data class InFlightRequest(
        val strategyId: Long,
        val workerLabel: String,
        val deferred: CompletableDeferred<String>,
        val onProgressJson: (String) -> Unit
    )

    fun optimalWorkerCount(requestedWorkers: Int, strategyCount: Int = 1): Int {
        return (requestedWorkers.coerceAtLeast(1) * strategyCount.coerceAtLeast(1))
            .coerceAtMost(MAX_WORKER_PROCESSES)
    }

    suspend fun runSingle(
        context: Context,
        csvFile: File,
        spec: BatchStrategySpec,
        estimatedRows: Int,
        maxStopPoints: Double,
        pointSize: Double,
        enableTimeSplit: Boolean = true,
        workerCount: Int = 4,
        onProgress: (BacktestProgressSnapshot) -> Unit
    ): BacktestParseOutcome {
        val totalFromBin = BinaryCandleCache.readCandleCount(csvFile)
        if (enableTimeSplit &&
            csvFile.length() >= LARGE_DATASET_TIME_SPLIT_BYTES &&
            totalFromBin != null &&
            totalFromBin >= MIN_CANDLES_FOR_TIME_SPLIT
        ) {
            val sharded = runTimeShardedSingle(
                context = context,
                csvFile = csvFile,
                spec = spec,
                totalCandles = totalFromBin,
                maxStopPoints = maxStopPoints,
                pointSize = pointSize,
                workerCount = workerCount,
                onProgress = onProgress
            )
            if (sharded != null) return sharded
        }

        val appContext = context.applicationContext
        val ipcDir = File(appContext.cacheDir, "worker_ipc/${UUID.randomUUID()}").apply { mkdirs() }
        val replyThread = HandlerThread("BacktestWorkerReply").apply { start() }
        val inFlight = ConcurrentHashMap<String, InFlightRequest>()

        val replyMessenger = Messenger(
            object : Handler(replyThread.looper) {
                override fun handleMessage(msg: Message) {
                    val data = msg.data ?: return
                    val reqId = data.getString(BacktestWorkerService.EXTRA_REQUEST_ID) ?: return
                    val req = inFlight[reqId] ?: return
                    when (msg.what) {
                        BacktestWorkerService.MSG_PROGRESS -> {
                            val json = data.getString(BacktestWorkerService.EXTRA_PROGRESS_JSON).orEmpty()
                            if (json.isNotEmpty()) req.onProgressJson(json)
                        }
                        BacktestWorkerService.MSG_RESULT -> {
                            val resultPath = data.getString(BacktestWorkerService.EXTRA_RESULT_FILE_PATH).orEmpty()
                            val errMsg = data.getString(BacktestWorkerService.EXTRA_ERROR_MSG)
                            val raw = runCatching { File(resultPath).readText(Charsets.UTF_8) }.getOrNull()
                            inFlight.remove(reqId)
                            req.deferred.complete(
                                raw ?: """{"error":"${(errMsg ?: "Worker returned no result").replace("\"", "'")}","details":""}"""
                            )
                        }
                    }
                }
            }
        )

        var worker: BoundWorker? = null
        try {
            worker = bindWorkers(appContext, 1).firstOrNull()
            if (worker == null) {
                val json = PythonBacktestRunner.runWithStrategyFile(
                    context = appContext,
                    csvPath = csvFile.absolutePath,
                    strategyCode = spec.code,
                    className = spec.className,
                    maxStopPoints = maxStopPoints,
                    pointSize = pointSize,
                    progressListener = BacktestProgressListener { json ->
                        BacktestCodec.parseProgressJson(json)?.let(onProgress)
                    }
                )
                return BacktestCodec.parseBacktestJson(json)
            }

            val codeFile = File(ipcDir, "strat_${spec.id}.py").apply { writeText(spec.code, Charsets.UTF_8) }
            val resultFile = File(ipcDir, "res_${spec.id}.json")
            val rawJson = executeOnWorker(
                worker = worker,
                replyMessenger = replyMessenger,
                inFlight = inFlight,
                csvFile = csvFile,
                spec = spec,
                codeFile = codeFile,
                resultFile = resultFile,
                maxStopPoints = maxStopPoints,
                pointSize = pointSize,
                onProgressJson = { pJson ->
                    BacktestCodec.parseProgressJson(pJson)?.let { snap ->
                        onProgress(
                            if (snap.totalCandles > 0) snap else snap.copy(totalCandles = estimatedRows)
                        )
                    }
                }
            )
            return if (rawJson != null) {
                BacktestCodec.parseBacktestJson(rawJson)
            } else {
                // Worker process died mid-run -> clean in-process retry
                val fallbackJson = PythonBacktestRunner.runWithStrategyFile(
                    context = appContext,
                    csvPath = csvFile.absolutePath,
                    strategyCode = spec.code,
                    className = spec.className,
                    maxStopPoints = maxStopPoints,
                    pointSize = pointSize,
                    progressListener = BacktestProgressListener { json ->
                        BacktestCodec.parseProgressJson(json)?.let(onProgress)
                    }
                )
                BacktestCodec.parseBacktestJson(fallbackJson)
            }
        } finally {
            worker?.let { w -> runCatching { appContext.unbindService(w.connection) } }
            replyThread.quitSafely()
            ipcDir.deleteRecursively()
        }
    }

    private suspend fun runTimeShardedSingle(
        context: Context,
        csvFile: File,
        spec: BatchStrategySpec,
        totalCandles: Int,
        maxStopPoints: Double,
        pointSize: Double,
        workerCount: Int,
        onProgress: (BacktestProgressSnapshot) -> Unit
    ): BacktestParseOutcome? = coroutineScope {
        val appContext = context.applicationContext
        val desiredWorkers = optimalWorkerCount(workerCount)
        val boundWorkers = bindWorkers(appContext, desiredWorkers)
        if (boundWorkers.size < 2) {
            boundWorkers.forEach { w -> runCatching { appContext.unbindService(w.connection) } }
            return@coroutineScope null
        }

        val shards = BacktestShardStitcher.planTimeShards(
            totalCandles = totalCandles,
            workerCount = boundWorkers.size
        )
        val ipcDir = File(appContext.cacheDir, "worker_ipc/${UUID.randomUUID()}").apply { mkdirs() }
        val replyThread = HandlerThread("BacktestShardReplies").apply { start() }
        val inFlight = ConcurrentHashMap<String, InFlightRequest>()
        val lastUiPushMs = AtomicInteger(0)

        val shardSpecs = shards.map { shard ->
            val startLabel = BinaryCandleCache.readTimestampAt(csvFile, shard.entryStart)
                ?.take(7)?.replace('.', '-') ?: "Bar ${shard.entryStart}"
            val endLabel = BinaryCandleCache.readTimestampAt(csvFile, (shard.entryEnd - 1).coerceAtLeast(0))
                ?.take(7)?.replace('.', '-') ?: "Bar ${shard.entryEnd}"
            BatchStrategySpec(
                id = 10_000L + shard.shardIndex,
                name = "${spec.name} [$startLabel → $endLabel]",
                className = spec.className,
                code = spec.code
            )
        }
        val shardTotals = shards.map { (it.entryEnd - it.entryStart).coerceAtLeast(1) }
        val aggregator = BatchProgressAggregator(
            specs = shardSpecs,
            defaultTotalCandles = totalCandles,
            sumCandleProgress = true,
            perSlotTotalCandles = shardTotals,
            workerPoolSize = boundWorkers.size
        )

        fun emitAggregated(force: Boolean = false, timeHint: String = "") {
            val nowMs = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
            val prev = lastUiPushMs.get()
            if (force || (nowMs - prev) >= 110) {
                lastUiPushMs.set(nowMs)
                val snapshot = aggregator.snapshot(timeHint)
                onProgress(
                    snapshot.copy(
                        strategyIndex = 1,
                        totalStrategies = 1,
                        currentStrategyName = spec.name,
                        strategyStatuses = snapshot.strategyStatuses
                            .filter { it.status == "error" }
                            .map { it.copy(name = spec.name) }
                    )
                )
            }
        }

        val replyMessenger = Messenger(
            object : Handler(replyThread.looper) {
                override fun handleMessage(msg: Message) {
                    val data = msg.data ?: return
                    val reqId = data.getString(BacktestWorkerService.EXTRA_REQUEST_ID) ?: return
                    val req = inFlight[reqId] ?: return
                    val wLabel = data.getString(BacktestWorkerService.EXTRA_WORKER_LABEL).orEmpty()
                    when (msg.what) {
                        BacktestWorkerService.MSG_PROGRESS -> {
                            val json = data.getString(BacktestWorkerService.EXTRA_PROGRESS_JSON).orEmpty()
                            if (json.isNotEmpty()) req.onProgressJson(json)
                        }
                        BacktestWorkerService.MSG_RESULT -> {
                            val resultPath = data.getString(BacktestWorkerService.EXTRA_RESULT_FILE_PATH).orEmpty()
                            val errMsg = data.getString(BacktestWorkerService.EXTRA_ERROR_MSG)
                            val raw = runCatching { File(resultPath).readText(Charsets.UTF_8) }.getOrNull()
                            inFlight.remove(reqId)
                            req.deferred.complete(
                                raw ?: """{"error":"${(errMsg ?: "Worker $wLabel returned no result").replace("\"", "'")}","details":""}"""
                            )
                        }
                    }
                }
            }
        )

        try {
            emitAggregated(force = true, timeHint = "Splitting ${shards.size} time periods...")
            val codeFile = File(ipcDir, "strat_shared.py").apply { writeText(spec.code, Charsets.UTF_8) }

            val outcomes = boundWorkers.zip(shards).mapIndexed { idx, (worker, shard) ->
                val shardSpec = shardSpecs[idx]
                async(Dispatchers.IO) {
                    aggregator.markAssigned(shardSpec.id, worker.label)
                    emitAggregated(force = true)

                    val resultFile = File(ipcDir, "shard_${shard.shardIndex}.json")
                    val rawJson = executeOnWorker(
                        worker = worker,
                        replyMessenger = replyMessenger,
                        inFlight = inFlight,
                        csvFile = csvFile,
                        spec = shardSpec,
                        codeFile = codeFile,
                        resultFile = resultFile,
                        maxStopPoints = maxStopPoints,
                        pointSize = pointSize,
                        shard = shard,
                        onProgressJson = { pJson ->
                            val parsed = BacktestCodec.parseProgressJson(pJson)
                            if (parsed != null) {
                                aggregator.onWorkerProgress(shardSpec.id, worker.label, parsed)
                                emitAggregated(force = false, timeHint = parsed.currentTime)
                            }
                        }
                    )

                    val workerOutcome = rawJson?.let(BacktestCodec::parseBacktestJson)
                    val shouldRetryInProcess = workerOutcome == null ||
                        (workerOutcome is BacktestParseOutcome.Failure &&
                            isWorkerStartupFailure(workerOutcome.error))
                    val outcome = if (!shouldRetryInProcess) {
                        workerOutcome!!
                    } else {
                        val fallbackJson = PythonBacktestRunner.runWithStrategyFileSlice(
                            context = appContext,
                            csvPath = csvFile.absolutePath,
                            strategyCode = spec.code,
                            className = spec.className,
                            maxStopPoints = maxStopPoints,
                            pointSize = pointSize,
                            loadStart = shard.loadStart,
                            loadEnd = shard.loadEnd,
                            entryStart = shard.entryStart,
                            entryEnd = shard.entryEnd
                        )
                        BacktestCodec.parseBacktestJson(fallbackJson)
                    }
                    aggregator.markFinished(shardSpec.id, outcome)
                    emitAggregated(force = true)
                    outcome
                }
            }.awaitAll()

            val firstFailure = outcomes.firstOrNull { it is BacktestParseOutcome.Failure } as? BacktestParseOutcome.Failure
            if (firstFailure != null) {
                return@coroutineScope firstFailure
            }
            val results = outcomes.map { (it as BacktestParseOutcome.Success).result }
            BacktestParseOutcome.Success(BacktestShardStitcher.stitch(results, totalCandles))
        } finally {
            boundWorkers.forEach { w -> runCatching { appContext.unbindService(w.connection) } }
            replyThread.quitSafely()
            ipcDir.deleteRecursively()
        }
    }

    private data class StrategyShardTask(
        val taskId: Long,
        val strategy: BatchStrategySpec,
        val shard: TimeShardPlan
    )

    private suspend fun runBatchTimeShardedParallel(
        context: Context,
        csvFile: File,
        specs: List<BatchStrategySpec>,
        totalCandles: Int,
        maxStopPoints: Double,
        pointSize: Double,
        workersPerStrategy: Int,
        onProgress: (BacktestProgressSnapshot) -> Unit
    ): BatchBacktestParseOutcome? = coroutineScope {
        val appContext = context.applicationContext
        val desiredWorkers = optimalWorkerCount(workersPerStrategy, specs.size)
        val boundWorkers = bindWorkers(appContext, desiredWorkers)
        if (boundWorkers.size < 2) {
            boundWorkers.forEach { worker -> runCatching { appContext.unbindService(worker.connection) } }
            return@coroutineScope null
        }

        val shards = BacktestShardStitcher.planTimeShards(
            totalCandles = totalCandles,
            workerCount = boundWorkers.size
        )
        val tasks = specs.flatMapIndexed { strategyIndex, strategy ->
            shards.map { shard ->
                StrategyShardTask(
                    taskId = 9_000_000_000_000L + (strategyIndex * shards.size) + shard.shardIndex,
                    strategy = strategy,
                    shard = shard
                )
            }
        }
        val progressSpecs = tasks.map { task ->
            BatchStrategySpec(
                id = task.taskId,
                name = "${task.strategy.name} [${task.shard.shardIndex + 1}/${shards.size}]",
                className = task.strategy.className,
                code = task.strategy.code
            )
        }
        val shardTotals = tasks.map { task ->
            (task.shard.entryEnd - task.shard.entryStart).coerceAtLeast(1)
        }
        val aggregator = BatchProgressAggregator(
            specs = progressSpecs,
            defaultTotalCandles = totalCandles * specs.size,
            sumCandleProgress = true,
            perSlotTotalCandles = shardTotals,
            workerPoolSize = boundWorkers.size
        )
        val ipcDir = File(appContext.cacheDir, "worker_ipc/${UUID.randomUUID()}").apply { mkdirs() }
        val replyThread = HandlerThread("BacktestMultiShardReplies").apply { start() }
        val inFlight = ConcurrentHashMap<String, InFlightRequest>()
        val lastUiPushMs = AtomicInteger(0)
        val queueLock = Any()
        val queue = ArrayDeque(tasks)
        val resultLock = Any()
        val shardResults = HashMap<Long, MutableMap<Int, com.example.backtest.BacktestResult>>()
        val strategyErrors = ConcurrentHashMap<Long, BacktestError>()
        val codeFiles = specs.associate { strategy ->
            strategy.id to File(ipcDir, "strat_${strategy.id}.py").apply {
                writeText(strategy.code, Charsets.UTF_8)
            }
        }

        fun emitAggregated(force: Boolean = false, timeHint: String = "") {
            val nowMs = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
            val previous = lastUiPushMs.get()
            if (force || (nowMs - previous) >= 110) {
                lastUiPushMs.set(nowMs)
                onProgress(aggregator.snapshot(timeHint))
            }
        }

        val replyMessenger = Messenger(
            object : Handler(replyThread.looper) {
                override fun handleMessage(msg: Message) {
                    val data = msg.data ?: return
                    val requestId = data.getString(BacktestWorkerService.EXTRA_REQUEST_ID) ?: return
                    val request = inFlight[requestId] ?: return
                    val workerLabel = data.getString(BacktestWorkerService.EXTRA_WORKER_LABEL).orEmpty()
                    when (msg.what) {
                        BacktestWorkerService.MSG_PROGRESS -> {
                            val json = data.getString(BacktestWorkerService.EXTRA_PROGRESS_JSON).orEmpty()
                            if (json.isNotEmpty()) request.onProgressJson(json)
                        }
                        BacktestWorkerService.MSG_RESULT -> {
                            val resultPath = data.getString(BacktestWorkerService.EXTRA_RESULT_FILE_PATH).orEmpty()
                            val errorMessage = data.getString(BacktestWorkerService.EXTRA_ERROR_MSG)
                            val raw = runCatching { File(resultPath).readText(Charsets.UTF_8) }.getOrNull()
                            inFlight.remove(requestId)
                            request.deferred.complete(
                                raw ?: """{"error":"${(errorMessage ?: "Worker $workerLabel returned no result").replace("\"", "'")}","details":""}"""
                            )
                        }
                    }
                }
            }
        )

        suspend fun executeTask(task: StrategyShardTask, worker: BoundWorker?): BacktestParseOutcome {
            val progressSpec = progressSpecs.first { it.id == task.taskId }
            aggregator.markAssigned(task.taskId, worker?.label ?: "Main")
            emitAggregated(force = true)
            val resultFile = File(ipcDir, "result_${task.taskId}.json")
            val rawJson = if (worker != null) {
                executeOnWorker(
                    worker = worker,
                    replyMessenger = replyMessenger,
                    inFlight = inFlight,
                    csvFile = csvFile,
                    spec = progressSpec,
                    codeFile = codeFiles.getValue(task.strategy.id),
                    resultFile = resultFile,
                    maxStopPoints = maxStopPoints,
                    pointSize = pointSize,
                    shard = task.shard,
                    onProgressJson = { json ->
                        BacktestCodec.parseProgressJson(json)?.let { snapshot ->
                            aggregator.onWorkerProgress(task.taskId, worker.label, snapshot)
                            emitAggregated(force = false, timeHint = snapshot.currentTime)
                        }
                    }
                )
            } else {
                null
            }
            val outcome = if (rawJson != null) {
                BacktestCodec.parseBacktestJson(rawJson)
            } else {
                val fallbackJson = PythonBacktestRunner.runWithStrategyFileSlice(
                    context = appContext,
                    csvPath = csvFile.absolutePath,
                    strategyCode = task.strategy.code,
                    className = task.strategy.className,
                    maxStopPoints = maxStopPoints,
                    pointSize = pointSize,
                    loadStart = task.shard.loadStart,
                    loadEnd = task.shard.loadEnd,
                    entryStart = task.shard.entryStart,
                    entryEnd = task.shard.entryEnd
                )
                BacktestCodec.parseBacktestJson(fallbackJson)
            }
            aggregator.markFinished(task.taskId, outcome)
            when (outcome) {
                is BacktestParseOutcome.Success -> synchronized(resultLock) {
                    shardResults.getOrPut(task.strategy.id) { HashMap() }[task.shard.shardIndex] = outcome.result
                }
                is BacktestParseOutcome.Failure -> strategyErrors.putIfAbsent(task.strategy.id, outcome.error)
            }
            emitAggregated(force = true)
            return outcome
        }

        try {
            emitAggregated(force = true, timeHint = "Splitting ${specs.size} strategies across ${shards.size} time ranges...")
            val workerJobs = boundWorkers.map { worker ->
                async(Dispatchers.IO) {
                    while (!worker.disconnected.isCompleted) {
                        val task = synchronized(queueLock) {
                            if (queue.isEmpty()) null else queue.removeFirst()
                        } ?: break
                        executeTask(task, worker)
                    }
                }
            }
            workerJobs.awaitAll()

            while (true) {
                val remaining = synchronized(queueLock) {
                    if (queue.isEmpty()) null else queue.removeFirst()
                } ?: break
                executeTask(remaining, null)
            }

            val runs = specs.map { strategy ->
                val error = strategyErrors[strategy.id]
                if (error != null) {
                    BatchStrategyRunResult(
                        strategyId = strategy.id,
                        strategyName = strategy.name,
                        className = strategy.className,
                        error = error
                    )
                } else {
                    val orderedResults = synchronized(resultLock) {
                        shardResults[strategy.id]?.toSortedMap()?.values?.toList().orEmpty()
                    }
                    if (orderedResults.size != shards.size) {
                        BatchStrategyRunResult(
                            strategyId = strategy.id,
                            strategyName = strategy.name,
                            className = strategy.className,
                            error = BacktestError("One or more time shards did not return a result for ${strategy.name}.")
                        )
                    } else {
                        BatchStrategyRunResult(
                            strategyId = strategy.id,
                            strategyName = strategy.name,
                            className = strategy.className,
                            result = BacktestShardStitcher.stitch(orderedResults, totalCandles)
                        )
                    }
                }
            }
            emitAggregated(force = true, timeHint = "Combining strategy results...")
            BatchBacktestParseOutcome.Success(totalCandles = totalCandles, runs = runs)
        } finally {
            boundWorkers.forEach { worker -> runCatching { appContext.unbindService(worker.connection) } }
            replyThread.quitSafely()
            ipcDir.deleteRecursively()
        }
    }

    suspend fun runBatchParallel(
        context: Context,
        csvFile: File,
        specs: List<BatchStrategySpec>,
        estimatedRows: Int,
        maxStopPoints: Double,
        pointSize: Double,
        workersPerStrategy: Int = 4,
        onProgress: (BacktestProgressSnapshot) -> Unit
    ): BatchBacktestParseOutcome = coroutineScope {
        if (specs.isEmpty()) {
            return@coroutineScope BatchBacktestParseOutcome.Failure(
                BacktestError("No strategies supplied for parallel batch backtest.")
            )
        }

        val binaryCandleCount = BinaryCandleCache.readCandleCount(csvFile)
        if (
            csvFile.length() >= LARGE_DATASET_TIME_SPLIT_BYTES &&
            binaryCandleCount != null &&
            binaryCandleCount >= MIN_CANDLES_FOR_TIME_SPLIT
        ) {
            val sharded = runBatchTimeShardedParallel(
                context = context,
                csvFile = csvFile,
                specs = specs,
                totalCandles = binaryCandleCount,
                maxStopPoints = maxStopPoints,
                pointSize = pointSize,
                workersPerStrategy = workersPerStrategy,
                onProgress = onProgress
            )
            if (sharded != null) return@coroutineScope sharded
        }

        val appContext = context.applicationContext
        val ipcDir = File(appContext.cacheDir, "worker_ipc/${UUID.randomUUID()}").apply { mkdirs() }
        val replyThread = HandlerThread("BacktestBatchReplies").apply { start() }
        val inFlight = ConcurrentHashMap<String, InFlightRequest>()
        val aggregator = BatchProgressAggregator(
            specs = specs,
            defaultTotalCandles = estimatedRows,
            workerPoolSize = specs.size.coerceAtMost(MAX_WORKER_PROCESSES)
        )
        val lastUiPushMs = AtomicInteger(0)

        fun emitAggregated(force: Boolean = false, timeHint: String = "") {
            val nowMs = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
            val prev = lastUiPushMs.get()
            if (force || (nowMs - prev) >= 110) {
                lastUiPushMs.set(nowMs)
                onProgress(aggregator.snapshot(timeHint))
            }
        }

        val replyMessenger = Messenger(
            object : Handler(replyThread.looper) {
                override fun handleMessage(msg: Message) {
                    val data = msg.data ?: return
                    val reqId = data.getString(BacktestWorkerService.EXTRA_REQUEST_ID) ?: return
                    val req = inFlight[reqId] ?: return
                    val wLabel = data.getString(BacktestWorkerService.EXTRA_WORKER_LABEL).orEmpty()
                    when (msg.what) {
                        BacktestWorkerService.MSG_PROGRESS -> {
                            val json = data.getString(BacktestWorkerService.EXTRA_PROGRESS_JSON).orEmpty()
                            if (json.isNotEmpty()) req.onProgressJson(json)
                        }
                        BacktestWorkerService.MSG_RESULT -> {
                            val resultPath = data.getString(BacktestWorkerService.EXTRA_RESULT_FILE_PATH).orEmpty()
                            val errMsg = data.getString(BacktestWorkerService.EXTRA_ERROR_MSG)
                            val raw = runCatching { File(resultPath).readText(Charsets.UTF_8) }.getOrNull()
                            inFlight.remove(reqId)
                            req.deferred.complete(
                                raw ?: """{"error":"${(errMsg ?: "Worker $wLabel returned no result").replace("\"", "'")}","details":""}"""
                            )
                        }
                    }
                }
            }
        )

        val desiredCount = specs.size.coerceAtMost(MAX_WORKER_PROCESSES)
        var boundWorkers: List<BoundWorker> = emptyList()
        try {
            boundWorkers = bindWorkers(appContext, desiredCount)
            if (boundWorkers.isEmpty()) {
                // Fallback to single-pass in-process engine if worker services could not bind
                val specsJson = BacktestCodec.encodeBatchStrategySpecs(specs)
                val rawBatch = PythonBacktestRunner.runWithMultipleStrategiesFile(
                    context = appContext,
                    csvPath = csvFile.absolutePath,
                    strategiesJson = specsJson,
                    maxStopPoints = maxStopPoints,
                    pointSize = pointSize,
                    progressListener = BacktestProgressListener { json ->
                        BacktestCodec.parseProgressJson(json)?.let(onProgress)
                    }
                )
                return@coroutineScope BacktestCodec.parseBatchBacktestJson(rawBatch)
            }

            // Emit initial queued state
            emitAggregated(force = true)

            val queueLock = Any()
            val queue = ArrayDeque(specs)
            val outcomesById = ConcurrentHashMap<Long, BatchStrategyRunResult>()

            // Launch one coroutine per bound OS worker process pulling tasks dynamically
            val workerJobs = boundWorkers.map { worker ->
                async(Dispatchers.IO) {
                    while (true) {
                        val nextSpec = synchronized(queueLock) {
                            if (queue.isEmpty()) null else queue.removeFirst()
                        } ?: break

                        aggregator.markAssigned(nextSpec.id, worker.label)
                        emitAggregated(force = true)

                        val codeFile = File(ipcDir, "strat_${nextSpec.id}.py").apply {
                            writeText(nextSpec.code, Charsets.UTF_8)
                        }
                        val resultFile = File(ipcDir, "res_${nextSpec.id}.json")

                        val rawJson = executeOnWorker(
                            worker = worker,
                            replyMessenger = replyMessenger,
                            inFlight = inFlight,
                            csvFile = csvFile,
                            spec = nextSpec,
                            codeFile = codeFile,
                            resultFile = resultFile,
                            maxStopPoints = maxStopPoints,
                            pointSize = pointSize,
                            onProgressJson = { pJson ->
                                val parsed = BacktestCodec.parseProgressJson(pJson)
                                if (parsed != null) {
                                    aggregator.onWorkerProgress(nextSpec.id, worker.label, parsed)
                                    emitAggregated(force = false, timeHint = parsed.currentTime)
                                }
                            }
                        )

                        val outcome = if (rawJson != null) {
                            BacktestCodec.parseBacktestJson(rawJson)
                        } else {
                            // Worker process disconnected unexpectedly -> re-queue or fallback
                            BacktestParseOutcome.Failure(
                                BacktestError("Worker ${worker.label} disconnected while running ${nextSpec.name}.")
                            )
                        }

                        aggregator.markFinished(nextSpec.id, outcome)
                        emitAggregated(force = true)

                        val runResult = when (outcome) {
                            is BacktestParseOutcome.Success -> BatchStrategyRunResult(
                                strategyId = nextSpec.id,
                                strategyName = nextSpec.name,
                                className = nextSpec.className,
                                result = outcome.result
                            )
                            is BacktestParseOutcome.Failure -> BatchStrategyRunResult(
                                strategyId = nextSpec.id,
                                strategyName = nextSpec.name,
                                className = nextSpec.className,
                                error = outcome.error
                            )
                        }
                        outcomesById[nextSpec.id] = runResult

                        if (worker.disconnected.isCompleted) break
                    }
                }
            }

            workerJobs.awaitAll()

            // Recover any specs left unstarted if a worker exited early
            while (true) {
                val leftover = synchronized(queueLock) {
                    if (queue.isEmpty()) null else queue.removeFirst()
                } ?: break
                aggregator.markAssigned(leftover.id, "Main")
                emitAggregated(force = true)
                val fallbackJson = PythonBacktestRunner.runWithStrategyFile(
                    context = appContext,
                    csvPath = csvFile.absolutePath,
                    strategyCode = leftover.code,
                    className = leftover.className,
                    maxStopPoints = maxStopPoints,
                    pointSize = pointSize
                )
                val outcome = BacktestCodec.parseBacktestJson(fallbackJson)
                aggregator.markFinished(leftover.id, outcome)
                emitAggregated(force = true)
                outcomesById[leftover.id] = when (outcome) {
                    is BacktestParseOutcome.Success -> BatchStrategyRunResult(
                        strategyId = leftover.id,
                        strategyName = leftover.name,
                        className = leftover.className,
                        result = outcome.result
                    )
                    is BacktestParseOutcome.Failure -> BatchStrategyRunResult(
                        strategyId = leftover.id,
                        strategyName = leftover.name,
                        className = leftover.className,
                        error = outcome.error
                    )
                }
            }

            val orderedRuns = specs.map { spec ->
                outcomesById[spec.id] ?: BatchStrategyRunResult(
                    strategyId = spec.id,
                    strategyName = spec.name,
                    className = spec.className,
                    error = BacktestError("No result returned for ${spec.name}.")
                )
            }
            val totalCandles = orderedRuns.firstNotNullOfOrNull {
                it.result?.summary?.totalCandles?.takeIf { c -> c > 0 }
            } ?: estimatedRows
            BatchBacktestParseOutcome.Success(totalCandles = totalCandles, runs = orderedRuns)
        } finally {
            boundWorkers.forEach { w -> runCatching { appContext.unbindService(w.connection) } }
            replyThread.quitSafely()
            ipcDir.deleteRecursively()
        }
    }

    private fun isWorkerStartupFailure(error: BacktestError): Boolean {
        val message = error.error.lowercase()
        val details = error.details.lowercase()
        return (message.contains("java.io.ioexception") && message.contains("failed to create")) ||
            (details.contains("chaquopy") && details.contains("python.start")) ||
            (details.contains("androidplatform") && details.contains("python.start"))
    }

    private suspend fun bindWorkers(context: Context, count: Int): List<BoundWorker> = coroutineScope {
        WORKER_CLASSES.take(count).map { (label, cls) ->
            async(Dispatchers.Main) {
                bindSingleWorker(context, label, cls)
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun bindSingleWorker(
        context: Context,
        label: String,
        serviceClass: Class<out BacktestWorkerService>
    ): BoundWorker? {
        val ready = CompletableDeferred<Messenger?>()
        val disconnected = CompletableDeferred<Unit>()
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                ready.complete(if (service != null) Messenger(service) else null)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                disconnected.complete(Unit)
                if (!ready.isCompleted) ready.complete(null)
            }

            override fun onBindingDied(name: ComponentName?) {
                disconnected.complete(Unit)
                if (!ready.isCompleted) ready.complete(null)
            }
        }

        val bound = runCatching {
            context.bindService(Intent(context, serviceClass), conn, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)

        if (!bound) {
            runCatching { context.unbindService(conn) }
            return null
        }

        val messenger = withTimeoutOrNull(4_000L) { ready.await() }
        if (messenger == null) {
            runCatching { context.unbindService(conn) }
            return null
        }
        return BoundWorker(label, conn, messenger, disconnected)
    }

    private suspend fun executeOnWorker(
        worker: BoundWorker,
        replyMessenger: Messenger,
        inFlight: ConcurrentHashMap<String, InFlightRequest>,
        csvFile: File,
        spec: BatchStrategySpec,
        codeFile: File,
        resultFile: File,
        maxStopPoints: Double,
        pointSize: Double,
        shard: TimeShardPlan? = null,
        onProgressJson: (String) -> Unit
    ): String? {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<String>()
        inFlight[requestId] = InFlightRequest(spec.id, worker.label, deferred, onProgressJson)

        val msg = Message.obtain(null, BacktestWorkerService.MSG_RUN_STRATEGY).apply {
            replyTo = replyMessenger
            data = Bundle().apply {
                putString(BacktestWorkerService.EXTRA_REQUEST_ID, requestId)
                putLong(BacktestWorkerService.EXTRA_STRATEGY_ID, spec.id)
                putString(BacktestWorkerService.EXTRA_STRATEGY_NAME, spec.name)
                putString(BacktestWorkerService.EXTRA_CLASS_NAME, spec.className)
                putString(BacktestWorkerService.EXTRA_CODE_FILE_PATH, codeFile.absolutePath)
                putString(BacktestWorkerService.EXTRA_CSV_PATH, csvFile.absolutePath)
                putString(BacktestWorkerService.EXTRA_RESULT_FILE_PATH, resultFile.absolutePath)
                putDouble(BacktestWorkerService.EXTRA_MAX_STOP_POINTS, maxStopPoints)
                putDouble(BacktestWorkerService.EXTRA_POINT_SIZE, pointSize)
                if (shard != null) {
                    putInt(BacktestWorkerService.EXTRA_LOAD_START, shard.loadStart)
                    putInt(BacktestWorkerService.EXTRA_LOAD_END, shard.loadEnd)
                    putInt(BacktestWorkerService.EXTRA_ENTRY_START, shard.entryStart)
                    putInt(BacktestWorkerService.EXTRA_ENTRY_END, shard.entryEnd)
                }
            }
        }

        val sent = runCatching {
            worker.messenger.send(msg)
            true
        }.getOrDefault(false)

        if (!sent) {
            inFlight.remove(requestId)
            return null
        }

        // Wait until either the result arrives or the worker service process dies
        return coroutineScope {
            val disconnectWatcher = async(Dispatchers.Default) {
                worker.disconnected.await()
                if (!deferred.isCompleted) {
                    inFlight.remove(requestId)
                    deferred.complete("")
                }
            }
            val res = deferred.await()
            disconnectWatcher.cancel()
            res.ifBlank { null }
        }
    }
}
