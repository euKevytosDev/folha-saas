#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SITE="$ROOT/site"
API_URL="${FOLHA_API_URL:-}"
BASE_PATH="${FOLHA_BASE_PATH:-}"

rm -rf "$SITE"
mkdir -p "$SITE"
cp -R "$ROOT/frontend/css" "$ROOT/frontend/js" "$ROOT/frontend/assets" "$SITE/"
cp -R "$ROOT/frontend/pages" "$SITE/pages"
cp "$ROOT/frontend/public/favicon.svg" "$SITE/favicon.svg"
cp "$ROOT/frontend/public/robots.txt" "$SITE/robots.txt"
cp "$ROOT/frontend/pages/"*.html "$SITE/"
cp "$ROOT/frontend/pages/404.html" "$SITE/404.html"

# Cache-bust com hash do conteúdo (antes de reescrever caminhos)
python3 "$ROOT/scripts/stamp-asset-versions.py" "$SITE/pages" "$SITE"
# HTMLs na raiz do site também
python3 "$ROOT/scripts/stamp-asset-versions.py" "$SITE" "$SITE"

python3 - "$SITE" "$API_URL" "$BASE_PATH" <<'PY'
import pathlib
import re
import sys

site, api_url, base_path = sys.argv[1], sys.argv[2].rstrip("/"), sys.argv[3].rstrip("/")
env = pathlib.Path(site) / "js" / "env.js"
env.write_text(
    "window.__FOLHA__ = {\n"
    f"    apiBaseUrl: {api_url!r},\n"
    f"    basePath: {base_path!r},\n"
    "    staticHost: true\n"
    "};\n",
    encoding="utf-8",
)

replacements = [
    ('href="/css/', f'href="{base_path}/css/' if base_path else 'href="css/'),
    ('href="/js/', f'href="{base_path}/js/' if base_path else 'href="js/'),
    ('src="/js/', f'src="{base_path}/js/' if base_path else 'src="js/'),
    ('src="/assets/', f'src="{base_path}/assets/' if base_path else 'src="assets/'),
    ('href="/assets/', f'href="{base_path}/assets/' if base_path else 'href="assets/'),
    ('href="/favicon.svg"', f'href="{base_path}/favicon.svg"' if base_path else 'href="favicon.svg"'),
    ('href="/login"', f'href="{base_path}/login.html"' if base_path else 'href="login.html"'),
    ('href="/cadastro"', f'href="{base_path}/cadastro.html"' if base_path else 'href="cadastro.html"'),
    ('href="/recuperar-senha"', f'href="{base_path}/recuperar-senha.html"' if base_path else 'href="recuperar-senha.html"'),
    ('href="/redefinir-senha"', f'href="{base_path}/redefinir-senha.html"' if base_path else 'href="redefinir-senha.html"'),
    ('href="/admin"', f'href="{base_path}/admin.html"' if base_path else 'href="admin.html"'),
    ('href="/superadmin"', f'href="{base_path}/superadmin.html"' if base_path else 'href="superadmin.html"'),
    ('href="/checkout"', f'href="{base_path}/checkout.html"' if base_path else 'href="checkout.html"'),
    ('href="/pedido"', f'href="{base_path}/pedido.html"' if base_path else 'href="pedido.html"'),
    ('href="/"', f'href="{base_path}/index.html"' if base_path else 'href="index.html"'),
]

for html in pathlib.Path(site).rglob("*.html"):
    text = html.read_text(encoding="utf-8")
    for old, new in replacements:
        text = text.replace(old, new)
    html.write_text(text, encoding="utf-8")
PY

echo "Site estático gerado em $SITE"
