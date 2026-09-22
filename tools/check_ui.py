#!/usr/bin/env python3
"""Static checks for the mod's .ui files.

The client refuses to load ANY custom UI when a single file is malformed, and it reports one error
per connection attempt. Testing by restarting the server therefore costs one round trip per typo.
This finds every problem in one pass, before the game is ever launched.

Checks, all of them rules learned from real crashes:

1. Relative asset paths must resolve. Paths are relative to the .ui file's own directory —
   `../textures/x.png` from `Common/UI/Custom/` points at `Common/UI/textures/`, not at
   `Common/UI/Custom/textures/`.
2. `%key` translation references must not contain underscores. The parser stops at `_`
   ("Expected ;, found _") and takes the whole client down with it.
3. Every `%key` must exist in the matching .lang file. A missing key does not fail the parse — it
   renders as the raw key in game, which is much harder to notice.
4. Braces and parens must balance.

Usage:  python3 tools/check_ui.py
Exit code is non-zero when anything is wrong, so it can gate a build.
"""

import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RESOURCES = os.path.join(REPO, "src/main/resources")
UI_ROOT = os.path.join(RESOURCES, "Common/UI")
LANG_DIR = os.path.join(RESOURCES, "Server/Languages")

# A .ui may reference the game's own assets as freely as the mod's — the theme is built almost
# entirely out of Hytale's button and panel textures. Without this root the checker floods with
# false positives on paths that work perfectly in game.
GAME_ASSETS = os.environ.get("HYTALE_ASSETS", os.path.expanduser("~/Documents/Assets"))

ASSET_RE = re.compile(r'(?:TexturePath|Background|Image|Icon)\s*:\s*"([^"]+\.(?:png|jpg|jpeg|svg))"')
IMPORT_RE = re.compile(r'\$\w+\s*=\s*"([^"]+\.ui)"')
KEY_RE = re.compile(r'%([A-Za-z0-9._]+)')


def strip_comments(text):
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//.*", "", text)


def load_lang_keys():
    """Keys from every .lang file, namespaced by file stem the way Message.translation sees them."""
    keys = set()
    for root, _, files in os.walk(LANG_DIR):
        for f in files:
            if not f.endswith(".lang"):
                continue
            namespace = f[:-5]
            for line in open(os.path.join(root, f), encoding="utf8"):
                if "=" in line and not line.strip().startswith("#"):
                    keys.add(namespace + "." + line.split("=")[0].strip())
    return keys


def search_dirs(here):
    """Where a relative reference from `here` may legitimately point: the mod, then the game."""
    dirs = [here]
    if GAME_ASSETS and os.path.isdir(GAME_ASSETS):
        inside_resources = os.path.relpath(here, RESOURCES)
        if not inside_resources.startswith(".."):
            dirs.append(os.path.join(GAME_ASSETS, inside_resources))
    return dirs


def resolves(here, reference):
    return any(
        os.path.exists(os.path.normpath(os.path.join(d, reference))) for d in search_dirs(here)
    )


def suggest(asset, here):
    """Point at the same file name elsewhere in the mod, which is nearly always the typo."""
    name = os.path.basename(asset)
    for root, _, files in os.walk(os.path.join(RESOURCES, "Common/UI")):
        if name in files:
            return f'  -> existe em "{os.path.relpath(os.path.join(root, name), here)}"'
    return ""


def main():
    lang_keys = load_lang_keys()
    problems = []
    mod_ui_names = {f for _, _, fs in os.walk(UI_ROOT) for f in fs if f.endswith(".ui")}

    ui_files = []
    for root, _, files in os.walk(UI_ROOT):
        for f in files:
            if f.endswith(".ui"):
                ui_files.append(os.path.join(root, f))

    for path in sorted(ui_files):
        rel = os.path.relpath(path, REPO)
        raw = open(path, encoding="utf8").read()
        text = strip_comments(raw)
        here = os.path.dirname(path)

        braces = text.count("{") - text.count("}")
        parens = text.count("(") - text.count(")")
        if braces:
            problems.append(f"{rel}: chaves desbalanceadas ({braces:+d})")
        if parens:
            problems.append(f"{rel}: parenteses desbalanceados ({parens:+d})")

        for match in ASSET_RE.finditer(text):
            asset = match.group(1)
            line = raw[: match.start()].count("\n") + 1
            if resolves(here, asset):
                continue
            # Only a mod asset can be proven wrong. The client ships UI textures that are not in
            # the extracted asset pack, so an unresolved path whose file name appears nowhere in
            # this repo is assumed to be one of those rather than reported as a false alarm.
            hint = suggest(asset, here)
            if hint:
                problems.append(f"{rel}:{line}: asset do mod referenciado errado: {asset}{hint}")

        for match in IMPORT_RE.finditer(text):
            imported = match.group(1)
            line = raw[: match.start()].count("\n") + 1
            if not resolves(here, imported) and os.path.basename(imported) in mod_ui_names:
                problems.append(f"{rel}:{line}: import .ui do mod referenciado errado: {imported}")

        for match in KEY_RE.finditer(text):
            key = match.group(1)
            line = raw[: match.start()].count("\n") + 1
            if "_" in key:
                camel = re.sub(r"_(\w)", lambda m: m.group(1).upper(), key)
                problems.append(
                    f"{rel}:{line}: chave com underscore quebra o parser: %{key}  -> use %{camel}"
                )
            elif key not in lang_keys:
                problems.append(f"{rel}:{line}: chave ausente nos .lang: %{key}")

    print(f"Arquivos .ui verificados: {len(ui_files)}")
    if not problems:
        print("Nenhum problema encontrado.")
        return 0

    print(f"\n{len(problems)} problema(s):\n")
    for p in problems:
        print("  " + p)
    return 1


if __name__ == "__main__":
    sys.exit(main())
