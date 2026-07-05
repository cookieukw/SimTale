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
- textureLayout e size nunca são tocados para preservar as coordenadas de textura.
  Em vez disso, escalamos a propriedade 'stretch'.

Suporta shape.type == "box" (size x/y/z) e "quad" (size só x/y).
"""
import json
import copy
import os

# Caminhos locais na máquina do usuário
DST_DIR = "/home/cookie/Documents/hy mods/SimTale/src/main/resources/Common/NPC/Player_Child"

FILES_MAP = {
    "/home/cookie/Documents/Assets/Common/Characters/Player_With_Face.blockymodel": (True, "Player_With_Face_Child.blockymodel"),
    "/home/cookie/Documents/Assets/Common/Characters/Player.blockymodel": (True, "Player_Child.blockymodel"),
    "/home/cookie/Documents/Assets/Common/Cosmetics/Underwears/Underwear.blockymodel": (False, "Underwear_Child.blockymodel"),
    "/home/cookie/Documents/Assets/Common/Cosmetics/Shoes/FrostyWarm_Boots.blockymodel": (False, "FrostyWarm_Boots_Child.blockymodel"),
    "/home/cookie/Documents/Assets/Common/Cosmetics/Pants/Skater_Shorts.blockymodel": (False, "Skater_Shorts_Child.blockymodel"),
    "/home/cookie/Documents/Assets/Common/Characters/Body_Attachments/Beards/DoubleBraid.blockymodel": (False, "DoubleBraid_Child.blockymodel"),
    "/home/cookie/Documents/Assets/Common/Characters/Body_Attachments/Mouths/Mouth1.blockymodel": (False, "Mouth1_Child.blockymodel")
}

# Tabela calibrada
NODE_SCALES = {
    "Pelvis":       (0.97, 0.95, 0.97),
    "Belly":        (0.97, 0.92, 0.97),
    "Chest":        (0.97, 0.92, 0.97),
    "Head":         (1.10, 1.10, 1.10),
    "R-Shoulder":   (0.93, 0.93, 0.93),
    "L-Shoulder":   (0.93, 0.93, 0.93),
    "R-Arm":        (0.92, 0.88, 0.92),
    "L-Arm":        (0.92, 0.88, 0.92),
    "R-Forearm":    (0.92, 0.86, 0.92),
    "L-Forearm":    (0.92, 0.86, 0.92),
    "R-Hand":       (0.93, 0.93, 0.93),
    "L-Hand":       (0.93, 0.93, 0.93),
    "R-Thigh":      (0.93, 0.85, 0.93),
    "L-Thigh":      (0.93, 0.85, 0.93),
    "R-Calf":       (0.93, 0.82, 0.93),
    "L-Calf":       (0.93, 0.82, 0.93),
    "R-Foot":       (0.96, 0.96, 0.96),
    "L-Foot":       (0.96, 0.96, 0.96),
}

# Agora que usamos 'stretch' ao invés de 'size', o reposicionamento dos attachments
# é fundamental para manter os olhos/orelhas/boca na superfície da cabeça escalada.
SKIP_FACE_ATTACHMENT_REPOSITION = False

FACE_ATTACHMENT_NAMES = {
    "Neck", "L-Eyelid", "R-Eyelid", "L-Eyelid-Bot", "R-Eyelid-Bot",
    "L-Eyebrow-Attachment", "R-Eyebrow-Attachment",
    "R-Eye-Attachment", "L-Eye-Attachment", "Mouth-Attachment",
    "R-Ear-Attachment", "L-Ear-Attachment",
}

ROOT_HEIGHT_SCALE = 0.90


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
        # ESCALAMOS 'STRETCH' E NÃO 'SIZE' PARA NÃO QUEBRAR O ENCAIXE DE TEXTURA
        stretch = shape.get("stretch")
        if stretch is None:
            stretch = {"x": 1.0, "y": 1.0, "z": 1.0}
            shape["stretch"] = stretch
        scale_vec(stretch, *own_scale)
        scale_vec(shape.get("offset"), *own_scale)


def process_node(node, parent_scale):
    name = node.get("name")
    skip_position = SKIP_FACE_ATTACHMENT_REPOSITION and name in FACE_ATTACHMENT_NAMES
    if not skip_position:
        scale_vec(node.get("position"), *parent_scale)
    own_scale = get_own_scale(name, parent_scale)
    scale_shape(node.get("shape"), own_scale)
    for child in node.get("children", []):
        process_node(child, own_scale)


def process_root(root, is_body_origin):
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


def process_file(src_path, is_body_origin, dest_name):
    dst = os.path.join(DST_DIR, dest_name)

    with open(src_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    data = copy.deepcopy(data)

    for root in data["nodes"]:
        process_root(root, is_body_origin)

    with open(dst, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)

    print(f"Gerado com sucesso: {dst}")


if __name__ == "__main__":
    os.makedirs(DST_DIR, exist_ok=True)
    for src_path, (is_body_origin, dest_name) in FILES_MAP.items():
        if os.path.exists(src_path):
            process_file(src_path, is_body_origin, dest_name)
        else:
            print(f"Aviso: Arquivo de origem não encontrado: {src_path}")
