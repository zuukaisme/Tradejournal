package com.example.backtest

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

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
        pointSize: Double = 1.0
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
                pointSize
            )
            .toString()
    }
}
