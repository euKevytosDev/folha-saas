#!/usr/bin/env bash
# Falha se frontend/ e static/ estiverem dessincronizados.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$TMP/frontend" "$TMP/static"
rsync -a --exclude '.DS_Store' \
  frontend/css frontend/js frontend/assets frontend/pages frontend/public \
  "$TMP/frontend/"

python3 scripts/stamp-asset-versions.py "$TMP/frontend/pages" "$TMP/frontend" >/dev/null

rsync -a --delete \
  --exclude '.DS_Store' \
  "$TMP/frontend/css" "$TMP/frontend/js" "$TMP/frontend/assets" \
  "$TMP/frontend/pages" "$TMP/frontend/public" \
  "$TMP/static/"

if ! diff -rq "$TMP/static" backend/src/main/resources/static >/dev/null; then
  echo "ERRO: frontend/ e backend/.../static estão dessincronizados." >&2
  echo "Rode: bash scripts/sync-frontend.sh && git add frontend backend/src/main/resources/static" >&2
  diff -rq "$TMP/static" backend/src/main/resources/static >&2 || true
  exit 1
fi

echo "OK: frontend e static estão sincronizados."
