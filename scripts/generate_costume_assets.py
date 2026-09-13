import os
import json

# Gera os assets de fantasia por-NPC: em vez de "Parent" apontar para a base generica
# (SimTale_Human_Male/Female/Child, que tem seu proprio cabelo/rosto padrao), cada arquivo
# gerado aqui aponta "Parent" para o id ESPECIFICO daquela NPC em Generated/<id>.json --
# assim a NPC mantem a propria cara (cabelo, rosto, olhos, roupa) e so ganha o item extra
# do evento por cima, em vez de virar visualmente identica a qualquer outra NPC fantasiada.
#
# Ver docs/experimentos.md / wiki/dev/experiments.md, secao "a limitacao de 'trocar o
# modelo inteiro'" (13/09), para o raciocinio completo de por que isso e necessario e por
# que uma alternativa em runtime (montar um ModelAsset na mao) foi descartada.
#
# Idempotente: pode ser rodado de novo a qualquer momento (ex: quando novas variantes forem
# adicionadas em Generated/) sem duplicar nem sobrescrever o que ja existe.

MODELS_DIR = "/home/cookie/Documents/hy mods/SimTale/src/main/resources/Server/Models"
GENERATED_DIR = os.path.join(MODELS_DIR, "Generated")
OUTPUT_DIR = os.path.join(MODELS_DIR, "Events", "Generated")

# Mesmos attachments usados pelos assets de fantasia genericos existentes
# (Server/Models/Events/SimTale_Human_Male_Christmas.json / _Halloween.json), copiados aqui
# como dado simples para este script nao depender daqueles arquivos existirem/terem esse
# formato exato.
EVENTS = {
    "Christmas": [
        {
            "Model": "Cosmetics/Head/SantaHat.blockymodel",
            "Texture": "Cosmetics/Head/SantaHat_Texture/SantaHat_Greyscale_Texture.png",
            "GradientSet": "Colored_Cotton",
            "GradientId": "Red"
        }
    ],
    "Halloween": [
        {
            "Model": "Cosmetics/Head/StrawHat.blockymodel",
            "Texture": "Cosmetics/Head/StrawHat_Textures/WitchHat_Colored_Greyscale_Texture.png"
        }
    ],
}

# Mesmos attachments acima, mas com "Model" apontando para a versao _Child (escalada em
# 1.2x, gerada por scripts/generate_child_event_hats.py) em vez do modelo adulto sem escala.
# A "Texture" continua igual a adulta de proposito -- e assim que todo outro cosmetico de
# crianca do projeto funciona (ver Generated/SimTale_Human_Child_*.json): so o "Model" muda,
# a textura/gradiente sao os mesmos, o textureLayout dentro do .blockymodel escalado ja mapeia
# pros mesmos pixels.
#
# Bug que isso existe pra evitar (13/09): antes, as variantes de CRIANCA usavam o mesmo
# EVENTS acima (chapeu em tamanho adulto), o unico cosmetico de cabeca do projeto que nao
# passava pelo pipeline de escala pra crianca -- resultado: chapeu malencaixado na cabeca
# menor, com faces sem UV (nunca precisaram de UV no tamanho adulto, ficavam sempre escondidas
# dentro do proximo box) aparecendo ("cabeca bugada": lateral transparente, parte de tras
# fora do lugar). Ver docs/experimentos.md para o relato completo.
EVENTS_CHILD = {
    "Christmas": [
        {
            "Model": "NPC/Player_Child/Cosmetics/Head/SantaHat_Child.blockymodel",
            "Texture": "Cosmetics/Head/SantaHat_Texture/SantaHat_Greyscale_Texture.png",
            "GradientSet": "Colored_Cotton",
            "GradientId": "Red"
        }
    ],
    "Halloween": [
        {
            "Model": "NPC/Player_Child/Cosmetics/Head/StrawHat_Child.blockymodel",
            "Texture": "Cosmetics/Head/StrawHat_Textures/WitchHat_Colored_Greyscale_Texture.png"
        }
    ],
}

os.makedirs(OUTPUT_DIR, exist_ok=True)

generated_ids = sorted(f[:-5] for f in os.listdir(GENERATED_DIR) if f.endswith(".json"))

written = 0
skipped = 0
for npc_id in generated_ids:
    events_for_npc = EVENTS_CHILD if npc_id.startswith("SimTale_Human_Child") else EVENTS
    for event_name, attachments in events_for_npc.items():
        out_name = f"{npc_id}_{event_name}.json"
        out_path = os.path.join(OUTPUT_DIR, out_name)
        if os.path.exists(out_path):
            skipped += 1
            continue

        data = {
            "Parent": npc_id,
            "DefaultAttachments": attachments,
        }
        with open(out_path, "w") as f:
            json.dump(data, f, indent=2)
        written += 1

print(f"{len(generated_ids)} variantes de NPC encontradas em Generated/.")
print(f"Escritos {written} arquivos de fantasia em {OUTPUT_DIR}.")
if skipped:
    print(f"Pulados {skipped} que ja existiam (rodar de novo e seguro).")
