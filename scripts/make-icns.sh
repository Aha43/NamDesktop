#!/usr/bin/env bash
# Generate packaging/macos/NamDesktop.icns from src/icons/logo-mark.svg.
# Requires: iconutil (macOS built-in) and sips (macOS built-in).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$SCRIPT_DIR/.."
SRC="$ROOT/src/icons/logo-mark.svg"
ICONSET="$ROOT/packaging/macos/NamDesktop.iconset"
OUT="$ROOT/packaging/macos/NamDesktop.icns"

mkdir -p "$ICONSET"

for SIZE in 16 32 64 128 256 512; do
  sips -s format png -z "$SIZE" "$SIZE" "$SRC" --out "$ICONSET/icon_${SIZE}x${SIZE}.png" > /dev/null
  DOUBLE=$((SIZE * 2))
  sips -s format png -z "$DOUBLE" "$DOUBLE" "$SRC" --out "$ICONSET/icon_${SIZE}x${SIZE}@2x.png" > /dev/null
done

iconutil -c icns "$ICONSET" -o "$OUT"
rm -rf "$ICONSET"

echo "Generated: $OUT"
