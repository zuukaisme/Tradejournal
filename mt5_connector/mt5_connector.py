from __future__ import annotations

import csv
import hmac
import io
import json
import os
import secrets
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

def timeframe_map(mt5):
    return {
    "M1": mt5.TIMEFRAME_M1,
    "M2": mt5.TIMEFRAME_M2,
    "M3": mt5.TIMEFRAME_M3,
    "M4": mt5.TIMEFRAME_M4,
    "M5": mt5.TIMEFRAME_M5,
    "M6": mt5.TIMEFRAME_M6,
    "M10": mt5.TIMEFRAME_M10,
    "M12": mt5.TIMEFRAME_M12,
    "M15": mt5.TIMEFRAME_M15,
    "M20": mt5.TIMEFRAME_M20,
    "M30": mt5.TIMEFRAME_M30,
    "H1": mt5.TIMEFRAME_H1,
    "H2": mt5.TIMEFRAME_H2,
    "H3": mt5.TIMEFRAME_H3,
    "H4": mt5.TIMEFRAME_H4,
    "H6": mt5.TIMEFRAME_H6,
    "H8": mt5.TIMEFRAME_H8,
    "H12": mt5.TIMEFRAME_H12,
    "D1": mt5.TIMEFRAME_D1,
    "W1": mt5.TIMEFRAME_W1,
    "MN1": mt5.TIMEFRAME_MN1,
    }


def get_mt5():
    import MetaTrader5 as mt5
    return mt5


MAX_BARS = 500_000


def parse_utc(value: str) -> datetime:
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise ValueError("Dates must include a timezone, preferably Z for UTC")
    return parsed.astimezone(timezone.utc)


def rates_to_csv(rates) -> str:
    output = io.StringIO(newline="")
    writer = csv.writer(output)
    writer.writerow(["time", "open", "high", "low", "close", "tick_volume", "spread", "real_volume"])
    for rate in rates:
        writer.writerow([
            datetime.fromtimestamp(int(rate["time"]), timezone.utc).strftime("%Y-%m-%d %H:%M:%S"),
            rate["open"], rate["high"], rate["low"], rate["close"],
            rate["tick_volume"], rate["spread"], rate["real_volume"],
        ])
    return output.getvalue()


class Handler(BaseHTTPRequestHandler):
    server_version = "TradePulseMT5Connector/1.0"

    def respond(self, status: int, payload, content_type: str = "application/json") -> None:
        body = payload.encode("utf-8") if isinstance(payload, str) else json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def authorized(self) -> bool:
        expected = self.server.api_token
        supplied = self.headers.get("Authorization", "")
        return bool(expected) and hmac.compare_digest(supplied, f"Bearer {expected}")

    def do_GET(self) -> None:
        if not self.authorized():
            self.respond(401, {"error": "Unauthorized"})
            return
        parsed = urlparse(self.path)
        query = parse_qs(parsed.query)
        if parsed.path == "/health":
            self.respond(200, {"ok": True, "connector": self.server_version})
            return
        mt5 = get_mt5()
        timeframes = timeframe_map(mt5)
        if not mt5.initialize():
            self.respond(503, {"error": "Could not initialize the MT5 terminal", "detail": str(mt5.last_error())})
            return
        try:
            if parsed.path == "/account":
                account = mt5.account_info()
                terminal = mt5.terminal_info()
                if account is None or terminal is None:
                    self.respond(503, {"error": "No active MT5 account", "detail": str(mt5.last_error())})
                    return
                self.respond(200, {
                    "connected": True,
                    "login": int(account.login),
                    "server": account.server,
                    "company": account.company,
                    "currency": account.currency,
                    "terminal_connected": bool(terminal.connected),
                })
                return
            if parsed.path == "/symbols":
                symbols = mt5.symbols_get()
                if symbols is None:
                    self.respond(503, {"error": "Could not load symbols", "detail": str(mt5.last_error())})
                    return
                self.respond(200, [{
                    "name": item.name,
                    "description": item.description,
                    "path": item.path,
                    "visible": bool(item.visible),
                    "trade_mode": int(item.trade_mode),
                } for item in symbols])
                return
            if parsed.path == "/rates.csv":
                symbol = query.get("symbol", [""])[0].strip()
                timeframe = query.get("timeframe", [""])[0].upper()
                if not symbol or timeframe not in timeframes:
                    self.respond(400, {"error": "Provide a valid symbol and timeframe"})
                    return
                start = parse_utc(query.get("from", [""])[0])
                end = parse_utc(query.get("to", [""])[0])
                if start >= end:
                    self.respond(400, {"error": "The start date must be before the end date"})
                    return
                if not mt5.symbol_select(symbol, True):
                    self.respond(404, {"error": f"Symbol unavailable: {symbol}", "detail": str(mt5.last_error())})
                    return
                rates = mt5.copy_rates_range(symbol, timeframes[timeframe], start, end)
                if rates is None:
                    self.respond(503, {"error": "MT5 could not retrieve historical candles", "detail": str(mt5.last_error())})
                    return
                if len(rates) > MAX_BARS:
                    self.respond(413, {"error": f"Range exceeds the {MAX_BARS} candle limit; split the date range"})
                    return
                self.respond(200, rates_to_csv(rates), "text/csv; charset=utf-8")
                return
            self.respond(404, {"error": "Unknown endpoint"})
        except (ValueError, KeyError) as error:
            self.respond(400, {"error": str(error)})
        except Exception as error:
            self.respond(500, {"error": "Connector request failed", "detail": str(error)})
        finally:
            mt5.shutdown()

    def log_message(self, format: str, *args) -> None:
        print(f"[{self.log_date_time_string()}] {format % args}")


def main() -> None:
    token = os.environ.get("TRADEPULSE_MT5_TOKEN", "").strip()
    if len(token) < 32:
        raise SystemExit("Set TRADEPULSE_MT5_TOKEN to a random secret of at least 32 characters.")
    host = os.environ.get("TRADEPULSE_MT5_HOST", "127.0.0.1")
    port = int(os.environ.get("TRADEPULSE_MT5_PORT", "8765"))
    if host not in {"127.0.0.1", "localhost"} and os.environ.get("TRADEPULSE_MT5_ALLOW_LAN") != "1":
        raise SystemExit("LAN binding requires TRADEPULSE_MT5_ALLOW_LAN=1. Use a trusted private network.")
    server = ThreadingHTTPServer((host, port), Handler)
    server.api_token = token
    print(f"TradePulse MT5 connector listening on {host}:{port}")
    print("Read-only endpoints: /health, /account, /symbols, /rates.csv")
    server.serve_forever()


if __name__ == "__main__":
    main()
