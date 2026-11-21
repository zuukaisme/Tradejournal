# TradePulse local MT5 bridge

TradePulse now includes a local TCP bridge protocol and an MQL5 Expert Advisor source file at `app/src/main/assets/mt5/TradePulseBridge.mq5`.

## Connection model

- The Android app binds a TCP server to `127.0.0.1:8765` only.
- The MT5 Expert Advisor connects to `127.0.0.1:8765` from the terminal running under Wine.
- The app creates a persistent random 192-bit token and requires the EA to send `AUTH|<token>` as its first line.
- The token is displayed in TradePulse Settings while the runtime service is active.
- The server rejects unauthenticated clients and does not bind to the phone's Wi-Fi/LAN interface.

## Terminal setup

1. Start the embedded MT5 runtime from TradePulse after a compatible Wine runtime has been installed.
2. In MT5, open Tools → Options → Expert Advisors.
3. Add `127.0.0.1` to the allowed socket addresses. MQL5 requires socket destinations to be explicitly allowed by the terminal user.
4. Compile `TradePulseBridge.mq5` in MetaEditor and attach the EA to a chart.
5. Set the EA's `BridgeToken` input to the token shown by TradePulse.
6. Enable algorithmic trading in MT5 when you are ready to test with a demo account.

The MQL5 Socket API documents the terminal-side allowlist requirement: https://www.mql5.com/en/docs/network/socketconnect

## Wire protocol

Messages are UTF-8, one line per message, with fields separated by `|`.

### EA → app

- `AUTH|token`
- `HELLO|login|server|company`
- `ACCOUNT|login|server|currency|balance|equity|margin|freeMargin`
- `POSITION|ticket|symbol|type|volume|openPrice|profit`
- `SNAPSHOT_END`
- `BAR|symbol|timeframe|unixTime|open|high|low|close|tickVolume`
- `BARS_END|symbol|timeframe`
- `ORDER_RESULT|side|symbol|status|retcode|description`
- `CLOSE_RESULT|ticket|status|retcode`
- `CLOSE_ALL_RESULT|closedCount|failedCount`
- `ERROR|category|message`

### App → EA

- `PING`
- `BARS|symbol|timeframe|count`
- `BUY|symbol|volume`
- `SELL|symbol|volume`
- `CLOSE|ticket`
- `CLOSE_ALL`

The EA caps historical candle requests at 1,000 bars. It uses market orders without adding stop-loss or take-profit values.

## Current integration status

The Android server, session token, account/position snapshot parser, and EA source are implemented. The Settings card displays the bridge connection state, account snapshot and open positions when the EA connects.

The command method is available to the app, but it is not yet wired to the Python bot runner or a user-facing order-control UI. The current experimental Wine service also does not yet install the Winlator Mali rootfs/Proton runtime. Therefore this bridge code is not evidence that MT5 is already running on-device.

Use a demo account first. Before live trading is enabled, the app must add explicit user confirmation, command/result tracking, disconnection handling and tests against the actual MT5 terminal.
