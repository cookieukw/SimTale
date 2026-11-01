"""Add a neutral base shirt beneath existing overtop-only NPC variants."""

import glob
import json

BASE_ADULT = {
    "Model": "Cosmetics/Undertops/Tshirt.blockymodel",
    "Texture": "Cosmetics/Undertops/Tshirt_Textures/ShortSleeves_Greyscale.png",
    "GradientSet": "Colored_Cotton",
    "GradientId": "White",
}
BASE_CHILD = {
    "Model": "NPC/Player_Child/Cosmetics/Undertops/Tshirt_Child.blockymodel",
    "Texture": "Cosmetics/Undertops/Tshirt_Textures/ShortSleeves_Greyscale.png",
    "GradientSet": "Colored_Cotton",
    "GradientId": "White",
}


def repair(path):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    attachments = data.get("DefaultAttachments")
    if not attachments:
        return False
    models = [a.get("Model", "") for a in attachments]
    if not any("/Overtops/" in model for model in models):
        return False
    if any("/Undertops/" in model for model in models):
        return False
    base = BASE_CHILD if "Child" in path else BASE_ADULT
    first_overtop = next(i for i, model in enumerate(models) if "/Overtops/" in model)
    attachments.insert(first_overtop, dict(base))
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    return True


patterns = [
    "src/main/resources/Server/Models/Generated/*.json",
    "src/main/resources/Server/Models/Events/Generated/*.json",
]
changed = 0
for pattern in patterns:
    for path in glob.glob(pattern):
        changed += repair(path)
print(f"Repaired {changed} overtop-only model variants.")
