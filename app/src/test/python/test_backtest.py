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
    run_with_strategy_file,
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

    def test_research_analytics_monte_carlo_breakdown_and_confidence(self):
        from backtest import build_research_analytics

        trades = [
            {"r": 1.5, "openTime": "2025.01.06 09:00:00"},
            {"r": -1.0, "openTime": "2025.01.06 14:00:00"},
            {"r": 0.5, "openTime": "2025.01.07 10:00:00"},
            {"r": -0.5, "openTime": "2025.02.03 15:00:00"},
        ]
        result = build_research_analytics(trades, simulations=500, seed=7)
        self.assertEqual(result["monteCarlo"]["simulations"], 500)
        self.assertEqual(result["monteCarlo"]["tradeCountPerSimulation"], 4)
        self.assertEqual(result["confidence"]["trades"], 4)
        self.assertEqual(result["confidence"]["label"], "Low")
        self.assertEqual(sum(x["trades"] for x in result["performanceBreakdown"]["byWeekday"]), 4)
        self.assertEqual(sum(x["trades"] for x in result["performanceBreakdown"]["bySession"]), 4)
        self.assertEqual(result["monteCarlo"], build_research_analytics(trades, simulations=500, seed=7)["monteCarlo"])

    def test_compute_stats_embeds_research_analytics(self):
        candles = make_candles(80)
        trades, skipped = run_backtest(candles, FrequentSignalStrategy())
        stats = compute_stats(trades, skipped, total_candles=len(candles))
        self.assertIn("research", stats)
        self.assertIn("monteCarlo", stats["research"])
        self.assertIn("confidence", stats["research"])
        self.assertIn("performanceBreakdown", stats["research"])

    def test_run_with_multiple_strategies_file_runs_simultaneously_with_parity_and_isolation(self):
        fd, csv_path = tempfile.mkstemp(suffix=".csv")
        os.close(fd)
        progress_updates = []
        try:
            with open(csv_path, "w", encoding="utf-8") as fh:
                fh.write("<DATE>\t<TIME>\t<OPEN>\t<HIGH>\t<LOW>\t<CLOSE>\t<TICKVOL>\n")
                for idx in range(150):
                    hour = 9 + (idx // 60)
                    minute = idx % 60
                    base = 2850.0 + (idx % 5) * 0.4
                    fh.write(
                        f"2025.02.07\t{hour:02d}:{minute:02d}:00\t"
                        f"{base:.2f}\t{base + 3.0:.2f}\t{base - 2.0:.2f}\t{base + 1.0:.2f}\t100\n"
                    )

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
            syntax_err_code = "class BadSyntax(Strategy)\n    pass\n"

            single_buy = json.loads(run_with_strategy_file(csv_path, buy_code, "BuyStrat"))
            single_sell = json.loads(run_with_strategy_file(csv_path, sell_code, "SellStrat"))

            specs = [
                {"id": 1, "name": "Buy Scalp", "className": "BuyStrat", "code": buy_code},
                {"id": 2, "name": "Sell Scalp", "className": "SellStrat", "code": sell_code},
                {"id": 3, "name": "Broken", "className": "BrokenStrat", "code": broken_code},
                {"id": 4, "name": "Bad Syntax", "className": "BadSyntax", "code": syntax_err_code},
            ]
            raw = run_with_multiple_strategies_file(
                csv_path=csv_path,
                strategies_json=json.dumps(specs),
                progress_callback=lambda msg: progress_updates.append(json.loads(msg)),
            )
            payload = json.loads(raw)
            self.assertEqual(payload["totalCandles"], 150)
            self.assertEqual(len(payload["results"]), 4)

            # Exact parity between single-pass simultaneous batch run and individual single-strategy runs
            self.assertEqual(payload["results"][0]["result"], single_buy)
            self.assertEqual(payload["results"][1]["result"], single_sell)
            self.assertIn("ZeroDivisionError", payload["results"][2]["error"])
            self.assertIn("SyntaxError", payload["results"][3]["error"])

            # Verify progress advances in a single monotonic pass with per-strategy statuses together
            processed_seq = [p["candlesProcessed"] for p in progress_updates]
            self.assertGreaterEqual(len(processed_seq), 2)
            self.assertEqual(processed_seq[0], 0)
            self.assertEqual(processed_seq[-1], 150)
            self.assertEqual(processed_seq, sorted(processed_seq))

            final_progress = progress_updates[-1]
            self.assertEqual(final_progress["totalStrategies"], 4)
            statuses = final_progress["strategyStatuses"]
            self.assertEqual(len(statuses), 4)
            self.assertEqual(statuses[0]["status"], "done")
            self.assertEqual(statuses[1]["status"], "done")
            self.assertEqual(statuses[2]["status"], "error")
            self.assertEqual(statuses[3]["status"], "error")
            self.assertEqual(statuses[0]["closedTrades"], single_buy["summary"]["totalTrades"])
            self.assertEqual(statuses[1]["closedTrades"], single_sell["summary"]["totalTrades"])
        finally:
            if os.path.exists(csv_path):
                os.remove(csv_path)
            if os.path.exists(csv_path + ".btbin"):
                os.remove(csv_path + ".btbin")

    def test_btbin_binary_cache_roundtrip_and_backtest_parity(self):
        from backtest import _populate_from_stream, _save_btbin, _try_load_btbin, load_candles

        fd, csv_path = tempfile.mkstemp(suffix=".csv")
        os.close(fd)
        bin_path = csv_path + ".btbin"
        try:
            with open(csv_path, "w", encoding="utf-8") as fh:
                fh.write("<DATE>\t<TIME>\t<OPEN>\t<HIGH>\t<LOW>\t<CLOSE>\t<TICKVOL>\n")
                for idx in range(240):
                    hour = 8 + (idx // 60)
                    minute = idx % 60
                    base = 2900.0 + (idx % 7) * 0.5
                    fh.write(
                        f"2025.03.03\t{hour:02d}:{minute:02d}:00\t"
                        f"{base:.2f}\t{base + 2.5:.2f}\t{base - 1.5:.2f}\t{base + 1.0:.2f}\t{100 + idx}\n"
                    )

            code = (
                "class BinCacheStrat(Strategy):\n"
                "    def on_candle(self, i, candles):\n"
                "        c = candles[i]\n"
                "        return Signal('buy', c.close - 1.5, c.close + 1.5)\n"
            )
            raw_res = json.loads(run_with_strategy_file(csv_path, code, "BinCacheStrat"))

            with open(csv_path, "r", encoding="utf-8-sig") as fh:
                compact = _populate_from_stream(fh, use_compact_series=True)
            st = os.stat(csv_path)
            _save_btbin(csv_path, st, compact)
            self.assertTrue(os.path.exists(bin_path))

            loaded_bin = _try_load_btbin(csv_path, st)
            self.assertIsNotNone(loaded_bin)
            self.assertEqual(len(loaded_bin), 240)
            for idx in (0, 17, 120, 239):
                self.assertEqual(loaded_bin[idx], compact[idx])

            bin_res = json.loads(run_with_strategy_file(csv_path, code, "BinCacheStrat"))
            self.assertEqual(bin_res["trades"], raw_res["trades"])
            self.assertEqual(bin_res["summary"], raw_res["summary"])
        finally:
            if os.path.exists(bin_path):
                os.remove(bin_path)
            if os.path.exists(csv_path):
                os.remove(csv_path)

    def test_btbin_slice_and_time_sharded_execution_parity(self):
        from backtest import (
            _populate_from_stream,
            _save_btbin,
            _try_load_btbin_slice,
            run_with_strategy_file_slice,
        )

        fd, csv_path = tempfile.mkstemp(suffix=".csv")
        os.close(fd)
        bin_path = csv_path + ".btbin"
        try:
            with open(csv_path, "w", encoding="utf-8") as fh:
                fh.write("<DATE>\t<TIME>\t<OPEN>\t<HIGH>\t<LOW>\t<CLOSE>\t<TICKVOL>\n")
                for idx in range(600):
                    hour = 0 + (idx // 60)
                    minute = idx % 60
                    wave = (idx % 20) * 0.4
                    base = 2800.0 + wave
                    fh.write(
                        f"2025.04.01\t{hour:02d}:{minute:02d}:00\t"
                        f"{base:.2f}\t{base + 2.5:.2f}\t{base - 1.5:.2f}\t{base + 1.0:.2f}\t{50 + idx}\n"
                    )

            with open(csv_path, "r", encoding="utf-8-sig") as fh:
                full_series = _populate_from_stream(fh, use_compact_series=True)
            st = os.stat(csv_path)
            _save_btbin(csv_path, st, full_series)

            # Verify .btbin slice matches full_series slice byte-for-byte
            bt_slice, actual_start, full_count = _try_load_btbin_slice(csv_path, st, 150, 320)
            self.assertEqual(actual_start, 150)
            self.assertEqual(full_count, 600)
            self.assertEqual(len(bt_slice), 170)
            for k in (0, 25, 169):
                self.assertEqual(bt_slice[k], full_series[150 + k])

            code = (
                "class EmaWarmStrat(Strategy):\n"
                "    def __init__(self):\n"
                "        self.ema = None\n"
                "        self.last_i = -1\n"
                "    def on_candle(self, i, candles):\n"
                "        alpha = 2.0 / 21.0\n"
                "        for k in range(self.last_i + 1, i + 1):\n"
                "            c = candles[k].close\n"
                "            self.ema = c if self.ema is None else (alpha * c + (1.0 - alpha) * self.ema)\n"
                "        self.last_i = i\n"
                "        c = candles[i]\n"
                "        if c.close > self.ema:\n"
                "            return Signal('buy', c.close - 2.0, c.close + 2.0)\n"
                "        return None\n"
            )
            full_res = json.loads(run_with_strategy_file(csv_path, code, "EmaWarmStrat"))

            shard0 = json.loads(
                run_with_strategy_file_slice(
                    csv_path, code, "EmaWarmStrat",
                    load_start=0, load_end=340, entry_start=0, entry_end=300,
                )
            )
            shard1 = json.loads(
                run_with_strategy_file_slice(
                    csv_path, code, "EmaWarmStrat",
                    load_start=180, load_end=600, entry_start=300, entry_end=600,
                )
            )

            # Shard 0 entries all originate before bar 300; Shard 1 entries all >= 300 in global coords
            for t in shard0["trades"]:
                self.assertLess(t["openIndex"], 302)
            self.assertGreater(len(shard1["trades"]), 0)
            for t in shard1["trades"]:
                self.assertGreaterEqual(t["openIndex"], 300)
                self.assertLess(t["closeIndex"], 600)

            # Stitch with overlap dedupe
            stitched = []
            max_close = -1
            for t in shard0["trades"] + shard1["trades"]:
                if t["openIndex"] <= max_close:
                    continue
                stitched.append(t)
                max_close = max(max_close, t["closeIndex"])
            self.assertEqual(len(stitched), len(full_res["trades"]))
            self.assertEqual([t["openIndex"] for t in stitched], [t["openIndex"] for t in full_res["trades"]])
        finally:
            if os.path.exists(bin_path):
                os.remove(bin_path)
            if os.path.exists(csv_path):
                os.remove(csv_path)


if __name__ == "__main__":
    unittest.main()
