#!/usr/bin/env bash
# Generate packaging/macos/NamDesktop.icns from assets/logo-mark.svg.
# Requires: rsvg-convert (librsvg) and iconutil (macOS).
# Install rsvg-convert: brew install librsvg
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$SCRIPT_DIR/.."
SRC="$ROOT/assets/logo-mark.svg"
ICONSET="$ROOT/packaging/macos/NamDesktop.iconset"
OUT="$ROOT/packaging/macos/NamDesktop.icns"

if ! command -v rsvg-convert &>/dev/null; then
  echo "rsvg-convert not found. Install with: brew install librsvg" >&2
  exit 1
fi

mkdir -p "$ICONSET"

for SIZE in 16 32 64 128 256 512; do
  rsvg-convert -w "$SIZE" -h "$SIZE" "$SRC" -o "$ICONSET/icon_${SIZE}x${SIZE}.png"
  DOUBLE=$((SIZE * 2))
  rsvg-convert -w "$DOUBLE" -h "$DOUBLE" "$SRC" -o "$ICONSET/icon_${SIZE}x${SIZE}@2x.png"
done

iconutil -c icns "$ICONSET" -o "$OUT"
rm -rf "$ICONSET"

echo "Generated: $OUT"
