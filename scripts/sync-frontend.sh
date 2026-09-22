#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/frontend"
DEST="$ROOT/backend/src/main/resources/static"

mkdir -p "$DEST"

# 1) Cache-bust nos HTML da fonte de verdade
python3 "$ROOT/scripts/stamp-asset-versions.py" "$SRC/pages" "$SRC"

# 2) Copia espelhada para o static do backend (Render/JAR)
rsync -a --delete \
  --exclude '.DS_Store' \
  "$SRC/css" "$SRC/js" "$SRC/assets" "$SRC/pages" "$SRC/public" \
  "$DEST/"

echo "Frontend sincronizado em $DEST"
echo "Edite apenas frontend/; rode este script antes de commit."
