# TradePulse headless X11 through Termux

TradePulse uses Termux's Android ARM64 Xvfb as a background headless X11 server. It does not open Termux:X11 or display a desktop. Wine connects to the server over loopback TCP using a per-start MIT-MAGIC-COOKIE-1 authorization file.

## 1. Install the Xvfb packages in Termux

```sh
pkg install x11-repo
pkg install xorg-server-xvfb xorg-fonts-misc
```

The Xvfb package supplies its X11 libraries, XKB compiler and X authority utility through Termux dependencies.

## 2. Allow TradePulse to start the background process

In Termux, edit `~/.termux/termux.properties` and set:

```properties
allow-external-apps=true
```

Then run:

```sh
termux-reload-settings
```

In Android Settings, open TradePulse > Permissions > Additional permissions and grant **Run commands in Termux environment**.

This permission is powerful because it allows TradePulse to request commands in Termux. TradePulse only sends its fixed Xvfb start/stop commands and a randomly generated X11 authentication cookie.

## 3. Start the runtime check in TradePulse

When the runtime check starts, TradePulse asks Termux to run Xvfb in the background, waits for an authenticated X11 handshake on `127.0.0.1:6000`, then runs the Winlator Proton Wine version probe. The Xvfb process is stopped when the probe finishes.

The X11 TCP listener requires the generated MIT cookie; the server is not started with access control disabled. The app does not launch MT5 or any trading bot during this diagnostic.

## Important

- Keep Termux installed and exempt it from battery restrictions if Android stops its background service.
- This only supplies the virtual display. A valid Winlator Mali ImageFS, Proton ARM64EC Wine, Wine prefix and installed MT5 terminal are still required.
- Passing the Wine version probe does not prove MT5 can log in, load the bridge EA, or execute a bot. Those steps require separate validation on the phone.
