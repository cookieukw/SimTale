# Architecture

## Overview

SimTale is a Hytale server-side mod focused on NPC management, social interactions, and persistence. It follows a component-system-event architecture provided by the Hytale Modding SDK.

## Key Components

- **SimNPCComponent:** Attaches custom NPC data to Hytale entities.
- **SimNPCFactory:** Handles spawning and initialization of NPCs.
- **Caskara Integration:** Manages database persistence for NPC stats.

## Systems

- **Tick Systems:** Handles NPC resource consumption (needs) and behavior updates.
- **Event Handlers:** Listens for player interactions and world events to trigger NPC reactions.
- **Command System:** Provides administrative tools for managing NPCs.
