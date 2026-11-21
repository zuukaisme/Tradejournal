#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

OUT="${1:-$HOME/storage/shared/Download/tradepulse-headless-x11.zip}"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
ROOT="$STAGE/runtime"
mkdir -p "$ROOT/bin" "$ROOT/lib" "$ROOT/share/X11" "$ROOT/share/fonts" "$ROOT/etc/fonts"

if [ ! -x "$PREFIX/bin/Xvfb" ]; then
    printf '%s\n' "Xvfb is not installed. Install xorg-server-xvfb from the Termux X11 repository first."
    exit 1
fi

if ! command -v ldd >/dev/null 2>&1; then
    printf '%s\n' "The ldd command is missing. Install binutils in Termux and retry."
    exit 1
fi

cp -L "$PREFIX/bin/Xvfb" "$ROOT/bin/Xvfb"
QUEUE=("$PREFIX/bin/Xvfb")
declare -A SEEN=()

while [ "${#QUEUE[@]}" -gt 0 ]; do
    CURRENT="${QUEUE[0]}"
    QUEUE=("${QUEUE[@]:1}")
    OUTPUT="$(ldd "$CURRENT" 2>&1)"
    if printf '%s' "$OUTPUT" | grep -q "not found"; then
        printf '%s\n' "$OUTPUT"
        printf '%s\n' "A required Xvfb library is missing. Repair the Termux package installation and retry."
        exit 1
    fi
    while IFS= read -r LIB; do
        case "$LIB" in
            "$PREFIX"/*)
                [ -f "$LIB" ] || continue
                REAL="$(readlink -f "$LIB")"
                [ -n "$REAL" ] || continue
                [ -n "${SEEN[$REAL]+x}" ] && continue
                SEEN["$REAL"]=1
                cp -L "$LIB" "$ROOT/lib/$(basename "$LIB")"
                QUEUE+=("$LIB")
                ;;
        esac
    done < <(printf '%s\n' "$OUTPUT" | awk '{ for (i = 1; i <= NF; i++) if ($i ~ /^\//) { print $i; break } }' | sort -u)
done

if [ -d "$PREFIX/share/X11/xkb" ]; then
    cp -a "$PREFIX/share/X11/xkb" "$ROOT/share/X11/"
else
    printf '%s\n' "Termux xkeyboard-config data is missing."
    exit 1
fi

if [ -d "$PREFIX/share/fonts/misc" ]; then
    cp -a "$PREFIX/share/fonts/." "$ROOT/share/fonts/"
else
    printf '%s\\n' "The Xorg misc fonts are missing. Install xorg-fonts-misc in Termux and retry."
    exit 1
fi

if [ -d "$PREFIX/etc/fonts" ]; then
    cp -a "$PREFIX/etc/fonts/." "$ROOT/etc/fonts/"
fi

printf 'Termux Xvfb runtime export\nPrefix: %s\nArchitecture: %s\n' "$PREFIX" "$(uname -m)" > "$ROOT/manifest.txt"
mkdir -p "$(dirname "$OUT")"
rm -f "$OUT"
(
    cd "$ROOT"
    zip -qr "$OUT" .
)
printf 'Created %s\n' "$OUT"
printf 'Bundle size: '
du -h "$OUT" | awk '{print $1}'
