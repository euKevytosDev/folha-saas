#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/frontend"
DEST="$ROOT/backend/src/main/resources/static"

mkdir -p "$DEST"
rsync -a --delete \
  --exclude '.DS_Store' \
  "$SRC/css" "$SRC/js" "$SRC/assets" "$SRC/pages" "$SRC/public" \
  "$DEST/"

echo "Frontend sincronizado em $DEST"
