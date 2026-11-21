# TradePulse headless X11 runtime

TradePulse can import an Android ARM64 Xvfb bundle exported from the user's existing Termux installation. The export script copies Xvfb's resolved Termux library dependencies and its XKB/font data; it does not download binaries itself.

In Termux:

```sh
termux-setup-storage
pkg install x11-repo
pkg install xorg-server-xvfb binutils zip
```

If those packages are already installed, no package reinstall is needed. Then run the export script from the TradePulse repository:

```sh
bash scripts/export-termux-xvfb-runtime.sh
```

The ZIP is created in `~/storage/shared/Download/tradepulse-headless-x11.zip`. Import that ZIP using the Headless X11 Runtime control in TradePulse.

The exported Xvfb executable is ARM64/Bionic, not the x86_64 Xvfb prototype used by the old runtime ZIP. TradePulse still validates the Wine/ARM64EC environment and probes Wine separately; importing Xvfb alone does not mean MT5 is running.
