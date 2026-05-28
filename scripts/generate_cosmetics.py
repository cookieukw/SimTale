import json
import random
import os

COSMETICS_DIR = "/home/cookie/Documents/Assets_do_Hytale/Cosmetics/CharacterCreator"
MOD_RESOURCES = "/home/cookie/Documents/hy mods/SimTale/src/main/resources/Server"

MODELS_DIR = os.path.join(MOD_RESOURCES, "Models/Generated")
ROLES_DIR = os.path.join(MOD_RESOURCES, "NPC/Roles/Generated")

os.makedirs(MODELS_DIR, exist_ok=True)
os.makedirs(ROLES_DIR, exist_ok=True)

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
            
    # Fallback to empty if not found, but log if no model (shouldn't happen for valid items)
    if not model:
        return None
        
    att = {
        "Model": model,
        "Texture": tex
    }
    if "GradientSet" in item:
        att["GradientSet"] = item["GradientSet"]
        if item["GradientSet"] in gradients:
            att["GradientId"] = random.choice(gradients[item["GradientSet"]])
    return att

def generate_model(gender, index):
    skin_gradient = random.choice(gradients["Skin"])
    hair_gradient = random.choice(gradients["Hair"])
    
    face_texture = "Characters/Body_Attachments/Faces/Faces_Detached_Textures/Face.png" if gender == "Male" else "Characters/Body_Attachments/Faces/Faces_Detached_Textures/MakeUp_Face.png"
    mouth_texture = "Characters/Body_Attachments/Mouths/Mouth1_Textures/Default_Greyscale.png" if gender == "Male" else "Characters/Body_Attachments/Mouths/Mouth1_Textures/Makeup_Greyscale.png"
    eyebrow_texture = "Characters/Body_Attachments/Eyebrows/Medium_Greyscale.png" if gender == "Male" else "Characters/Body_Attachments/Eyebrows/Thin_Greyscale.png"
    
    attachments = []
    
    # Hair
    while True:
        hair = extract_attachment(random.choice(haircuts))
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
        pant = extract_attachment(random.choice(pants))
        if pant:
            attachments.append(pant)
            break
            
    # Tops
    while True:
        top_catalog = undertops if random.choice([True, False]) else overtops
        top = extract_attachment(random.choice(top_catalog))
        if top:
            attachments.append(top)
            break
            
    # Shoes
    while True:
        shoe = extract_attachment(random.choice(shoes))
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
        "Texture": "Characters/Body_Attachments/Ears/Ears1_Textures/2.png"
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
            
    model_name = f"SimTale_Human_{gender}_{index}"
    
    model_json = {
        "Parent": "Player",
        "Model": "Characters/Player.blockymodel",
        "DefaultAttachments": attachments
    }
    
    with open(os.path.join(MODELS_DIR, f"{model_name}.json"), "w") as f:
        json.dump(model_json, f, indent=2)
        
    role_json = {
        "Type": "Generic",
        "Appearance": f"Generated/{model_name}",
        "MaxHealth": 200,
        "MotionControllerList": [
            {
                "Type": "CharacterMotion",
                "Speed": 2.2,
                "RotationSpeed": 8.0
            }
        ]
    }
    
    with open(os.path.join(ROLES_DIR, f"{model_name}.json"), "w") as f:
        json.dump(role_json, f, indent=2)

for i in range(1, 51):
    generate_model("Male", i)
    generate_model("Female", i)

print("Generated 50 male and 50 female variants.")
