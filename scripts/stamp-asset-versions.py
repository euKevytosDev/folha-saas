#!/usr/bin/env python3
"""Atualiza ?v= nos HTML com hash do arquivo referenciado (cache-bust automático)."""

from __future__ import annotations

import hashlib
import pathlib
import re
import sys

ASSET_RE = re.compile(
    r"""(?P<attr>(?:href|src))=(?P<q>["'])(?P<path>/(?:css|js)/[^"'?]+)\?v=[^"']*(?P=q)"""
)


def short_hash(path: pathlib.Path) -> str:
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    return digest[:10]


def stamp_tree(pages_dir: pathlib.Path, root: pathlib.Path) -> int:
    changed = 0
    for html in sorted(pages_dir.rglob("*.html")):
        text = html.read_text(encoding="utf-8")

        def repl(match: re.Match[str]) -> str:
            rel = match.group("path").lstrip("/")
            asset = root / rel
            version = short_hash(asset) if asset.is_file() else "missing"
            return f'{match.group("attr")}={match.group("q")}{match.group("path")}?v={version}{match.group("q")}'

        updated = ASSET_RE.sub(repl, text)
        if updated != text:
            html.write_text(updated, encoding="utf-8")
            changed += 1
    return changed


def main() -> int:
    if len(sys.argv) < 3:
        print("Uso: stamp-asset-versions.py <pages-dir> <asset-root>", file=sys.stderr)
        return 2
    pages = pathlib.Path(sys.argv[1])
    root = pathlib.Path(sys.argv[2])
    count = stamp_tree(pages, root)
    print(f"Versões de assets atualizadas em {count} HTML(s) sob {pages}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
