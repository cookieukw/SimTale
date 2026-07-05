"""
Aplica a MESMA tabela de escala "criança" (NODE_SCALES) tanto no corpo
quanto em qualquer cosmético (roupa, cabelo, calçado, boca, etc.),
mantendo tudo sincronizado.

Como os cosméticos funcionam (confirmado inspecionando os arquivos):
- Os nós "âncora" (Pelvis, Chest, R-Foot, Head, Mouth-Attachment...) têm
  o MESMO NOME dos ossos do corpo, mas shape.type == "none" (não têm
  caixa própria -- servem só de ponto de encaixe no esqueleto).
- A peça visível de verdade (ex: "Pelvis-Suit", "R-Boot", "Beard",
  "Mouth") fica DENTRO desse nó âncora, com um nome genérico que não
  bate com nenhum osso conhecido.

Regra geral aplicada:
- Se o nome do nó é um osso conhecido (está em NODE_SCALES) -> usa a
  escala definida ali.
- Se o nome é um attachment de rosto (olho, sobrancelha...) -> escala
  própria 1.0 (não aumenta o item, só reposiciona).
- Qualquer outro nome (a malha visível do cosmético em si) -> HERDA a
  escala do pai. Assim "Pelvis-Suit" encolhe igual o osso "Pelvis",
  "R-Boot" (dentro de "R-Foot") encolhe igual o osso "R-Foot", etc.
- A posição de todo filho é sempre escalada pela escala PRÓPRIA do pai
  (ponto de encaixe acompanha o encolhimento do pai).
- textureLayout nunca é tocado.

Suporta shape.type == "box" (size x/y/z) e "quad" (size só x/y).
"""
import json
import copy
import os

SRC_DIR = "/mnt/user-data/uploads"
DST_DIR = "/mnt/user-data/outputs"

# Mesma tabela usada no corpo -- ajuste aqui se recalibrar o corpo,
# e todos os cosméticos acompanham automaticamente.
NODE_SCALES = {
    "Pelvis":       (0.95, 0.90, 0.95),
    "Belly":        (0.95, 0.82, 0.95),
    "Chest":        (0.95, 0.82, 0.95),
    "Head":         (1.30, 1.30, 1.30),
    "R-Shoulder":   (0.85, 0.85, 0.85),
    "L-Shoulder":   (0.85, 0.85, 0.85),
    "R-Arm":        (0.80, 0.72, 0.80),
    "L-Arm":        (0.80, 0.72, 0.80),
    "R-Forearm":    (0.80, 0.68, 0.80),
    "L-Forearm":    (0.80, 0.68, 0.80),
    "R-Hand":       (0.85, 0.85, 0.85),
    "L-Hand":       (0.85, 0.85, 0.85),
    "R-Thigh":      (0.85, 0.68, 0.85),
    "L-Thigh":      (0.85, 0.68, 0.85),
    "R-Calf":       (0.85, 0.62, 0.85),
    "L-Calf":       (0.85, 0.62, 0.85),
    "R-Foot":       (0.92, 0.92, 0.92),
    "L-Foot":       (0.92, 0.92, 0.92),
}

FACE_ATTACHMENT_NAMES = {
    "Neck", "L-Eyelid", "R-Eyelid", "L-Eyelid-Bot", "R-Eyelid-Bot",
    "L-Eyebrow-Attachment", "R-Eyebrow-Attachment",
    "R-Eye-Attachment", "L-Eye-Attachment", "Mouth-Attachment",
    "R-Ear-Attachment", "L-Ear-Attachment",
}

# Mesmo fator usado pra baixar o quadril do corpo (só se aplica no
# arquivo do CORPO, que tem "Origin" como raiz -- cosméticos não têm).
ROOT_HEIGHT_SCALE = 0.76


def get_own_scale(name, inherited_scale):
    if name in FACE_ATTACHMENT_NAMES:
        return (1.0, 1.0, 1.0)
    if name in NODE_SCALES:
        return NODE_SCALES[name]
    return inherited_scale  # malha genérica do cosmético -> herda do pai


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
        size = shape.get("settings", {}).get("size")
        scale_vec(size, *own_scale)
        scale_vec(shape.get("offset"), *own_scale)
    # textureLayout: NUNCA tocar


def process_node(node, parent_scale):
    """Uso geral: escala a posição deste nó pela escala do pai, calcula
    a escala própria deste nó (explícita ou herdada), aplica na própria
    shape, e recursiona pros filhos passando a escala própria."""
    scale_vec(node.get("position"), *parent_scale)
    own_scale = get_own_scale(node.get("name"), parent_scale)
    scale_shape(node.get("shape"), own_scale)
    for child in node.get("children", []):
        process_node(child, own_scale)


def process_root(root, is_body_origin):
    """Trata a raiz de forma especial:
    - Corpo (root == "Origin"): a própria "Origin" não escala; o filho
      direto (Pelvis) usa ROOT_HEIGHT_SCALE pra reposicionar a altura.
    - Cosmético (root já é um osso, ex: "Pelvis", "R-Foot", "Head"):
      a posição da própria raiz NÃO é tocada (é o offset de encaixe no
      esqueleto, definido pelo autor do cosmético -- não faz sentido
      reescalar), só calculamos a escala própria dela pra propagar
      pros filhos, e escalamos a própria shape dela se tiver uma.
    """
    if is_body_origin:
        for child in root.get("children", []):
            scale_vec(child.get("position"), ROOT_HEIGHT_SCALE, ROOT_HEIGHT_SCALE, ROOT_HEIGHT_SCALE)
            own_scale = get_own_scale(child.get("name"), (ROOT_HEIGHT_SCALE,) * 3)
            scale_shape(child.get("shape"), own_scale)
            for grandchild in child.get("children", []):
                process_node(grandchild, own_scale)
    else:
        own_scale = get_own_scale(root.get("name"), (1.0, 1.0, 1.0))
        scale_shape(root.get("shape"), own_scale)
        for child in root.get("children", []):
            process_node(child, own_scale)


def process_file(filename, is_body_origin=False):
    src = os.path.join(SRC_DIR, filename)
    name, ext = os.path.splitext(filename)
    dst = os.path.join(DST_DIR, f"{name}_Child{ext}")

    with open(src, "r", encoding="utf-8") as f:
        data = json.load(f)
    data = copy.deepcopy(data)

    for root in data["nodes"]:
        process_root(root, is_body_origin)

    with open(dst, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)

    print(f"Gerado: {dst}")


if __name__ == "__main__":
    # Corpo (tem nó "Origin" envolvendo tudo)
    process_file("Player_With_Face.blockymodel", is_body_origin=True)

    # Cosméticos (raiz já é o próprio osso -- sem "Origin")
    for cosmetic in [
        "Underwear.blockymodel",
        "FrostyWarm_Boots.blockymodel",
        "Skater_Shorts.blockymodel",
        "DoubleBraid.blockymodel",
        "Mouth1.blockymodel",
    ]:
        process_file(cosmetic, is_body_origin=False)
