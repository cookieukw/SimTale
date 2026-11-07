"""Builds the five male and five female merchant appearances and their NPC roles.

The merchant is not a new body: each variant starts from an existing SimTale human of the same
number, keeps that person's face, hair, skin and shoes, and only swaps the clothes for the vanilla
merchant set (hat, dress, belted pants, mittens). Roles are copied from the matching human role so
health, movement and AI instructions stay identical to every other resident.
"""
import copy
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RESOURCES = os.path.join(ROOT, "src", "main", "resources")
MODELS_DIR = os.path.join(RESOURCES, "Server", "Models", "Generated")
ROLES_DIR = os.path.join(RESOURCES, "Server", "NPC", "Roles", "Generated")

VARIANTS = [1, 2, 3, 4, 5]
GENDERS = ["Male", "Female"]

MERCHANT_ATTACHMENTS = {
    "pants": {
        "Model": "Cosmetics/Pants/Short_Bermuda_Belted.blockymodel",
        "Texture": "Cosmetics/Pants/Short_Bermuda_Belted_Textures/Merchant_Belted_Pants_Orange.png",
        "GradientSet": "Colored_Cotton",
        "GradientId": "Brown",
    },
    "overtop": {
        "Model": "Cosmetics/Overtops/Merchant_Dress.blockymodel",
        "Texture": "Cosmetics/Overtops/Merchant_Dress_Texture/Merchant_Dress_Orange.png",
        "GradientSet": "Colored_Cotton",
        "GradientId": "Orange",
    },
    "gloves": {
        "Model": "Cosmetics/Gloves/LongGloves.blockymodel",
        "Texture": "Cosmetics/Gloves/LongGloves_Textures/Merchant_Mittens_Texture.png",
        "GradientSet": "Colored_Cotton",
        "GradientId": "Brown",
    },
    "hat": {
        "Model": "Cosmetics/Head/Merchant_Hat.blockymodel",
        "Texture": "Cosmetics/Head/Merchant_Hat_Textures/Merchant_Hat_Orange.png",
        "GradientSet": "Colored_Cotton",
        "GradientId": "Orange",
    },
}

# Clothing replaced by the merchant set. Everything else (face, hair, eyes, ears, mouth, eyebrows,
# facial hair, undertop, shoes) is preserved exactly as the base variant had it.
REPLACED_PREFIXES = (
    "Cosmetics/Pants/",
    "Cosmetics/Overpants/",
    "Cosmetics/Overtops/",
    "Cosmetics/Gloves/",
    "Cosmetics/Capes/",
)

# Head accessories (hats) belong to the merchant hat, so they are dropped too. Jewellery and ear
# accessories live under other folders and stay.
REPLACED_HEAD_PREFIXES = ("Cosmetics/Head/",)


def attachment_model(attachment):
    return attachment.get("Model") or ""


def is_clothing(attachment):
    model = attachment_model(attachment)
    return model.startswith(REPLACED_PREFIXES) or model.startswith(REPLACED_HEAD_PREFIXES)


def merchant_model(base_model, variant, gender):
    model = copy.deepcopy(base_model)
    model["Parent"] = f"SimTale_Human_{gender}_{variant}"

    kept = []
    for attachment in model.get("DefaultAttachments", []):
        if not is_clothing(attachment):
            kept.append(attachment)

    # Pants keep the slot they had; the dress is inserted after the undertop so it draws over it.
    outfit = [MERCHANT_ATTACHMENTS["pants"]]
    for attachment in kept:
        outfit.append(attachment)
        if "Undertops/" in attachment_model(attachment):
            outfit.append(MERCHANT_ATTACHMENTS["overtop"])
    if MERCHANT_ATTACHMENTS["overtop"] not in outfit:
        outfit.append(MERCHANT_ATTACHMENTS["overtop"])
    outfit.append(MERCHANT_ATTACHMENTS["gloves"])
    outfit.append(MERCHANT_ATTACHMENTS["hat"])

    model["DefaultAttachments"] = outfit
    return model


def merchant_role(base_role, variant, gender):
    role = copy.deepcopy(base_role)
    role["Appearance"] = f"SimTale_Merchant_{gender}_{variant}"
    return role


def main():
    written = 0
    for gender in GENDERS:
        for variant in VARIANTS:
            base_model_path = os.path.join(MODELS_DIR, f"SimTale_Human_{gender}_{variant}.json")
            base_role_path = os.path.join(ROLES_DIR, f"SimTale_Human_{gender}_{variant}.json")
            with open(base_model_path, encoding="utf-8") as handle:
                base_model = json.load(handle)
            with open(base_role_path, encoding="utf-8") as handle:
                base_role = json.load(handle)

            model = merchant_model(base_model, variant, gender)
            role = merchant_role(base_role, variant, gender)

            stem = f"SimTale_Merchant_{gender}_{variant}"
            with open(os.path.join(MODELS_DIR, stem + ".json"), "w", encoding="utf-8") as handle:
                json.dump(model, handle, indent=2, ensure_ascii=False)
                handle.write("\n")
            with open(os.path.join(ROLES_DIR, stem + ".json"), "w", encoding="utf-8") as handle:
                json.dump(role, handle, indent=2, ensure_ascii=False)
                handle.write("\n")
            written += 2

    print(f"wrote {written} merchant files")


if __name__ == "__main__":
    main()
