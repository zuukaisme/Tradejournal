#!/usr/bin/env python3
"""
Backtester skeleton. Standard library only, so it runs in Termux.

Run:
    python backtest.py --demo            (fake data, to see it working)
    python backtest.py EURUSD_M15.csv    (your MT5 export)
    python backtest.py data.csv --trades (also print every trade)

Rules of the engine:
  * A strategy sees candles[0..i] only (never the future).
  * A signal on candle i enters at the OPEN of candle i+1.
  * If SL and TP are both inside one candle, SL is assumed to hit first.
  * One trade at a time.
"""
import argparse
import csv
import io
import json
import random
import traceback
from dataclasses import dataclass
from typing import List, Optional, Tuple


# ---------- data types ----------

@dataclass
class Candle:
    time: str
    open: float
    high: float
    low: float
    close: float
    volume: float = 0.0


@dataclass
class Signal:
    side: str   # "buy" or "sell"
    sl: float   # stop loss price
    tp: float   # take profit price


@dataclass
class Trade:
    side: str
    entry: float
    sl: float
    tp: float
    open_time: str
    exit: float = 0.0
    close_time: str = ""
    r: float = 0.0       # result in R (1R = distance to stop)
    reason: str = ""     # "tp" or "sl"
    open_i: int = 0
    close_i: int = 0


# ---------- 1. data loader ----------

def load_candles(path: str) -> List[Candle]:
    """Reads MT5 history exports (tab separated, <DATE> <TIME> ...)
    or any CSV with date/time/open/high/low/close columns."""
    raw = open(path, "rb").read()
    if raw[:2] in (b"\xff\xfe", b"\xfe\xff"):
        text = raw.decode("utf-16")
    else:
        text = raw.decode("utf-8-sig")
    return parse_candles(text)


def parse_candles(text: str) -> List[Candle]:
    first = text.splitlines()[0]
    delim = "\t" if "\t" in first else (";" if ";" in first else ",")
    reader = csv.reader(io.StringIO(text), delimiter=delim)

    header = [h.strip().strip("<>").lower() for h in next(reader)]
    idx = {name: i for i, name in enumerate(header)}
    vol_i = idx.get("tickvol", idx.get("volume"))

    candles = []
    for row in reader:
        if not row:
            continue
        if "date" in idx and "time" in idx:
            t = row[idx["date"]] + " " + row[idx["time"]]
        else:
            t = row[idx["time"]]
        candles.append(Candle(
            time=t,
            open=float(row[idx["open"]]),
            high=float(row[idx["high"]]),
            low=float(row[idx["low"]]),
            close=float(row[idx["close"]]),
            volume=float(row[vol_i]) if vol_i is not None else 0.0,
        ))
    return candles


def demo_candles(n: int = 3000) -> List[Candle]:
    """Random walk candles, only for testing the pipeline."""
    random.seed(7)
    price, out = 1000.0, []
    for i in range(n):
        o = price
        c = o + random.gauss(0, 2)
        h = max(o, c) + abs(random.gauss(0, 1))
        l = min(o, c) - abs(random.gauss(0, 1))
        out.append(Candle(f"demo {i}", o, h, l, c))
        price = c
    return out


# ---------- 2. strategy slot ----------

class Strategy:
    """Subclass this. Return a Signal or None.
    Only use candles[0..i]. Never look at candles[i+1:]."""

    def on_candle(self, i: int, candles: List[Candle]) -> Optional[Signal]:
        return None


class DummyStrategy(Strategy):
    """Placeholder rule: three bullish candles in a row, then buy.
    Stop is one average range below, target is 2R."""

    def on_candle(self, i, candles):
        last3 = candles[i - 2:i + 1]
        if not all(c.close > c.open for c in last3):
            return None
        window = candles[i - 13:i + 1]
        risk = sum(c.high - c.low for c in window) / len(window)
        price = candles[i].close
        return Signal("buy", sl=price - risk, tp=price + 2 * risk)


# ---------- engine ----------

def run_backtest(candles: List[Candle], strategy: Strategy, warmup: int = 50,
                 max_stop: Optional[float] = None) -> Tuple[List[Trade], int]:
    trades: List[Trade] = []
    pos: Optional[Trade] = None
    skipped = 0

    for i, c in enumerate(candles):
        # 1) manage the open trade on this candle
        if pos:
            hit = None
            if pos.side == "buy":
                if c.low <= pos.sl:
                    hit = (pos.sl, "sl")
                elif c.high >= pos.tp:
                    hit = (pos.tp, "tp")
            else:
                if c.high >= pos.sl:
                    hit = (pos.sl, "sl")
                elif c.low <= pos.tp:
                    hit = (pos.tp, "tp")
            if hit:
                pos.exit, pos.reason = hit
                pos.close_time = c.time
                pos.close_i = i
                risk = abs(pos.entry - pos.sl)
                move = pos.exit - pos.entry if pos.side == "buy" else pos.entry - pos.exit
                pos.r = move / risk
                trades.append(pos)
                pos = None

        # 2) look for a new signal, enter next candle open
        if pos is None and i >= warmup and i + 1 < len(candles):
            sig = strategy.on_candle(i, candles)
            if sig:
                nxt = candles[i + 1]
                entry = nxt.open
                valid = (sig.sl < entry < sig.tp) if sig.side == "buy" else (sig.tp < entry < sig.sl)
                if valid and max_stop is not None and abs(entry - sig.sl) > max_stop:
                    skipped += 1
                    valid = False
                if valid:
                    pos = Trade(sig.side, entry, sig.sl, sig.tp, nxt.time, open_i=i + 1)
    return trades, skipped


# ---------- 3. results ----------

def report(trades: List[Trade]) -> None:
    n = len(trades)
    if n == 0:
        print("No trades taken.")
        return
    wins = [t for t in trades if t.r > 0]
    gross_win = sum(t.r for t in wins)
    gross_loss = abs(sum(t.r for t in trades if t.r <= 0))
    net = sum(t.r for t in trades)

    peak = equity = max_dd = 0.0
    for t in trades:
        equity += t.r
        peak = max(peak, equity)
        max_dd = max(max_dd, peak - equity)

    pf = gross_win / gross_loss if gross_loss else float("inf")
    print(f"Trades:          {n}")
    print(f"Win rate:        {len(wins) / n * 100:.1f}%")
    print(f"Net result:      {net:+.2f} R")
    print(f"Average trade:   {net / n:+.2f} R")
    print(f"Profit factor:   {pf:.2f}")
    print(f"Max drawdown:    {max_dd:.2f} R")
    if n < 50:
        print("Note: under 50 trades, treat these numbers as noise.")


# ---------- app entry point (called from Kotlin via Chaquopy) ----------

STRATEGIES = {"dummy": DummyStrategy}


def _pts(t: Trade, ps: float) -> float:
    move = t.exit - t.entry if t.side == "buy" else t.entry - t.exit
    return move / ps


def _avg(xs):
    return sum(xs) / len(xs) if xs else 0.0


def compute_stats(trades: List[Trade], skipped: int, point_size: float = 1.0) -> dict:
    n = len(trades)
    pts = [_pts(t, point_size) for t in trades]
    wins = [t for t in trades if t.r > 0]
    losses = [t for t in trades if t.r <= 0]
    win_pts = [p for p in pts if p > 0]
    loss_pts = [p for p in pts if p <= 0]
    stops = [abs(t.entry - t.sl) / point_size for t in trades]
    targets = [abs(t.tp - t.entry) / point_size for t in trades]

    gross_win = sum(t.r for t in wins)
    gross_loss = abs(sum(t.r for t in losses))

    peak = eq = dd = 0.0
    peak_r = eq_r = dd_r = 0.0
    curve = []
    for t, p in zip(trades, pts):
        eq += p
        eq_r += t.r
        curve.append(round(eq, 2))
        peak, peak_r = max(peak, eq), max(peak_r, eq_r)
        dd, dd_r = max(dd, peak - eq), max(dd_r, peak_r - eq_r)

    cw = cl = mw = ml = 0
    for t in trades:
        if t.r > 0:
            cw, cl = cw + 1, 0
        else:
            cl, cw = cl + 1, 0
        mw, ml = max(mw, cw), max(ml, cl)

    def side(s):
        ts = [t for t in trades if t.side == s]
        w = [t for t in ts if t.r > 0]
        return {
            "trades": len(ts),
            "winRatePct": round(len(w) / len(ts) * 100, 1) if ts else 0.0,
            "netPoints": round(sum(_pts(t, point_size) for t in ts), 2),
        }

    warnings = []
    if n < 50:
        warnings.append("Under 50 trades, results are not statistically reliable")
    if n == 0:
        warnings.append("No trades taken")

    return {
        "summary": {
            "totalTrades": n,
            "wins": len(wins),
            "losses": len(losses),
            "winRatePct": round(len(wins) / n * 100, 1) if n else 0.0,
            "netPoints": round(sum(pts), 2),
            "netR": round(sum(t.r for t in trades), 2),
            "avgR": round(sum(t.r for t in trades) / n, 3) if n else 0.0,
            "profitFactor": round(gross_win / gross_loss, 2) if gross_loss else None,
            "skippedByStopCap": skipped,
        },
        "points": {
            "avgWinPoints": round(_avg(win_pts), 2),
            "avgLossPoints": round(_avg(loss_pts), 2),
            "largestWinPoints": round(max(win_pts), 2) if win_pts else 0.0,
            "largestLossPoints": round(min(loss_pts), 2) if loss_pts else 0.0,
            "avgStopDistancePoints": round(_avg(stops), 2),
            "maxStopDistancePoints": round(max(stops), 2) if stops else 0.0,
            "avgTargetDistancePoints": round(_avg(targets), 2),
        },
        "risk": {
            "maxDrawdownPoints": round(dd, 2),
            "maxDrawdownR": round(dd_r, 2),
            "maxConsecutiveWins": mw,
            "maxConsecutiveLosses": ml,
        },
        "bySide": {"buy": side("buy"), "sell": side("sell")},
        "timing": {
            "avgTradeDurationCandles": round(_avg([t.close_i - t.open_i + 1 for t in trades]), 1)
        },
        "equityCurve": curve,
        "trades": [
            {
                "side": t.side, "entry": t.entry, "sl": t.sl, "tp": t.tp, "exit": t.exit,
                "openTime": t.open_time, "closeTime": t.close_time, "reason": t.reason,
                "points": round(_pts(t, point_size), 2), "r": round(t.r, 2),
                "durationCandles": t.close_i - t.open_i + 1,
            }
            for t in trades
        ],
        "warnings": warnings,
    }


def run_from_csv_text(csv_text: str, max_stop_points: float = 0.0,
                      point_size: float = 1.0, strategy: str = "dummy") -> str:
    """Single entry point for the app. Takes CSV text, returns a JSON string.
    max_stop_points = 0 means no stop cap."""
    candles = parse_candles(csv_text)
    max_stop = max_stop_points * point_size if max_stop_points > 0 else None
    trades, skipped = run_backtest(candles, STRATEGIES[strategy](), max_stop=max_stop)
    return json.dumps(compute_stats(trades, skipped, point_size))


def run_with_strategy_code(csv_text: str, strategy_code: str, class_name: str = "",
                           max_stop_points: float = 0.0, point_size: float = 1.0) -> str:
    """Runs ANY user supplied strategy file. strategy_code is the full text of a
    .py file containing a class that extends Strategy. If class_name is empty,
    the first Strategy subclass found in the file is used.
    Always returns JSON: either the stats object, or {"error": ..., "details": ...}."""
    try:
        ns = {"__name__": "user_strategy", "Strategy": Strategy, "Signal": Signal,
              "Candle": Candle, "List": List, "Optional": Optional}
        exec(compile(strategy_code, "strategy.py", "exec"), ns)

        found = [v for v in ns.values()
                 if isinstance(v, type) and issubclass(v, Strategy) and v is not Strategy]
        if class_name:
            found = [c for c in found if c.__name__ == class_name]
        if not found:
            msg = (f"Class '{class_name}' not found in the file" if class_name
                   else "No class extending Strategy found in the file")
            return json.dumps({"error": msg, "details": ""})

        candles = parse_candles(csv_text)
        max_stop = max_stop_points * point_size if max_stop_points > 0 else None
        trades, skipped = run_backtest(candles, found[0](), max_stop=max_stop)
        return json.dumps(compute_stats(trades, skipped, point_size))
    except Exception as e:
        return json.dumps({"error": f"{type(e).__name__}: {e}",
                           "details": traceback.format_exc(limit=3)})


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("csv", nargs="?", help="candle CSV from MT5")
    ap.add_argument("--demo", action="store_true", help="use fake data")
    ap.add_argument("--trades", action="store_true", help="print every trade")
    args = ap.parse_args()

    if args.demo:
        candles = demo_candles()
    elif args.csv:
        candles = load_candles(args.csv)
    else:
        ap.error("give a CSV file or use --demo")

    print(f"Loaded {len(candles)} candles")
    trades, _ = run_backtest(candles, DummyStrategy())
    if args.trades:
        for t in trades:
            print(f"{t.open_time} {t.side} {t.entry:.2f} -> {t.exit:.2f} ({t.reason}) {t.r:+.2f}R")
    report(trades)


if __name__ == "__main__":
    main()
