import json
import os
import copy

MOD_RESOURCES = "/home/cookie/Documents/hy mods/SimTale/src/main/resources"
ASSETS_DIR = "/home/cookie/Documents/Assets/Common"

MODELS_DIR = os.path.join(MOD_RESOURCES, "Server/Models/Generated")
DOLLS_COSMETICS_DIR = os.path.join(MOD_RESOURCES, "Common/NPC/Dolls/Cosmetics")

os.makedirs(DOLLS_COSMETICS_DIR, exist_ok=True)

# Proporções da ChibiDoll em relação ao Player.blockymodel padrão (Infladas ligeiramente nas roupas para evitar clipping/atravessar a malha)
NODE_SCALES = {
    "Pelvis":       (0.64, 0.54, 0.67),
    "Belly":        (0.64, 0.41, 0.67),
    "Chest":        (0.63, 0.40, 0.66),
    "Head":         (0.60, 0.643, 0.643), # Cabeça e rosto mantidos proporcionais
    "R-Shoulder":   (0.63, 0.40, 0.66),
    "L-Shoulder":   (0.63, 0.40, 0.66),
    "R-Arm":        (0.72, 0.36, 0.72),
    "L-Arm":        (0.72, 0.36, 0.72),
    "R-Forearm":    (0.72, 0.29, 0.72),
    "L-Forearm":    (0.72, 0.29, 0.72),
    "R-Hand":       (0.75, 0.71, 0.75),
    "L-Hand":       (0.75, 0.71, 0.75),
    "R-Thigh":      (0.83, 0.36, 0.83),
    "L-Thigh":      (0.83, 0.36, 0.83),
    "R-Calf":       (0.83, 0.40, 0.83),
    "L-Calf":       (0.83, 0.40, 0.83),
    "R-Foot":       (0.86, 0.64, 0.69),
    "L-Foot":       (0.86, 0.64, 0.69),
}

FACE_ATTACHMENT_NAMES = {
    "Neck", "L-Eyelid", "R-Eyelid", "L-Eyelid-Bot", "R-Eyelid-Bot",
    "L-Eyebrow-Attachment", "R-Eyebrow-Attachment",
    "R-Eye-Attachment", "L-Eye-Attachment", "Mouth-Attachment",
    "R-Ear-Attachment", "L-Ear-Attachment",
}

def get_own_scale(name, inherited_scale):
    if name in FACE_ATTACHMENT_NAMES:
        return NODE_SCALES["Head"]
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
    # Reset root attachment node position to 0,0,0 so it snaps exactly onto ChibiDoll's bone pivots
    root["position"] = {"x": 0.0, "y": 0.0, "z": 0.0}
    own_scale = get_own_scale(root.get("name"), (1.0, 1.0, 1.0))
    scale_shape(root.get("shape"), own_scale)
    for child in root.get("children", []):
        process_node(child, own_scale)

def scale_cosmetic_model(model_rel_path):
    # Always load the ADULT original cosmetic to avoid double-scaling, 
    # since NODE_SCALES are relative to the standard Player.
    # Replace the folder path first, then remove the _Child suffix from the filename
    clean_path = model_rel_path.replace("NPC/Player_Child/Cosmetics/", "Cosmetics/").replace("_Child", "").strip()
    
    # Haircuts in the adult assets are stored in Characters/Haircuts, not Cosmetics/Haircuts
    if clean_path.startswith("Cosmetics/Haircuts/"):
        clean_path = clean_path.replace("Cosmetics/Haircuts/", "Characters/Haircuts/")
        
    # Try resolving from base assets folder first
    src_path = os.path.join(ASSETS_DIR, clean_path)
    if not os.path.exists(src_path):
        # Fall back to mod resources
        src_path = os.path.join(MOD_RESOURCES, "Common", clean_path)
        if not os.path.exists(src_path):
            print(f"Warning: Could not find source model for {clean_path}")
            return None

    filename = os.path.basename(src_path)
    name_part, ext = os.path.splitext(filename)
    dest_filename = f"{name_part}_ChibiDoll{ext}"
    
    # Identify folder (Haircuts, Pants, Shoes, Undertops, etc)
    subfolder = os.path.dirname(model_rel_path).split('/')[-1]
    if subfolder == "Cosmetics":
        subfolder = "Pants" # default fallback
    
    dest_dir = os.path.join(DOLLS_COSMETICS_DIR, subfolder)
    os.makedirs(dest_dir, exist_ok=True)
    dest_path = os.path.join(dest_dir, dest_filename)

    with open(src_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    data = copy.deepcopy(data)

    for root in data["nodes"]:
        process_root(root)

    with open(dest_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)

    return f"NPC/Dolls/Cosmetics/{subfolder}/{dest_filename}"

def generate_doll_config(doll_id, child_file_name):
    with open(os.path.join(MODELS_DIR, child_file_name)) as f:
        child_data = json.load(f)
    
    child_attachments = child_data.get("DefaultAttachments", [])
    doll_attachments = []
    
    for att in child_attachments:
        new_att = att.copy()
        model_path = att.get("Model", "")
        
        # Check if it needs scaling
        if model_path:
            # Scale ALL attachments (including face features) so they align with ChibiDoll proportions!
            scaled_path = scale_cosmetic_model(model_path)
            if scaled_path:
                new_att["Model"] = scaled_path
            
            # Remove any scale/translation overrides so it loads completely cleanly
            new_att.pop("Scale", None)
            new_att.pop("Translation", None)
            
        doll_attachments.append(new_att)
        
    doll_json = {
        "Parent": "Player",
        "Model": "NPC/Dolls/ChibiDoll.blockymodel",
        "GradientSet": child_data.get("GradientSet", "Skin"),
        "GradientId": child_data.get("GradientId", "02"),
        "HitBox": {
            "Max": {
                "X": 0.3,
                "Y": 1.0,
                "Z": 0.3
            },
            "Min": {
                "X": -0.3,
                "Y": 0.0,
                "Z": -0.3
            }
        },
        "DefaultAttachments": doll_attachments
    }
    
    dest_path = os.path.join(MODELS_DIR, f"Doll_{doll_id}.json")
    with open(dest_path, "w") as f:
        json.dump(doll_json, f, indent=2)
        
    print(f"Generated Doll_{doll_id}.json using cosmetics from {child_file_name}")

if __name__ == "__main__":
    child_files = sorted([f for f in os.listdir(MODELS_DIR) if "Child" in f and f.endswith(".json") and not f.startswith("Doll_")])
    for i in range(1, 11):
        child_file = child_files[i-1]
        generate_doll_config(i, child_file)
    print("All 10 Doll models successfully compiled with ChibiDoll proportional cosmetics!")
