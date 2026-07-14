import os, re
files = [
    "src/main/java/com/cookieukw/SimTale/systems/PlumbobSystem.java",
    "src/main/java/com/cookieukw/SimTale/systems/SimTaleChatHandler.java",
    "src/main/java/com/cookieukw/SimTale/db/SimNPCData.java",
    "src/main/java/com/cookieukw/SimTale/db/SimNPCPersistence.java",
    "src/main/java/com/cookieukw/SimTale/core/SimNPCNameGenerator.java",
    "src/main/java/com/cookieukw/SimTale/SimTale.java"
]

fqns = {
    "com.hypixel.hytale.component.RemoveReason": "RemoveReason",
    "com.cookieukw.SimTale.SimTale.SIM_NPC_COMPONENT_TYPE": "SimTale.SIM_NPC_COMPONENT_TYPE",
    "com.cookieukw.SimTale.core.SimNPCComponent": "SimNPCComponent",
    "com.cookieukw.SimTale.core.Profession": "Profession",
    "com.cookieukw.SimTale.core.MemoryManager": "MemoryManager",
    "com.hypixel.hytale.logger.HytaleLogger": "HytaleLogger",
    "com.hypixel.hytale.server.core.universe.world.World": "World",
    "com.hypixel.hytale.component.Store": "Store",
    "com.hypixel.hytale.server.core.universe.world.storage.EntityStore": "EntityStore",
    "com.hypixel.hytale.component.ComponentAccessor": "ComponentAccessor",
    "com.cookieukw.SimTale.SimTale.ACTIVE_NPCS": "SimTale.ACTIVE_NPCS",
    "com.hypixel.hytale.component.Ref": "Ref",
    "com.cookieukw.SimTale.ai.RoutineAIComponent.class": "RoutineAIComponent.class",
    "com.cookieukw.SimTale.SimTale": "SimTale"
}

imports = {
    "RemoveReason": "com.hypixel.hytale.component.RemoveReason",
    "SimTale": "com.cookieukw.SimTale.SimTale",
    "SimNPCComponent": "com.cookieukw.SimTale.core.SimNPCComponent",
    "Profession": "com.cookieukw.SimTale.core.Profession",
    "MemoryManager": "com.cookieukw.SimTale.core.MemoryManager",
    "HytaleLogger": "com.hypixel.hytale.logger.HytaleLogger",
    "World": "com.hypixel.hytale.server.core.universe.world.World",
    "Store": "com.hypixel.hytale.component.Store",
    "EntityStore": "com.hypixel.hytale.server.core.universe.world.storage.EntityStore",
    "ComponentAccessor": "com.hypixel.hytale.component.ComponentAccessor",
    "Ref": "com.hypixel.hytale.component.Ref",
    "RoutineAIComponent": "com.cookieukw.SimTale.ai.RoutineAIComponent"
}

for f in files:
    if not os.path.exists(f): continue
    with open(f, "r") as file:
        content = file.read()
    
    needed_imports = set()
    for fqn, simple in fqns.items():
        if fqn in content:
            needed_imports.add(simple.split('.')[0])
            content = content.replace(fqn, simple)
            
    # Add imports
    lines = content.split('\n')
    import_lines = [l for l in lines if l.startswith("import ")]
    existing_imports = set([l.split(' ')[1].replace(';','') for l in import_lines if len(l.split(' ')) > 1])
    
    for imp in needed_imports:
        if imp in imports and imports[imp] not in existing_imports:
            for i, l in enumerate(lines):
                if l.startswith("package "):
                    lines.insert(i+1, "import " + imports[imp] + ";")
                    break
    
    with open(f, "w") as file:
        file.write('\n'.join(lines))
