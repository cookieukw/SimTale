---
sidebar_position: 2
title: Installation
---

# Installation

## Requirements

| Item | Version | Required |
|---|---|---|
| Hytale server | `>= 0.5.8` | yes |
| Caskara (database) | `>= 3.0.0` | yes |
| RuneCore | 1.0.12 | yes — used to apply damage and healing to NPCs |
| Java | 25 | only to build |

:::info Where these numbers come from
`ServerVersion` and `Dependencies` in the mod's `manifest.json`. The RuneCore dependency is not
declared there, but the code calls `com.cookie.runecore.api.StatHelper` — without that jar, hunger
cannot take or restore health.
:::

## Install

1. Drop `SimTale-1.0.0.jar`, `Caskara.jar` and `RuneCore-1.0.12.jar` into the server's `Mods/` folder.
2. Start the server.
3. Join a world and craft your first Immigration Contract.

```
[SimTale] Scan found 2 new beds and 1 new chests. Totals: 2 beds, 1 chests
```

That line is the furniture scan that runs when you join a world. It only appears when it finds
something new.

## Building from source

```bash
./gradlew deploy
```

The `deploy` task builds the jar and copies it into Hytale's `Mods` folder.

:::warning Close the game before building
The copy is atomic precisely to avoid this, but building with the game closed is still safer.
Hytale watches the `Mods` folder and reloads the mod by itself when the file changes — with an
11 MB jar, a reload fired mid-write produced `ZipException: invalid LOC header` and no NPC loaded
at all.
:::

## Verifying it works

Join the game, craft an **Immigration Contract**, and use it.

If an NPC shows up with a name of its own and a diamond floating above its head (the *plumbob*,
which shows mood), the mod is live.

## Uninstalling

Remove the jar from `Mods`. NPC data stays in the Caskara database; reinstalling brings everyone
back.
