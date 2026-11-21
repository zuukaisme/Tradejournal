# TradePulse embedded MT5 runtime bundle

TradePulse stores the runtime in its private app storage. The user imports a ZIP bundle; the app does not download or redistribute MetaTrader terminal binaries.

## Required ZIP layout

The ZIP may contain the files directly or inside a single top-level `runtime/` directory.

```
launch-mt5.sh
bin/
  proot
rootfs/
  usr/bin/
    box64
    wine
    Xvfb
mt5/
  terminal64.exe
```

- `bin/proot` must be an Android ARM64 executable compatible with the app's Android version.
- `rootfs/usr/bin/box64` must be an ARM64 Linux executable with its required rootfs libraries.
- `rootfs/usr/bin/wine` and `rootfs/usr/bin/Xvfb` must run inside the supplied root filesystem.
- `mt5/terminal64.exe` must be a user-installed MetaTrader 5 Windows terminal.
- `launch-mt5.sh` is the bundle's startup entry point. It receives the runtime directory as its working directory and receives `TRADEPULSE_RUNTIME_DIR`, `TRADEPULSE_MT5_DIR`, `TRADEPULSE_WINEPREFIX` and `TRADEPULSE_LOG_FILE` in its environment.

## Startup expectations

The launcher should start a virtual X server (for example Xvfb), configure a Wine prefix, launch the MT5 terminal through Box64, and remain in the foreground while the terminal is running. It should forward termination signals to its child processes.

A launcher can use these paths:

- Runtime root: `$TRADEPULSE_RUNTIME_DIR`
- Root filesystem: `$TRADEPULSE_RUNTIME_DIR/rootfs`
- PRoot: `$TRADEPULSE_RUNTIME_DIR/bin/proot`
- MT5 files: `$TRADEPULSE_MT5_DIR`
- Wine prefix: `$TRADEPULSE_WINEPREFIX`

## Important

This contract is a packaging boundary, not a claim that a generic Winlator archive is directly compatible. The runtime still needs device-specific validation. The Galaxy A12 Exynos/Mali configuration is outside the minimum hardware profile published by some Winlator builds, so a real on-device launch test is required before enabling automated trading. Do not place broker passwords in the ZIP or in launcher arguments.


## Licensing and distribution

Do not redistribute the MetaTrader 5 terminal or broker-specific files in TradePulse releases. The terminal directory in a bundle must come from the user's own installation. If the Wine/Box64/PRoot components are derived from Winlator or other third-party projects, preserve their applicable license notices, source-offer obligations and attribution. This repository currently defines the bundle interface; it does not contain those third-party runtime binaries.
