import os
import json
from PIL import Image, ImageOps

MODELS_DIR = "/home/cookie/Documents/hy mods/SimTale/src/main/resources/Server/Models"
TEXTURES_DIR = "/home/cookie/Documents/hy mods/SimTale/src/main/resources/Common/NPC/Plumbob"
BASE_IMG_PATH = os.path.join(TEXTURES_DIR, "plumbob.png")
BASE_MODEL_PATH = os.path.join(TEXTURES_DIR, "plumbob.blockymodel")

MOOD_COLORS = {
    "NEUTRAL": (220, 220, 220), # Light Gray
    "HAPPY": (50, 255, 50),     # Bright Green
    "ANGRY": (255, 30, 30),     # Bright Red
    "SAD": (30, 100, 255),      # Deep Blue
    "SCARED": (255, 140, 0),    # Orange
    "SLEEPY": (148, 0, 211),    # Purple
    "EXCITED": (255, 215, 0),   # Gold
    "BORED": (160, 160, 160)    # Slate Gray
}

# Ensure directories exist
os.makedirs(MODELS_DIR, exist_ok=True)
os.makedirs(TEXTURES_DIR, exist_ok=True)

try:
    base_img = Image.open(BASE_IMG_PATH).convert("RGBA")
except Exception as e:
    print(f"Failed to open base image: {e}")
    exit(1)

# Convert to grayscale for tinting, but keep alpha
gray_img = ImageOps.grayscale(base_img.convert("RGB"))

for mood, color in MOOD_COLORS.items():
    print(f"Generating assets for {mood}...")
    
    # 1. Generate tinted image
    colored_img = ImageOps.colorize(gray_img, black="black", white=color)
    
    # Put alpha back
    final_img = colored_img.convert("RGBA")
    final_img.putalpha(base_img.getchannel("A"))
    
    tex_filename = f"plumbob_{mood.lower()}.png"
    tex_path = os.path.join(TEXTURES_DIR, tex_filename)
    final_img.save(tex_path)
    
    # 2. Generate Model JSON
    model_name = f"Plumbob_{mood}"
    model_json = {
      "Model": "NPC/Plumbob/plumbob.blockymodel",
      "Texture": f"NPC/Plumbob/{tex_filename}",
      "HitBox": {
        "Min": {
            "X": -0.5,
            "Y": -0.5,
            "Z": -0.5
        },
        "Max": {
            "X": 0.5,
            "Y": 0.5,
            "Z": 0.5
        }
      }
    }
    
    with open(os.path.join(MODELS_DIR, f"{model_name}.json"), "w") as f:
        json.dump(model_json, f, indent=2)

print("Generated all Plumbobs!")
