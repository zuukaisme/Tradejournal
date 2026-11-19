# TradePulse MT5 Data Connector

This is the free, official MetaTrader 5 Python integration running beside the Windows MT5 desktop terminal. It is a **read-only historical-data bridge** for TradePulse. It has no order, position-modification, or trade-execution endpoints.

## Requirements

- Windows 10/11
- 64-bit Python installed from python.org
- MetaTrader 5 desktop terminal installed and signed in to the target account
- The broker's exact MT5 server selected in the terminal
- Python package published by MetaQuotes: `pip install MetaTrader5`

The Python package communicates with the locally installed MT5 terminal. It is not an Android-compatible library and does not expose a public MetaQuotes cloud REST API. Candle availability is limited to history downloaded/available in the terminal's chart history and its maximum-bars setting.

## Install

Open PowerShell in this folder:

```powershell
py -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -r requirements.txt
```

Sign in to the MT5 desktop terminal first. Use the broker-provided MT5 account login and server there. The connector intentionally does not accept or store the trading password; MT5 manages its own authenticated terminal session. In MT5 terminal settings, enable **Disable automatic trading via external Python API**. The connector only calls read-only account, symbol and candle functions, but this terminal setting provides an additional safety barrier.

Generate a strong API token:

```powershell
$env:TRADEPULSE_MT5_TOKEN = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
```

Start locally:

```powershell
python mt5_connector.py
```

The default listener is `127.0.0.1:8765`. For a phone on the same trusted private Wi-Fi, set the Windows PC's LAN address and explicitly allow LAN binding:

```powershell
$env:TRADEPULSE_MT5_HOST = "0.0.0.0"
$env:TRADEPULSE_MT5_ALLOW_LAN = "1"
python mt5_connector.py
```

Create a Windows Firewall rule limited to your private network and only the chosen port. Do not expose this HTTP service directly to the public internet. A LAN connection is not encrypted; use it only on a trusted network. The bearer token protects the endpoints but is not a substitute for TLS on untrusted networks.

## TradePulse Android workflow

1. Start this connector beside the signed-in MT5 desktop terminal.
2. Open TradePulse Settings and enter the connector URL and bearer token.
3. Test the connection, then load symbols from the broker.
4. Select the exact broker symbol, timeframe and UTC date range.
5. Download candles. TradePulse streams the CSV to app storage, inspects the rows/timeframe, and registers it in Backtester Saved CSVs.
6. Open Backtester and select the imported dataset if it is not already active.

The Android app does not run the MetaQuotes Python package or MT5 terminal itself. A compatible MT5 desktop terminal must be running on the computer/server hosting this connector. The phone only communicates with the read-only HTTP API.

## API

All endpoints require `Authorization: Bearer <TRADEPULSE_MT5_TOKEN>`.

- `GET /health` — connector health
- `GET /account` — connected account number, broker server, company and currency (no balance or trade data)
- `GET /symbols` — symbols returned by the broker's MT5 terminal, including exact broker suffixes
- `GET /rates.csv?symbol=EURUSD&timeframe=M1&from=2025-01-01T00:00:00Z&to=2025-02-01T00:00:00Z` — standard OHLCV CSV

Supported timeframes: M1, M2, M3, M4, M5, M6, M10, M12, M15, M20, M30, H1, H2, H3, H4, H6, H8, H12, D1, W1 and MN1. Date parameters must include a timezone; UTC with a trailing `Z` is recommended. Requests above 500,000 bars must be split into smaller date ranges.

The CSV columns are `time,open,high,low,close,tick_volume,spread,real_volume`, compatible with TradePulse's candle importer.

## Security

- Keep the API token private and rotate it if exposed.
- Keep the listener on localhost unless you intentionally enable private-LAN access.
- Never port-forward this connector or expose it to the public internet.
- Do not share screenshots or logs containing tokens.
- The connector only provides read-only market/account metadata and historical candle retrieval.
