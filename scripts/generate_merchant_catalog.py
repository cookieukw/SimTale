"""Builds the travelling merchant's item pool from the installed Hytale assets.

The merchant used to carry only three hand-picked entries per specialty, which meant most visits
had nothing worth buying. This script selects a broad, level-appropriate pool (400 entries) from
the vanilla item list: ores, bars, gems, food, potions, seeds, tools, weapons, armour, decoration
and ingredients.

The generated JSON is committed with the mod, so players do not need this script or a local
Hytale install. Run it only when the Hytale item list changes:

    python3 scripts/generate_merchant_catalog.py

Set HYTALE_ASSETS to the Assets.zip path if it is not in the default launcher location.
"""
from __future__ import annotations

import json
import os
import re
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUTPUT = os.path.join(ROOT, "src", "main", "resources", "Common", "SimTale",
                      "merchant_catalog.json")
DEFAULT_ASSETS = ("/home/cookie/.var/app/com.hypixel.HytaleLauncher/data/Hytale/"
                  "install/release/package/game/latest/Assets.zip")

BAD_MARKERS = ("template", "debug", "test", "creative", "placeholder", "empty",
               "unused", "dev", "admin", "spawnegg")

QUALITY_FACTOR = {
    "common": 1.0,
    "uncommon": 1.25,
    "rare": 1.8,
    "epic": 2.8,
    "legendary": 4.5,
}

BASE_PRICE = {
    "ore": 4,
    "bar": 12,
    "gem": 22,
    "food": 4,
    "potion": 10,
    "seed": 3,
    "tool": 22,
    "weapon": 26,
    "armor": 24,
    "deco": 8,
    "ingredient": 5,
}

QUANTITY = {
    "ore": (2, 8),
    "bar": (1, 4),
    "gem": (1, 2),
    "food": (2, 8),
    "potion": (1, 3),
    "seed": (4, 16),
    "tool": (1, 1),
    "weapon": (1, 1),
    "armor": (1, 1),
    "deco": (1, 3),
    "ingredient": (2, 8),
}

# Exactly 400 entries. The pool is deliberately wider than one visit; each merchant rolls five
# distinct entries from the selected specialty.
QUOTAS = {
    "ore": 40,
    "bar": 11,
    "gem": 7,
    "food": 27,
    "potion": 36,
    "seed": 50,
    "tool": 40,
    "weapon": 70,
    "armor": 60,
    "deco": 30,
    "ingredient": 29,
}

FEATURED_KINDS = {"bar", "gem", "tool", "weapon", "armor"}


def kind_of(item_id: str) -> str | None:
    if item_id.startswith("Ore_"):
        return "ore"
    if item_id.startswith("Ingredient_Bar_"):
        return "bar"
    if item_id.startswith("Rock_Gem_"):
        return "gem"
    if item_id.startswith("Food_"):
        return "food"
    if item_id.startswith("Potion_"):
        return "potion"
    if item_id.startswith("Plant_Seeds_"):
        return "seed"
    if item_id.startswith("Tool_"):
        return "tool"
    if item_id.startswith("Weapon_"):
        return "weapon"
    if item_id.startswith("Armor_"):
        return "armor"
    if item_id.startswith("Deco_"):
        return "deco"
    if item_id.startswith("Ingredient_"):
        return "ingredient"
    return None


def usable(item_id: str, kind: str) -> bool:
    lowered = item_id.lower()
    if any(marker in lowered for marker in BAD_MARKERS):
        return False
    if kind == "seed" and item_id.endswith("_Eternal"):
        return False
    return True


def even_pick(items: list[dict], count: int) -> list[dict]:
    if count >= len(items):
        return items
    if count <= 1:
        return items[:count]
    # Spread across the sorted list instead of taking the first N, which would cluster around a
    # single material tier when the ids are alphabetical.
    return [items[round(index * (len(items) - 1) / (count - 1))] for index in range(count)]


def assets_path() -> str:
    env = os.environ.get("HYTALE_ASSETS")
    if env:
        return env
    local = os.path.join(ROOT, "local.properties")
    if os.path.isfile(local):
        with open(local, encoding="utf-8") as handle:
            for line in handle:
                if line.strip().startswith("hytale.assets="):
                    return line.split("=", 1)[1].strip()
    return DEFAULT_ASSETS


def iter_item_files(archive: str):
    """Yields (itemId, parsedJson) from either an Assets.zip or an unpacked Assets folder."""
    if os.path.isdir(archive):
        root = os.path.join(archive, "Server", "Item", "Items")
        for directory, _, files in os.walk(root):
            for filename in files:
                if not filename.endswith(".json"):
                    continue
                path = os.path.join(directory, filename)
                try:
                    with open(path, encoding="utf-8") as handle:
                        yield filename[:-5], json.load(handle)
                except Exception:
                    continue
        return

    with zipfile.ZipFile(archive) as zipped:
        for name in zipped.namelist():
            if not name.startswith("Server/Item/Items/") or not name.endswith(".json"):
                continue
            try:
                yield os.path.basename(name)[:-5], json.loads(zipped.read(name))
            except Exception:
                continue


def main() -> None:
    archive = assets_path()
    if not os.path.exists(archive):
        raise SystemExit(f"Assets.zip/folder not found: {archive}")

    by_kind: dict[str, list[dict]] = {kind: [] for kind in QUOTAS}
    for item_id, data in iter_item_files(archive):
        kind = kind_of(item_id)
        if kind is None or not usable(item_id, kind):
            continue
        quality = str(data.get("Quality") or "Common").lower()
        level = data.get("ItemLevel") or 1
        try:
            level = int(level)
        except (TypeError, ValueError):
            level = 1
        by_kind[kind].append({"itemId": item_id, "quality": quality, "level": level})

    entries = []
    for kind, quota in QUOTAS.items():
        candidates = sorted(by_kind[kind], key=lambda entry: entry["itemId"])
        for item in even_pick(candidates, quota):
            factor = QUALITY_FACTOR.get(item["quality"], 1.0)
            buy = max(2, round((BASE_PRICE[kind] + item["level"] * 0.35) * factor))
            buy = min(buy, 300)
            sell = max(1, min(buy - 1, round(buy * 0.5)))
            minimum, maximum = QUANTITY[kind]
            entries.append({
                "itemId": item["itemId"],
                "kind": kind,
                "featured": kind in FEATURED_KINDS,
                "minQuantity": minimum,
                "maxQuantity": maximum,
                "buyPrice": buy,
                "sellPrice": sell,
            })

    entries.sort(key=lambda entry: (entry["kind"], entry["itemId"]))
    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    with open(OUTPUT, "w", encoding="utf-8") as handle:
        json.dump({"version": 1, "entries": entries}, handle, indent=2)
        handle.write("\n")
    print(f"wrote {len(entries)} merchant entries to {os.path.relpath(OUTPUT, ROOT)}")


if __name__ == "__main__":
    main()
