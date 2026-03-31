# Project Structure

## Directory Layout

- `src/main/java/com/cookieukw/SimTale/`: Core plugin classes.
  - `core/`: NPC components, factory, and core data models (Needs, Personality).
  - `db/`: Persistence layer interacting with Caskara.
  - `logic/`: NPC interaction and social mechanics.
  - `systems/`: Hytale event and tick systems.

- `src/main/resources/`: Configuration and asset definitions.
  - `manifest.json`: Mod metadata and dependencies.
  - `Server/`: Server-side assets (NPC roles, item recipes, and NPC models).

- `.planning/`: Project management and design documentation.
- `assets/`: Custom mod assets (models, textures).
