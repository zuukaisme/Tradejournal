# TradePulse embedded MT5 runtime bundle

TradePulse stores the runtime in its private app storage. The user imports a ZIP bundle; the app does not download or redistribute MetaTrader terminal binaries.

## Required ZIP layout

The ZIP may contain the files directly or inside a single top-level `runtime/` directory.

```
bin/
  box64
rootfs/
  usr/bin/
    wine64
    Xvfb
  usr/lib/x86_64-linux-gnu/
  lib/x86_64-linux-gnu/
mt5/
  terminal64.exe
```

- `bin/box64` must be an Android ARM64/Bionic executable compatible with the phone.
- `rootfs/usr/bin/wine64` and `rootfs/usr/bin/Xvfb` must be Linux x86_64 ELF binaries.
- The root filesystem must include all shared libraries required by Wine and Xvfb. ZIP packages must materialize symbolic links as regular files at the linked paths because the current ZIP importer does not restore Unix symlink metadata.
- `mt5/terminal64.exe` must be a user-installed MetaTrader 5 Windows terminal.
- TradePulse starts Box64 directly through a small JNI process launcher. It does not execute downloaded binaries with `ProcessBuilder`.

## Runtime environment

TradePulse creates a private Wine prefix and supplies:

- `HOME`: runtime-local home directory
- `WINEPREFIX`: runtime-local Wine prefix
- `DISPLAY=:0`: virtual X display
- `BOX64_LD_LIBRARY_PATH`: x86_64 library paths inside the root filesystem
- `WINEDEBUG=-all`
- `TMPDIR`: runtime-local temporary directory

The app launches Xvfb through Box64, then launches the MT5 terminal through Wine/Box64. Logs are written to the app's private `mt5-runtime.log` file.

## Android executable restrictions

On Android 10 and later, executing an ELF copied into an app-private data directory can be denied by SELinux even after setting its executable bit. TradePulse therefore stages the top-level Android ARM64 Box64 executable into an anonymous memfd and launches it with `execveat(AT_EMPTY_PATH)`. This is a native execution path, not a shell workaround. It still requires an actual NDK build and device verification.

## Licensing and distribution

Do not redistribute the MetaTrader 5 terminal or broker-specific files in TradePulse releases. The terminal directory in a bundle must come from the user's own installation. If the Wine/Box64/Xvfb components are derived from Winlator or other third-party projects, preserve their applicable license notices, source-offer obligations and attribution. This repository defines the runtime bundle interface; it does not contain those third-party runtime binaries.

## Validation status

The runtime manager and process-launch integration do not prove that a particular Wine build can run MT5. The exact Wine/Box64 command and rootfs layout must be validated against the selected runtime build. The Galaxy A12 Exynos/Mali configuration needs a real on-device launch test before automated trading is enabled. Do not place broker passwords in the ZIP or in process arguments.
