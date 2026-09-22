"""
Gera o ChibiDoll.blockymodel (CORPO da boneca) usando exatamente a mesma
lógica comprovada em generate_child_models.py:

- NUNCA tocamos em "size" nem em "textureLayout". É isso que preserva o
  encaixe com o atlas de textura compartilhado do jogo (pele, roupas,
  cabelos continuam funcionando sem re-mapear UV nenhum).
- Toda a redução de proporção é feita mexendo em "stretch".
- A posição de cada nó filho é recalculada automaticamente a partir da
  escala acumulada do pai -- nada é posicionado a olho no Blockbench.
- A hierarquia e os nomes dos nós vêm 100% do Player.blockymodel original,
  então qualquer cosmético que já funciona no player/criança encontra os
  mesmos pontos de encaixe (Mouth-Attachment, L-Eye-Attachment, etc.)
  automaticamente, sem precisar recriar/posicionar esses nós à mão.

A tabela NODE_SCALES abaixo é A MESMA usada em generate_chibidoll_variants.py
para escalar os cosméticos da boneca -- com uma correção: "L-Forearm" estava
assimétrico em relação a "R-Forearm" (0.48/0.29/0.60 contra 0.72/0.29/0.72).
Essa mesma assimetria aparece batendo, quase exatamente, no ChibiDoll.blockymodel
atual (antebraço esquerdo com size diferente do direito) -- é a causa mais
provável do "antebraço sem textura" que você reportou. Usar a MESMA tabela
aqui e no script de cosméticos garante que corpo e roupa fiquem
matematicamente consistentes por construção, em vez de depender de ajuste
manual/"inflar um pouco pra não vazar através da malha".

Depois de rodar este script, rode de novo o generate_chibidoll_variants.py
(depois de aplicar a mesma correção do L-Forearm nele) para regenerar os
cosméticos da boneca com a tabela corrigida.
"""
import json
import os
import copy

MOD_RESOURCES = "/home/cookie/Documents/hy mods/SimTale/src/main/resources"
ASSETS_DIR = "/home/cookie/Documents/Assets/Common"

DST_DIR = os.path.join(MOD_RESOURCES, "Common/NPC/Dolls")

# origem: (is_body_origin, nome_de_destino)
# is_body_origin=True porque a raiz "Origin" do Player.blockymodel tem
# "Pelvis" como filho direto -- mesmo caso do Player.blockymodel/
# Player_With_Face.blockymodel no script da criança.
FILES_MAP = {
    os.path.join(ASSETS_DIR, "Characters/Player.blockymodel"): (True, "ChibiDoll.blockymodel"),
}

# Tabela idêntica à de generate_chibidoll_variants.py, com o L-Forearm
# corrigido para espelhar o R-Forearm.
NODE_SCALES = {
    "Pelvis":       (0.64, 0.54, 0.67),
    "Belly":        (0.64, 0.41, 0.67),
    "Chest":        (0.63, 0.40, 0.66),
    "Head":         (0.60, 0.643, 0.643),
    "R-Shoulder":   (0.63, 0.40, 0.66),
    "L-Shoulder":   (0.63, 0.40, 0.66),
    "R-Arm":        (0.72, 0.36, 0.72),
    "L-Arm":        (0.72, 0.36, 0.72),
    "R-Forearm":    (0.72, 0.29, 0.72),
    "L-Forearm":    (0.72, 0.29, 0.72),   # corrigido: era (0.48, 0.29, 0.60)
    "R-Hand":       (0.75, 0.71, 0.75),
    "L-Hand":       (0.75, 0.71, 0.75),
    "R-Thigh":      (0.83, 0.36, 0.83),
    "L-Thigh":      (0.83, 0.36, 0.83),
    "R-Calf":       (0.83, 0.40, 0.83),
    "L-Calf":       (0.83, 0.40, 0.83),
    "R-Foot":       (0.86, 0.64, 0.69),
    "L-Foot":       (0.86, 0.64, 0.69),
}

# Escala adicional aplicada SÓ à posição da raiz (deslocamento Origin -> Pelvis),
# igual ao ROOT_HEIGHT_SCALE do script da criança. Controla a altura total
# da boneca (o "quão perto do chão" o quadril fica) sem afetar a proporção
# interna de cada osso, que já é controlada pelo NODE_SCALES acima.
# Comece em 1.0; se a boneca ficar alta demais em relação à cabeça, reduza.
ROOT_HEIGHT_SCALE = 1.0

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
        # ESCALAMOS 'stretch', NUNCA 'size' -- isso é o que preserva o
        # textureLayout e mantém o encaixe com o atlas de textura do jogo.
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

    print("ChibiDoll.blockymodel gerado a partir do Player.blockymodel original.")
    print("Agora rode generate_chibidoll_variants.py de novo (com o L-Forearm corrigido)")
    print("para regenerar os cosméticos com a mesma tabela de escala.")
