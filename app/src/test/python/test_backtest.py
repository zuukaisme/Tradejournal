import unittest

from backtest import Candle, CandleSeries, Signal, Strategy, run_backtest


class FrequentSignalStrategy(Strategy):
    def on_candle(self, i, candles):
        candle = candles[i]
        return Signal("buy", candle.close - 1.0, candle.close + 1.0)


def make_candles(count):
    rows = []
    for index in range(count):
        timestamp = "2025.01.02 09:00:00"
        rows.append(Candle(timestamp, 100.0, 102.0, 99.0, 100.0, 1.0))
    return rows


def make_series(candles):
    series = CandleSeries()
    for candle in candles:
        series.append_row(
            candle.time,
            candle.open,
            candle.high,
            candle.low,
            candle.close,
            candle.volume,
        )
    return series


class BacktestRegressionTests(unittest.TestCase):
    def test_more_than_ten_trades_per_day_are_allowed(self):
        candles = make_candles(3000)
        trades, skipped = run_backtest(candles, FrequentSignalStrategy())

        self.assertGreater(len(trades), 10)
        self.assertEqual(skipped, 0)

    def test_compact_series_matches_regular_candle_list(self):
        candles = make_candles(3000)
        regular_trades, regular_skipped = run_backtest(candles, FrequentSignalStrategy())
        compact_trades, compact_skipped = run_backtest(make_series(candles), FrequentSignalStrategy())

        self.assertEqual(regular_skipped, compact_skipped)
        self.assertEqual(len(regular_trades), len(compact_trades))
        self.assertEqual(
            [
                (trade.side, trade.entry, trade.sl, trade.tp, trade.exit, trade.open_i, trade.close_i, trade.reason, trade.r)
                for trade in regular_trades
            ],
            [
                (trade.side, trade.entry, trade.sl, trade.tp, trade.exit, trade.open_i, trade.close_i, trade.reason, trade.r)
                for trade in compact_trades
            ],
        )

    def test_compact_series_preserves_indexing_and_slicing(self):
        candles = make_candles(20)
        series = make_series(candles)

        self.assertEqual(series[0], candles[0])
        self.assertEqual(series[-1], candles[-1])
        self.assertEqual(series[5:12], candles[5:12])


if __name__ == "__main__":
    unittest.main()
