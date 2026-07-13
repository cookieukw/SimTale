import os
import json

GENERATED_DIR = "/home/cookie/Documents/hy mods/SimTale/src/main/resources/Server/Models/Generated"

for filename in os.listdir(GENERATED_DIR):
    if not filename.endswith(".json"):
        continue
        
    filepath = os.path.join(GENERATED_DIR, filename)
    with open(filepath, "r") as f:
        try:
            data = json.load(f)
        except Exception as e:
            print(f"Error reading {filename}: {e}")
            continue
            
    # Determine the correct parent based on filename prefix
    if filename.startswith("SimTale_Human_Female_"):
        data["Parent"] = "SimTale_Human_Female"
    elif filename.startswith("SimTale_Human_Male_"):
        data["Parent"] = "SimTale_Human_Male"
    elif filename.startswith("SimTale_Human_Child_Female_") or filename.startswith("SimTale_Human_Child_Male_") or filename.startswith("SimTale_Human_Child_"):
        data["Parent"] = "SimTale_Human_Child"
        
    with open(filepath, "w") as f:
        json.dump(data, f, indent=2)

print("Parent mappings updated successfully for all generated NPCs!")
