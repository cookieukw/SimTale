---
sidebar_position: 3
title: Build environment
---

# Build environment

## Requirements

- **JDK 21+** — the project targets a toolchain that older JDKs cannot compile
- Gradle wrapper (included)
- A local Hytale install, for deploying

## Building

```bash
./gradlew jar       # build only
./gradlew deploy    # build and copy into Hytale's Mods folder
```

## The deploy task is atomic on purpose

`deploy` writes to a temporary file and then calls `Files.move(..., ATOMIC_MOVE)`.

The previous version was a plain Gradle `Copy`, writing straight over
`Mods/SimTale-x.y.z.jar`. Hytale watches that folder and reloads the mod as soon as the file
changes — so it fired a reload the instant writing began and read a half-written jar:

```
FAIL: /Server/NPC/Roles/SimTale_Human_Male.json: Failed to load builder:
java.util.zip.ZipException: invalid LOC header (bad signature)
```

followed by `Reloading nonexistent role ...` for every generated role. The jar on disk was fine the
whole time; the problem was purely the moment of reading. With 11 MB, the corruption window is wide.

## Verifying without compiling

Sometimes you cannot compile — a sandbox with an older JDK, for instance. These checks catch most
mistakes:

### Brace and paren balance

```bash
python3 -c "
import re
s = open('File.java').read()
s = re.sub(r'//.*', '', s)
s = re.sub(r'/\*.*?\*/', '', s, flags=re.S)
s = re.sub(r'\".*?\"', '\"\"', s)
print('braces', s.count('{') - s.count('}'))
print('parens', s.count('(') - s.count(')'))
"
```

### Checking an engine API actually exists

Unzip the server jar and read the constant pool. Method names, field names and descriptors are all
in there as plain strings:

```bash
unzip -q HytaleServer.jar "com/hypixel/hytale/.../Target.class" -d out
python3 -c "
import re
d = open('out/.../Target.class','rb').read()
print([s.decode('utf8','ignore') for s in re.findall(rb'[ -~]{3,80}', d)])
"
```

This is how `ItemIcon`'s `#Icon.ItemId` property was confirmed: the literal string `" #Icon.ItemId"`
sits in the constant pool of `ItemRepairElement`.

### Checking an asset exists

Game assets live under `Server/` and `Common/` in the Hytale install. Item ids are file names:

```bash
find Server/Item/Items -iname "*popcorn*"
# Server/Item/Items/Food/Food_Popcorn.json  ->  the id is Food_Popcorn
```

## Automated UI Validations

O processo de build (`processResources`) executa automaticamente o `tools/check_ui.py`. Esse script é vital porque o cliente do Hytale rejeita **toda a interface customizada** se um único arquivo estiver malformado, falhando a conexão silenciosamente.

O script roda durante o build e falha a compilação imediatamente se detectar:
- Erros de sintaxe nos arquivos `.ui`.
- Chaves de tradução faltando (evitando o bug de renderizar a chave crua).
- Caminhos de assets inválidos (distinguindo texturas do jogo de arquivos não encontrados, usando `HYTALE_ASSETS` do `local.properties`).

## Language files

`src/main/resources/Server/Languages/<locale>/*.lang`, in `key = value` form.

Both `pt-BR` and `en-US` must be updated together. A key present in one and missing in the other
renders as the raw key in game — that is how `prof.hunter` went missing for a long time without
anyone noticing. A validação automatizada de UI agora pega chaves ausentes referenciadas em arquivos de UI, mas o uso manual no código ainda exige cuidado.

## UI files

`src/main/resources/Common/UI/Custom/<Page>/<Page>.ui`.

Every `#id` is a contract with the Java page class. Removing or renaming one throws
`KeyNotFoundException` and the screen fails to load entirely.

Rows have fixed heights, and container heights are **sums that must be maintained by hand**.
Getting the sum wrong does not clip the content — it lets it spill over whatever comes next.
