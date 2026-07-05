import json

with open("/home/cookie/Documents/hy mods/SimTale/src/main/resources/Common/NPC/Player_Child/Player_With_Face_Child.blockymodel", "r") as f:
    data = json.load(f)

def print_nodes(nodes, indent=""):
    for node in nodes:
        name = node.get("name")
        shape = node.get("shape")
        shape_type = shape.get("type") if shape else "none"
        size = ""
        if shape and "settings" in shape and "size" in shape["settings"]:
            size = f" size={shape['settings']['size']}"
        print(f"{indent}- {name} ({shape_type}){size}")
        if "children" in node:
            print_nodes(node["children"], indent + "  ")

print_nodes(data["nodes"])
