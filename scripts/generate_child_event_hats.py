import os
import json
import copy

# Extraido/adaptado de scripts/generate_child_variants.py (mesmo NODE_SCALES/FACE_ATTACHMENT_NAMES
# e mesma logica de scale_cosmetic_model) para escalar os dois cosmeticos de chapeu de evento
# (SantaHat/StrawHat) do jeito que TODO cosmetico de cabeca do projeto ja e escalado pra crianca.
#
# Bug que isso conserta (13/09): o comando/gatilho de fantasia sazonal usava
# "Cosmetics/Head/SantaHat.blockymodel" (tamanho adulto, sem escalar) tambem nas variantes de
# CRIANCA -- unico cosmetico de cabeca do projeto que nao passou por esse pipeline. Como o chapeu
# nao redimensiona sozinho pro encaixe da cabeca menor da crianca, ele fica malencaixado (visto
# em jogo: lateral parecendo transparente, parte de tras parecendo fora do lugar -- faces sem UV
# definida nos boxes internos do modelo, que na versao adulta ficam sempre escondidas dentro do
# proximo box e so aparecem quando o aninhamento nao bate certo).

SOURCE_DIR = "/home/cookie/Documents/Assets/Common/Cosmetics/Head"
DEST_DIR = "/home/cookie/Documents/hy mods/SimTale/src/main/resources/Common/NPC/Player_Child/Cosmetics/Head"

NODE_SCALES = {
    "Pelvis":       (0.70, 0.68, 0.70),
    "Belly":        (0.70, 0.65, 0.70),
    "Chest":        (0.70, 0.65, 0.70),
    "Head":         (1.20, 1.20, 1.20),
    "R-Shoulder":   (0.70, 0.70, 0.70),
    "L-Shoulder":   (0.70, 0.70, 0.70),
    "R-Arm":        (0.68, 0.65, 0.68),
    "L-Arm":        (0.68, 0.65, 0.68),
    "R-Forearm":    (0.68, 0.65, 0.68),
    "L-Forearm":    (0.68, 0.65, 0.68),
    "R-Hand":       (0.70, 0.70, 0.70),
    "L-Hand":       (0.70, 0.70, 0.70),
    "R-Thigh":      (0.70, 0.65, 0.70),
    "L-Thigh":      (0.70, 0.65, 0.70),
    "R-Calf":       (0.70, 0.62, 0.70),
    "L-Calf":       (0.70, 0.62, 0.70),
    "R-Foot":       (0.75, 0.75, 0.75),
    "L-Foot":       (0.75, 0.75, 0.75),
}

FACE_ATTACHMENT_NAMES = {
    "Neck", "L-Eyelid", "R-Eyelid", "L-Eyelid-Bot", "R-Eyelid-Bot",
    "L-Eyebrow-Attachment", "R-Eyebrow-Attachment",
    "R-Eye-Attachment", "L-Eye-Attachment", "Mouth-Attachment",
    "R-Ear-Attachment", "L-Ear-Attachment",
}

def get_own_scale(name, inherited_scale):
    if name in FACE_ATTACHMENT_NAMES:
        return (1.0, 1.0, 1.0)
    if name in NODE_SCALES:
        return NODE_SCALES[name]
    return inherited_scale

def scale_vec(vec, sx, sy, sz):
    if vec is None:
        return
    if "x" in vec:
        vec["x"] = vec["x"] * sx
    if "y" in vec:
        vec["y"] = vec["y"] * sy
    if "z" in vec:
        vec["z"] = vec["z"] * sz

def scale_shape(shape, own_scale):
    if not shape:
        return
    if shape.get("type") in ("box", "quad"):
        stretch = shape.get("stretch")
        if stretch is None:
            stretch = {"x": 1.0, "y": 1.0, "z": 1.0}
            shape["stretch"] = stretch
        scale_vec(stretch, *own_scale)
        scale_vec(shape.get("offset"), *own_scale)

def process_node(node, parent_scale):
    name = node.get("name")
    scale_vec(node.get("position"), *parent_scale)
    own_scale = get_own_scale(name, parent_scale)
    scale_shape(node.get("shape"), own_scale)
    for child in node.get("children", []):
        process_node(child, own_scale)

def process_root(root):
    own_scale = get_own_scale(root.get("name"), (1.0, 1.0, 1.0))
    scale_shape(root.get("shape"), own_scale)
    for child in root.get("children", []):
        process_node(child, own_scale)

def scale_hat(filename):
    src_path = os.path.join(SOURCE_DIR, filename)
    name_part, ext = os.path.splitext(filename)
    dest_path = os.path.join(DEST_DIR, f"{name_part}_Child{ext}")

    with open(src_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    data = copy.deepcopy(data)

    for root in data["nodes"]:
        process_root(root)

    os.makedirs(DEST_DIR, exist_ok=True)
    with open(dest_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)

    print(f"{filename} -> {dest_path}")

if __name__ == "__main__":
    scale_hat("SantaHat.blockymodel")
    scale_hat("StrawHat.blockymodel")
