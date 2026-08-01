#!/usr/bin/env python3
"""
Patches the generated NPC appearance models in place. Nothing is regenerated and no file is
ever deleted — each model is loaded, adjusted and written back.

FOUR PROBLEMS, MEASURED BEFORE FIXING
-------------------------------------
1. Bare torso (57 models).
   Half the roster has no `Undertops` piece, but that is mostly harmless: a long-sleeve
   Overtop covers the body on its own. The real problem is the 57 whose only upper-body item
   is an accessory — Cheststrap, Necklace, Arm_Band, Scarf, Belt — or who have nothing at all.
   The Player model has no anatomy, so those read as an undressed torso.

2. Eyes were never coloured (820 of 820).
   Every eye attachment points at a greyscale texture with no GradientSet at all, so it
   renders flat grey. The game ships an `Eyes_Gradient` set with 18 tints
   (Cosmetics/CharacterCreator/GradientSets.json) that the generator simply never used.

3. Mouths were always tinted `Skin` (820 of 820).
   Half of them use `Makeup_Greyscale.png`, a texture that exists precisely to be tinted a
   lip colour. Tinting it with the skin tone throws that away.

4. Skirts and dresses on male models (11 occurrences, all children).

WHAT IS *NOT* TOUCHED
---------------------
Anything already dressed keeps its outfit: this only adds an undertop where the torso is
actually exposed. Existing garments, textures and their gradients are left alone.

Choices are deterministic — seeded from the file name — so re-running produces the same
roster instead of reshuffling everyone's face, and so `--check` can be trusted.

USAGE
    python3 scripts/fix_npc_appearance.py            # apply
    python3 scripts/fix_npc_appearance.py --check    # report only
    python3 scripts/fix_npc_appearance.py --revert   # undo (restores from the backup below)
"""

import json
import os
import random
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODELS_DIR = ROOT / "src/main/resources/Server/Models/Generated"
BACKUP = Path(__file__).resolve().parent / "appearance_backup.json"

MARKER = "fix_npc_appearance"

# ---------------------------------------------------------------- eyes
# Ids straight out of Cosmetics/CharacterCreator/GradientSets.json -> Eyes_Gradient.
# White and Red are deliberately excluded: they read as undead rather than as a person.
EYE_COLORS = [
    "Brown", "BrownDark", "BrownLight", "Honey", "Blue", "BlueLight",
    "Green", "GreenLight", "Grey", "Turquoise", "Black", "Blond",
    "Orange", "Purple", "Pink",
]

# ---------------------------------------------------------------- mouths
# Only applied to the Makeup texture, which is built to be tinted. Mouths using
# Default_Greyscale keep the skin tint, because a lip colour on the plain mouth shape looks
# painted on rather than natural.
LIP_COLORS = [
    ("Pastel_Cotton", "Red"), ("Pastel_Cotton", "Carmin"), ("Pastel_Cotton", "Pink"),
    ("Colored_Cotton", "Red"), ("Fantasy_Cotton", "Red"), ("Fantasy_Cotton_Dark", "Red"),
]

# ---------------------------------------------------------------- undertops
# Every entry below is copied verbatim from a model that already ships with it, so these are
# known-good model/texture/gradient triples rather than paths assembled by hand.
UNDERTOPS_ADULT = [
    ("Cosmetics/Undertops/LongSleeveShirt.blockymodel",
     "Cosmetics/Undertops/LongSleeveShirt_Textures/Flowy_Shirt_Greyscale.png", "Pastel_Cotton"),
    ("Cosmetics/Undertops/Tshirt.blockymodel",
     "Cosmetics/Undertops/Tshirt_Textures/Wideneck_Tshirt_Greyscale.png", "Colored_Cotton"),
    ("Cosmetics/Undertops/BasicUndertop.blockymodel",
     "Cosmetics/Undertops/BasicTop_Textures/FrostyF_Top_Texture.png", "Colored_Cotton"),
]
# Child garments live under NPC/Player_Child/, NOT under the bare Cosmetics/ path the adults use.
# The MODEL is what moves; the TEXTURE stays shared with the adult version.
#
# This list originally reused the adult convention and just appended "_Child" to the filename,
# which produced paths no asset ever matched. The result was not a missing shirt: the whole model
# failed to register, so every role pointing at it died with
#   IllegalStateException: The model with the name "..." does not exist for attribute "Appearance"
# and the mod refused to load. 34 models were bricked that way. Derive these by copying from a
# model that already ships the garment — never by pattern-matching a filename.
UNDERTOPS_CHILD = [
    ("NPC/Player_Child/Cosmetics/Undertops/LongSleeveShirt_Child.blockymodel",
     "Cosmetics/Undertops/LongSleeveShirt_Textures/Villager_Shirt_Greyscale.png", "Pastel_Cotton"),
    ("NPC/Player_Child/Cosmetics/Undertops/Tshirt_Child.blockymodel",
     "Cosmetics/Undertops/Tshirt_Textures/Dipcut_Greyscale.png", "Colored_Cotton"),
    ("NPC/Player_Child/Cosmetics/Undertops/BasicUndertop_Child.blockymodel",
     "Cosmetics/Undertops/BasicTop_Textures/FrostyF_Top_Texture.png", "Colored_Cotton"),
]

# Ids that exist in BOTH Pastel_Cotton and Colored_Cotton, checked against
# Cosmetics/CharacterCreator/GradientSets.json. Picking from the intersection means the same
# list is safe whichever undertop fabric gets chosen.
FABRIC_COLORS = ["Red", "Orange", "Yellow", "Green", "Blue", "Purple", "Pink"]

# Jean_Generic uses its own naming — no plain "Blue"/"Red" — so the trousers keep a separate
# list rather than borrowing the fabric one.
JEAN_COLORS = ["Blue", "BluePastel", "Blue_Night", "Marine_Blue", "GreyBlue", "Turquoise_Dark"]

# Upper-body items that leave the torso exposed.
ACCESSORY_TOPS = ("Cheststrap", "Necklace", "Arm_Band", "Arm_Bandage",
                  "Scarf", "Belt_Large", "Straps_Wasteland")

# Garments that should not land on a male model.
FEMININE = ("Dress", "Skirt", "PetalTop", "HighSkirt")

# Replacement bottoms for a male model wearing a skirt or dress.
# Again copied verbatim from models that already ship with them.
MALE_PANTS = [
    ("Cosmetics/Pants/Pants_Slim.blockymodel",
     "Cosmetics/Pants/Pants_Slim_Textures/Jean_Tight_Greyscale.png", "Jean_Generic"),
    ("Cosmetics/Pants/Shorty_Slim.blockymodel",
     "Cosmetics/Pants/Shorty_Slim_Textures/Jean_Fantasy_Geyscale_Texture.png", "Jean_Generic"),
]
# Same NPC/Player_Child/ rule as UNDERTOPS_CHILD above — see the note there.
MALE_PANTS_CHILD = [
    ("NPC/Player_Child/Cosmetics/Pants/Pants_Straight_Child.blockymodel",
     "Cosmetics/Pants/Pants_Straight_Textures/Wrecked_Greyscale.png", "Jean_Generic"),
    ("NPC/Player_Child/Cosmetics/Pants/Pants_Slim_Child.blockymodel",
     "Cosmetics/Pants/Pants_Slim_Textures/Jean_Tight_Greyscale.png", "Jean_Generic"),
]


def slot_of(att):
    path = att.get("Model", "")
    for s in ("Haircuts", "Overtops", "Undertops", "Pants", "Overpants",
              "Shoes", "Eyes", "Mouths", "Ears", "Eyebrows", "Faces"):
        if "/" + s + "/" in path:
            return s
    return "?"


def base_name(att):
    return os.path.basename(att.get("Model", "")).replace(".blockymodel", "")


def is_accessory_top(name):
    # Scarf_Jumper and NeckHighJumper_Ribbon are jumpers that happen to have a scarf; they
    # cover the torso, so they must not be mistaken for bare accessories.
    if "Jumper" in name:
        return False
    return any(k in name for k in ACCESSORY_TOPS)


def torso_exposed(atts):
    if any(slot_of(a) == "Undertops" for a in atts):
        return False
    tops = [base_name(a) for a in atts if slot_of(a) == "Overtops"]
    return not tops or all(is_accessory_top(t) for t in tops)


def patch(model, filename, rng):
    """Returns a list of human-readable changes, mutating `model` in place."""
    changes = []
    atts = model.get("DefaultAttachments")
    if not isinstance(atts, list):
        return changes

    is_child = "Child" in filename
    is_male = "Female" not in filename

    # 1. Replace feminine garments on male models FIRST.
    #
    # Order matters and getting it wrong is not cosmetic. A Dress sits in the Overtops slot, so
    # it counts as torso coverage. Running the torso check first would clear such a model as
    # "dressed", and only then would the swap turn its dress into trousers — leaving a boy in
    # two pairs of trousers and no shirt. That is exactly what happened to
    # SimTale_Human_Child_Male_13 on the first pass.
    for att in atts:
        if not is_male:
            break
        name = base_name(att)
        if not any(k in name for k in FEMININE):
            continue
        if slot_of(att) == "Overtops":
            # A dress is the top half too, so it becomes a shirt rather than trousers.
            pool = UNDERTOPS_CHILD if is_child else UNDERTOPS_ADULT
            m, t, grad = rng.choice(pool)
            att["Model"], att["Texture"] = m, t
            att["GradientSet"], att["GradientId"] = grad, rng.choice(FABRIC_COLORS)
        else:
            pool = MALE_PANTS_CHILD if is_child else MALE_PANTS
            m, t, grad = rng.choice(pool)
            att["Model"], att["Texture"] = m, t
            att["GradientSet"], att["GradientId"] = grad, rng.choice(JEAN_COLORS)
        changes.append("garment")

    # 2. Cover an exposed torso, inserting under the accessory so the accessory stays on top.
    if torso_exposed(atts):
        pool = UNDERTOPS_CHILD if is_child else UNDERTOPS_ADULT
        m, t, grad = rng.choice(pool)
        piece = {"Model": m, "Texture": t, "GradientSet": grad,
                 "GradientId": rng.choice(FABRIC_COLORS), "$SimTale": MARKER}
        insert_at = next((i for i, a in enumerate(atts) if slot_of(a) == "Overtops"), len(atts))
        atts.insert(insert_at, piece)
        changes.append("undertop")

    for att in atts:
        s = slot_of(att)

        # 2. Give the eyes a colour of their own.
        if s == "Eyes" and att.get("GradientSet") != "Eyes_Gradient":
            att["GradientSet"] = "Eyes_Gradient"
            att["GradientId"] = rng.choice(EYE_COLORS)
            changes.append("eyes")

        # 3. Lip colour, but only on the mouth texture meant to carry one.
        elif s == "Mouths" and "Makeup" in att.get("Texture", "") and att.get("GradientSet") == "Skin":
            grad, gid = rng.choice(LIP_COLORS)
            att["GradientSet"] = grad
            att["GradientId"] = gid
            changes.append("lips")

    return changes


def main():
    check = "--check" in sys.argv
    do_revert = "--revert" in sys.argv

    if not MODELS_DIR.is_dir():
        print(f"ERRO: pasta nao encontrada: {MODELS_DIR}")
        return 1

    files = sorted(MODELS_DIR.glob("*.json"))
    if not files:
        print("ERRO: nenhum modelo gerado encontrado")
        return 1

    if do_revert:
        if not BACKUP.is_file():
            print(f"ERRO: sem backup em {BACKUP.name} — nada a reverter.")
            return 1
        saved = json.loads(BACKUP.read_text(encoding="utf8"))
        restored = 0
        for name, content in saved.items():
            path = MODELS_DIR / name
            path.write_text(json.dumps(content, indent=2, ensure_ascii=False) + "\n", encoding="utf8")
            restored += 1
        print(f"\nrestaurados: {restored} modelos")
        return 0

    backup = {}
    if BACKUP.is_file():
        backup = json.loads(BACKUP.read_text(encoding="utf8"))

    tally = {"undertop": 0, "eyes": 0, "lips": 0, "garment": 0}
    touched = 0

    for path in files:
        try:
            model = json.loads(path.read_text(encoding="utf8"))
        except json.JSONDecodeError as exc:
            print(f"  JSON invalido, ignorado: {path.name} ({exc})")
            continue

        original = json.loads(json.dumps(model))
        # Seeded per file so a second run reproduces the same roster.
        rng = random.Random(path.name)
        changes = patch(model, path.name, rng)
        if not changes:
            continue

        for c in changes:
            tally[c] += 1
        touched += 1

        if not check:
            backup.setdefault(path.name, original)
            path.write_text(json.dumps(model, indent=2, ensure_ascii=False) + "\n", encoding="utf8")

    if not check and backup:
        BACKUP.write_text(json.dumps(backup, indent=2, ensure_ascii=False) + "\n", encoding="utf8")

    verb = "precisam de patch" if check else "corrigidos"
    print()
    print(f"modelos {verb}: {touched} de {len(files)}")
    print(f"  torso coberto : {tally['undertop']}")
    print(f"  olhos coloridos: {tally['eyes']}")
    print(f"  boca com batom : {tally['lips']}")
    print(f"  peça trocada   : {tally['garment']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
