import os
import re

# List of files in order for the player manual
FILES = [
    "intro.md",
    "installation.md",
    "getting-started.md",
    "houses/building-a-house.md",
    "houses/beds-and-residents.md",
    "houses/villages.md",
    "interacting.md",
    "npcs/needs.md",
    "npcs/personality-and-tastes.md",
    "npcs/relationships.md",
    "npcs/family-and-growth.md",
    "npcs/jobs-and-hobbies.md",
    "items.md",
    "faq.md"
]

def process_file(content, is_first, is_pt, is_curseforge=False):
    # 1. Strip frontmatter
    content = re.sub(r'^---\n.*?\n---\n*', '', content, flags=re.DOTALL)
    
    # 2. Adjust heading levels (except for the first file intro.md which already has H1 # SimTale)
    if not is_first:
        # Demote headings: # -> ##, ## -> ###
        def demote_heading(match):
            return '#' + match.group(0)
        content = re.sub(r'^#', '##', content, flags=re.MULTILINE)
        content = re.sub(r'^##\s', '### ', content, flags=re.MULTILINE)

    # 2.5 Replace the React div with the <p> layout
    react_div = r"<div style=\{\{display:\s*'flex',\s*flexWrap:\s*'wrap',\s*gap:\s*'10px',\s*justifyContent:\s*'center'\}\}>\n\s*<img src=\"/img/variant_1.png\".*?\n\s*<img src=\"/img/variant_2.png\".*?\n\s*<img src=\"/img/variant_3.png\".*?\n\s*<img src=\"/img/variant_4.png\".*?\n</div>"
    
    html_layout = """<p align="center">
  <img src="/img/variant_1.png" width="49%" />
  <img src="/img/variant_2.png" width="49%" />
  <br />
  <img src="/img/variant_3.png" width="49%" />
  <img src="/img/variant_4.png" width="49%" />
</p>"""
    content = re.sub(react_div, html_layout, content, flags=re.DOTALL)

    # 3. Fix image paths
    if is_curseforge:
        # CurseForge requires absolute URLs
        base_img_url = "https://simtale.kukkie.org/img"
        content = re.sub(r'src="(/img/[^"]+)"', lambda m: f'src="{base_img_url}{m.group(1).replace("/img", "")}"', content)
        content = re.sub(r'!\[(.*?)\]\((/path/to/[^)]+|/img/[^)]+)\)', lambda m: f'![{m.group(1)}]({base_img_url}{m.group(2).replace("/path/to", "").replace("/img", "")})', content)
        
        # Also replace the HTML table images
        content = content.replace('src="wiki/static/img/', f'src="{base_img_url}/')
    else:
        # GitHub relative paths
        content = re.sub(r'src="(/img/[^"]+)"', lambda m: f'src="wiki/static{m.group(1)}"', content)
        content = re.sub(r'!\[(.*?)\]\((/path/to/[^)]+|/img/[^)]+)\)', lambda m: f'![{m.group(1)}](wiki/static{m.group(2).replace("/path/to", "/img")})', content)
    
    # Fix React JSX style={{verticalAlign: "middle"}} and similar
    content = re.sub(r'style=\{\{verticalAlign:\s*["\']middle["\']\}\}', 'align="absmiddle"', content)
    content = re.sub(r'style=\{\{textAlign:\s*["\']center["\']\}\}', 'style="text-align: center;"', content)
    content = re.sub(r'style=\{\{imageRendering:\s*["\']pixelated["\']\}\}', 'style="image-rendering: pixelated;"', content)
    
    # Sometimes it has multiple styles like style={{imageRendering: 'pixelated', margin: '0 10px'}}
    content = re.sub(r'style=\{\{imageRendering:\s*["\']pixelated["\'],\s*margin:\s*["\']0\s+10px["\']\}\}', 'style="image-rendering: pixelated; margin: 0 10px;"', content)

    
    # Strip explicit Docusaurus heading anchors e.g. {#breaking-a-bed}
    content = re.sub(r'\{#[^}]+\}', '', content)

    # 4. Rewrite internal wiki links to hash anchors
    # e.g., [Getting started](getting-started.md) -> [Getting started](#getting-started)
    # e.g., [Building a house](houses/building-a-house.md) -> [Building a house](#building-a-house)
    def rewrite_link(match):
        text = match.group(1)
        url = match.group(2)
        
        if url.startswith('http') or url.startswith('wiki/') or url.startswith('#'):
            return match.group(0)
            
        if url.endswith('.md'):
            anchor = os.path.basename(url)[:-3].lower()
            return f'[{text}](#{anchor})'
            
        # If it's not an .md file, it's a Docusaurus route like /admin/generative-ai
        clean_url = url if url.startswith('/') else '/' + url
        return f'[{text}](https://simtale.kukkie.org{clean_url})'
    
    content = re.sub(r'\[([^\]]+)\]\(([^)]+)\)', rewrite_link, content)
    
    # 5. Docusaurus tags (admonitions)
    note_text = "**Nota:**" if is_pt else "**Nota:**"
    caution_text = "**Atenção:**" if is_pt else "**Caution:**"
    tip_text = "**Dica:**" if is_pt else "**Tip:**"
    
    content = re.sub(r':::note\s*(.*?)\n(.*?):::', lambda m: f'> {note_text} {m.group(1)}\n> {m.group(2).replace(chr(10), chr(10)+"> ")}', content, flags=re.DOTALL)
    content = re.sub(r':::caution\s*(.*?)\n(.*?):::', lambda m: f'> {caution_text} {m.group(1)}\n> {m.group(2).replace(chr(10), chr(10)+"> ")}', content, flags=re.DOTALL)
    content = re.sub(r':::tip\s*(.*?)\n(.*?):::', lambda m: f'> {tip_text} {m.group(1)}\n> {m.group(2).replace(chr(10), chr(10)+"> ")}', content, flags=re.DOTALL)
    content = re.sub(r':::info\s*(.*?)\n(.*?):::', lambda m: f'> {note_text} {m.group(1)}\n> {m.group(2).replace(chr(10), chr(10)+"> ")}', content, flags=re.DOTALL)
    content = re.sub(r':::warning\s*(.*?)\n(.*?):::', lambda m: f'> {caution_text} {m.group(1)}\n> {m.group(2).replace(chr(10), chr(10)+"> ")}', content, flags=re.DOTALL)
    
    # Add a language switch at the top of intro.md
    if is_first:
        if is_curseforge:
            # Remove "Where to start" / "Por onde começar" completely
            content = re.sub(r'## Where to start.*?(?=## The other tracks)', '', content, flags=re.DOTALL)
            content = re.sub(r'## Por onde começar.*?(?=## As outras trilhas)', '', content, flags=re.DOTALL)
            
            # Remove "The other tracks" / "As outras trilhas" completely
            content = re.sub(r'## The other tracks.*?(?=:::)', '', content, flags=re.DOTALL)
            content = re.sub(r'## As outras trilhas.*?(?=:::)', '', content, flags=re.DOTALL)
            content = re.sub(r'## The other tracks.*?(?=> \*\*Nota)', '', content, flags=re.DOTALL)
            content = re.sub(r'## As outras trilhas.*?(?=> \*\*Nota)', '', content, flags=re.DOTALL)
            
            # Add a global wiki link at the top
            wiki_link = "*Read the full documentation at [simtale.kukkie.org](https://simtale.kukkie.org/)*\n\n"
            if is_pt:
                wiki_link = "*Leia a documentação completa em [simtale.kukkie.org](https://simtale.kukkie.org/)*\n\n"
            content = content.replace("# SimTale", f"# SimTale\n\n{wiki_link}")
        else:
            if is_pt:
                content = content.replace("# SimTale", "# SimTale\n\n*Leia em [Inglês](README.md)*\n\n")
            else:
                content = content.replace("# SimTale", "# SimTale\n\n*Read this in [Portuguese](README-pt-BR.md)*\n\n")

    return content.strip()

def build_readme(base_dir, out_file, is_pt, is_curseforge=False):
    combined = []
    
    for i, file in enumerate(FILES):
        # Skip installation.md for CurseForge
        if is_curseforge and file == "installation.md":
            continue
            
        path = os.path.join(base_dir, file)
        if not os.path.exists(path):
            print(f"Warning: {path} not found")
            continue
            
        with open(path, 'r', encoding='utf-8') as f:
            raw = f.read()
            
        processed = process_file(raw, i == 0, is_pt, is_curseforge)
        combined.append(processed)
        
    with open(out_file, 'w', encoding='utf-8') as f:
        f.write('\n\n---\n\n'.join(combined))
    
    print(f"Generated {out_file} ({len(combined)} sections)")

if __name__ == '__main__':
    # English
    en_base = 'wiki/docs'
    build_readme(en_base, 'README.md', False)
    build_readme(en_base, 'CURSEFORGE.md', False, True)
    
    # Portuguese
    pt_base = 'wiki/i18n/pt-BR/docusaurus-plugin-content-docs/current'
    build_readme(pt_base, 'README-pt-BR.md', True)
    build_readme(pt_base, 'CURSEFORGE-pt-BR.md', True, True)
