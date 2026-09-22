#!/usr/bin/env python3
"""
Repara os caminhos de peças de roupa infantis gravados sem o prefixo NPC/Player_Child/.

O QUE ACONTECEU
---------------
O fix_npc_appearance.py montava os caminhos das peças de criança copiando a convenção dos
adultos ("Cosmetics/Undertops/...") e colando "_Child" no fim do nome do arquivo. Peças de
criança não moram lá: elas ficam em "NPC/Player_Child/Cosmetics/...".

O resultado não foi uma camiseta faltando. O modelo inteiro falhava ao registrar, e todo role
que apontava para ele morria no boot com

    IllegalStateException: The model with the name "SimTale_Human_Child_Male_13"
                           does not exist for attribute "Appearance"

derrubando o carregamento do mod inteiro. 34 modelos ficaram nesse estado.

Só o campo "Model" muda. A "Texture" é compartilhada com a versão adulta e já estava correta —
verificado comparando com as entradas que funcionam da mesma peça.

USO
---
    python3 fix_child_asset_paths.py --check     # só relata, não escreve
    python3 fix_child_asset_paths.py             # aplica e grava backup
    python3 fix_child_asset_paths.py --revert    # desfaz usando o backup
"""

import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MODELS_DIR = os.path.join(
    HERE, "..", "src", "main", "resources", "Server", "Models", "Generated")
BACKUP = os.path.join(HERE, "child_asset_paths_backup.json")

CHILD_PREFIX = "NPC/Player_Child/"

# Peças de criança que o script anterior gravou com o caminho de adulto.
# Cada destino abaixo é confirmado: já é usado por dezenas de modelos que carregam sem erro.
BROKEN = {
    "Cosmetics/Undertops/LongSleeveShirt_Child.blockymodel",
    "Cosmetics/Undertops/Tshirt_Child.blockymodel",
    "Cosmetics/Undertops/BasicUndertop_Child.blockymodel",
    "Cosmetics/Pants/Pants_Straight_Child.blockymodel",
    "Cosmetics/Pants/Pants_Slim_Child.blockymodel",
}


def model_files():
    for name in sorted(os.listdir(MODELS_DIR)):
        if name.startswith("SimTale_Human_Child") and name.endswith(".json"):
            yield os.path.join(MODELS_DIR, name)


def load(path):
    with open(path, "r", encoding="utf-8") as fh:
        return json.load(fh)


def save(path, data):
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


def apply(check_only):
    backup = {}
    fixed_files = 0
    fixed_refs = 0

    for path in model_files():
        data = load(path)
        changed = []

        for i, att in enumerate(data.get("DefaultAttachments", [])):
            model = att.get("Model", "")
            if model in BROKEN:
                changed.append([i, model])
                if not check_only:
                    att["Model"] = CHILD_PREFIX + model
                fixed_refs += 1

        if changed:
            fixed_files += 1
            name = os.path.basename(path)
            backup[name] = changed
            print("  %-42s %s" % (name[:-5], ", ".join(m.split("/")[-1] for _, m in changed)))
            if not check_only:
                save(path, data)

    print()
    if check_only:
        print("MODO CHECK: nada foi escrito.")
        print("A corrigir: %d referencias em %d arquivos." % (fixed_refs, fixed_files))
        return

    with open(BACKUP, "w", encoding="utf-8") as fh:
        json.dump(backup, fh, indent=2)

    print("Corrigidas %d referencias em %d arquivos." % (fixed_refs, fixed_files))
    print("Backup em %s" % os.path.relpath(BACKUP, HERE))


def revert():
    if not os.path.exists(BACKUP):
        print("Nada para reverter: %s nao existe." % BACKUP)
        return

    with open(BACKUP, "r", encoding="utf-8") as fh:
        backup = json.load(fh)

    restored = 0
    for name, entries in backup.items():
        path = os.path.join(MODELS_DIR, name)
        if not os.path.exists(path):
            print("  aviso: %s sumiu, pulando" % name)
            continue
        data = load(path)
        atts = data.get("DefaultAttachments", [])
        for idx, original in entries:
            # Restaura por indice, mas confere o valor antes de escrever: se o arquivo mudou
            # por outro motivo desde o backup, e melhor pular do que sobrescrever cegamente.
            if idx < len(atts) and atts[idx].get("Model") == CHILD_PREFIX + original:
                atts[idx]["Model"] = original
                restored += 1
        save(path, data)

    print("Revertidas %d referencias." % restored)


def main():
    args = sys.argv[1:]
    if "--revert" in args:
        revert()
    else:
        apply(check_only="--check" in args)


if __name__ == "__main__":
    main()
