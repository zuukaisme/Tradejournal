# MT5 Android runtime integration research

## Direction

The current generic Wine/Box64 ZIP launcher is only a prototype. It is not yet compatible with a real Winlator runtime package and must not be described as a finished MT5 integration.

The strongest reference implementation found for this device class is [GunaCharanTeja/WinlatorMali](https://github.com/GunaCharanTeja/WinlatorMali), branch `bionic-mali-1.2`. Its README explicitly targets non-Snapdragon phones including Samsung Exynos and Mali GPUs. The project identifies 1.2.1 as its final release, so it is a reference/forking candidate rather than an actively maintained dependency.

The repository's top-level app source license is MIT. Its bundled components have separate licenses; every copied source file, binary, archive, and notice must be reviewed independently before redistribution.

## Important implementation findings

Winlator Mali is not just a Wine binary wrapper:

- `ImageFsInstaller` installs a Linux root filesystem into `filesDir/imagefs`.
- `ImageFs` manages the rootfs and the Wine prefix at `/home/xuser/.wine`.
- Wine runtimes are installed below `imagefs/opt/<runtime-id>`.
- `GuestProgramLauncherComponent` configures Wine's environment, Box64/Arm64EC translation, and launches the selected Windows executable.
- For ARM64EC Wine, the launcher uses the Wine runtime executable and sets `HODLL` to `wowbox64.dll` or `libwow64fex.dll`; it does not simply execute `box64 wine64 terminal64.exe`.
- `XServerDisplayActivity`, `XServerComponent`, and `XServerView` provide the app-integrated X server and render Windows windows onto an Android Surface. A visible embedded terminal requires this display/input layer; an Xvfb-only process is headless and cannot provide an in-app terminal screen.

The current TradePulse experimental service launches a different, generic bundle layout. It is not compatible with Winlator Mali's actual imagefs/Wine/Arm64EC layout. Do not try to import Winlator Mali's archives into the current generic ZIP importer.

## Upstream runtime assets

The Winlator Mali build references the following upstream assets:

| Asset | Size | SHA-256 / source identifier |
|---|---:|---|
| `imagefs.tzst` | 199,788,880 bytes | `0c8c6c1c7653766323378f3294be6618392075bd354d50b2416a534a6e231620` |
| `proton-10-arm64ec.tzst` | 204,777,490 bytes | `d00ff472fb2ee9fe1bfe1a6e7b28d012e7f854b1ed10596aaa702295e8410548` |
| `container_pattern_common.tzst` | 45,299,596 bytes | `e6f9d37ef9a98605dfa59acde047bb2ede7af7f4c16da19d362ad785e4ee3ca5` |
| `wowbox64-0.4.3.tzst` | 1,022,740 bytes | Git blob SHA-1 `f8741cfe894b05e719edc60c9f8148c24912d283` |
| `box64-0.4.3.tzst` | 3,538,220 bytes | Git blob SHA-1 `357d1fbdf34cb5e94f6802266e53fdbdbfc1b205` |

The first three are published in the upstream `Winlator-Extras` release tagged `Sd`. The Box64/WOWBox64 archives are in the Winlator Mali source tree under `app/src/main/assets`.

These are upstream build inputs, not yet installed by TradePulse. The app should download them over HTTPS, verify their digests, show progress, extract into private app storage, and keep a manifest of the installed versions. The complete extraction footprint will be substantially larger than the compressed download; check free storage before starting.

## Integration plan

1. Replace the generic ZIP-only runtime bootstrap with a versioned Winlator-compatible runtime installer.
2. Integrate the minimum required rootfs, Proton ARM64EC runtime, and WOWBox64 emulator component. Do not include optional game graphics stacks that MT5 does not need.
3. Port or isolate the required Wine launch/environment logic and native X server/display/input components. Avoid copying the whole gaming UI, game launchers, overlays, and unrelated assets.
4. Add a TradePulse MT5 session activity that hosts the embedded terminal display, plus a separate bot/session controller.
5. Let the user install or select their own MetaTrader terminal files. Do not bundle or redistribute MetaQuotes terminal binaries.
6. Test terminal startup, account login, symbol data, order operations, bot execution, background/foreground transitions, and thermal/RAM behaviour on the actual Galaxy A12 before enabling live trading.

## Validation still required

- The upstream README supports Exynos/Mali as a device category, but does not establish that the exact Galaxy A12 model can run MT5 acceptably.
- MT5 is a trading terminal, not a documented generic Android SDK. Bot integration must be implemented against the running terminal's supported interfaces (for example, an MQL5 EA/bridge inside the terminal) and verified with a demo account first.
- The current TradePulse debug APK build only validates that the prototype code compiles. It does not validate a Wine runtime, MT5 startup, broker connectivity, or live order execution.


## TradePulse runtime-layout validation

TradePulse now has a read-only layout inspector for the Winlator Mali imagefs. It checks for the imagefs version marker, ARM64 Box64, Proton 10 ARM64EC, the Wine prefix, a WOWBox64/FEX host DLL, and the installed MT5 terminal executable. Unit tests cover missing components and both supported host-DLL layouts.

This is a filesystem preflight only. It does not extract the upstream archives, configure the Wine prefix, launch Proton, create the X server, or prove that MT5 works on a device. The MT5 start control remains unavailable until the Android X server/Surface/input host and the ARM64EC process launcher are integrated. The old generic ZIP bundle must not be mistaken for a Winlator Mali installation.


## Gemini Mt5android architecture review

The companion repository `zuukaisme/Mt5android` was reviewed as an implementation reference. Its PRoot layout uses an app-private Linux rootfs, a native ARM64 `libproot.so`, Box64 and Wine in the guest rootfs, an app-private Wine prefix, and a native `libXlorie.so` X11 server. Its setup and launch scripts are useful reference material.

The repository does not contain the referenced `libproot.so` or `libXlorie.so` binaries. Its X11 bridge stores an Android Surface and sends input packets to a local socket, but it does not attach the Surface to the native renderer or implement the server-side socket protocol. The SurfaceView therefore does not currently prove that an X11 desktop can be rendered in-app. Its latest workflow failed during Android SDK setup, before Gradle compilation.

TradePulse now has a PRoot runtime filesystem preflight for that layout. It checks native PRoot/Xlorie libraries, guest Box64/Wine, the Wine prefix and a valid MT5 executable header. It deliberately retains launch blockers for native Surface presentation and the X11 input socket until those paths are implemented and verified. The older generic ZIP runtime is not considered a runnable desktop runtime.

No runtime binaries or MT5 installer are copied from the companion repository. Runtime assets must be sourced and redistributed only after their licenses and provenance are verified.


## Embedded X11 implementation decision

Termux:X11 is the strongest Android-13-compatible upstream starting point identified so far: its published project describes a full X server built with the Android NDK and lists Android 7+ as its minimum OS. It is not a drop-in Android library. The upstream project includes an Android application, a companion Termux package, native X server sources, JNI entry points, submodules and a GPL-3.0 license.

TradePulse must integrate the native server and the Android host lifecycle directly rather than launching the standalone Termux:X11 APK or assuming a SurfaceView is sufficient. Before source is vendored, pin an exact upstream commit, initialize and record every submodule revision, preserve all notices, and verify the complete dependency license set. The native engine must expose a documented host contract for startup, Surface attach/detach, frame presentation, pointer/keyboard input, resize, focus, and shutdown. The existing Gemini socket bridge is not reused because its server-side protocol and renderer attachment are absent.

The current PR does not yet contain the native X server. This is intentional: adding an unbuilt native library or a nonfunctional Surface wrapper would create a false-positive runtime. The PRoot preflight now rejects placeholder files and requires ELF64 little-endian AArch64 (EM_AARCH64=183) for libproot.so, libXlorie.so, and Box64. Unit tests cover valid ARM64 ELF headers, invalid native payloads, Windows executable headers, and the still-blocked display lifecycle.

A complete embedded renderer remains the next implementation milestone. Until native build, JNI host integration, lifecycle tests, and on-device presentation/input tests pass, the terminal launch guard stays closed.
