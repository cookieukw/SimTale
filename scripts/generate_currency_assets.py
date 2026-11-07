"""Generates the SimTale currency and piggy bank assets.

The models are deterministic part lists (no LLM geometry): a coin disc and a
small piggy bank. Every box maps its faces into one 32x32 tile of a 128x128
texture atlas, so a part can have its own flat material without needing a
separate texture file.

Run from the repository root:

    python3 scripts/generate_currency_assets.py
"""
from __future__ import annotations

import json
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RESOURCES = os.path.join(ROOT, "src", "main", "resources")
MODELS = os.path.join(RESOURCES, "Common", "Resources")
ICONS = os.path.join(RESOURCES, "Common", "Icons", "Items")

TILE = 32
ATLAS = 128


def tile_pos(index: int) -> tuple[int, int]:
    return (index % 4) * TILE, (index // 4) * TILE


def shape_none() -> dict:
    return {
        "type": "none",
        "offset": {"x": 0, "y": 0, "z": 0},
        "stretch": {"x": 1, "y": 1, "z": 1},
        "settings": {"isPiece": True},
        "textureLayout": {},
        "unwrapMode": "custom",
        "visible": True,
        "doubleSided": False,
        "shadingMode": "flat",
    }


def texture_layout(tiles: dict[str, int]) -> dict:
    faces = {}
    for face in ("back", "right", "front", "left", "top", "bottom"):
        x, y = tile_pos(tiles.get(face, tiles["all"]))
        faces[face] = {
            "offset": {"x": x, "y": y},
            "mirror": {"x": False, "y": False},
            "angle": 0,
        }
    return faces


def box(name: str, size: tuple[float, float, float], center: tuple[float, float, float],
        tiles: dict[str, int]) -> dict:
    return {
        "name": name,
        "position": {"x": center[0], "y": center[1], "z": center[2]},
        "orientation": {"x": 0, "y": 0, "z": 0, "w": 1},
        "shape": {
            "type": "box",
            "offset": {"x": 0, "y": 0, "z": 0},
            "stretch": {"x": 1, "y": 1, "z": 1},
            "settings": {
                "isPiece": False,
                "size": {"x": size[0], "y": size[1], "z": size[2]},
            },
            "textureLayout": texture_layout(tiles),
            "unwrapMode": "custom",
            "visible": True,
            "doubleSided": False,
            "shadingMode": "flat",
        },
        "children": [],
    }


def build_model(parts: list[dict], filename: str) -> None:
    min_y = min(part["position"]["y"] - part["shape"]["settings"]["size"]["y"] / 2 for part in parts)
    root = {
        "id": "1",
        "name": "Root",
        "position": {"x": 0, "y": -min_y, "z": 0},
        "orientation": {"x": 0, "y": 0, "z": 0, "w": 1},
        "shape": shape_none(),
        "children": [],
    }
    for index, part in enumerate(parts, start=2):
        part = json.loads(json.dumps(part))
        part["id"] = str(index)
        root["children"].append(part)

    model = {"nodes": [root], "format": "character", "lod": "auto"}
    path = os.path.join(MODELS, filename)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(model, handle, indent=2)
        handle.write("\n")
    print("wrote", os.path.relpath(path, ROOT))


def paint_tile(image: Image.Image, index: int, color: tuple[int, int, int],
               shade: int = 0) -> None:
    """Paints one flat material tile with a subtle vertical light gradient."""
    x, y = tile_pos(index)
    draw = ImageDraw.Draw(image)
    for row in range(TILE):
        factor = 1.0 + (row / TILE - 0.5) * 0.12 + shade
        rgb = tuple(max(0, min(255, int(channel * factor))) for channel in color)
        draw.line([(x, y + row), (x + TILE - 1, y + row)], fill=rgb + (255,))


def make_coin_texture(path: str, base: tuple[int, int, int], dark: tuple[int, int, int],
                      light: tuple[int, int, int]) -> None:
    image = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    paint_tile(image, 0, base)
    paint_tile(image, 1, dark, shade=0.05)
    paint_tile(image, 2, light, shade=0.03)
    paint_tile(image, 3, dark, shade=-0.05)
    image.save(path)
    print("wrote", os.path.relpath(path, ROOT))


def make_piggy_texture(path: str) -> None:
    image = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    paint_tile(image, 0, (232, 154, 176))   # body pink
    paint_tile(image, 1, (206, 120, 146))   # legs / shadow pink
    paint_tile(image, 2, (250, 190, 204))   # snout / ear inner
    paint_tile(image, 3, (58, 38, 48))      # slot / nostrils
    paint_tile(image, 4, (255, 255, 255))   # eye white
    paint_tile(image, 5, (30, 24, 32))      # pupil / mouth
    paint_tile(image, 6, (232, 185, 34))    # coin in the slot
    paint_tile(image, 7, (196, 146, 24))    # coin shadow
    image.save(path)
    print("wrote", os.path.relpath(path, ROOT))


def make_coin_icon(path: str, base: tuple[int, int, int], dark: tuple[int, int, int],
                   light: tuple[int, int, int]) -> None:
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.ellipse((10, 8, 54, 52), fill=dark + (255,))
    draw.ellipse((11, 10, 53, 50), fill=base + (255,))
    draw.ellipse((17, 15, 47, 45), outline=light + (255,), width=2)
    draw.polygon([(32, 20), (40, 32), (32, 44), (24, 32)], fill=light + (255,))
    draw.polygon([(32, 25), (36, 32), (32, 39), (28, 32)], fill=dark + (255,))
    draw.arc((10, 8, 54, 52), 205, 335, fill=light + (255,), width=2)
    image.save(path)
    print("wrote", os.path.relpath(path, ROOT))


def make_piggy_icon(path: str) -> None:
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.ellipse((8, 18, 56, 52), fill=(232, 154, 176, 255))
    draw.ellipse((22, 8, 42, 28), fill=(232, 154, 176, 255))
    draw.polygon([(22, 12), (16, 2), (28, 8)], fill=(206, 120, 146, 255))
    draw.polygon([(42, 12), (48, 2), (36, 8)], fill=(206, 120, 146, 255))
    draw.ellipse((14, 30, 50, 48), fill=(250, 190, 204, 255))
    draw.ellipse((19, 33, 25, 39), fill=(58, 38, 48, 255))
    draw.ellipse((39, 33, 45, 39), fill=(58, 38, 48, 255))
    draw.ellipse((24, 39, 40, 47), fill=(206, 120, 146, 255))
    draw.ellipse((28, 41, 31, 44), fill=(58, 38, 48, 255))
    draw.ellipse((33, 41, 36, 44), fill=(58, 38, 48, 255))
    draw.rectangle((14, 49, 22, 56), fill=(206, 120, 146, 255))
    draw.rectangle((42, 49, 50, 56), fill=(206, 120, 146, 255))
    draw.rectangle((25, 19, 39, 22), fill=(58, 38, 48, 255))
    draw.ellipse((28, 14, 36, 20), fill=(232, 185, 34, 255))
    image.save(path)
    print("wrote", os.path.relpath(path, ROOT))


def coin_parts() -> list[dict]:
    base = {"all": 0, "top": 2, "bottom": 1}
    return [
        # A plus-shaped disc reads as a round coin from the held-item camera and
        # stays inside a handful of boxes, unlike a 12-sided approximation.
        box("disc_horizontal", (9, 0.9, 6), (0, 0.65, 0), base),
        box("disc_vertical", (6, 0.9, 9), (0, 0.65, 0), base),
        box("rim", (7, 0.4, 7), (0, 1.3, 0), {"all": 1, "top": 1}),
        box("face", (5.5, 0.35, 5.5), (0, 1.55, 0), {"all": 2, "top": 2}),
        box("emblem", (2.2, 0.3, 2.2), (0, 1.8, 0), {"all": 3, "top": 3}),
    ]


def piggy_parts() -> list[dict]:
    body = {"all": 0, "top": 2}
    return [
        box("body", (16, 10, 12), (0, 7.5, 0), body),
        box("back_hump", (12, 3, 8), (0, 13, -0.5), {"all": 0, "top": 2}),
        box("snout", (5, 3.5, 2), (0, 7.5, 7), {"all": 2}),
        box("nostril_left", (0.8, 0.8, 0.4), (-1.2, 7.8, 8.1), {"all": 3}),
        box("nostril_right", (0.8, 0.8, 0.4), (1.2, 7.8, 8.1), {"all": 3}),
        box("eye_left", (1.8, 1.8, 0.5), (-3.3, 11, 6.1), {"all": 4, "front": 4}),
        box("eye_right", (1.8, 1.8, 0.5), (3.3, 11, 6.1), {"all": 4, "front": 4}),
        box("pupil_left", (0.8, 0.8, 0.35), (-3.3, 11, 6.45), {"all": 5, "front": 5}),
        box("pupil_right", (0.8, 0.8, 0.35), (3.3, 11, 6.45), {"all": 5, "front": 5}),
        box("ear_left", (2.5, 2.5, 1.5), (-4.8, 15, 0), {"all": 0, "front": 2}),
        box("ear_right", (2.5, 2.5, 1.5), (4.8, 15, 0), {"all": 0, "front": 2}),
        box("slot", (5, 0.5, 1.4), (0, 14.6, -1), {"all": 3}),
        box("slot_coin", (3, 0.7, 0.8), (0, 15.1, -1), {"all": 6, "top": 6}),
        box("leg_fl", (2.6, 2.5, 3), (-5.5, 1.25, 3.6), {"all": 1}),
        box("leg_fr", (2.6, 2.5, 3), (5.5, 1.25, 3.6), {"all": 1}),
        box("leg_bl", (2.6, 2.5, 3), (-5.5, 1.25, -3.6), {"all": 1}),
        box("leg_br", (2.6, 2.5, 3), (5.5, 1.25, -3.6), {"all": 1}),
        box("tail_1", (1.4, 1.4, 1.4), (0, 8.5, -6.8), {"all": 1}),
        box("tail_2", (1, 1, 1), (0, 9.6, -7.4), {"all": 1}),
    ]


def main() -> None:
    os.makedirs(os.path.join(MODELS, "SimTale_Coin"), exist_ok=True)
    os.makedirs(os.path.join(MODELS, "SimTale_PiggyBank"), exist_ok=True)
    os.makedirs(ICONS, exist_ok=True)

    build_model(coin_parts(), os.path.join("SimTale_Coin", "SimTale_Coin.blockymodel"))
    build_model(piggy_parts(), os.path.join("SimTale_PiggyBank", "SimTale_PiggyBank.blockymodel"))

    make_coin_texture(
        os.path.join(MODELS, "SimTale_Coin", "SimTale_Coin_Copper_texture.png"),
        (181, 107, 59), (125, 59, 32), (220, 154, 100))
    make_coin_texture(
        os.path.join(MODELS, "SimTale_Coin", "SimTale_Coin_Thorium_texture.png"),
        (42, 112, 94), (20, 61, 58), (96, 172, 144))
    make_coin_texture(
        os.path.join(MODELS, "SimTale_Coin", "SimTale_Coin_Gold_texture.png"),
        (218, 166, 38), (154, 78, 21), (250, 220, 90))
    make_piggy_texture(os.path.join(MODELS, "SimTale_PiggyBank", "SimTale_PiggyBank_texture.png"))

    make_coin_icon(os.path.join(ICONS, "SimTale_Coin_Copper.png"),
                   (181, 107, 59), (125, 59, 32), (226, 164, 109))
    make_coin_icon(os.path.join(ICONS, "SimTale_Coin_Thorium.png"),
                   (42, 112, 94), (20, 61, 58), (104, 182, 152))
    make_coin_icon(os.path.join(ICONS, "SimTale_Coin_Gold.png"),
                   (218, 166, 38), (154, 78, 21), (252, 226, 100))
    make_piggy_icon(os.path.join(ICONS, "SimTale_PiggyBank.png"))


if __name__ == "__main__":
    main()
