"""
Institutional Tri-Horizon Strategy (.py)
========================================
Combines all three trading horizons inside one offline Chaquopy / Python 3.11
strategy module compatible with `backtest.py`:
  1. SCALP       (minutes to ~2h): Kill-zone liquidity sweeps, Asian/PDH/PDL sweeps,
                                   Institutional Price Levels (00/20/50/80),
                                   Modified MACD (6,12,1) cross + Pin Bar / RRT.
  2. HOLD HOURS  (4h to ~20h):     "London Express" & NY continuation setups from
                                   H1 Order Blocks / FVGs / Breakers + OTE (62-79%)
                                   aligned with 25 SMA, 240 LWMA & MACD (12,26,9);
                                   managed via 1:1 break-even + ATR trailing stop.
  3. HOLD DAYS   (multi-day):      HTF H4/Daily BOS & CHoCH + widening Daily 18/40
                                   EMA stack + Deep OTE Discount/Premium H4/Daily OB;
                                   "sit on your hands" wide structural stop until
                                   +1.5R BE lock, then slow H4/D1 ATR runner trail.

Textbook Synthesis:
  - Mason Anderson       : Market Structure (Major vs Multiplex BOS/CHoCH),
                           Supply/Demand, Liquidity, Premium/Discount & Chart Patterns.
  - James Jecool King    : Smart Money Concepts — Sponsor/Order Blocks (50% MTH),
                           Breaker Blocks, FVG Imbalances, OTE (62-79%), Judas Swing,
                           Turtle Soup, Asian & CBDR Flout ranges, Daily 18/40 EMA,
                           5-Day ADR exhaustion & London Close reversals.
  - Jim Brown            : 25 SMA + 240 LWMA trend baseline, Modified MACD (6,12,1)
                           momentum triggers, Standard MACD (12,26,9) alignment, and
                           ATR Stop trailing / zero-cross management.
  - Raymond M. Smith     : Institutional Sponsor Candles, PDH/PDL liquidity targets,
                           unmitigated imbalance entries & multi-timeframe flow.
  - Dave Matias          : Top-down structural execution, Institutional Price Levels
                           (00/20/50/80), 1:1 break-even rule & patient "sit on your
                           hands" swing runner management.

Classes inside this file:
  - InstitutionalTriHorizonStrategy  (Default AUTO: dynamically takes Scalp, Hours, & Days)
  - ScalpKillZoneStrategy            (Locked to Tier 1 Scalp mode)
  - IntradayLondonExpressStrategy    (Locked to Tier 2 Hold-for-Hours mode)
  - SwingMultiDayOrderBlockStrategy  (Locked to Tier 3 Hold-for-Days mode)
"""

from backtest import Strategy, Signal
from collections import deque
from dataclasses import dataclass
from datetime import datetime, timedelta
import math
from typing import Deque, List, Optional, Tuple


# MetaTrader 5 / broker server offset vs GMT/UTC (2 = standard EET server time; 0 = UTC)
SERVER_GMT_OFFSET = 2


@dataclass(slots=True)
class _Bar:
    open: float
    high: float
    low: float
    close: float
    start_min: int
    end_idx: int


@dataclass(slots=True)
class _Zone:
    low: float
    high: float
    mid: float
    is_bull: bool
    is_breaker: bool
    tf: str
    created_i: int
    touches: int = 0


@dataclass(slots=True)
class _FVG:
    low: float
    high: float
    is_bull: bool
    tf: str
    created_i: int


class _EMA:
    __slots__ = ("period", "alpha", "value", "prev", "count", "_seed_sum")

    def __init__(self, period: int) -> None:
        self.period = max(1, int(period))
        self.alpha = 2.0 / (self.period + 1.0)
        self.value: Optional[float] = None
        self.prev: Optional[float] = None
        self.count = 0
        self._seed_sum = 0.0

    def update(self, price: float) -> Optional[float]:
        self.prev = self.value
        self.count += 1
        if self.count <= self.period:
            self._seed_sum += price
            if self.count == self.period:
                self.value = self._seed_sum / self.period
        else:
            self.value = self.alpha * price + (1.0 - self.alpha) * self.value
        return self.value


class _SMA:
    __slots__ = ("period", "buf", "total", "value")

    def __init__(self, period: int) -> None:
        self.period = max(1, int(period))
        self.buf: Deque[float] = deque(maxlen=self.period)
        self.total = 0.0
        self.value: Optional[float] = None

    def update(self, price: float) -> Optional[float]:
        if len(self.buf) == self.period:
            self.total -= self.buf[0]
        self.buf.append(price)
        self.total += price
        if len(self.buf) == self.period:
            self.value = self.total / self.period
        return self.value


class _LWMA:
    """Exact O(1) incremental Linear Weighted Moving Average (240 LMA from Jim Brown)."""

    __slots__ = ("period", "denom", "buf", "simple_sum", "weighted_sum", "steps", "value")

    def __init__(self, period: int) -> None:
        self.period = max(1, int(period))
        self.denom = self.period * (self.period + 1) * 0.5
        self.buf: Deque[float] = deque(maxlen=self.period)
        self.simple_sum = 0.0
        self.weighted_sum = 0.0
        self.steps = 0
        self.value: Optional[float] = None

    def update(self, price: float) -> Optional[float]:
        n = len(self.buf)
        if n < self.period:
            self.buf.append(price)
            self.simple_sum += price
            self.weighted_sum += (n + 1) * price
            if n + 1 == self.period:
                self.value = self.weighted_sum / self.denom
        else:
            oldest = self.buf[0]
            self.buf.append(price)
            # W_new = W_old - S_old + N * p_new; S_new = S_old - p_oldest + p_new
            self.weighted_sum = self.weighted_sum - self.simple_sum + self.period * price
            self.simple_sum = self.simple_sum - oldest + price
            self.steps += 1
            if (self.steps & 2047) == 0:
                # Re-sync floating point sums every 2048 bars
                self.simple_sum = sum(self.buf)
                self.weighted_sum = sum((idx + 1) * val for idx, val in enumerate(self.buf))
            self.value = self.weighted_sum / self.denom
        return self.value


class _ATR:
    __slots__ = ("period", "prev_close", "count", "_seed", "value")

    def __init__(self, period: int = 14) -> None:
        self.period = max(1, int(period))
        self.prev_close: Optional[float] = None
        self.count = 0
        self._seed = 0.0
        self.value: float = 0.0

    def update(self, high: float, low: float, close: float) -> float:
        if self.prev_close is None:
            tr = max(0.0, high - low)
        else:
            tr = max(
                high - low,
                abs(high - self.prev_close),
                abs(low - self.prev_close),
            )
        self.prev_close = close
        self.count += 1
        if self.count <= self.period:
            self._seed += tr
            self.value = self._seed / self.count
        else:
            self.value = ((self.value * (self.period - 1)) + tr) / self.period
        return self.value


class _StructureTracker:
    """Tracks causal 5-bar swing highs/lows, BOS/CHoCH, Order Blocks, Breakers, and FVGs."""

    __slots__ = (
        "tf",
        "window",
        "last_swing_high",
        "prev_swing_high",
        "last_swing_low",
        "prev_swing_low",
        "trend_bias",  # +1 bullish, -1 bearish, 0 neutral
        "range_low",
        "range_high",
        "obs",
        "fvgs",
        "last_bos_dir",
        "last_bos_idx",
        "last_choch_dir",
        "last_choch_idx",
    )

    def __init__(self, tf: str) -> None:
        self.tf = tf
        self.window: Deque[Tuple[float, float, float, float, int]] = deque(maxlen=8)
        self.last_swing_high: Optional[float] = None
        self.prev_swing_high: Optional[float] = None
        self.last_swing_low: Optional[float] = None
        self.prev_swing_low: Optional[float] = None
        self.trend_bias: int = 0
        self.range_low: Optional[float] = None
        self.range_high: Optional[float] = None
        self.obs: Deque[_Zone] = deque(maxlen=6)
        self.fvgs: Deque[_FVG] = deque(maxlen=8)
        self.last_bos_dir: int = 0
        self.last_bos_idx: int = -1000
        self.last_choch_dir: int = 0
        self.last_choch_idx: int = -1000

    def update(self, o: float, h: float, l: float, c: float, idx: int, atr: float) -> None:
        self.window.append((o, h, l, c, idx))
        w_len = len(self.window)

        # 1. Mitigate / flip broken Order Blocks & fill touched FVGs on closed bar
        new_obs: Deque[_Zone] = deque(maxlen=6)
        for z in self.obs:
            if z.is_bull:
                if c < z.low:
                    # Bullish OB cleanly broken -> becomes Bearish Breaker Block (King SMC)
                    if not z.is_breaker:
                        new_obs.append(_Zone(z.low, z.high, z.mid, False, True, z.tf, idx, 0))
                    continue
                if l <= z.high:
                    z.touches += 1
                    if z.touches <= 3:
                        new_obs.append(z)
                else:
                    new_obs.append(z)
            else:
                if c > z.high:
                    # Bearish OB cleanly broken -> becomes Bullish Breaker Block (King SMC)
                    if not z.is_breaker:
                        new_obs.append(_Zone(z.low, z.high, z.mid, True, True, z.tf, idx, 0))
                    continue
                if h >= z.low:
                    z.touches += 1
                    if z.touches <= 3:
                        new_obs.append(z)
                else:
                    new_obs.append(z)
        self.obs = new_obs

        active_fvgs: Deque[_FVG] = deque(maxlen=8)
        for gap in self.fvgs:
            if gap.is_bull:
                # Invalidate once closed completely below FVG low
                if c >= gap.low:
                    active_fvgs.append(gap)
            else:
                if c <= gap.high:
                    active_fvgs.append(gap)
        self.fvgs = active_fvgs

        # 2. Causal 5-bar fractal pivot confirmation at w[-3] (zero lookahead!)
        if w_len >= 5:
            p_o, p_h, p_l, p_c, p_idx = self.window[-3]
            h0 = self.window[-5][1]
            h1 = self.window[-4][1]
            h3 = self.window[-2][1]
            h4 = self.window[-1][1]
            if p_h >= h0 and p_h >= h1 and p_h > h3 and p_h >= h4:
                self.prev_swing_high = self.last_swing_high
                self.last_swing_high = p_h
                if self.last_swing_low is not None:
                    self.range_low = self.last_swing_low
                    self.range_high = p_h

            l0 = self.window[-5][2]
            l1 = self.window[-4][2]
            l3 = self.window[-2][2]
            l4 = self.window[-1][2]
            if p_l <= l0 and p_l <= l1 and p_l < l3 and p_l <= l4:
                self.prev_swing_low = self.last_swing_low
                self.last_swing_low = p_l
                if self.last_swing_high is not None:
                    self.range_low = p_l
                    self.range_high = self.last_swing_high

        # 3. 3-Candle FVG / Imbalance detection
        created_bull_fvg = False
        created_bear_fvg = False
        if w_len >= 3 and atr > 0.0:
            c0 = self.window[-3]  # [o, h, l, c, idx]
            c1 = self.window[-2]
            c2 = self.window[-1]
            mid_body = abs(c1[3] - c1[0])
            if c2[2] > c0[1] and (c2[2] - c0[1]) >= 0.08 * atr and mid_body >= 0.35 * atr:
                self.fvgs.append(_FVG(c0[1], c2[2], True, self.tf, idx))
                created_bull_fvg = True
            elif c2[1] < c0[2] and (c0[2] - c2[1]) >= 0.08 * atr and mid_body >= 0.35 * atr:
                self.fvgs.append(_FVG(c2[1], c0[2], False, self.tf, idx))
                created_bear_fvg = True

        # 4. BOS / CHoCH + Sponsor / Order Block capture (King / Smith / Anderson)
        bull_break = self.last_swing_high is not None and c > self.last_swing_high
        bear_break = self.last_swing_low is not None and c < self.last_swing_low

        if bull_break and (idx - self.last_bos_idx > 2 or self.last_bos_dir != 1):
            if self.trend_bias == -1:
                self.last_choch_dir = 1
                self.last_choch_idx = idx
            else:
                self.last_bos_dir = 1
                self.last_bos_idx = idx
            self.trend_bias = 1
            if self.last_swing_low is not None:
                self.range_low = self.last_swing_low
                self.range_high = max(h, self.last_swing_high)
            self._capture_order_block(is_bull=True, created_idx=idx)
            self.prev_swing_high = self.last_swing_high
            self.last_swing_high = h
        elif bear_break and (idx - self.last_bos_idx > 2 or self.last_bos_dir != -1):
            if self.trend_bias == 1:
                self.last_choch_dir = -1
                self.last_choch_idx = idx
            else:
                self.last_bos_dir = -1
                self.last_bos_idx = idx
            self.trend_bias = -1
            if self.last_swing_high is not None:
                self.range_high = self.last_swing_high
                self.range_low = min(l, self.last_swing_low)
            self._capture_order_block(is_bull=False, created_idx=idx)
            self.prev_swing_low = self.last_swing_low
            self.last_swing_low = l
        elif created_bull_fvg:
            self._capture_order_block(is_bull=True, created_idx=idx)
        elif created_bear_fvg:
            self._capture_order_block(is_bull=False, created_idx=idx)

    def _capture_order_block(self, is_bull: bool, created_idx: int) -> None:
        # Last opposite-close candle inside recent impulse origin
        bars = list(self.window)
        for bo, bh, bl, bc, _ in reversed(bars[:-1]):
            if is_bull and bc < bo:
                self.obs.append(_Zone(bl, bh, 0.5 * (bl + bh), True, False, self.tf, created_idx, 0))
                return
            if (not is_bull) and bc > bo:
                self.obs.append(_Zone(bl, bh, 0.5 * (bl + bh), False, False, self.tf, created_idx, 0))
                return


class InstitutionalTriHorizonStrategy(Strategy):
    """
    Unified Institutional Smart Money & Multi-Timeframe Strategy covering
    Scalping, Holding for Hours, and Holding for Days on End.

    Public parameters in __init__ can be tuned or run through the app's Optimizer:
      horizon_profile    : 0 = Auto (All 3 Horizons), 1 = Scalp, 2 = Hours, 3 = Days
      server_gmt_offset  : Broker server offset vs GMT (2 for standard MT5 EET, 0 for UTC)
      atr_period         : ATR lookback period (default 14)
      scalp_rr           : Target R multiple for Tier 1 Scalps (default 1.8)
      intraday_rr        : Target R multiple for Tier 2 Hold-for-Hours (default 3.0)
      swing_rr           : Target R multiple for Tier 3 Hold-for-Days (default 5.0)
      be_trigger_r       : Break-even lock trigger for Intraday (default 1.0R)
      min_confluence     : Minimum textbook confluence score required (default 3)
    """

    def __init__(self) -> None:
        # Public optimizable parameters
        self.horizon_profile: int = 0
        self.server_gmt_offset: int = SERVER_GMT_OFFSET
        self.atr_period: int = 14
        self.scalp_rr: float = 1.8
        self.intraday_rr: float = 3.0
        self.swing_rr: float = 5.0
        self.be_trigger_r: float = 1.0
        self.min_confluence: int = 3
        self.require_displacement_confirmation: bool = True
        self.require_rejection_confirmation: bool = True
        self.displacement_atr: float = 0.55
        self.rejection_close_fraction: float = 0.68
        self.ote_retracement_min: float = 0.62
        self.ote_retracement_max: float = 0.79
        self.last_entry_diagnostics: dict = {}
        self.last_management_diagnostics: dict = {}

        # Catch-up & clock state (Rule 9: never miss candles skipped while trade is open)
        self._last_processed_index: int = -1
        self._epoch_origin = datetime(1970, 1, 1)
        self._last_dt: Optional[datetime] = None
        self._last_gmt_min: Optional[int] = None
        self._recent_deltas: Deque[int] = deque(maxlen=25)
        self._bar_minutes: int = 15

        # Base-chart incremental indicators
        self._atr_base = _ATR(14)
        self._sma25 = _SMA(25)
        self._lwma240 = _LWMA(240)
        self._ema6 = _EMA(6)
        self._ema12 = _EMA(12)
        self._ema26 = _EMA(26)
        self._mod_macd: float = 0.0
        self._prev_mod_macd: float = 0.0
        self._std_macd: float = 0.0
        self._std_signal = _EMA(9)
        self._std_hist: float = 0.0
        self._prev_std_hist: float = 0.0

        # Rolling base bars for Price Action & structural lookbacks (bounded)
        self._ltf_bars: Deque[Tuple[float, float, float, float, int]] = deque(maxlen=32)
        self._ltf_struct = _StructureTracker("LTF")

        # Multi-timeframe aggregators & structures (H1, H4, Daily)
        self._h1_bucket: Optional[int] = None
        self._h1_cur: Optional[List[float]] = None  # [o, h, l, c, start_min]
        self._h1_atr = _ATR(14)
        self._h1_struct = _StructureTracker("H1")

        self._h4_bucket: Optional[int] = None
        self._h4_cur: Optional[List[float]] = None
        self._h4_atr = _ATR(14)
        self._h4_struct = _StructureTracker("H4")

        self._d1_bucket: Optional[int] = None
        self._d1_cur: Optional[List[float]] = None
        self._d1_atr = _ATR(14)
        self._d1_ema18 = _EMA(18)
        self._d1_ema40 = _EMA(40)
        self._d1_sep: float = 0.0
        self._d1_prev_sep: float = 0.0
        self._d1_widening: bool = False
        self._d1_ranges: Deque[float] = deque(maxlen=5)
        self._adr5: float = 0.0
        self._d1_struct = _StructureTracker("D1")

        # Daily liquidity & session levels (reset each GMT calendar day)
        self._cur_day_ord: Optional[int] = None
        self._prev_day_high: Optional[float] = None
        self._prev_day_low: Optional[float] = None
        self._today_high: Optional[float] = None
        self._today_low: Optional[float] = None
        self._pdh_swept_today: bool = False
        self._pdl_swept_today: bool = False

        # Asian Range (00:00-05:00 GMT) & CBDR Flout (20:00-00:00 GMT)
        self._asia_high: Optional[float] = None
        self._asia_low: Optional[float] = None
        self._asia_done: bool = False
        self._asia_high_swept: bool = False
        self._asia_low_swept: bool = False
        self._cbdr_high: Optional[float] = None
        self._cbdr_low: Optional[float] = None

        # Active & pending trade horizon tracking
        self._pending_meta: Optional[dict] = None
        self._active_trade_key: Optional[Tuple[int, str]] = None
        self._active_meta: Optional[dict] = None
        self._prev_close_i: int = -1000
        self._bar_atr_period_synced: int = 14

    # ---------- Time Parsing & Multi-Timeframe Synchronization ----------

    def _parse_gmt_datetime(self, raw_time: str, idx: int) -> Tuple[datetime, int, bool]:
        s = raw_time.strip()
        dt: Optional[datetime] = None
        n = len(s)
        # Fast-path for standard MT5/ISO strings ("YYYY.MM.DD HH:MM:SS" / "YYYY-MM-DD HH:MM")
        if n >= 16 and (s[4] == "." or s[4] == "-" or s[4] == "/") and (s[10] == " " or s[10] == "T"):
            try:
                yr = int(s[0:4])
                mo = int(s[5:7])
                dy = int(s[8:10])
                hr = int(s[11:13])
                mn = int(s[14:16])
                sc = int(s[17:19]) if n >= 19 and s[16] == ":" else 0
                dt = datetime(yr, mo, dy, hr, mn, sc)
            except ValueError:
                dt = None
        elif n == 10 and (s[4] == "." or s[4] == "-" or s[4] == "/"):
            try:
                dt = datetime(int(s[0:4]), int(s[5:7]), int(s[8:10]))
            except ValueError:
                dt = None

        if dt is None:
            for fmt in (
                "%Y.%m.%d %H:%M:%S",
                "%Y-%m-%d %H:%M:%S",
                "%Y.%m.%d %H:%M",
                "%Y-%m-%d %H:%M",
                "%Y/%m/%d %H:%M:%S",
                "%Y.%m.%d",
                "%Y-%m-%d",
            ):
                try:
                    dt = datetime.strptime(s, fmt)
                    break
                except ValueError:
                    pass

        if dt is None:
            # Synthetic fallback for headless/demo strings (assume 15m progression)
            synthetic = datetime(2025, 1, 1) + timedelta(minutes=idx * 15)
            gmt_min = int((synthetic - self._epoch_origin).total_seconds() // 60)
            return synthetic, gmt_min, False

        gmt_dt = dt - timedelta(hours=int(self.server_gmt_offset))
        gmt_min = int((gmt_dt - self._epoch_origin).total_seconds() // 60)
        return gmt_dt, gmt_min, True

    def _finalize_htf_bar(self, stream: str, bar_arr: List[float], idx: int) -> None:
        o, h, l, c, start_min = bar_arr
        if stream == "H1":
            atr_val = self._h1_atr.update(h, l, c)
            self._h1_struct.update(o, h, l, c, idx, atr_val)
        elif stream == "H4":
            atr_val = self._h4_atr.update(h, l, c)
            self._h4_struct.update(o, h, l, c, idx, atr_val)
        elif stream == "D1":
            atr_val = self._d1_atr.update(h, l, c)
            e18 = self._d1_ema18.update(c)
            e40 = self._d1_ema40.update(c)
            if e18 is not None and e40 is not None:
                self._d1_prev_sep = self._d1_sep
                self._d1_sep = abs(e18 - e40)
                self._d1_widening = self._d1_sep > self._d1_prev_sep
            self._d1_ranges.append(max(0.0, h - l))
            self._adr5 = sum(self._d1_ranges) / len(self._d1_ranges)
            self._d1_struct.update(o, h, l, c, idx, atr_val)

    def _update_htf_streams(self, gmt_dt: datetime, gmt_min: int, o: float, h: float, l: float, c: float, idx: int) -> None:
        # H1 stream
        h1_b = gmt_min // 60
        if self._h1_bucket is None:
            self._h1_bucket = h1_b
            self._h1_cur = [o, h, l, c, float(gmt_min)]
        elif h1_b != self._h1_bucket and self._h1_cur is not None:
            self._finalize_htf_bar("H1", self._h1_cur, idx - 1)
            self._h1_bucket = h1_b
            self._h1_cur = [o, h, l, c, float(gmt_min)]
        elif self._h1_cur is not None:
            if h > self._h1_cur[1]:
                self._h1_cur[1] = h
            if l < self._h1_cur[2]:
                self._h1_cur[2] = l
            self._h1_cur[3] = c

        # H4 stream
        h4_b = gmt_min // 240
        if self._h4_bucket is None:
            self._h4_bucket = h4_b
            self._h4_cur = [o, h, l, c, float(gmt_min)]
        elif h4_b != self._h4_bucket and self._h4_cur is not None:
            self._finalize_htf_bar("H4", self._h4_cur, idx - 1)
            self._h4_bucket = h4_b
            self._h4_cur = [o, h, l, c, float(gmt_min)]
        elif self._h4_cur is not None:
            if h > self._h4_cur[1]:
                self._h4_cur[1] = h
            if l < self._h4_cur[2]:
                self._h4_cur[2] = l
            self._h4_cur[3] = c

        # Daily stream (GMT trading day)
        d_ord = gmt_dt.date().toordinal()
        if self._d1_bucket is None:
            self._d1_bucket = d_ord
            self._d1_cur = [o, h, l, c, float(gmt_min)]
        elif d_ord != self._d1_bucket and self._d1_cur is not None:
            self._finalize_htf_bar("D1", self._d1_cur, idx - 1)
            self._d1_bucket = d_ord
            self._d1_cur = [o, h, l, c, float(gmt_min)]
        elif self._d1_cur is not None:
            if h > self._d1_cur[1]:
                self._d1_cur[1] = h
            if l < self._d1_cur[2]:
                self._d1_cur[2] = l
            self._d1_cur[3] = c

    def _update_session_liquidity(self, gmt_dt: datetime, h: float, l: float, c: float) -> None:
        day_ord = gmt_dt.date().toordinal()
        hour = gmt_dt.hour

        if self._cur_day_ord is None:
            self._cur_day_ord = day_ord
            self._today_high = h
            self._today_low = l
        elif day_ord != self._cur_day_ord:
            self._prev_day_high = self._today_high
            self._prev_day_low = self._today_low
            self._cur_day_ord = day_ord
            self._today_high = h
            self._today_low = l
            self._pdh_swept_today = False
            self._pdl_swept_today = False
            self._asia_high = None
            self._asia_low = None
            self._asia_done = False
            self._asia_high_swept = False
            self._asia_low_swept = False
        else:
            if self._today_high is None or h > self._today_high:
                self._today_high = h
            if self._today_low is None or l < self._today_low:
                self._today_low = l

        # CBDR Flout Range (20:00 - 00:00 GMT)
        if 20 <= hour < 24:
            if hour == 20 and gmt_dt.minute == 0:
                self._cbdr_high = h
                self._cbdr_low = l
            else:
                self._cbdr_high = h if self._cbdr_high is None else max(self._cbdr_high, h)
                self._cbdr_low = l if self._cbdr_low is None else min(self._cbdr_low, l)

        # Asian Range (00:00 - 05:00 GMT)
        if 0 <= hour < 5:
            self._asia_high = h if self._asia_high is None else max(self._asia_high, h)
            self._asia_low = l if self._asia_low is None else min(self._asia_low, l)
        elif hour >= 5:
            self._asia_done = True

        # Track liquidity sweeps on closed bar
        if self._prev_day_high is not None and h > self._prev_day_high and c < self._prev_day_high:
            self._pdh_swept_today = True
        if self._prev_day_low is not None and l < self._prev_day_low and c > self._prev_day_low:
            self._pdl_swept_today = True
        if self._asia_done and self._asia_high is not None and h > self._asia_high and c < self._asia_high:
            self._asia_high_swept = True
        if self._asia_done and self._asia_low is not None and l < self._asia_low and c > self._asia_low:
            self._asia_low_swept = True

    def _sync_to(self, target_idx: int, candles) -> Tuple[datetime, int, bool]:
        if int(self.atr_period) != self._bar_atr_period_synced and self._last_processed_index < 0:
            self._atr_base = _ATR(int(self.atr_period))
            self._bar_atr_period_synced = int(self.atr_period)

        last_parsed = True
        last_dt = self._last_dt or datetime(2025, 1, 1)
        last_min = self._last_gmt_min or 0

        for k in range(self._last_processed_index + 1, target_idx + 1):
            candle = candles[k]
            o = candle.open
            h = candle.high
            l = candle.low
            c = candle.close

            gmt_dt, gmt_min, parsed = self._parse_gmt_datetime(candle.time, k)
            if self._last_gmt_min is not None and parsed:
                delta = gmt_min - self._last_gmt_min
                if 0 < delta <= 1440:
                    self._recent_deltas.append(delta)
                    if len(self._recent_deltas) >= 5:
                        sorted_d = sorted(self._recent_deltas)
                        self._bar_minutes = sorted_d[len(sorted_d) // 2]

            self._last_dt = gmt_dt
            self._last_gmt_min = gmt_min
            last_dt, last_min, last_parsed = gmt_dt, gmt_min, parsed

            # Base-timeframe indicators
            atr_val = self._atr_base.update(h, l, c)
            self._sma25.update(c)
            self._lwma240.update(c)

            e6 = self._ema6.update(c)
            e12 = self._ema12.update(c)
            e26 = self._ema26.update(c)
            if e6 is not None and e12 is not None:
                self._prev_mod_macd = self._mod_macd
                self._mod_macd = e6 - e12
            if e12 is not None and e26 is not None:
                self._std_macd = e12 - e26
                sig_val = self._std_signal.update(self._std_macd)
                if sig_val is not None:
                    self._prev_std_hist = self._std_hist
                    self._std_hist = self._std_macd - sig_val

            self._ltf_bars.append((o, h, l, c, k))
            self._ltf_struct.update(o, h, l, c, k, atr_val)
            self._update_htf_streams(gmt_dt, gmt_min, o, h, l, c, k)
            self._update_session_liquidity(gmt_dt, h, l, c)

        self._last_processed_index = target_idx
        return last_dt, last_min, last_parsed

    # ---------- Textbook Pattern & Confluence Detectors ----------

    def _near_institutional_level(self, price: float, atr: float) -> bool:
        """Detects proximity to Institutional Price Levels (.00 / .20 / .50 / .80 handles)."""
        if atr <= 0.0:
            return False
        tol = 0.35 * atr
        # Choose a clean decimal decade around ~12x ATR
        probe = max(1e-5, atr * 12.0)
        unit = 10.0 ** math.floor(math.log10(probe))
        # Normalized fraction inside institutional figure [0.0, 1.0)
        frac = (price / unit) % 1.0
        for lvl in (0.0, 0.2, 0.5, 0.8, 1.0):
            if abs(frac - lvl) * unit <= tol:
                return True
        return False

    def _price_action_trigger(self, atr: float) -> Tuple[bool, bool, bool, bool]:
        """Returns (bull_pa, bear_pa, mod_macd_bull_cross, mod_macd_bear_cross)."""
        bull_cross = self._prev_mod_macd <= 0.0 < self._mod_macd
        bear_cross = self._prev_mod_macd >= 0.0 > self._mod_macd
        if len(self._ltf_bars) < 2 or atr <= 0.0:
            return False, False, bull_cross, bear_cross

        o1, h1, l1, c1, _ = self._ltf_bars[-2]
        o2, h2, l2, c2, _ = self._ltf_bars[-1]
        rng2 = max(1e-9, h2 - l2)
        body2 = abs(c2 - o2)
        body1 = abs(c1 - o1)
        upper_wick = h2 - max(o2, c2)
        lower_wick = min(o2, c2) - l2

        # Pin bar / Hammer / Shooting Star (Anderson / King)
        bull_pin = (
            lower_wick >= max(1.8 * max(body2, 0.05 * atr), 0.45 * rng2)
            and upper_wick <= 0.30 * rng2
            and c2 >= l2 + 0.55 * rng2
        )
        bear_pin = (
            upper_wick >= max(1.8 * max(body2, 0.05 * atr), 0.45 * rng2)
            and lower_wick <= 0.30 * rng2
            and c2 <= h2 - 0.55 * rng2
        )

        # Rail Road Tracks (RRT) / Engulfing reversal
        bull_rrt = (
            c1 < o1
            and c2 > o2
            and body1 >= 0.25 * atr
            and body2 >= 0.85 * body1
            and c2 >= o1
        )
        bear_rrt = (
            c1 > o1
            and c2 < o2
            and body1 >= 0.25 * atr
            and body2 >= 0.85 * body1
            and c2 <= o1
        )

        # Tweezer Bottom / Top
        tol = 0.15 * atr
        bull_tweezer = c1 < o1 and c2 > o2 and abs(l1 - l2) <= tol
        bear_tweezer = c1 > o1 and c2 < o2 and abs(h1 - h2) <= tol

        bull_pa = bull_pin or bull_rrt or bull_tweezer
        bear_pa = bear_pin or bear_rrt or bear_tweezer
        return bull_pa, bear_pa, bull_cross, bear_cross

    def _has_recent_displacement(self, side: str, atr: float) -> bool:
        if atr <= 0.0 or not self._ltf_bars:
            return False
        threshold = max(0.1, float(self.displacement_atr)) * atr
        close_fraction = min(0.95, max(0.55, float(self.rejection_close_fraction)))
        for o, h, l, c, _ in list(self._ltf_bars)[-4:]:
            candle_range = max(1e-9, h - l)
            body = abs(c - o)
            if side == "buy":
                if c > o and body >= threshold and (c - l) / candle_range >= close_fraction:
                    return True
            else:
                if c < o and body >= threshold and (h - c) / candle_range >= close_fraction:
                    return True
        return False

    def _entry_confirmation(self, side: str, atr: float, price_action: bool, swept: bool) -> bool:
        direction = 1 if side == "buy" else -1
        idx = self._ltf_bars[-1][4] if self._ltf_bars else -1
        micro_shift = (
            self._ltf_struct.last_choch_dir == direction
            and 0 <= idx - self._ltf_struct.last_choch_idx <= 5
        ) or (
            self._ltf_struct.last_bos_dir == direction
            and 0 <= idx - self._ltf_struct.last_bos_idx <= 5
        )
        displacement_ok = not self.require_displacement_confirmation or self._has_recent_displacement(side, atr)
        rejection_ok = not self.require_rejection_confirmation or price_action or micro_shift or swept
        self.last_entry_diagnostics = {
            "side": side,
            "displacement": displacement_ok,
            "rejection_or_structure": rejection_ok,
            "sweep": bool(swept),
            "micro_shift": bool(micro_shift),
        }
        return displacement_ok and rejection_ok

    def _confirmed_reversal(self, side: str, atr: float) -> Tuple[bool, dict]:
        opposite = "sell" if side == "buy" else "buy"
        direction = -1 if side == "buy" else 1
        idx = self._ltf_bars[-1][4] if self._ltf_bars else -1
        displacement = False
        if self._ltf_bars and atr > 0.0:
            o, h, l, c, _ = self._ltf_bars[-1]
            candle_range = max(1e-9, h - l)
            body = abs(c - o)
            if side == "buy":
                displacement = c < o and body >= 0.65 * atr and (h - c) / candle_range >= 0.65
            else:
                displacement = c > o and body >= 0.65 * atr and (c - l) / candle_range >= 0.65
        choch = (
            self._ltf_struct.last_choch_dir == direction
            and 0 <= idx - self._ltf_struct.last_choch_idx <= 5
        )
        macd_reversal = (
            (self._prev_mod_macd >= 0.0 > self._mod_macd) if side == "buy"
            else (self._prev_mod_macd <= 0.0 < self._mod_macd)
        )
        macd_softening = (
            self._std_hist < self._prev_std_hist and self._mod_macd < self._prev_mod_macd
            if side == "buy"
            else self._std_hist > self._prev_std_hist and self._mod_macd > self._prev_mod_macd
        )
        macd = macd_reversal or macd_softening
        sweep, _ = self._sweep_detected(opposite, lookback=8)
        evidence = {
            "opposite_displacement": bool(displacement),
            "micro_choch": bool(choch),
            "macd_reversal_or_deterioration": bool(macd),
            "opposite_liquidity_sweep": bool(sweep),
        }
        return sum(evidence.values()) >= 2, evidence

    def _check_ote_zone(self, tracker: _StructureTracker, price: float, side: str) -> Tuple[bool, bool]:
        rl = tracker.range_low
        rh = tracker.range_high
        if rl is None or rh is None or rh <= rl:
            return True, False
        pos = (price - rl) / (rh - rl)
        retracement_min = min(0.95, max(0.0, float(self.ote_retracement_min)))
        retracement_max = min(0.99, max(retracement_min, float(self.ote_retracement_max)))
        if side == "buy":
            in_half = pos <= 0.52
            in_ote = (1.0 - retracement_max) <= pos <= (1.0 - retracement_min)
            return in_half, in_ote
        in_half = pos >= 0.48
        in_ote = retracement_min <= pos <= retracement_max
        return in_half, in_ote

    def _in_zone(self, zones: Deque[_Zone], low: float, high: float, close: float, is_bull: bool, atr: float) -> Tuple[bool, bool, Optional[_Zone]]:
        """Returns (touched_ob_or_breaker, is_breaker_only, matched_zone)."""
        pad = 0.15 * atr
        for z in reversed(zones):
            if z.is_bull != is_bull:
                continue
            if is_bull and low <= z.high + pad and close >= z.low - 0.25 * atr:
                return True, z.is_breaker, z
            if (not is_bull) and high >= z.low - pad and close <= z.high + 0.25 * atr:
                return True, z.is_breaker, z
        return False, False, None

    def _in_fvg(self, fvgs: Deque[_FVG], low: float, high: float, close: float, is_bull: bool) -> bool:
        for gap in reversed(fvgs):
            if gap.is_bull != is_bull:
                continue
            if is_bull and low <= gap.high and close >= gap.low:
                return True
            if (not is_bull) and high >= gap.low and close <= gap.high:
                return True
        return False

    def _sweep_detected(self, side: str, lookback: int = 16) -> Tuple[bool, Optional[float]]:
        """Turtle Soup / Judas liquidity sweep of prior swing pool, Asian Range, or PDH/PDL."""
        if len(self._ltf_bars) < lookback + 2:
            return False, None
        bars = list(self._ltf_bars)
        curr_o, curr_h, curr_l, curr_c, _ = bars[-1]
        prev_bars = bars[-(lookback + 1):-1]
        prior_low = min(b[2] for b in prev_bars)
        prior_high = max(b[1] for b in prev_bars)

        if side == "buy":
            pool_swept = curr_l < prior_low and curr_c > prior_low
            asia_swept = (
                self._asia_done
                and self._asia_low is not None
                and curr_l < self._asia_low
                and curr_c > self._asia_low
            )
            pdl_swept = (
                self._prev_day_low is not None
                and curr_l < self._prev_day_low
                and curr_c > self._prev_day_low
            )
            if pool_swept or asia_swept or pdl_swept:
                return True, curr_l
        else:
            pool_swept = curr_h > prior_high and curr_c < prior_high
            asia_swept = (
                self._asia_done
                and self._asia_high is not None
                and curr_h > self._asia_high
                and curr_c < self._asia_high
            )
            pdh_swept = (
                self._prev_day_high is not None
                and curr_h > self._prev_day_high
                and curr_c < self._prev_day_high
            )
            if pool_swept or asia_swept or pdh_swept:
                return True, curr_h
        return False, None

    def _htf_bias(self, close: float) -> int:
        """Top-down directional bias (-1..+1 summed across Daily EMA, 240 LMA, H4 & H1 Flow)."""
        score = 0
        if self._d1_ema18.value is not None and self._d1_ema40.value is not None:
            if self._d1_ema18.value > self._d1_ema40.value:
                score += 1
            elif self._d1_ema18.value < self._d1_ema40.value:
                score -= 1
        if self._lwma240.value is not None:
            if close > self._lwma240.value:
                score += 1
            elif close < self._lwma240.value:
                score -= 1
        if self._h4_struct.trend_bias != 0:
            score += self._h4_struct.trend_bias
        if self._h1_struct.trend_bias != 0:
            score += self._h1_struct.trend_bias
        return score

    # ---------- Multi-Horizon Setup Evaluation ----------

    def _build_swing_days_signal(
        self, i: int, c_open: float, c_high: float, c_low: float, c_close: float, atr: float
    ) -> Optional[Tuple[Signal, dict]]:
        """Tier 3: HOLD FOR DAYS — HTF Daily/H4 OB + Deep OTE + Daily 18/40 EMA / 240 LMA."""
        bias = self._htf_bias(c_close)
        htf_atr = max(atr * 2.2, self._h4_atr.value or 0.0, (self._d1_atr.value or 0.0) * 0.65)
        if htf_atr <= 0.0:
            return None

        bull_pa, bear_pa, bull_cross, bear_cross = self._price_action_trigger(atr)
        recent_ltf_bull = (
            (self._ltf_struct.last_choch_dir == 1 and 0 <= i - self._ltf_struct.last_choch_idx <= 4)
            or (self._ltf_struct.last_bos_dir == 1 and 0 <= i - self._ltf_struct.last_bos_idx <= 4)
        )
        recent_ltf_bear = (
            (self._ltf_struct.last_choch_dir == -1 and 0 <= i - self._ltf_struct.last_choch_idx <= 4)
            or (self._ltf_struct.last_bos_dir == -1 and 0 <= i - self._ltf_struct.last_bos_idx <= 4)
        )
        recent_h4_bull = self._h4_struct.trend_bias == 1 or self._d1_struct.trend_bias == 1
        recent_h4_bear = self._h4_struct.trend_bias == -1 or self._d1_struct.trend_bias == -1

        for side in ("buy", "sell"):
            is_bull = side == "buy"
            if is_bull and bias < 2:
                continue
            if (not is_bull) and bias > -2:
                continue
            if is_bull and not recent_h4_bull:
                continue
            if (not is_bull) and not recent_h4_bear:
                continue

            # Daily 18/40 EMA stack check (King): avoid entering when D1 EMAs oppose direction
            ema_stacked = False
            if self._d1_ema18.value is not None and self._d1_ema40.value is not None:
                ema_ok = (self._d1_ema18.value > self._d1_ema40.value) if is_bull else (self._d1_ema18.value < self._d1_ema40.value)
                if not ema_ok:
                    continue
                ema_stacked = True

            # 240 LMA alignment (Brown)
            if self._lwma240.value is not None:
                if is_bull and c_close < self._lwma240.value - 0.5 * atr:
                    continue
                if (not is_bull) and c_close > self._lwma240.value + 0.5 * atr:
                    continue

            # H4 / D1 OB, Breaker, or FVG mitigation inside H4 Discount/Premium
            htf_ob, is_brk, matched_ob = self._in_zone(self._h4_struct.obs, c_low, c_high, c_close, is_bull, htf_atr)
            if not htf_ob:
                htf_ob, is_brk, matched_ob = self._in_zone(self._d1_struct.obs, c_low, c_high, c_close, is_bull, htf_atr)
            htf_fvg = self._in_fvg(self._h4_struct.fvgs, c_low, c_high, c_close, is_bull) or self._in_fvg(
                self._d1_struct.fvgs, c_low, c_high, c_close, is_bull
            )
            in_half_h4, in_ote_h4 = self._check_ote_zone(self._h4_struct, c_close, side)
            in_half_d1, in_ote_d1 = self._check_ote_zone(self._d1_struct, c_close, side)

            if not in_half_h4:
                continue

            score = 0
            if htf_ob:
                score += 2
            if is_brk:
                score += 1
            if htf_fvg:
                score += 1
            if in_ote_h4 or in_ote_d1:
                score += 2
            if in_half_d1:
                score += 1
            if ema_stacked and self._d1_widening:
                score += 1
            if (is_bull and (bull_pa or bull_cross)) or ((not is_bull) and (bear_pa or bear_cross)):
                score += 1
            if (is_bull and recent_ltf_bull and self._ltf_struct.trend_bias == 1) or (
                (not is_bull) and recent_ltf_bear and self._ltf_struct.trend_bias == -1
            ):
                score += 1

            swept, sweep_ext = self._sweep_detected(side, lookback=18)
            if swept:
                score += 1

            # Trigger gate: HTF POI/OTE confluence + reversal/momentum confirmation
            directional_pa = (bull_pa or bull_cross) if is_bull else (bear_pa or bear_cross)
            trigger_ok = (htf_ob or htf_fvg or in_ote_h4 or swept) and (
                directional_pa or
                (is_bull and self._std_hist > self._prev_std_hist)
                or ((not is_bull) and self._std_hist < self._prev_std_hist)
            )
            trigger_ok = trigger_ok and self._entry_confirmation(side, atr, directional_pa, swept)
            if not trigger_ok or score < max(4, int(self.min_confluence) + 1):
                continue

            min_sl_dist = max(2.2 * atr, 1.35 * htf_atr)
            if is_bull:
                ref_low = min(c_low, matched_ob.low if matched_ob else c_low, sweep_ext if sweep_ext else c_low)
                sl = min(ref_low - 0.5 * atr, c_close - min_sl_dist)
                risk = c_close - sl
                if risk <= 0.0:
                    continue
                tp = c_close + float(self.swing_rr) * risk
            else:
                ref_high = max(c_high, matched_ob.high if matched_ob else c_high, sweep_ext if sweep_ext else c_high)
                sl = max(ref_high + 0.5 * atr, c_close + min_sl_dist)
                risk = sl - c_close
                if risk <= 0.0:
                    continue
                tp = c_close - float(self.swing_rr) * risk

            meta = {
                "horizon": "DAYS",
                "risk": risk,
                "entry_close": c_close,
                "open_min_est": self._last_gmt_min or 0,
            }
            return Signal(side, sl, tp), meta
        return None

    def _build_hours_signal(
        self, i: int, gmt_dt: datetime, c_high: float, c_low: float, c_close: float, atr: float
    ) -> Optional[Tuple[Signal, dict]]:
        """Tier 2: HOLD FOR HOURS — London Express / NY Flow from H1 OB/FVG + 25 SMA + 240 LMA."""
        hour = gmt_dt.hour
        in_main_session = (6 <= hour < 17) or (self._bar_minutes >= 240)
        if not in_main_session:
            return None

        # Ensure daily ADR still has room to run (King/Matias) unless D1 trend strongly aligned
        adr_room = True
        if self._adr5 > 0.0 and self._today_high is not None and self._today_low is not None:
            today_rng = self._today_high - self._today_low
            adr_room = (today_rng / self._adr5) < 0.88

        h1_atr = max(atr * 1.4, self._h1_atr.value or atr)
        bull_pa, bear_pa, bull_cross, bear_cross = self._price_action_trigger(atr)
        sma25 = self._sma25.value
        lma240 = self._lwma240.value
        bias = self._htf_bias(c_close)

        for side in ("buy", "sell"):
            is_bull = side == "buy"
            if is_bull and bias < 1:
                continue
            if (not is_bull) and bias > -1:
                continue

            # Jim Brown trend anchor: 25 SMA & 240 LMA alignment
            if lma240 is not None:
                if is_bull and c_close < lma240:
                    continue
                if (not is_bull) and c_close > lma240:
                    continue
            if sma25 is not None:
                if is_bull and c_close < sma25 - 0.4 * atr:
                    continue
                if (not is_bull) and c_close > sma25 + 0.4 * atr:
                    continue

            if not adr_room and abs(bias) < 3:
                continue

            ob_hit, is_brk, ob_zone = self._in_zone(self._h1_struct.obs, c_low, c_high, c_close, is_bull, h1_atr)
            if not ob_hit:
                ob_hit, is_brk, ob_zone = self._in_zone(self._ltf_struct.obs, c_low, c_high, c_close, is_bull, atr)
            fvg_hit = self._in_fvg(self._h1_struct.fvgs, c_low, c_high, c_close, is_bull) or self._in_fvg(
                self._ltf_struct.fvgs, c_low, c_high, c_close, is_bull
            )
            in_half, in_ote = self._check_ote_zone(self._h1_struct, c_close, side)
            if not in_half:
                continue

            swept, sweep_ext = self._sweep_detected(side, lookback=16)
            macd_ok = (self._std_macd > 0.0 and self._std_hist >= self._prev_std_hist) if is_bull else (
                self._std_macd < 0.0 and self._std_hist <= self._prev_std_hist
            )
            trigger = (is_bull and (bull_pa or bull_cross)) or ((not is_bull) and (bear_pa or bear_cross))

            score = 0
            if ob_hit:
                score += 2
            if is_brk:
                score += 1
            if fvg_hit:
                score += 1
            if in_ote:
                score += 1
            if swept:
                score += 2
            if macd_ok:
                score += 1
            if trigger:
                score += 1
            if self._near_institutional_level(c_close, atr):
                score += 1

            if score < max(3, int(self.min_confluence)):
                continue
            if not (ob_hit or fvg_hit or swept):
                continue
            if not (trigger or macd_ok):
                continue
            if not self._entry_confirmation(side, atr, trigger, swept):
                continue

            min_sl_dist = max(1.35 * atr, 0.9 * h1_atr)
            if is_bull:
                ref_low = min(c_low, ob_zone.low if ob_zone else c_low, sweep_ext if sweep_ext else c_low)
                sl = min(ref_low - 0.35 * atr, c_close - min_sl_dist)
                risk = c_close - sl
                if risk <= 0.0:
                    continue
                tp = c_close + float(self.intraday_rr) * risk
            else:
                ref_high = max(c_high, ob_zone.high if ob_zone else c_high, sweep_ext if sweep_ext else c_high)
                sl = max(ref_high + 0.35 * atr, c_close + min_sl_dist)
                risk = sl - c_close
                if risk <= 0.0:
                    continue
                tp = c_close - float(self.intraday_rr) * risk

            meta = {
                "horizon": "HOURS",
                "risk": risk,
                "entry_close": c_close,
                "open_min_est": self._last_gmt_min or 0,
            }
            return Signal(side, sl, tp), meta
        return None

    def _build_scalp_signal(
        self, gmt_dt: datetime, c_high: float, c_low: float, c_close: float, atr: float
    ) -> Optional[Tuple[Signal, dict]]:
        """Tier 1: SCALP — High-Selectivity Kill-Zone Sweep / OB + IPL + ModMACD (6,12,1) + LTF PA."""
        hour = gmt_dt.hour
        in_london_open = 7 <= hour < 10
        in_ny_open = 12 <= hour < 15
        in_london_close = 15 <= hour < 18

        adr_ratio = 0.0
        if self._adr5 > 0.0 and self._today_high is not None and self._today_low is not None:
            adr_ratio = (self._today_high - self._today_low) / self._adr5

        in_killzone = in_london_open or in_ny_open or (in_london_close and adr_ratio >= 0.72) or (self._bar_minutes >= 60)
        if not in_killzone:
            return None

        bull_pa, bear_pa, bull_cross, bear_cross = self._price_action_trigger(atr)
        ipl_hit = self._near_institutional_level(c_close, atr)
        bias = self._htf_bias(c_close)

        for side in ("buy", "sell"):
            is_bull = side == "buy"
            swept, sweep_ext = self._sweep_detected(side, lookback=12)
            ob_hit, _, ob_zone = self._in_zone(self._ltf_struct.obs, c_low, c_high, c_close, is_bull, atr)
            fvg_hit = self._in_fvg(self._ltf_struct.fvgs, c_low, c_high, c_close, is_bull)

            # Must interact with an actual structural catalyst (Sweep, Order Block, or FVG)
            if not (swept or ob_hit or fvg_hit):
                continue

            # Counter-trend London Close scalp only allowed when ADR >= 72% after clear sweep
            counter_trend = (is_bull and bias < 0) or ((not is_bull) and bias > 0)
            if counter_trend:
                if not (in_london_close and adr_ratio >= 0.72 and swept):
                    continue
            else:
                # Trend-aligned scalps require H1/LMA bias not neutral/flat
                if abs(bias) < 1:
                    continue

            trigger = (is_bull and (bull_pa or bull_cross)) or ((not is_bull) and (bear_pa or bear_cross))
            if not trigger or not self._entry_confirmation(side, atr, trigger, swept):
                continue

            score = 0
            if swept:
                score += 2
            if ob_hit:
                score += 1
            if fvg_hit:
                score += 1
            if ipl_hit:
                score += 1
            if (is_bull and bull_cross) or ((not is_bull) and bear_cross):
                score += 1
            if (is_bull and bull_pa) or ((not is_bull) and bear_pa):
                score += 1
            if abs(bias) >= 2:
                score += 1

            if score < max(4, int(self.min_confluence) + 1):
                continue

            min_sl_dist = max(0.95 * atr, 1e-6)
            if is_bull:
                ref_low = min(c_low, sweep_ext if sweep_ext else c_low, ob_zone.low if ob_zone else c_low)
                sl = min(ref_low - 0.25 * atr, c_close - min_sl_dist)
                risk = c_close - sl
                if risk <= 0.0:
                    continue
                tp = c_close + float(self.scalp_rr) * risk
            else:
                ref_high = max(c_high, sweep_ext if sweep_ext else c_high, ob_zone.high if ob_zone else c_high)
                sl = max(ref_high + 0.25 * atr, c_close + min_sl_dist)
                risk = sl - c_close
                if risk <= 0.0:
                    continue
                tp = c_close - float(self.scalp_rr) * risk

            meta = {
                "horizon": "SCALP",
                "risk": risk,
                "entry_close": c_close,
                "open_min_est": self._last_gmt_min or 0,
            }
            return Signal(side, sl, tp), meta
        return None

    # ---------- Backtest Engine Callbacks ----------

    def on_candle(self, i: int, candles: List) -> Optional[Signal]:
        if i < 45:
            # Ensure even warmup bars feed indicators cleanly once engine reaches warmup
            if self._last_processed_index < i:
                self._sync_to(i, candles)
            return None

        gmt_dt, _, _ = self._sync_to(i, candles)
        atr = self._atr_base.value
        if atr <= 0.0 or len(self._ltf_bars) < 20:
            return None

        # 2-bar cooldown after a trade closes to prevent chasing the close candle
        if i - self._prev_close_i < 2:
            return None

        candle = candles[i]
        c_open = candle.open
        c_high = candle.high
        c_low = candle.low
        c_close = candle.close

        profile = int(self.horizon_profile)
        chosen: Optional[Tuple[Signal, dict]] = None

        # Auto mode prioritizes highest institutional HTF conviction first: Days -> Hours -> Scalp
        if profile in (0, 3):
            chosen = self._build_swing_days_signal(i, c_open, c_high, c_low, c_close, atr)
        if chosen is None and profile in (0, 2):
            chosen = self._build_hours_signal(i, gmt_dt, c_high, c_low, c_close, atr)
        if chosen is None and profile in (0, 1):
            chosen = self._build_scalp_signal(gmt_dt, c_high, c_low, c_close, atr)

        if chosen is None:
            return None

        sig, meta = chosen
        if sig.side == "buy" and not (sig.sl < c_close < sig.tp):
            return None
        if sig.side == "sell" and not (sig.tp < c_close < sig.sl):
            return None

        self._pending_meta = meta
        return sig

    def manage_trade(self, i: int, candles: List, trade) -> Optional[str]:
        """Horizon-aware trade lifecycle manager (Scalp vs Hold-for-Hours vs Hold-for-Days)."""
        _, gmt_min, _ = self._sync_to(i, candles)
        self._prev_close_i = i

        key = (trade.open_i, trade.side)
        if self._active_trade_key != key:
            self._active_trade_key = key
            self._active_meta = self._pending_meta or {
                "horizon": "HOURS",
                "risk": abs(trade.entry - (trade.initial_sl if trade.initial_sl is not None else trade.sl)),
                "open_min_est": gmt_min,
            }
            self._pending_meta = None

        meta = self._active_meta or {"horizon": "HOURS", "open_min_est": gmt_min}
        horizon = meta.get("horizon", "HOURS")

        init_sl = trade.initial_sl if trade.initial_sl is not None else trade.sl
        risk = abs(trade.entry - init_sl)
        if risk <= 0.0:
            return None

        c = candles[i]
        close = c.close
        atr = max(self._atr_base.value, 1e-9)
        elapsed_bars = max(1, i - trade.open_i + 1)
        elapsed_mins = max(elapsed_bars * max(1, self._bar_minutes), gmt_min - meta.get("open_min_est", gmt_min))

        if trade.side == "buy":
            favorable_r = (c.high - trade.entry) / risk
            close_r = (close - trade.entry) / risk
        else:
            favorable_r = (trade.entry - c.low) / risk
            close_r = (trade.entry - close) / risk

        target_r = abs(trade.tp - trade.entry) / risk if getattr(trade, "tp", None) is not None else 0.0
        target_progress = max(0.0, close_r / target_r) if target_r > 0.0 else 0.0
        reversal_confirmed = False
        reversal_evidence = {}

        if target_progress >= 0.50:
            reversal_confirmed, reversal_evidence = self._confirmed_reversal(trade.side, atr)
        if target_progress >= 0.95 and reversal_confirmed:
            self.last_management_diagnostics = {
                "index": i,
                "target_progress": target_progress,
                "action": "confirmed_reversal_exit",
                "evidence": reversal_evidence,
            }
            return "exit"

        if trade.side == "buy":
            if target_progress >= 0.65:
                lock = trade.entry + 0.5 * risk
                if lock < close and lock > trade.sl:
                    trade.sl = lock
            if target_progress >= 0.80:
                lock = trade.entry + 1.0 * risk
                if lock < close and lock > trade.sl:
                    trade.sl = lock
            if target_progress >= 0.90:
                trail = max(trade.entry + risk, close - 0.85 * atr)
                if trail < close and trail > trade.sl:
                    trade.sl = trail
        else:
            if target_progress >= 0.65:
                lock = trade.entry - 0.5 * risk
                if lock > close and lock < trade.sl:
                    trade.sl = lock
            if target_progress >= 0.80:
                lock = trade.entry - 1.0 * risk
                if lock > close and lock < trade.sl:
                    trade.sl = lock
            if target_progress >= 0.90:
                trail = min(trade.entry - risk, close + 0.85 * atr)
                if trail > close and trail < trade.sl:
                    trade.sl = trail

        self.last_management_diagnostics = {
            "index": i,
            "target_progress": target_progress,
            "reversal_monitoring": target_progress >= 0.50,
            "reversal_evidence": reversal_evidence,
            "stop": trade.sl,
        }
        self._prev_close_i = i

        if trade.side == "buy":
            if horizon == "SCALP":
                # Move SL to BE at +0.85R; trail half-R above entry after +1.25R
                if favorable_r >= 0.85 and trade.sl < trade.entry:
                    trade.sl = trade.entry
                if favorable_r >= 1.25:
                    lock = trade.entry + 0.45 * risk
                    if lock < close and lock > trade.sl:
                        trade.sl = lock
                # Exit stagnant scalps beyond ~2.5h if ModMACD rolls bearish
                if elapsed_mins >= 120 and self._mod_macd < 0.0:
                    return "exit"
                if elapsed_mins >= 210:
                    return "exit"

            elif horizon == "HOURS":
                # Matias 1:1 Break-Even Rule
                if favorable_r >= float(self.be_trigger_r) and trade.sl < trade.entry:
                    trade.sl = trade.entry
                # Jim Brown ATR trailing stop once >= 1.6R
                if favorable_r >= 1.6:
                    trail = close - 1.65 * atr
                    if trail > trade.sl and trail < close:
                        trade.sl = max(trail, trade.entry + 0.15 * risk)
                # Brown ModMACD zero-cross exit rule after at least 4 hours in profit
                if elapsed_mins >= 240 and close_r >= 0.35 and self._prev_mod_macd >= 0.0 > self._mod_macd:
                    return "exit"
                if elapsed_mins >= 1200:  # ~20h intraday ceiling
                    return "exit"

            else:  # DAYS ("sit on your hands" multi-day trend rider)
                # Wide breathing room early; lock BE + 0.25R only once +1.4R achieved
                if favorable_r >= 1.4 and trade.sl < trade.entry + 0.25 * risk:
                    trade.sl = trade.entry + 0.25 * risk
                # Slow HTF ATR trailing stop beyond +2.0R
                if favorable_r >= 2.0:
                    htf_atr = max(atr * 2.2, self._h4_atr.value or 0.0, (self._d1_atr.value or 0.0) * 0.70)
                    trail = close - 2.30 * htf_atr
                    if trail > trade.sl and trail < close:
                        trade.sl = trail
                # Exit ONLY on confirmed Daily EMA cross + H4 bearish shift after holding >= 48h
                if (
                    elapsed_mins >= 2880
                    and close_r > 0.0
                    and self._d1_ema18.value is not None
                    and self._d1_ema40.value is not None
                    and self._d1_ema18.value < self._d1_ema40.value
                    and self._h4_struct.trend_bias == -1
                ):
                    return "exit"

        else:  # sell
            favorable_r = (trade.entry - c.low) / risk
            close_r = (trade.entry - close) / risk

            if horizon == "SCALP":
                if favorable_r >= 0.85 and trade.sl > trade.entry:
                    trade.sl = trade.entry
                if favorable_r >= 1.25:
                    lock = trade.entry - 0.45 * risk
                    if lock > close and lock < trade.sl:
                        trade.sl = lock
                if elapsed_mins >= 120 and self._mod_macd > 0.0:
                    return "exit"
                if elapsed_mins >= 210:
                    return "exit"

            elif horizon == "HOURS":
                if favorable_r >= float(self.be_trigger_r) and trade.sl > trade.entry:
                    trade.sl = trade.entry
                if favorable_r >= 1.6:
                    trail = close + 1.65 * atr
                    if trail < trade.sl and trail > close:
                        trade.sl = min(trail, trade.entry - 0.15 * risk)
                if elapsed_mins >= 240 and close_r >= 0.35 and self._prev_mod_macd <= 0.0 < self._mod_macd:
                    return "exit"
                if elapsed_mins >= 1200:
                    return "exit"

            else:  # DAYS
                if favorable_r >= 1.4 and trade.sl > trade.entry - 0.25 * risk:
                    trade.sl = trade.entry - 0.25 * risk
                if favorable_r >= 2.0:
                    htf_atr = max(atr * 2.2, self._h4_atr.value or 0.0, (self._d1_atr.value or 0.0) * 0.70)
                    trail = close + 2.30 * htf_atr
                    if trail < trade.sl and trail > close:
                        trade.sl = trail
                if (
                    elapsed_mins >= 2880
                    and close_r > 0.0
                    and self._d1_ema18.value is not None
                    and self._d1_ema40.value is not None
                    and self._d1_ema18.value > self._d1_ema40.value
                    and self._h4_struct.trend_bias == 1
                ):
                    return "exit"

        return None


class ScalpKillZoneStrategy(InstitutionalTriHorizonStrategy):
    """Tier 1 Preset: Kill-zone Liquidity Sweeps + ModMACD (6,12,1) Scalper."""

    def __init__(self) -> None:
        super().__init__()
        self.horizon_profile = 1


class IntradayLondonExpressStrategy(InstitutionalTriHorizonStrategy):
    """Tier 2 Preset: Hold-for-Hours Session Express (H1 OB/FVG + 25 SMA / 240 LMA)."""

    def __init__(self) -> None:
        super().__init__()
        self.horizon_profile = 2


class SwingMultiDayOrderBlockStrategy(InstitutionalTriHorizonStrategy):
    """Tier 3 Preset: Hold-for-Days Macro SMC & Daily 18/40 EMA Runner."""

    def __init__(self) -> None:
        super().__init__()
        self.horizon_profile = 3
