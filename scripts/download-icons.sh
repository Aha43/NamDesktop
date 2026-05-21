#!/usr/bin/env bash
# Downloads named Tabler Icons (MIT licence) into src/icons/.
# Usage: bash scripts/download-icons.sh
# Add icon names (without .svg) to the ICONS array as needed.

set -euo pipefail

ICONS_DIR="$(dirname "$0")/../src/icons"
BASE_URL="https://raw.githubusercontent.com/tabler/tabler-icons/main/icons/outline"

ICONS=(
  plus
  search
  tag
  trash
  pencil
)

mkdir -p "$ICONS_DIR"

for icon in "${ICONS[@]}"; do
  dest="$ICONS_DIR/$icon.svg"
  if [ -f "$dest" ]; then
    echo "Already exists: $icon.svg"
  else
    echo "Downloading $icon.svg..."
    curl -fsSL "$BASE_URL/$icon.svg" -o "$dest"
  fi
done

echo "Done. Icons in $ICONS_DIR"
