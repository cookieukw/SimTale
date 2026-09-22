import os
import re

JAVA_DIR = 'src/main/java'

# Match package prefixes up to the first Capitalized word (the class name)
FQN_REGEX = re.compile(r'(?<!import\s)(?<!package\s)\b((?:com\.hypixel\.hytale|java\.util|com\.cookieukw\.SimTale)(?:\.[a-z0-9_]+)*\.[A-Z][a-zA-Z0-9_]*)\b')

def process_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    matches = FQN_REGEX.findall(content)
    if not matches:
        return

    imports = set()
    for match in re.finditer(r'^import\s+([\w\.]+);', content, re.MULTILINE):
        imports.add(match.group(1))
        
    new_imports = set()
    
    def replacer(match):
        fqn = match.group(1)
        class_name = fqn.split('.')[-1]
        new_imports.add(fqn)
        return class_name

    new_content = FQN_REGEX.sub(replacer, content)
    
    # Filter out imports that are already present
    to_add = [f"import {imp};" for imp in new_imports if imp not in imports]
    
    if to_add:
        # Find where to insert imports (after package declaration)
        package_match = re.search(r'^package\s+[\w\.]+;\s*', new_content, re.MULTILINE)
        if package_match:
            insert_pos = package_match.end()
            imports_str = "\n" + "\n".join(sorted(to_add)) + "\n"
            new_content = new_content[:insert_pos] + imports_str + new_content[insert_pos:]
        else:
            # If no package declaration, insert at top
            imports_str = "\n".join(sorted(to_add)) + "\n\n"
            new_content = imports_str + new_content
            
    if new_content != content:
        with open(filepath, 'w') as f:
            f.write(new_content)
        print(f"Updated {filepath}")

for root, _, files in os.walk(JAVA_DIR):
    for f in files:
        if f.endswith('.java'):
            process_file(os.path.join(root, f))
