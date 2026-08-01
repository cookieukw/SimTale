#!/usr/bin/env python3
"""
Faz a boca das NPCs femininas acompanhar o tom de pele do proprio modelo.

O PROBLEMA
----------
O fix_npc_appearance.py pintava a boca feminina com um gradiente de TECIDO
(Pastel_Cotton/Pink, Fantasy_Cotton/Red, Colored_Cotton/Carmin...). Esses conjuntos existem para
roupa e nao tem relacao nenhuma com o tom de pele: a cor saia igual para todo mundo. Numa
personagem de pele escura o resultado era uma boca clara chapada, destacada do rosto.

Os modelos masculinos nunca tiveram isso — eles ja usavam GradientSet "Skin" com o mesmo
GradientId da pele do modelo. Este script alinha as femininas a esse mesmo comportamento.

O QUE MUDA (e o que NAO muda)
-----------------------------
Muda APENAS o par GradientSet/GradientId do attachment de boca:

    "GradientSet": "Pastel_Cotton"  ->  "Skin"
    "GradientId":  "Pink"           ->  <o GradientId de pele do proprio modelo>

NAO muda:
  * o GradientId de pele do modelo (a pele fica exatamente como esta);
  * a textura da boca — segue "Makeup_Greyscale.png", entao o formato de labio feminino
    continua ali, so que num tom que pertence ao rosto;
  * qualquer outro attachment.

Como o valor gravado e lido de cada modelo individualmente, cada NPC recebe o seu proprio tom
em vez de uma cor unica para todas.

USO
---
    python3 fix_female_mouth_color.py --check     # so relata, nao escreve
    python3 fix_female_mouth_color.py             # aplica e grava backup
    python3 fix_female_mouth_color.py --revert    # desfaz usando o backup
"""

import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MODELS_DIR = os.path.join(
    HERE, "..", "src", "main", "resources", "Server", "Models", "Generated")
BACKUP = os.path.join(HERE, "female_mouth_backup.json")

SKIN_SET = "Skin"
MOUTH_MARKER = "/Mouths/"

# Adultas e criancas: o defeito e o mesmo nos dois conjuntos.
PREFIXES = ("SimTale_Human_Female_", "SimTale_Human_Child_Female_")


def model_files():
    for name in sorted(os.listdir(MODELS_DIR)):
        if name.endswith(".json") and name.startswith(PREFIXES):
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
    changed_files = 0
    changed_mouths = 0
    skipped_no_skin = []
    already_ok = 0

    for path in model_files():
        data = load(path)
        skin_id = data.get("GradientId")
        name = os.path.basename(path)

        # Sem um id de pele no modelo nao ha de onde tirar o tom. Melhor pular e relatar do que
        # inventar um valor padrao e deixar a boca errada de um jeito diferente.
        if not skin_id:
            skipped_no_skin.append(name)
            continue

        entries = []
        for i, att in enumerate(data.get("DefaultAttachments", [])):
            if MOUTH_MARKER not in att.get("Model", ""):
                continue

            old_set = att.get("GradientSet")
            old_id = att.get("GradientId")
            if old_set == SKIN_SET and old_id == skin_id:
                already_ok += 1
                continue

            entries.append([i, old_set, old_id])
            if not check_only:
                att["GradientSet"] = SKIN_SET
                att["GradientId"] = skin_id
            changed_mouths += 1

        if entries:
            changed_files += 1
            backup[name] = entries
            if not check_only:
                save(path, data)

    print("Bocas a ajustar: %d (em %d arquivos)" % (changed_mouths, changed_files))
    print("Ja corretas: %d" % already_ok)
    if skipped_no_skin:
        print("Puladas por nao ter GradientId de pele: %d" % len(skipped_no_skin))
        for n in skipped_no_skin[:10]:
            print("    %s" % n)

    if check_only:
        print("\nMODO CHECK: nada foi escrito.")
        return

    with open(BACKUP, "w", encoding="utf-8") as fh:
        json.dump(backup, fh, indent=2)
    print("\nBackup em %s" % os.path.relpath(BACKUP, HERE))


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
        for idx, old_set, old_id in entries:
            # Confere que o slot ainda e uma boca antes de escrever: se o arquivo mudou por
            # outro motivo desde o backup, e melhor pular do que sobrescrever no indice errado.
            if idx < len(atts) and MOUTH_MARKER in atts[idx].get("Model", ""):
                atts[idx]["GradientSet"] = old_set
                atts[idx]["GradientId"] = old_id
                restored += 1
        save(path, data)

    print("Revertidas %d bocas." % restored)


def main():
    args = sys.argv[1:]
    if "--revert" in args:
        revert()
    else:
        apply(check_only="--check" in args)


if __name__ == "__main__":
    main()
