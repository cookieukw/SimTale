import json
import os
import sys

# Gera os assets de fantasia por-NPC em Server/Models/Events/Generated/<id>_<evento>.json e
# os 6 genericos em Server/Models/Events/SimTale_Human_<Male|Female|Child>_<evento>.json.
#
# Cada fantasia = TODOS os attachments da NPC (cabelo, rosto, olhos, boca, orelhas, sobrancelhas,
# roupa) + o chapeu do evento.
#
# Por que a lista inteira (bug de 05/10, "olhos vazios, sem boca"): no ModelAsset o campo
# DefaultAttachments NAO soma com o do Parent, ele SUBSTITUI. A heranca do codec so copia o array
# do pai quando o filho nao declara o campo (lambda do ModelAsset: filho.defaultAttachments =
# pai.defaultAttachments). Os arquivos antigos eram {"Parent": "<id>", "DefaultAttachments":
# [chapeu]}, entao a NPC fantasiada ficava so com o chapeu: sem olhos, boca, cabelo e roupa. O
# Parent continua apontando pra NPC especifica, so pra herdar o resto (Model, GradientSet da pele,
# AnimationSets, EyeHeight...).
#
# Roda de qualquer lugar (os caminhos sao relativos a este script) e sempre reescreve tudo, pra
# que uma correcao aqui chegue nos 1.646 arquivos. Uso: python3 scripts/generate_costume_assets.py

HERE = os.path.dirname(os.path.abspath(__file__))
MODELS_DIR = os.path.join(HERE, "..", "src", "main", "resources", "Server", "Models")
GENERATED_DIR = os.path.join(MODELS_DIR, "Generated")
EVENTS_DIR = os.path.join(MODELS_DIR, "Events")
OUTPUT_DIR = os.path.join(EVENTS_DIR, "Generated")
BASES = ["SimTale_Human_Male", "SimTale_Human_Female", "SimTale_Human_Child"]

EVENTS = {
    "Christmas": [
        {
            "Model": "Cosmetics/Head/SantaHat.blockymodel",
            "Texture": "Cosmetics/Head/SantaHat_Texture/SantaHat_Greyscale_Texture.png",
            "GradientSet": "Colored_Cotton",
            "GradientId": "Red"
        }
    ],
    "Halloween": [
        {
            "Model": "Cosmetics/Head/StrawHat.blockymodel",
            "Texture": "Cosmetics/Head/StrawHat_Textures/WitchHat_Colored_Greyscale_Texture.png"
        }
    ],
}

# Mesmos chapeus, mas com "Model" apontando para a versao _Child (escalada em 1.2x, gerada por
# scripts/generate_child_event_hats.py). A "Texture" continua igual a adulta de proposito: o
# textureLayout dentro do .blockymodel escalado ja mapeia pros mesmos pixels. Sem isso o chapeu
# adulto fica malencaixado na cabeca menor de crianca (faces sem UV aparecendo).
EVENTS_CHILD = {
    "Christmas": [
        {
            "Model": "NPC/Player_Child/Cosmetics/Head/SantaHat_Child.blockymodel",
            "Texture": "Cosmetics/Head/SantaHat_Texture/SantaHat_Greyscale_Texture.png",
            "GradientSet": "Colored_Cotton",
            "GradientId": "Red"
        }
    ],
    "Halloween": [
        {
            "Model": "NPC/Player_Child/Cosmetics/Head/StrawHat_Child.blockymodel",
            "Texture": "Cosmetics/Head/StrawHat_Textures/WitchHat_Colored_Greyscale_Texture.png"
        }
    ],
}


def model_path(model_id):
    for folder in (GENERATED_DIR, MODELS_DIR):
        path = os.path.join(folder, model_id + ".json")
        if os.path.exists(path):
            return path
    return None


def resolved_attachments(model_id, depth=0):
    """DefaultAttachments as the engine resolves them: the first asset up the Parent chain that
    declares the field wins (vanilla parents such as 'Player' are not in this repo: empty)."""
    path = model_path(model_id)
    if path is None or depth > 10:
        return []
    with open(path) as f:
        data = json.load(f)
    if "DefaultAttachments" in data:
        return data["DefaultAttachments"]
    parent = data.get("Parent")
    return resolved_attachments(parent, depth + 1) if parent else []


def is_head_cosmetic(attachment):
    return "/Head/" in attachment.get("Model", "")


def costume(model_id, hats):
    own = [a for a in resolved_attachments(model_id) if not is_head_cosmetic(a)]
    if not own:
        sys.exit("sem attachments para %s: nao gero uma fantasia que apagaria o rosto" % model_id)
    return {"Parent": model_id, "DefaultAttachments": own + hats}


def write(path, data):
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    generated_ids = sorted(f[:-5] for f in os.listdir(GENERATED_DIR) if f.endswith(".json"))
    written = 0
    for npc_id in generated_ids:
        events = EVENTS_CHILD if npc_id.startswith("SimTale_Human_Child") else EVENTS
        for event_name, hats in events.items():
            write(os.path.join(OUTPUT_DIR, "%s_%s.json" % (npc_id, event_name)), costume(npc_id, hats))
            written += 1
    for base in BASES:
        events = EVENTS_CHILD if base == "SimTale_Human_Child" else EVENTS
        for event_name, hats in events.items():
            write(os.path.join(EVENTS_DIR, "%s_%s.json" % (base, event_name)), costume(base, hats))
            written += 1
    print("%d variantes de NPC em Generated/; %d arquivos de fantasia escritos." % (len(generated_ids), written))


if __name__ == "__main__":
    main()
