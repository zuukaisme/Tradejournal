package com.example.backtest

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

fun interface BacktestProgressListener {
    fun onProgress(progressJson: String)
}

object PythonBacktestRunner {
    private val lock = Any()

    fun ensureStarted(context: Context) {
        if (!Python.isStarted()) {
            synchronized(lock) {
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(context.applicationContext))
                }
            }
        }
    }

    fun runWithStrategyCode(
        context: Context,
        csvText: String,
        strategyCode: String,
        className: String = "",
        maxStopPoints: Double = 0.0,
        pointSize: Double = 1.0,
        progressListener: BacktestProgressListener? = null
    ): String {
        ensureStarted(context)
        return Python.getInstance()
            .getModule("backtest")
            .callAttr(
                "run_with_strategy_code",
                csvText,
                strategyCode,
                className,
                maxStopPoints,
                pointSize,
                progressListener
            )
            .toString()
    }

    fun runWithStrategyFile(
        context: Context,
        csvPath: String,
        strategyCode: String,
        className: String = "",
        maxStopPoints: Double = 0.0,
        pointSize: Double = 1.0,
        progressListener: BacktestProgressListener? = null
    ): String {
        ensureStarted(context)
        return Python.getInstance()
            .getModule("backtest")
            .callAttr(
                "run_with_strategy_file",
                csvPath,
                strategyCode,
                className,
                maxStopPoints,
                pointSize,
                progressListener
            )
            .toString()
    }

    fun runWithMultipleStrategiesFile(
        context: Context,
        csvPath: String,
        strategiesJson: String,
        maxStopPoints: Double = 0.0,
        pointSize: Double = 1.0,
        progressListener: BacktestProgressListener? = null
    ): String {
        ensureStarted(context)
        return Python.getInstance()
            .getModule("backtest")
            .callAttr(
                "run_with_multiple_strategies_file",
                csvPath,
                strategiesJson,
                maxStopPoints,
                pointSize,
                progressListener
            )
            .toString()
    }

    fun runWithStrategyFileSlice(
        context: Context,
        csvPath: String,
        strategyCode: String,
        className: String = "",
        maxStopPoints: Double = 0.0,
        pointSize: Double = 1.0,
        progressListener: BacktestProgressListener? = null,
        loadStart: Int = 0,
        loadEnd: Int = -1,
        entryStart: Int = 0,
        entryEnd: Int = -1
    ): String {
        ensureStarted(context)
        return Python.getInstance()
            .getModule("backtest")
            .callAttr(
                "run_with_strategy_file_slice",
                csvPath,
                strategyCode,
                className,
                maxStopPoints,
                pointSize,
                progressListener,
                loadStart,
                loadEnd,
                entryStart,
                entryEnd
            )
            .toString()
    }
}
