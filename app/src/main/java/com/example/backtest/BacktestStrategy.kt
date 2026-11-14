package com.example.backtest

interface Strategy {
    fun onCandle(i: Int, candles: List<Candle>): Signal?
}

interface NamedStrategy : Strategy {
    val id: String
    val name: String
    val description: String
}

/**
 * Placeholder strategy:
 * Three consecutive bullish candles (close > open) -> BUY,
 * Stop Loss = 1 average range (last 14 candles) below entry reference,
 * Take Profit = 2R above entry reference.
 */
class ThreeBullishCandlesStrategy : NamedStrategy {
    override val id: String = "three_bullish_candles"
    override val name: String = "3 Bullish Candles (2R)"
    override val description: String = "3 consecutive bullish candles -> BUY, SL = 1x ATR(14) range, TP = 2R"

    override fun onCandle(i: Int, candles: List<Candle>): Signal? {
        if (i < 13 || i >= candles.size) return null

        val c0 = candles[i - 2]
        val c1 = candles[i - 1]
        val c2 = candles[i]

        val isThreeBullish = c0.close > c0.open &&
            c1.close > c1.open &&
            c2.close > c2.open
        if (!isThreeBullish) return null

        var rangeSum = 0.0
        for (idx in (i - 13)..i) {
            rangeSum += (candles[idx].high - candles[idx].low)
        }
        val avgRange = rangeSum / 14.0
        if (avgRange <= 0.0) return null

        val entryRef = c2.close
        val sl = entryRef - avgRange
        val tp = entryRef + (2.0 * avgRange)
        return Signal(side = Side.BUY, sl = sl, tp = tp)
    }
}

object BacktestStrategies {
    val all: List<NamedStrategy> = listOf(
        ThreeBullishCandlesStrategy()
    )
}
