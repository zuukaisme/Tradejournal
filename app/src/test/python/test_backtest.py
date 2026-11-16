import json
import os
import tempfile
import unittest

from backtest import (
    Candle,
    CandleSeries,
    Signal,
    Strategy,
    compute_stats,
    run_backtest,
    run_with_multiple_strategies_file,
)


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

    def test_compute_stats_includes_open_and_close_indices(self):
        candles = make_candles(80)
        trades, skipped = run_backtest(candles, FrequentSignalStrategy())
        self.assertGreater(len(trades), 0)
        stats = compute_stats(trades, skipped, total_candles=len(candles))
        first_trade = stats["trades"][0]
        self.assertIn("openIndex", first_trade)
        self.assertIn("closeIndex", first_trade)
        self.assertEqual(first_trade["openIndex"], trades[0].open_i)
        self.assertEqual(first_trade["closeIndex"], trades[0].close_i)

    def test_run_with_multiple_strategies_file_isolates_errors_and_tracks_progress(self):
        fd, csv_path = tempfile.mkstemp(suffix=".csv")
        os.close(fd)
        progress_updates = []
        try:
            with open(csv_path, "w", encoding="utf-8") as fh:
                fh.write("<DATE>\t<TIME>\t<OPEN>\t<HIGH>\t<LOW>\t<CLOSE>\t<TICKVOL>\n")
                for idx in range(120):
                    hour = 9 + (idx // 60)
                    minute = idx % 60
                    fh.write(f"2025.02.07\t{hour:02d}:{minute:02d}:00\t2850.0\t2853.0\t2848.0\t2851.0\t100\n")

            buy_code = (
                "class BuyStrat(Strategy):\n"
                "    def on_candle(self, i, candles):\n"
                "        c = candles[i]\n"
                "        return Signal('buy', c.close - 1.5, c.close + 1.5)\n"
            )
            sell_code = (
                "class SellStrat(Strategy):\n"
                "    def on_candle(self, i, candles):\n"
                "        c = candles[i]\n"
                "        return Signal('sell', c.close + 1.5, c.close - 1.5)\n"
            )
            broken_code = (
                "class BrokenStrat(Strategy):\n"
                "    def on_candle(self, i, candles):\n"
                "        return 1 / 0\n"
            )
            specs = [
                {"id": 1, "name": "Buy Scalp", "className": "BuyStrat", "code": buy_code},
                {"id": 2, "name": "Sell Scalp", "className": "SellStrat", "code": sell_code},
                {"id": 3, "name": "Broken", "className": "BrokenStrat", "code": broken_code},
            ]
            raw = run_with_multiple_strategies_file(
                csv_path=csv_path,
                strategies_json=json.dumps(specs),
                progress_callback=lambda msg: progress_updates.append(json.loads(msg)),
            )
            payload = json.loads(raw)
            self.assertEqual(payload["totalCandles"], 120)
            self.assertEqual(len(payload["results"]), 3)

            self.assertIn("result", payload["results"][0])
            self.assertGreater(payload["results"][0]["result"]["summary"]["totalTrades"], 0)
            self.assertIn("result", payload["results"][1])
            self.assertGreater(payload["results"][1]["result"]["summary"]["totalTrades"], 0)
            self.assertIn("error", payload["results"][2])
            self.assertIn("ZeroDivisionError", payload["results"][2]["error"])
            self.assertTrue(any(p.get("totalStrategies") == 3 for p in progress_updates))
        finally:
            if os.path.exists(csv_path):
                os.remove(csv_path)


if __name__ == "__main__":
    unittest.main()
