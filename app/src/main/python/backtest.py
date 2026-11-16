#!/usr/bin/env python3
"""
Backtester skeleton. Standard library only, so it runs in Termux & Chaquopy.

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
import hashlib
import io
import json
import os
import random
import struct
import sys
import time
import traceback
from array import array
from collections.abc import Sequence
from dataclasses import dataclass
from typing import Dict, List, Optional, Tuple


# ---------- data types ----------

@dataclass(slots=True)
class Candle:
    time: str
    open: float
    high: float
    low: float
    close: float
    volume: float = 0.0


@dataclass(slots=True)
class Signal:
    side: str   # "buy" or "sell"
    sl: float   # stop loss price
    tp: float   # take profit price


@dataclass(slots=True)
class Trade:
    side: str
    entry: float
    sl: float
    tp: float
    open_time: str
    exit: float = 0.0
    close_time: str = ""
    r: float = 0.0       # result in R (1R = initial distance to stop)
    reason: str = ""     # "tp", "sl", "be", or "exit"
    open_i: int = 0
    close_i: int = 0
    initial_sl: Optional[float] = None


class CandleSeries(Sequence):
    """Memory-compact sequence of Candle objects backed by C double arrays.
    Uses ~5x less RAM than millions of Python Candle instances (~250MB for 4M bars)
    while presenting the exact List[Candle] indexing, negative indexing, slicing, and
    iteration API expected by strategies."""

    __slots__ = ("opens", "highs", "lows", "closes", "volumes", "_time_buf", "_time_offsets")

    def __init__(self) -> None:
        self.opens = array("d")
        self.highs = array("d")
        self.lows = array("d")
        self.closes = array("d")
        self.volumes = array("d")
        self._time_buf = bytearray()
        self._time_offsets = array("I", (0,))

    def append_row(self, ts: str, o: float, h: float, l: float, c: float, v: float) -> None:
        encoded = ts.encode("utf-8", "replace")
        self._time_buf.extend(encoded)
        self._time_offsets.append(len(self._time_buf))
        self.opens.append(o)
        self.highs.append(h)
        self.lows.append(l)
        self.closes.append(c)
        self.volumes.append(v)

    def time_at(self, idx: int) -> str:
        offsets = self._time_offsets
        return self._time_buf[offsets[idx]:offsets[idx + 1]].decode("utf-8")

    def _candle_at(self, idx: int) -> Candle:
        offsets = self._time_offsets
        return Candle(
            self._time_buf[offsets[idx]:offsets[idx + 1]].decode("utf-8"),
            self.opens[idx],
            self.highs[idx],
            self.lows[idx],
            self.closes[idx],
            self.volumes[idx],
        )

    def __len__(self) -> int:
        return len(self.closes)

    def __getitem__(self, item):
        n = len(self.closes)
        if isinstance(item, slice):
            start, stop, step = item.indices(n)
            return [self._candle_at(k) for k in range(start, stop, step)]
        if item < 0:
            item += n
        if item < 0 or item >= n:
            raise IndexError("candle index out of range")
        return self._candle_at(item)

    def __iter__(self):
        n = len(self.closes)
        opens = self.opens
        highs = self.highs
        lows = self.lows
        closes = self.closes
        volumes = self.volumes
        buf = self._time_buf
        offsets = self._time_offsets
        candle_cls = Candle
        for k in range(n):
            yield candle_cls(
                buf[offsets[k]:offsets[k + 1]].decode("utf-8"),
                opens[k],
                highs[k],
                lows[k],
                closes[k],
                volumes[k],
            )


# ---------- 1. data loader ----------

_MAX_CACHE_CANDLES = 150_000
_PARSED_CACHE: Optional[Tuple[Tuple[int, bytes], Sequence]] = None
_FILE_CACHE: Optional[Tuple[Tuple[str, int, int], Sequence]] = None


def _resolve_header(first: str):
    delim = "\t" if "\t" in first else (";" if ";" in first else ",")
    if '"' in first:
        header_raw = next(csv.reader([first], delimiter=delim))
    else:
        header_raw = first.split(delim)

    header = [h.strip().strip("<>").lower() for h in header_raw]
    idx: Dict[str, int] = {}
    for col_i, name in enumerate(header):
        if name and name not in idx:
            idx[name] = col_i

    missing = [k for k in ("open", "high", "low", "close") if k not in idx]
    time_keys = [k for k in ("datetime", "timestamp", "date", "time") if k in idx]
    if missing or not time_keys:
        raise ValueError(
            "This does not look like a candle CSV. It needs date or time plus open, "
            "high, low, close columns. First header found: " + first[:80]
        )

    o_i = idx["open"]
    h_i = idx["high"]
    l_i = idx["low"]
    c_i = idx["close"]
    vol_i = idx.get("tickvol", idx.get("volume"))
    has_date_time = ("date" in idx and "time" in idx)
    date_i = idx.get("date", -1)
    time_i = idx.get("time", -1)
    ts_i = idx[time_keys[0]]
    return delim, o_i, h_i, l_i, c_i, vol_i, has_date_time, date_i, time_i, ts_i


def _populate_from_stream(stream, use_compact_series: bool):
    first = ""
    for raw_line in stream:
        stripped = raw_line.strip()
        if stripped:
            first = stripped
            break
    if not first:
        raise ValueError("The CSV file is empty")

    delim, o_i, h_i, l_i, c_i, vol_i, has_date_time, date_i, time_i, ts_i = _resolve_header(first)

    if use_compact_series:
        series = CandleSeries()
        opens = series.opens
        highs = series.highs
        lows = series.lows
        closes = series.closes
        volumes = series.volumes
        tbuf = series._time_buf
        toff = series._time_offsets
        tbuf_extend = tbuf.extend
        toff_append = toff.append
        o_app = opens.append
        h_app = highs.append
        l_app = lows.append
        c_app = closes.append
        v_app = volumes.append
        flt = float
        split_fn = str.split
        reader = csv.reader

        if has_date_time:
            if vol_i is not None:
                for raw_line in stream:
                    line = raw_line.strip()
                    if not line:
                        continue
                    row = next(reader([line], delimiter=delim)) if '"' in line else split_fn(line, delim)
                    tbuf_extend((row[date_i] + " " + row[time_i]).encode("utf-8", "replace"))
                    toff_append(len(tbuf))
                    o_app(flt(row[o_i])); h_app(flt(row[h_i])); l_app(flt(row[l_i])); c_app(flt(row[c_i])); v_app(flt(row[vol_i]))
            else:
                for raw_line in stream:
                    line = raw_line.strip()
                    if not line:
                        continue
                    row = next(reader([line], delimiter=delim)) if '"' in line else split_fn(line, delim)
                    tbuf_extend((row[date_i] + " " + row[time_i]).encode("utf-8", "replace"))
                    toff_append(len(tbuf))
                    o_app(flt(row[o_i])); h_app(flt(row[h_i])); l_app(flt(row[l_i])); c_app(flt(row[c_i])); v_app(0.0)
        else:
            if vol_i is not None:
                for raw_line in stream:
                    line = raw_line.strip()
                    if not line:
                        continue
                    row = next(reader([line], delimiter=delim)) if '"' in line else split_fn(line, delim)
                    tbuf_extend(row[ts_i].encode("utf-8", "replace"))
                    toff_append(len(tbuf))
                    o_app(flt(row[o_i])); h_app(flt(row[h_i])); l_app(flt(row[l_i])); c_app(flt(row[c_i])); v_app(flt(row[vol_i]))
            else:
                for raw_line in stream:
                    line = raw_line.strip()
                    if not line:
                        continue
                    row = next(reader([line], delimiter=delim)) if '"' in line else split_fn(line, delim)
                    tbuf_extend(row[ts_i].encode("utf-8", "replace"))
                    toff_append(len(tbuf))
                    o_app(flt(row[o_i])); h_app(flt(row[h_i])); l_app(flt(row[l_i])); c_app(flt(row[c_i])); v_app(0.0)
        return series

    candles: List[Candle] = []
    append = candles.append
    candle_cls = Candle
    flt = float
    split_fn = str.split
    reader = csv.reader

    if has_date_time:
        if vol_i is not None:
            for raw_line in stream:
                line = raw_line.strip()
                if not line:
                    continue
                row = next(reader([line], delimiter=delim)) if '"' in line else split_fn(line, delim)
                append(candle_cls(
                    row[date_i] + " " + row[time_i],
                    flt(row[o_i]), flt(row[h_i]), flt(row[l_i]), flt(row[c_i]), flt(row[vol_i]),
                ))
        else:
            for raw_line in stream:
                line = raw_line.strip()
                if not line:
                    continue
                row = next(reader([line], delimiter=delim)) if '"' in line else split_fn(line, delim)
                append(candle_cls(
                    row[date_i] + " " + row[time_i],
                    flt(row[o_i]), flt(row[h_i]), flt(row[l_i]), flt(row[c_i]), 0.0,
                ))
    else:
        if vol_i is not None:
            for raw_line in stream:
                line = raw_line.strip()
                if not line:
                    continue
                row = next(reader([line], delimiter=delim)) if '"' in line else split_fn(line, delim)
                append(candle_cls(
                    row[ts_i],
                    flt(row[o_i]), flt(row[h_i]), flt(row[l_i]), flt(row[c_i]), flt(row[vol_i]),
                ))
        else:
            for raw_line in stream:
                line = raw_line.strip()
                if not line:
                    continue
                row = next(reader([line], delimiter=delim)) if '"' in line else split_fn(line, delim)
                append(candle_cls(
                    row[ts_i],
                    flt(row[o_i]), flt(row[h_i]), flt(row[l_i]), flt(row[c_i]), 0.0,
                ))
    return candles


_BTBIN_MAGIC = b"BTBN"
_BTBIN_HEADER_FMT = "<4sIqqq"
_BTBIN_HEADER_SIZE = struct.calcsize(_BTBIN_HEADER_FMT)


def _btbin_path_for_csv(csv_path: str, for_write: bool = False) -> str:
    adjacent_path = csv_path + ".btbin"
    try:
        from java import jclass
        environment = jclass("android.os.Environment")
        downloads = str(environment.getExternalStoragePublicDirectory(environment.DIRECTORY_DOWNLOADS).getAbsolutePath())
        identity = hashlib.sha256(os.path.realpath(csv_path).encode("utf-8")).digest()[:12].hex()
        shared_path = os.path.join(downloads, "Tradepulse", "cache", f"{os.path.basename(csv_path)}-{identity}.btbin")
        if for_write or os.path.isfile(shared_path):
            return shared_path
    except Exception:
        pass
    return adjacent_path


def _try_load_btbin(csv_path: str, st) -> Optional[CandleSeries]:
    if sys.byteorder != "little" or array("I").itemsize != 4 or array("d").itemsize != 8:
        return None
    bin_path = _btbin_path_for_csv(csv_path)
    try:
        if not os.path.exists(bin_path) or os.path.getsize(bin_path) < _BTBIN_HEADER_SIZE:
            return None
        with open(bin_path, "rb") as f:
            hdr = f.read(_BTBIN_HEADER_SIZE)
            if len(hdr) != _BTBIN_HEADER_SIZE:
                return None
            magic, version, count, src_size, src_mtime_ms = struct.unpack(_BTBIN_HEADER_FMT, hdr)
            if magic != _BTBIN_MAGIC or version != 1 or count <= 0:
                return None
            csv_mtime_ms = int(getattr(st, "st_mtime", 0.0) * 1000)
            if src_size != st.st_size or abs(src_mtime_ms - csv_mtime_ms) > 2500:
                return None

            series = CandleSeries()
            series.opens.fromfile(f, count)
            series.highs.fromfile(f, count)
            series.lows.fromfile(f, count)
            series.closes.fromfile(f, count)
            series.volumes.fromfile(f, count)
            series._time_offsets = array("I")
            series._time_offsets.fromfile(f, count + 1)
            total_t_bytes = series._time_offsets[count]
            t_raw = f.read(total_t_bytes)
            if len(t_raw) != total_t_bytes:
                return None
            series._time_buf = bytearray(t_raw)
            return series
    except Exception:
        return None


def _try_load_btbin_slice(
    csv_path: str,
    st,
    load_start: int,
    load_end: int,
) -> Optional[Tuple[CandleSeries, int, int]]:
    """Loads ONLY [load_start, load_end) from the columnar .btbin cache via direct file seeks,
    keeping each worker process's RAM footprint to ~1/K of the full dataset."""
    if sys.byteorder != "little" or array("I").itemsize != 4 or array("d").itemsize != 8:
        return None
    bin_path = _btbin_path_for_csv(csv_path)
    try:
        if not os.path.exists(bin_path) or os.path.getsize(bin_path) < _BTBIN_HEADER_SIZE:
            return None
        with open(bin_path, "rb") as f:
            hdr = f.read(_BTBIN_HEADER_SIZE)
            if len(hdr) != _BTBIN_HEADER_SIZE:
                return None
            magic, version, count, src_size, src_mtime_ms = struct.unpack(_BTBIN_HEADER_FMT, hdr)
            if magic != _BTBIN_MAGIC or version != 1 or count <= 0:
                return None
            csv_mtime_ms = int(getattr(st, "st_mtime", 0.0) * 1000)
            if src_size != st.st_size or abs(src_mtime_ms - csv_mtime_ms) > 2500:
                return None

            s_idx = max(0, int(load_start))
            e_idx = count if load_end < 0 else min(count, int(load_end))
            if s_idx >= e_idx:
                return CandleSeries(), s_idx, count

            slice_len = e_idx - s_idx
            opens_base = _BTBIN_HEADER_SIZE
            highs_base = opens_base + 8 * count
            lows_base = highs_base + 8 * count
            closes_base = lows_base + 8 * count
            vols_base = closes_base + 8 * count
            offsets_base = vols_base + 8 * count
            time_buf_base = offsets_base + 4 * (count + 1)

            series = CandleSeries()
            for base, arr in (
                (opens_base, series.opens),
                (highs_base, series.highs),
                (lows_base, series.lows),
                (closes_base, series.closes),
                (vols_base, series.volumes),
            ):
                f.seek(base + s_idx * 8)
                arr.fromfile(f, slice_len)

            raw_offsets = array("I")
            f.seek(offsets_base + s_idx * 4)
            raw_offsets.fromfile(f, slice_len + 1)
            t_first = raw_offsets[0]
            t_last = raw_offsets[slice_len]
            span_bytes = max(0, t_last - t_first)
            if span_bytes > 0:
                f.seek(time_buf_base + t_first)
                t_raw = f.read(span_bytes)
                if len(t_raw) != span_bytes:
                    return None
                series._time_buf = bytearray(t_raw)
            else:
                series._time_buf = bytearray()

            if t_first == 0:
                series._time_offsets = raw_offsets
            else:
                series._time_offsets = array("I", (off - t_first for off in raw_offsets))
            return series, s_idx, count
    except Exception:
        return None


def load_candle_slice(csv_path: str, load_start: int = 0, load_end: int = -1) -> Tuple[Sequence[Candle], int, int]:
    """Loads [load_start, load_end) from .btbin in O(1) disk seek when available, or slices
    load_candles(csv_path) otherwise. Returns (candles_slice, actual_load_start, full_total_candles)."""
    st = os.stat(csv_path)
    bt_slice = _try_load_btbin_slice(csv_path, st, load_start, load_end)
    if bt_slice is not None:
        return bt_slice

    all_candles = load_candles(csv_path)
    full_total = len(all_candles)
    s_idx = max(0, int(load_start))
    e_idx = full_total if load_end < 0 else min(full_total, int(load_end))
    if s_idx == 0 and e_idx == full_total:
        return all_candles, 0, full_total
    return all_candles[s_idx:e_idx], s_idx, full_total


def _save_btbin(csv_path: str, st, series: CandleSeries) -> None:
    if sys.byteorder != "little" or array("I").itemsize != 4 or array("d").itemsize != 8:
        return
    n = len(series)
    if n <= 0 or len(series._time_offsets) != n + 1:
        return
    bin_path = _btbin_path_for_csv(csv_path, for_write=True)
    os.makedirs(os.path.dirname(bin_path), exist_ok=True)
    tmp_path = bin_path + f".pytmp.{os.getpid()}"
    try:
        mtime_ms = int(getattr(st, "st_mtime", 0.0) * 1000)
        hdr = struct.pack(_BTBIN_HEADER_FMT, _BTBIN_MAGIC, 1, n, st.st_size, mtime_ms)
        with open(tmp_path, "wb") as f:
            f.write(hdr)
            series.opens.tofile(f)
            series.highs.tofile(f)
            series.lows.tofile(f)
            series.closes.tofile(f)
            series.volumes.tofile(f)
            series._time_offsets.tofile(f)
            f.write(series._time_buf)
        os.replace(tmp_path, bin_path)
    except Exception:
        try:
            if os.path.exists(tmp_path):
                os.remove(tmp_path)
        except Exception:
            pass


def load_candles(path: str) -> Sequence[Candle]:
    """Streams MT5 history exports (tab separated, <DATE> <TIME> ...) or any CSV
    from disk without loading the full file bytes into RAM. Reads the compiled
    `.btbin` binary candle cache when present for sub-second loading across workers."""
    global _FILE_CACHE, _PARSED_CACHE

    st = os.stat(path)
    file_key = (path, st.st_size, getattr(st, "st_mtime_ns", int(st.st_mtime)))
    cached = _FILE_CACHE
    if cached is not None and cached[0] == file_key:
        return cached[1]

    # Release large prior caches before parsing a new large dataset
    if st.st_size > 8 * 1024 * 1024:
        _FILE_CACHE = None
        _PARSED_CACHE = None

    bin_series = _try_load_btbin(path, st)
    if bin_series is not None:
        if len(bin_series) <= _MAX_CACHE_CANDLES:
            _FILE_CACHE = (file_key, bin_series)
        else:
            _FILE_CACHE = None
        return bin_series

    with open(path, "rb") as bf:
        prefix = bf.read(4)
    encoding = "utf-16" if prefix[:2] in (b"\xff\xfe", b"\xfe\xff") else "utf-8-sig"
    use_compact = st.st_size > 2 * 1024 * 1024

    with open(path, "r", encoding=encoding, errors="replace", buffering=1024 * 1024) as f:
        candles = _populate_from_stream(f, use_compact_series=use_compact)

    if isinstance(candles, CandleSeries) and len(candles) > 0:
        _save_btbin(path, st, candles)

    if len(candles) <= _MAX_CACHE_CANDLES:
        _FILE_CACHE = (file_key, candles)
    else:
        _FILE_CACHE = None
    return candles


def parse_candles(text: str) -> List[Candle]:
    """Accepts MT5 exports (<DATE> <TIME> ...), daily exports with only <DATE>,
    and generic CSVs with date, datetime, time or timestamp columns."""
    global _PARSED_CACHE
    if text.startswith("\ufeff"):
        text = text.lstrip("\ufeff")

    cache_key = None
    if 4096 < len(text) <= 20 * 1024 * 1024:
        digest = hashlib.blake2b(text.encode("utf-8", "replace"), digest_size=16).digest()
        cache_key = (len(text), digest)
        cached = _PARSED_CACHE
        if cached is not None and cached[0] == cache_key:
            return cached[1]

    stream = io.StringIO(text)
    candles = _populate_from_stream(stream, use_compact_series=False)

    if cache_key is not None and len(candles) <= _MAX_CACHE_CANDLES:
        _PARSED_CACHE = (cache_key, candles)
    elif len(candles) > _MAX_CACHE_CANDLES:
        _PARSED_CACHE = None
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

    def manage_trade(self, i: int, candles: List[Candle], trade: Trade) -> Optional[str]:
        """Optional hook called after each completed candle while a trade is open.
        May update trade.sl / trade.tp for subsequent bars (e.g., move SL to trade.entry
        for break-even) or return 'be' / 'exit' to close at candles[i].close."""
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

BE_EPS = 1e-9


def _emit_progress(cb, payload: dict) -> None:
    if cb is None:
        return
    try:
        encoded = json.dumps(payload)
        if hasattr(cb, "onProgress"):
            cb.onProgress(encoded)
        elif callable(cb):
            cb(encoded)
    except Exception:
        pass


def _run_backtest_detailed(
    candles: Sequence[Candle],
    strategy: Strategy,
    warmup: int = 50,
    max_stop: Optional[float] = None,
    point_size: float = 1.0,
    progress_callback=None,
    global_offset: int = 0,
    local_entry_start: int = 0,
    local_entry_end: Optional[int] = None,
) -> Tuple[List[Trade], int, int, int]:
    trades: List[Trade] = []
    trades_append = trades.append
    pos: Optional[Trade] = None
    skipped = 0
    entered = 0
    wins_count = 0
    losses_count = 0
    be_count = 0
    running_pts = 0.0
    running_r = 0.0
    total_candles = len(candles)
    be_eps = BE_EPS
    abs_fn = abs
    trade_cls = Trade
    on_candle = strategy.on_candle
    manage_fn = strategy.manage_trade if type(strategy).manage_trade is not Strategy.manage_trade else None
    has_progress = progress_callback is not None
    inv_point_size = 1.0 / point_size if point_size else 1.0
    mono = time.monotonic
    last_emit_at = mono() if has_progress else 0.0
    is_series = isinstance(candles, CandleSeries)

    eff_entry_start = max(warmup, local_entry_start)
    eff_entry_end = total_candles if local_entry_end is None else min(total_candles, max(0, local_entry_end))
    owned_span = max(1, eff_entry_end - eff_entry_start) if local_entry_end is not None or local_entry_start > 0 else total_candles

    def push_progress(processed: int, current_time: str) -> None:
        if local_entry_end is not None or local_entry_start > 0:
            done_owned = min(processed, eff_entry_end) - eff_entry_start
            if done_owned < 0:
                done_owned = 0
            prog_total = max(1, eff_entry_end - eff_entry_start)
        else:
            done_owned = processed
            prog_total = total_candles
        _emit_progress(progress_callback, {
            "candlesProcessed": done_owned,
            "totalCandles": prog_total,
            "enteredTrades": entered,
            "closedTrades": len(trades),
            "openTrades": 1 if pos is not None else 0,
            "wins": wins_count,
            "losses": losses_count,
            "breakEvens": be_count,
            "skippedByStopCap": skipped,
            "netPoints": round(running_pts, 2),
            "netR": round(running_r, 2),
            "currentTime": current_time,
        })

    if has_progress:
        first_time = candles.time_at(0) if (is_series and total_candles > 0) else (candles[0].time if total_candles > 0 else "")
        push_progress(0, first_time)

    last_idx = -1
    if is_series:
        opens = candles.opens
        highs = candles.highs
        lows = candles.lows
        closes = candles.closes
        time_at = candles.time_at
        emit_mask = 1023 if total_candles > 50_000 else 255

        for i in range(total_candles):
            last_idx = i
            if pos is not None:
                c_low = lows[i]
                c_high = highs[i]
                hit = None
                if pos.side == "buy":
                    if c_low <= pos.sl:
                        rzn = "be" if abs_fn(pos.sl - pos.entry) <= be_eps else "sl"
                        hit = (pos.sl, rzn)
                    elif c_high >= pos.tp:
                        hit = (pos.tp, "tp")
                else:
                    if c_high >= pos.sl:
                        rzn = "be" if abs_fn(pos.sl - pos.entry) <= be_eps else "sl"
                        hit = (pos.sl, rzn)
                    elif c_low <= pos.tp:
                        hit = (pos.tp, "tp")

                if hit is None and manage_fn is not None:
                    action = manage_fn(i, candles, pos)
                    if isinstance(action, str):
                        act = action.strip().lower()
                        if act in ("be", "breakeven", "break_even", "exit", "close"):
                            exit_px = pos.entry if act in ("be", "breakeven", "break_even") else closes[i]
                            hit_move = (exit_px - pos.entry) if pos.side == "buy" else (pos.entry - exit_px)
                            rzn = "be" if abs_fn(hit_move) <= be_eps else "exit"
                            hit = (exit_px, rzn)

                if hit is not None:
                    exit_px, reason = hit
                    pos.exit = exit_px
                    pos.reason = reason
                    pos.close_time = time_at(i)
                    pos.close_i = i + global_offset
                    risk_sl = pos.initial_sl if pos.initial_sl is not None else pos.sl
                    risk = abs_fn(pos.entry - risk_sl)
                    move = (exit_px - pos.entry) if pos.side == "buy" else (pos.entry - exit_px)
                    r_val = move / risk if risk > 0.0 else 0.0
                    pos.r = r_val
                    if abs_fn(r_val) <= be_eps:
                        pos.reason = "be"
                        be_count += 1
                    elif r_val > be_eps:
                        wins_count += 1
                    else:
                        losses_count += 1
                    trades_append(pos)
                    running_pts += move * inv_point_size
                    running_r += r_val
                    pos = None

            if pos is None:
                if i >= eff_entry_end:
                    # Shard entry window finished and seam trade (if any) is now closed
                    break
                if i >= warmup and i + 1 < total_candles:
                    sig = on_candle(i, candles)
                    # Only record entries starting inside [eff_entry_start, eff_entry_end)
                    if sig is not None and i >= eff_entry_start:
                        nxt_i = i + 1
                        entry = opens[nxt_i]
                        valid = (sig.sl < entry < sig.tp) if sig.side == "buy" else (sig.tp < entry < sig.sl)
                        if valid and max_stop is not None and abs_fn(entry - sig.sl) > max_stop:
                            skipped += 1
                            valid = False
                        if valid:
                            pos = trade_cls(
                                sig.side,
                                entry,
                                sig.sl,
                                sig.tp,
                                time_at(nxt_i),
                                open_i=nxt_i + global_offset,
                                initial_sl=sig.sl,
                            )
                            entered += 1

            if has_progress and ((i & emit_mask) == emit_mask or i + 1 == total_candles):
                now = mono()
                if i + 1 == total_candles or (now - last_emit_at) >= 0.15:
                    last_emit_at = now
                    push_progress(i + 1, time_at(i))
    else:
        for i, c in enumerate(candles):
            last_idx = i
            if pos is not None:
                hit = None
                if pos.side == "buy":
                    if c.low <= pos.sl:
                        rzn = "be" if abs_fn(pos.sl - pos.entry) <= be_eps else "sl"
                        hit = (pos.sl, rzn)
                    elif c.high >= pos.tp:
                        hit = (pos.tp, "tp")
                else:
                    if c.high >= pos.sl:
                        rzn = "be" if abs_fn(pos.sl - pos.entry) <= be_eps else "sl"
                        hit = (pos.sl, rzn)
                    elif c.low <= pos.tp:
                        hit = (pos.tp, "tp")

                if hit is None and manage_fn is not None:
                    action = manage_fn(i, candles, pos)
                    if isinstance(action, str):
                        act = action.strip().lower()
                        if act in ("be", "breakeven", "break_even", "exit", "close"):
                            exit_px = pos.entry if act in ("be", "breakeven", "break_even") else c.close
                            hit_move = (exit_px - pos.entry) if pos.side == "buy" else (pos.entry - exit_px)
                            rzn = "be" if abs_fn(hit_move) <= be_eps else "exit"
                            hit = (exit_px, rzn)

                if hit is not None:
                    exit_px, reason = hit
                    pos.exit = exit_px
                    pos.reason = reason
                    pos.close_time = c.time
                    pos.close_i = i + global_offset
                    risk_sl = pos.initial_sl if pos.initial_sl is not None else pos.sl
                    risk = abs_fn(pos.entry - risk_sl)
                    move = (exit_px - pos.entry) if pos.side == "buy" else (pos.entry - exit_px)
                    r_val = move / risk if risk > 0.0 else 0.0
                    pos.r = r_val
                    if abs_fn(r_val) <= be_eps:
                        pos.reason = "be"
                        be_count += 1
                    elif r_val > be_eps:
                        wins_count += 1
                    else:
                        losses_count += 1
                    trades_append(pos)
                    running_pts += move * inv_point_size
                    running_r += r_val
                    pos = None

            if pos is None:
                if i >= eff_entry_end:
                    break
                if i >= warmup and i + 1 < total_candles:
                    sig = on_candle(i, candles)
                    if sig is not None and i >= eff_entry_start:
                        nxt = candles[i + 1]
                        entry = nxt.open
                        valid = (sig.sl < entry < sig.tp) if sig.side == "buy" else (sig.tp < entry < sig.sl)
                        if valid and max_stop is not None and abs_fn(entry - sig.sl) > max_stop:
                            skipped += 1
                            valid = False
                        if valid:
                            pos = trade_cls(
                                sig.side,
                                entry,
                                sig.sl,
                                sig.tp,
                                nxt.time,
                                open_i=i + 1 + global_offset,
                                initial_sl=sig.sl,
                            )
                            entered += 1

            if has_progress and (i & 255 == 255 or i + 1 == total_candles):
                now = mono()
                if i + 1 == total_candles or (now - last_emit_at) >= 0.12:
                    last_emit_at = now
                    push_progress(i + 1, c.time)

    if has_progress and last_idx >= 0:
        final_time = candles.time_at(last_idx) if is_series else candles[last_idx].time
        push_progress(eff_entry_end, final_time)

    open_at_end = 1 if pos is not None else 0
    return trades, skipped, open_at_end, owned_span


def run_backtest(candles: Sequence[Candle], strategy: Strategy, warmup: int = 50,
                 max_stop: Optional[float] = None) -> Tuple[List[Trade], int]:
    trades, skipped, _, _ = _run_backtest_detailed(
        candles=candles,
        strategy=strategy,
        warmup=warmup,
        max_stop=max_stop,
    )
    return trades, skipped


# ---------- 3. results ----------

def report(trades: List[Trade]) -> None:
    n = len(trades)
    if n == 0:
        print("No trades taken.")
        return
    wins = [t for t in trades if t.r > BE_EPS]
    gross_win = sum(t.r for t in wins)
    gross_loss = abs(sum(t.r for t in trades if t.r < -BE_EPS))
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


def compute_stats(
    trades: List[Trade],
    skipped: int,
    point_size: float = 1.0,
    open_trades: int = 0,
    total_candles: int = 0,
) -> dict:
    n = len(trades)
    entered_trades = n + max(0, open_trades)
    inv_ps = 1.0 / point_size if point_size else 1.0
    be_eps = BE_EPS

    win_count = 0
    loss_count = 0
    be_count = 0
    sum_win_pts = 0.0
    sum_loss_pts = 0.0
    largest_win = 0.0
    largest_loss = 0.0
    has_win = False
    has_loss = False
    sum_stops = 0.0
    max_stop_dist = 0.0
    sum_targets = 0.0
    tp_exits = 0
    sl_exits = 0
    gross_win = 0.0
    gross_loss = 0.0
    sum_pts = 0.0
    sum_r = 0.0
    sum_dur = 0

    peak = eq = dd = 0.0
    peak_r = eq_r = dd_r = 0.0
    curve: List[float] = []
    curve_append = curve.append
    trade_dicts: List[dict] = []
    trade_dicts_append = trade_dicts.append

    cw = cl = mw = ml = current_streak = 0
    buy_trades = buy_wins = 0
    buy_net_pts = 0.0
    sell_trades = sell_wins = 0
    sell_net_pts = 0.0

    for t in trades:
        move = (t.exit - t.entry) if t.side == "buy" else (t.entry - t.exit)
        p = move * inv_ps
        r_val = t.r
        sum_pts += p
        sum_r += r_val

        risk_sl = t.initial_sl if t.initial_sl is not None else t.sl
        stop_d = abs(t.entry - risk_sl) * inv_ps
        target_d = abs(t.tp - t.entry) * inv_ps
        sum_stops += stop_d
        if stop_d > max_stop_dist:
            max_stop_dist = stop_d
        sum_targets += target_d

        dur = t.close_i - t.open_i + 1
        sum_dur += dur

        if t.reason == "tp":
            tp_exits += 1
        elif t.reason == "sl":
            sl_exits += 1

        if r_val > be_eps:
            win_count += 1
            sum_win_pts += p
            gross_win += r_val
            if not has_win or p > largest_win:
                largest_win = p
                has_win = True
            cw += 1
            cl = 0
            current_streak = cw
            if cw > mw:
                mw = cw
        elif r_val < -be_eps:
            loss_count += 1
            sum_loss_pts += p
            gross_loss += -r_val
            if not has_loss or p < largest_loss:
                largest_loss = p
                has_loss = True
            cl += 1
            cw = 0
            current_streak = -cl
            if cl > ml:
                ml = cl
        else:
            be_count += 1
            cw = 0
            cl = 0
            current_streak = 0

        if t.side == "buy":
            buy_trades += 1
            buy_net_pts += p
            if r_val > be_eps:
                buy_wins += 1
        else:
            sell_trades += 1
            sell_net_pts += p
            if r_val > be_eps:
                sell_wins += 1

        eq += p
        eq_r += r_val
        curve_append(round(eq, 2))
        if eq > peak:
            peak = eq
        if peak - eq > dd:
            dd = peak - eq
        if eq_r > peak_r:
            peak_r = eq_r
        if peak_r - eq_r > dd_r:
            dd_r = peak_r - eq_r

        trade_dicts_append({
            "side": t.side,
            "entry": t.entry,
            "sl": t.sl,
            "tp": t.tp,
            "exit": t.exit,
            "openTime": t.open_time,
            "closeTime": t.close_time,
            "openIndex": t.open_i,
            "closeIndex": t.close_i,
            "reason": t.reason,
            "points": round(p, 2),
            "r": round(r_val, 2),
            "durationCandles": dur,
        })

    warnings = []
    if n < 50:
        warnings.append("Under 50 trades, results are not statistically reliable")
    if n == 0:
        warnings.append("No trades taken")

    return {
        "summary": {
            "totalTrades": n,
            "enteredTrades": entered_trades,
            "openTrades": max(0, open_trades),
            "wins": win_count,
            "losses": loss_count,
            "breakEvens": be_count,
            "winRatePct": round(win_count / n * 100, 1) if n else 0.0,
            "lossRatePct": round(loss_count / n * 100, 1) if n else 0.0,
            "breakEvenRatePct": round(be_count / n * 100, 1) if n else 0.0,
            "netPoints": round(sum_pts, 2),
            "netR": round(sum_r, 2),
            "avgR": round(sum_r / n, 3) if n else 0.0,
            "expectancyPoints": round(sum_pts / n, 2) if n else 0.0,
            "profitFactor": round(gross_win / gross_loss, 2) if gross_loss else None,
            "skippedByStopCap": skipped,
            "tpExits": tp_exits,
            "slExits": sl_exits,
            "totalCandles": total_candles,
            "currentStreak": current_streak,
        },
        "points": {
            "avgWinPoints": round(sum_win_pts / win_count, 2) if win_count else 0.0,
            "avgLossPoints": round(sum_loss_pts / loss_count, 2) if loss_count else 0.0,
            "largestWinPoints": round(largest_win, 2) if has_win else 0.0,
            "largestLossPoints": round(largest_loss, 2) if has_loss else 0.0,
            "avgStopDistancePoints": round(sum_stops / n, 2) if n else 0.0,
            "maxStopDistancePoints": round(max_stop_dist, 2) if n else 0.0,
            "avgTargetDistancePoints": round(sum_targets / n, 2) if n else 0.0,
        },
        "risk": {
            "maxDrawdownPoints": round(dd, 2),
            "maxDrawdownR": round(dd_r, 2),
            "maxConsecutiveWins": mw,
            "maxConsecutiveLosses": ml,
        },
        "bySide": {
            "buy": {
                "trades": buy_trades,
                "winRatePct": round(buy_wins / buy_trades * 100, 1) if buy_trades else 0.0,
                "netPoints": round(buy_net_pts, 2),
            },
            "sell": {
                "trades": sell_trades,
                "winRatePct": round(sell_wins / sell_trades * 100, 1) if sell_trades else 0.0,
                "netPoints": round(sell_net_pts, 2),
            },
        },
        "timing": {
            "avgTradeDurationCandles": round(sum_dur / n, 1) if n else 0.0
        },
        "equityCurve": curve,
        "trades": trade_dicts,
        "warnings": warnings,
    }


def _compile_and_find_strategy(strategy_code: str, class_name: str = "") -> Tuple[Optional[type], Optional[str]]:
    ns = {
        "__name__": "user_strategy",
        "Strategy": Strategy,
        "Signal": Signal,
        "Candle": Candle,
        "Trade": Trade,
        "List": List,
        "Optional": Optional,
    }
    exec(compile(strategy_code, "strategy.py", "exec"), ns)

    found = [
        v for v in ns.values()
        if isinstance(v, type) and issubclass(v, Strategy) and v is not Strategy
    ]
    if class_name:
        found = [c for c in found if c.__name__ == class_name]
    if not found:
        msg = (
            f"Class '{class_name}' not found in the file"
            if class_name
            else "No class extending Strategy found in the file"
        )
        return None, msg
    return found[0], None


def run_from_csv_text(csv_text: str, max_stop_points: float = 0.0,
                      point_size: float = 1.0, strategy: str = "dummy",
                      progress_callback=None) -> str:
    """Single entry point for small CSV text. Takes CSV text, returns JSON."""
    candles = parse_candles(csv_text)
    max_stop = max_stop_points * point_size if max_stop_points > 0 else None
    trades, skipped, open_trades, total_candles = _run_backtest_detailed(
        candles,
        STRATEGIES[strategy](),
        max_stop=max_stop,
        point_size=point_size,
        progress_callback=progress_callback,
    )
    return json.dumps(
        compute_stats(
            trades,
            skipped,
            point_size,
            open_trades=open_trades,
            total_candles=total_candles,
        )
    )


def run_with_strategy_code(csv_text: str, strategy_code: str, class_name: str = "",
                           max_stop_points: float = 0.0, point_size: float = 1.0,
                           progress_callback=None) -> str:
    """Runs ANY user supplied strategy file against CSV text."""
    try:
        strategy_cls, err_msg = _compile_and_find_strategy(strategy_code, class_name)
        if err_msg or strategy_cls is None:
            return json.dumps({"error": err_msg or "Missing Strategy class", "details": ""})

        candles = parse_candles(csv_text)
        max_stop = max_stop_points * point_size if max_stop_points > 0 else None
        trades, skipped, open_trades, total_candles = _run_backtest_detailed(
            candles,
            strategy_cls(),
            max_stop=max_stop,
            point_size=point_size,
            progress_callback=progress_callback,
        )
        return json.dumps(
            compute_stats(
                trades,
                skipped,
                point_size,
                open_trades=open_trades,
                total_candles=total_candles,
            )
        )
    except Exception as e:
        return json.dumps({"error": f"{type(e).__name__}: {e}",
                           "details": traceback.format_exc(limit=3)})


def run_with_strategy_file(csv_path: str, strategy_code: str, class_name: str = "",
                           max_stop_points: float = 0.0, point_size: float = 1.0,
                           progress_callback=None) -> str:
    """Streams a cached CSV file directly from disk to avoid multi-hundred-MB
    JVM/JNI string copies on large datasets (e.g. 222MB+ 1m exports)."""
    try:
        strategy_cls, err_msg = _compile_and_find_strategy(strategy_code, class_name)
        if err_msg or strategy_cls is None:
            return json.dumps({"error": err_msg or "Missing Strategy class", "details": ""})

        candles = load_candles(csv_path)
        max_stop = max_stop_points * point_size if max_stop_points > 0 else None
        trades, skipped, open_trades, total_candles = _run_backtest_detailed(
            candles,
            strategy_cls(),
            max_stop=max_stop,
            point_size=point_size,
            progress_callback=progress_callback,
        )
        return json.dumps(
            compute_stats(
                trades,
                skipped,
                point_size,
                open_trades=open_trades,
                total_candles=total_candles,
            )
        )
    except Exception as e:
        return json.dumps({"error": f"{type(e).__name__}: {e}",
                           "details": traceback.format_exc(limit=3)})


def run_with_strategy_file_slice(
    csv_path: str,
    strategy_code: str,
    class_name: str = "",
    max_stop_points: float = 0.0,
    point_size: float = 1.0,
    progress_callback=None,
    load_start: int = 0,
    load_end: int = -1,
    entry_start: int = 0,
    entry_end: int = -1,
) -> str:
    """Runs a strategy on a specific chronological shard [entry_start, entry_end) using a
    pre-warmed candle slice [load_start, load_end) seeked directly from .btbin. Recorded
    trade openIndex/closeIndex values preserve global CSV positions for replay."""
    try:
        strategy_cls, err_msg = _compile_and_find_strategy(strategy_code, class_name)
        if err_msg or strategy_cls is None:
            return json.dumps({"error": err_msg or "Missing Strategy class", "details": ""})

        candles, actual_load_start, full_total = load_candle_slice(csv_path, load_start, load_end)
        local_entry_start = max(0, int(entry_start) - actual_load_start)
        eff_global_end = full_total if entry_end < 0 else min(full_total, int(entry_end))
        local_entry_end = max(local_entry_start, eff_global_end - actual_load_start)

        max_stop = max_stop_points * point_size if max_stop_points > 0 else None
        trades, skipped, open_trades, owned_candles = _run_backtest_detailed(
            candles,
            strategy_cls(),
            max_stop=max_stop,
            point_size=point_size,
            progress_callback=progress_callback,
            global_offset=actual_load_start,
            local_entry_start=local_entry_start,
            local_entry_end=local_entry_end,
        )
        return json.dumps(
            compute_stats(
                trades,
                skipped,
                point_size,
                open_trades=open_trades,
                total_candles=owned_candles,
            )
        )
    except Exception as e:
        return json.dumps({"error": f"{type(e).__name__}: {e}",
                           "details": traceback.format_exc(limit=3)})


def _run_multi_backtest_detailed(
    candles: Sequence[Candle],
    strategy_specs: List[dict],
    warmup: int = 50,
    max_stop: Optional[float] = None,
    point_size: float = 1.0,
    progress_callback=None,
) -> List[dict]:
    """Runs all supplied strategies concurrently in a single pass over candles,
    keeping each strategy's positions, stats, and errors strictly isolated."""
    total_candles = len(candles)
    total_strategies = len(strategy_specs)
    be_eps = BE_EPS
    abs_fn = abs
    trade_cls = Trade
    has_progress = progress_callback is not None
    inv_point_size = 1.0 / point_size if point_size else 1.0
    mono = time.monotonic
    last_emit_at = mono() if has_progress else 0.0
    is_series = isinstance(candles, CandleSeries)

    slots: List[dict] = []
    active_slots: List[dict] = []

    for idx, spec in enumerate(strategy_specs):
        strat_id = spec.get("id", idx)
        strat_name = str(spec.get("name", f"Strategy {idx + 1}"))
        class_name = str(spec.get("className", "") or "")
        code = str(spec.get("code", "") or "")
        slot = {
            "id": strat_id,
            "name": strat_name,
            "className": class_name,
            "trades": [],
            "pos": None,
            "skipped": 0,
            "entered": 0,
            "wins": 0,
            "losses": 0,
            "be": 0,
            "running_pts": 0.0,
            "running_r": 0.0,
            "open_at_end": 0,
            "on_candle": None,
            "manage_fn": None,
            "active": False,
            "error": None,
            "details": "",
        }
        try:
            strategy_cls, err_msg = _compile_and_find_strategy(code, class_name)
            if err_msg or strategy_cls is None:
                slot["error"] = err_msg or "Missing Strategy class"
            else:
                strat_instance = strategy_cls()
                slot["className"] = class_name or strategy_cls.__name__
                slot["on_candle"] = strat_instance.on_candle
                slot["manage_fn"] = (
                    strat_instance.manage_trade
                    if type(strat_instance).manage_trade is not Strategy.manage_trade
                    else None
                )
                slot["active"] = True
                active_slots.append(slot)
        except Exception as strat_err:
            slot["error"] = f"{type(strat_err).__name__}: {strat_err}"
            slot["details"] = traceback.format_exc(limit=3)
        slots.append(slot)

    def push_progress(processed: int, current_time: str, finished: bool = False) -> None:
        if not has_progress:
            return
        agg_entered = 0
        agg_closed = 0
        agg_open = 0
        agg_wins = 0
        agg_losses = 0
        agg_be = 0
        agg_skipped = 0
        agg_pts = 0.0
        agg_r = 0.0
        statuses = []
        for s in slots:
            closed_n = len(s["trades"])
            open_n = 1 if s["pos"] is not None else 0
            agg_entered += s["entered"]
            agg_closed += closed_n
            agg_open += open_n
            agg_wins += s["wins"]
            agg_losses += s["losses"]
            agg_be += s["be"]
            agg_skipped += s["skipped"]
            agg_pts += s["running_pts"]
            agg_r += s["running_r"]
            if s["error"] is not None:
                status_label = "error"
            elif finished:
                status_label = "done"
            else:
                status_label = "running"
            statuses.append({
                "strategyId": s["id"],
                "name": s["name"],
                "closedTrades": closed_n,
                "openTrades": open_n,
                "wins": s["wins"],
                "losses": s["losses"],
                "breakEvens": s["be"],
                "netPoints": round(s["running_pts"], 2),
                "netR": round(s["running_r"], 2),
                "status": status_label,
                "error": s["error"] or "",
            })

        active_count = len(active_slots)
        _emit_progress(progress_callback, {
            "candlesProcessed": processed,
            "totalCandles": total_candles,
            "enteredTrades": agg_entered,
            "closedTrades": agg_closed,
            "openTrades": agg_open,
            "wins": agg_wins,
            "losses": agg_losses,
            "breakEvens": agg_be,
            "skippedByStopCap": agg_skipped,
            "netPoints": round(agg_pts, 2),
            "netR": round(agg_r, 2),
            "currentTime": current_time,
            "strategyIndex": active_count,
            "totalStrategies": total_strategies,
            "currentStrategyName": f"{active_count}/{total_strategies} active",
            "strategyStatuses": statuses,
        })

    if has_progress:
        first_time = (
            candles.time_at(0)
            if (is_series and total_candles > 0)
            else (candles[0].time if total_candles > 0 else "")
        )
        push_progress(0, first_time, finished=(total_candles == 0 or not active_slots))

    if active_slots and total_candles > 0:
        if is_series:
            opens = candles.opens
            highs = candles.highs
            lows = candles.lows
            closes = candles.closes
            time_at = candles.time_at
            emit_mask = 1023 if total_candles > 50_000 else 255

            for i in range(total_candles):
                if not active_slots:
                    break
                c_low = lows[i]
                c_high = highs[i]
                nxt_i = i + 1
                has_next = nxt_i < total_candles
                can_enter = i >= warmup and has_next
                c_time = None
                nxt_open = None
                nxt_time = None
                any_failed = False

                for slot in active_slots:
                    try:
                        pos = slot["pos"]
                        if pos is not None:
                            hit = None
                            if pos.side == "buy":
                                if c_low <= pos.sl:
                                    rzn = "be" if abs_fn(pos.sl - pos.entry) <= be_eps else "sl"
                                    hit = (pos.sl, rzn)
                                elif c_high >= pos.tp:
                                    hit = (pos.tp, "tp")
                            else:
                                if c_high >= pos.sl:
                                    rzn = "be" if abs_fn(pos.sl - pos.entry) <= be_eps else "sl"
                                    hit = (pos.sl, rzn)
                                elif c_low <= pos.tp:
                                    hit = (pos.tp, "tp")

                            manage_fn = slot["manage_fn"]
                            if hit is None and manage_fn is not None:
                                action = manage_fn(i, candles, pos)
                                if isinstance(action, str):
                                    act = action.strip().lower()
                                    if act in ("be", "breakeven", "break_even", "exit", "close"):
                                        exit_px = pos.entry if act in ("be", "breakeven", "break_even") else closes[i]
                                        hit_move = (exit_px - pos.entry) if pos.side == "buy" else (pos.entry - exit_px)
                                        rzn = "be" if abs_fn(hit_move) <= be_eps else "exit"
                                        hit = (exit_px, rzn)

                            if hit is not None:
                                exit_px, reason = hit
                                pos.exit = exit_px
                                pos.reason = reason
                                if c_time is None:
                                    c_time = time_at(i)
                                pos.close_time = c_time
                                pos.close_i = i
                                risk_sl = pos.initial_sl if pos.initial_sl is not None else pos.sl
                                risk = abs_fn(pos.entry - risk_sl)
                                move = (exit_px - pos.entry) if pos.side == "buy" else (pos.entry - exit_px)
                                r_val = move / risk if risk > 0.0 else 0.0
                                pos.r = r_val
                                if abs_fn(r_val) <= be_eps:
                                    pos.reason = "be"
                                    slot["be"] += 1
                                elif r_val > be_eps:
                                    slot["wins"] += 1
                                else:
                                    slot["losses"] += 1
                                slot["trades"].append(pos)
                                slot["running_pts"] += move * inv_point_size
                                slot["running_r"] += r_val
                                pos = None
                                slot["pos"] = None

                        if pos is None and can_enter:
                            sig = slot["on_candle"](i, candles)
                            if sig is not None:
                                if nxt_open is None:
                                    nxt_open = opens[nxt_i]
                                entry = nxt_open
                                valid = (
                                    (sig.sl < entry < sig.tp)
                                    if sig.side == "buy"
                                    else (sig.tp < entry < sig.sl)
                                )
                                if valid and max_stop is not None and abs_fn(entry - sig.sl) > max_stop:
                                    slot["skipped"] += 1
                                    valid = False
                                if valid:
                                    if nxt_time is None:
                                        nxt_time = time_at(nxt_i)
                                    slot["pos"] = trade_cls(
                                        sig.side,
                                        entry,
                                        sig.sl,
                                        sig.tp,
                                        nxt_time,
                                        open_i=nxt_i,
                                        initial_sl=sig.sl,
                                    )
                                    slot["entered"] += 1
                    except Exception as strat_err:
                        slot["error"] = f"{type(strat_err).__name__}: {strat_err}"
                        slot["details"] = traceback.format_exc(limit=3)
                        slot["active"] = False
                        slot["pos"] = None
                        any_failed = True

                if any_failed:
                    active_slots = [s for s in active_slots if s["active"]]

                if has_progress and (any_failed or (i & emit_mask) == emit_mask or i + 1 == total_candles or not active_slots):
                    now = mono()
                    if any_failed or i + 1 == total_candles or not active_slots or (now - last_emit_at) >= 0.15:
                        last_emit_at = now
                        if c_time is None:
                            c_time = time_at(i)
                        push_progress(
                            total_candles if (i + 1 == total_candles or not active_slots) else (i + 1),
                            c_time,
                            finished=(i + 1 == total_candles or not active_slots),
                        )
        else:
            for i, c in enumerate(candles):
                if not active_slots:
                    break
                nxt_i = i + 1
                has_next = nxt_i < total_candles
                can_enter = i >= warmup and has_next
                nxt_c = None
                any_failed = False

                for slot in active_slots:
                    try:
                        pos = slot["pos"]
                        if pos is not None:
                            hit = None
                            if pos.side == "buy":
                                if c.low <= pos.sl:
                                    rzn = "be" if abs_fn(pos.sl - pos.entry) <= be_eps else "sl"
                                    hit = (pos.sl, rzn)
                                elif c.high >= pos.tp:
                                    hit = (pos.tp, "tp")
                            else:
                                if c.high >= pos.sl:
                                    rzn = "be" if abs_fn(pos.sl - pos.entry) <= be_eps else "sl"
                                    hit = (pos.sl, rzn)
                                elif c.low <= pos.tp:
                                    hit = (pos.tp, "tp")

                            manage_fn = slot["manage_fn"]
                            if hit is None and manage_fn is not None:
                                action = manage_fn(i, candles, pos)
                                if isinstance(action, str):
                                    act = action.strip().lower()
                                    if act in ("be", "breakeven", "break_even", "exit", "close"):
                                        exit_px = pos.entry if act in ("be", "breakeven", "break_even") else c.close
                                        hit_move = (exit_px - pos.entry) if pos.side == "buy" else (pos.entry - exit_px)
                                        rzn = "be" if abs_fn(hit_move) <= be_eps else "exit"
                                        hit = (exit_px, rzn)

                            if hit is not None:
                                exit_px, reason = hit
                                pos.exit = exit_px
                                pos.reason = reason
                                pos.close_time = c.time
                                pos.close_i = i
                                risk_sl = pos.initial_sl if pos.initial_sl is not None else pos.sl
                                risk = abs_fn(pos.entry - risk_sl)
                                move = (exit_px - pos.entry) if pos.side == "buy" else (pos.entry - exit_px)
                                r_val = move / risk if risk > 0.0 else 0.0
                                pos.r = r_val
                                if abs_fn(r_val) <= be_eps:
                                    pos.reason = "be"
                                    slot["be"] += 1
                                elif r_val > be_eps:
                                    slot["wins"] += 1
                                else:
                                    slot["losses"] += 1
                                slot["trades"].append(pos)
                                slot["running_pts"] += move * inv_point_size
                                slot["running_r"] += r_val
                                pos = None
                                slot["pos"] = None

                        if pos is None and can_enter:
                            sig = slot["on_candle"](i, candles)
                            if sig is not None:
                                if nxt_c is None:
                                    nxt_c = candles[nxt_i]
                                entry = nxt_c.open
                                valid = (
                                    (sig.sl < entry < sig.tp)
                                    if sig.side == "buy"
                                    else (sig.tp < entry < sig.sl)
                                )
                                if valid and max_stop is not None and abs_fn(entry - sig.sl) > max_stop:
                                    slot["skipped"] += 1
                                    valid = False
                                if valid:
                                    slot["pos"] = trade_cls(
                                        sig.side,
                                        entry,
                                        sig.sl,
                                        sig.tp,
                                        nxt_c.time,
                                        open_i=nxt_i,
                                        initial_sl=sig.sl,
                                    )
                                    slot["entered"] += 1
                    except Exception as strat_err:
                        slot["error"] = f"{type(strat_err).__name__}: {strat_err}"
                        slot["details"] = traceback.format_exc(limit=3)
                        slot["active"] = False
                        slot["pos"] = None
                        any_failed = True

                if any_failed:
                    active_slots = [s for s in active_slots if s["active"]]

                if has_progress and (any_failed or (i & 255) == 255 or i + 1 == total_candles or not active_slots):
                    now = mono()
                    if any_failed or i + 1 == total_candles or not active_slots or (now - last_emit_at) >= 0.12:
                        last_emit_at = now
                        push_progress(
                            total_candles if (i + 1 == total_candles or not active_slots) else (i + 1),
                            c.time,
                            finished=(i + 1 == total_candles or not active_slots),
                        )

    for slot in slots:
        slot["open_at_end"] = 1 if slot["pos"] is not None else 0
    return slots


def run_with_multiple_strategies_file(
    csv_path: str,
    strategies_json: str,
    max_stop_points: float = 0.0,
    point_size: float = 1.0,
    progress_callback=None,
) -> str:
    """Loads candles once from csv_path and runs all strategies simultaneously in
    a single pass, returning a JSON batch payload with per-strategy stats or isolated errors."""
    try:
        strategy_specs = json.loads(strategies_json)
        if not isinstance(strategy_specs, list) or not strategy_specs:
            return json.dumps({"error": "No strategies supplied for batch backtest.", "details": ""})

        candles = load_candles(csv_path)
        total_candles = len(candles)
        max_stop = max_stop_points * point_size if max_stop_points > 0 else None

        slots = _run_multi_backtest_detailed(
            candles=candles,
            strategy_specs=strategy_specs,
            max_stop=max_stop,
            point_size=point_size,
            progress_callback=progress_callback,
        )

        results = []
        for slot in slots:
            if slot["error"] is not None:
                results.append({
                    "id": slot["id"],
                    "name": slot["name"],
                    "className": slot["className"],
                    "error": slot["error"],
                    "details": slot["details"],
                })
            else:
                stats = compute_stats(
                    slot["trades"],
                    slot["skipped"],
                    point_size,
                    open_trades=slot["open_at_end"],
                    total_candles=total_candles,
                )
                results.append({
                    "id": slot["id"],
                    "name": slot["name"],
                    "className": slot["className"],
                    "result": stats,
                })

        return json.dumps({
            "totalCandles": total_candles,
            "results": results,
        })
    except Exception as e:
        return json.dumps({
            "error": f"{type(e).__name__}: {e}",
            "details": traceback.format_exc(limit=3),
        })


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
