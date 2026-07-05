import json
import random
import os
import copy

COSMETICS_DIR = "/home/cookie/Documents/Assets/Assets/Cosmetics/CharacterCreator"
MOD_RESOURCES = "/home/cookie/Documents/hy mods/SimTale/src/main/resources"
ASSETS_DIR = "/home/cookie/Documents/Assets/Common"

MODELS_DIR = os.path.join(MOD_RESOURCES, "Server/Models/Generated")
ROLES_DIR = os.path.join(MOD_RESOURCES, "Server/NPC/Roles/Generated")
CHILD_COSMETICS_DIR = os.path.join(MOD_RESOURCES, "Common/NPC/Player_Child/Cosmetics")

os.makedirs(MODELS_DIR, exist_ok=True)
os.makedirs(ROLES_DIR, exist_ok=True)
os.makedirs(CHILD_COSMETICS_DIR, exist_ok=True)

# Proporções da Criança
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

# Carrega arquivos de cosméticos
with open(os.path.join(COSMETICS_DIR, "GradientSets.json"), "r") as f:
    gradient_sets = json.load(f)

gradients = {}
for gset in gradient_sets:
    gradients[gset["Id"]] = list(gset["Gradients"].keys())

def load_catalog(filename):
    with open(os.path.join(COSMETICS_DIR, filename), "r") as f:
        return json.load(f)

haircuts = load_catalog("Haircuts.json")
pants = load_catalog("Pants.json")
overtops = load_catalog("Overtops.json")
undertops = load_catalog("Undertops.json")
shoes = load_catalog("Shoes.json")

def scale_cosmetic_model(model_rel_path):
    # Resolve caminhos
    src_path = os.path.join(ASSETS_DIR, model_rel_path)
    if not os.path.exists(src_path):
        return None

    # Caminho de destino no Mod
    filename = os.path.basename(model_rel_path)
    name_part, ext = os.path.splitext(filename)
    dest_filename = f"{name_part}_Child{ext}"
    
    # Subpasta correspondente (ex: Pants, Shoes)
    subfolder = os.path.dirname(model_rel_path).split('/')[-1]
    dest_dir = os.path.join(CHILD_COSMETICS_DIR, subfolder)
    os.makedirs(dest_dir, exist_ok=True)
    dest_path = os.path.join(dest_dir, dest_filename)

    # Processa e escala o modelo do cosmético
    with open(src_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    data = copy.deepcopy(data)

    for root in data["nodes"]:
        process_root(root)

    with open(dest_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)

    # Retorna o caminho relativo do mod
    return f"NPC/Player_Child/Cosmetics/{subfolder}/{dest_filename}"

def extract_attachment(item):
    if "Variants" in item:
        variant_key = random.choice(list(item["Variants"].keys()))
        var_item = item["Variants"][variant_key]
        model = var_item.get("Model")
        tex = var_item.get("GreyscaleTexture", "")
    else:
        model = item.get("Model")
        tex = item.get("GreyscaleTexture", "")
        if "Textures" in item:
            tex_key = random.choice(list(item["Textures"].keys()))
            tex = item["Textures"][tex_key].get("Texture", "")
            
    if not model:
        return None

    # ESCALA o cosmético e gera a versão child
    scaled_model_path = scale_cosmetic_model(model)
    if not scaled_model_path:
        return None

    att = {
        "Model": scaled_model_path,
        "Texture": tex
    }
    if "GradientSet" in item:
        att["GradientSet"] = item["GradientSet"]
        if item["GradientSet"] in gradients:
            att["GradientId"] = random.choice(gradients[item["GradientSet"]])
    return att

def generate_child_model(index):
    skin_gradient = random.choice(gradients["Skin"])
    hair_gradient = random.choice(gradients["Hair"])
    gender = "Male" if random.choice([True, False]) else "Female"
    
    face_texture = "Characters/Body_Attachments/Faces/Faces_Detached_Textures/Face.png" if gender == "Male" else "Characters/Body_Attachments/Faces/Faces_Detached_Textures/MakeUp_Face.png"
    mouth_texture = "Characters/Body_Attachments/Mouths/Mouth1_Textures/Default_Greyscale.png" if gender == "Male" else "Characters/Body_Attachments/Mouths/Mouth1_Textures/Makeup_Greyscale.png"
    eyebrow_texture = "Characters/Body_Attachments/Eyebrows/Medium_Greyscale.png" if gender == "Male" else "Characters/Body_Attachments/Eyebrows/Thin_Greyscale.png"
    
    attachments = []
    
    # Hair
    while True:
        hair_item = random.choice(haircuts)
        hair = extract_attachment(hair_item)
        if hair:
            hair["GradientSet"] = "Hair"
            hair["GradientId"] = hair_gradient
            attachments.append(hair)
            break
            
    # Face
    attachments.append({
        "Model": "Characters/Body_Attachments/Faces/Player_Face_Detached.blockymodel",
        "Texture": face_texture,
        "GradientSet": "Skin",
        "GradientId": skin_gradient
    })
    
    # Eyes
    attachments.append({
        "Model": "Characters/Body_Attachments/Eyes/Eyes.blockymodel",
        "Texture": "Characters/Body_Attachments/Eyes/Eyes_Textures/Greyscale.png"
    })
    
    # Pants
    while True:
        pant_item = random.choice(pants)
        pant = extract_attachment(pant_item)
        if pant:
            attachments.append(pant)
            break
            
    # Tops
    while True:
        top_catalog = undertops if random.choice([True, False]) else overtops
        top_item = random.choice(top_catalog)
        top = extract_attachment(top_item)
        if top:
            attachments.append(top)
            break
            
    # Shoes
    while True:
        shoe_item = random.choice(shoes)
        shoe = extract_attachment(shoe_item)
        if shoe:
            attachments.append(shoe)
            break
            
    # Mouth
    attachments.append({
        "Model": "Characters/Body_Attachments/Mouths/Mouth1.blockymodel",
        "Texture": mouth_texture,
        "GradientSet": "Skin",
        "GradientId": skin_gradient
    })
    
    # Ears
    attachments.append({
        "Model": "Characters/Body_Attachments/Ears/Ears1.blockymodel",
        "Texture": "Characters/Body_Attachments/Ears/Ears.png",
        "GradientSet": "Skin",
        "GradientId": skin_gradient
    })
    
    # Eyebrows
    attachments.append({
        "Model": "Characters/Body_Attachments/Eyebrows/Eyebrows_Medium.blockymodel",
        "Texture": eyebrow_texture,
        "GradientSet": "Hair",
        "GradientId": hair_gradient
    })
    
    # Clean up empty textures
    for att in attachments:
        if "Texture" in att and att["Texture"] == "":
            del att["Texture"]
            
    model_name = f"SimTale_Human_Child_{index}"
    
    model_json = {
        "Parent": "Player",
        "Model": "NPC/Player_Child/Player_With_Face_Child.blockymodel",
        "EyeHeight": 1.1,
        "GradientSet": "Skin",
        "GradientId": skin_gradient,
        "DefaultAttachments": attachments
    }
    
    with open(os.path.join(MODELS_DIR, f"{model_name}.json"), "w") as f:
        json.dump(model_json, f, indent=2)
        
    role_json = {
        "Type": "Generic",
        "Appearance": model_name,
        "MaxHealth": 100,
        "MotionControllerList": [
            {
                "Type": "Walk",
                "MaxWalkSpeed": 2.5,
                "Gravity": 10,
                "MaxFallSpeed": 8,
                "Acceleration": 10
            }
        ],
        "StartState": "Idle",
        "Instructions": [
            {
                "Sensor": { "Type": "Any" },
                "Instructions": [
                    {
                        "Sensor": { "Type": "State", "State": "Idle" },
                        "BodyMotion": {
                            "Type": "Sequence",
                            "Looped": True,
                            "Motions": [
                                {
                                    "Type": "Timer",
                                    "Time": [4, 8],
                                    "Motion": {
                                        "Type": "WanderInCircle",
                                        "Radius": 8,
                                        "MaxHeadingChange": 60,
                                        "RelativeSpeed": 0.5
                                    }
                                },
                                {
                                    "Type": "Timer",
                                    "Time": [3, 7],
                                    "Motion": { "Type": "Nothing" }
                                }
                            ]
                        }
                    }
                ]
            }
        ],
        "NameTranslationKey": "npc.simtale.name"
    }
    
    with open(os.path.join(ROLES_DIR, f"{model_name}.json"), "w") as f:
        json.dump(role_json, f, indent=2)

for i in range(1, 11):
    generate_child_model(i)

print("Generated 10 clothed child NPC variants successfully.")
