# Embedded X11 and MT5 runtime integration assessment

## Decision

Use the DroidDesk implementation as the next Android-host integration reference, with Termux:X11 as the native X server source. Do not copy its complete Linux desktop runtime into TradePulse and do not treat it as a ready-made MT5 runtime.

The DroidDesk source provides a more complete host pattern than the Gemini prototype:

- A dedicated Android service process owns the native X server.
- The UI process binds to that service through Binder.
- The service transfers an X client connection using ParcelFileDescriptor.
- The UI-side LorieView connects the native client using the received file descriptor.
- A platform SurfaceView hosts LorieView.
- A separate input controller maps Android touch and keyboard events to X11 input.
- The X11 service has an explicit lifecycle and is stopped with the Linux session.

Relevant upstream source paths at the inspected revision:

- `app/android/app/src/main/kotlin/com/orailnoor/droiddesk/x11/X11ServerService.kt`
- `app/android/app/src/main/kotlin/com/orailnoor/droiddesk/x11/X11ServiceClient.kt`
- `app/android/app/src/main/kotlin/com/orailnoor/droiddesk/view/AndroidSurfaceView.kt`
- `app/android/app/src/main/java/com/termux/x11/LorieView.java`
- `app/android/app/src/main/AndroidManifest.xml`

The inspected DroidDesk revision is `5dd59ff861cea6995c84d1909e2935f3900e1e37`.

## Why this is not a drop-in port

DroidDesk's non-root runtime is a relocated native Termux userspace, not a PRoot guest. Its Linux desktop packages are installed for that userspace. The optional Debian PRoot container is not the mechanism used to launch the desktop itself.

TradePulse's target is different: it needs a Windows MT5 terminal running under a compatible Wine/Box64 or Proton ARM64EC stack. The X server alone does not provide that stack. The Galaxy A12 also uses a Mali GPU, so Adreno-specific Turnip/Zink assumptions must not be copied; software rendering or a verified Mali-compatible graphics path is required.

DroidDesk's source and bundled runtime also carry substantial third-party obligations. Its published inventory identifies GPL-3.0 Termux:X11 code, GPL PRoot components, additional native libraries with unresolved provenance, and bootstrap packages with individual licenses. Its own compliance checklist still lists unresolved source/build provenance items. TradePulse must independently pin source revisions, submodules, patches, build recipes, notices and hashes before redistributing any derived code or binary.

## Integration plan

1. Pin Termux:X11 and every submodule revision. Record the complete license inventory and source provenance before vendoring.
2. Build the ARM64 native server and its JNI dependencies from source in CI. Do not import opaque prebuilt shared libraries.
3. Implement the isolated X11 service, Binder contract and file-descriptor handoff in TradePulse.
4. Port the minimal LorieView, input controller and required support classes, adapting package names and Android APIs to the app's minSdk/compileSdk. Keep the UI Surface in the app process and the native X server in its isolated process.
5. Add instrumentation tests for service startup, Binder death, file-descriptor closure, Surface create/change/destroy, touch, keyboard, resize, reconnect and process shutdown.
6. Integrate one runtime stack only. Prefer the already evaluated Winlator Mali ImageFS/Proton ARM64EC path if its exact binaries and license provenance can be built and verified. Do not mix its filesystem layout with the Gemini PRoot/Box64/Wine layout.
7. Add the MT5 installer/prefix setup and terminal process launcher only after the X11 host and selected runtime pass independent tests.
8. Keep bot deployment and trading disabled until an actual terminal process is visible, accepts input, and can load a test EA on a demo account.

## Acceptance criteria

The runtime launch guard must remain closed until all of the following are true:

- Native server and all JNI dependencies are built for ARM64 and pass ABI checks.
- The app can start and stop the isolated X11 service repeatedly without leaked processes or stale sockets.
- A test X11 client renders frames into the TradePulse Surface.
- Touch, mouse buttons, keyboard, clipboard and resize work on Android 13.
- The selected Wine/Box64 stack starts a known Windows test application.
- MT5 terminal startup is observed and verified on the device.
- A demo EA can be deployed, loaded and stopped without reporting simulated success.
- The full Android, Python backtester and MT5 connector CI suites pass.

Until then, filesystem preflight success is not equivalent to a running terminal, and the MT5 launch control must remain disabled.
