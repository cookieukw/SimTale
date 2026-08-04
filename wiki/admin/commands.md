---
sidebar_position: 2
title: Commands
---

# Commands

All commands are subcommands of `/simtale`.

:::info Flag syntax
Hytale commands take **named flags** (`--name=value`), not positional arguments, and the parser
rejects decimal points.
:::

## Spawning and cleanup

| Command | What it does |
|---|---|
| `/simtale spawn <type>` | Spawns an NPC. Types: `HUMAN_MALE`, `HUMAN_FEMALE`, `CHILD_MALE`, `CHILD_FEMALE` |
| `/simtale forcespawn` | Spawns through the automatic path — the one that normally runs on its own |
| `/simtale clearall` | Removes every SimTale NPC |
| `/simtale tpall` | Teleports every NPC to you |
| `/simtale forget` | Releases entities adopted by mistake (cows, mobs) and deletes their records |

## Inspection

| Command | What it does |
|---|---|
| `/simtale debug` | Turns debug logging on/off (`on` / `off`) |
| `/simtale debugbeds` | Screen listing registered beds, owners, teleport and unclaim |
| `/simtale debugchests` | Screen listing registered chests, their house and their contents |
| `/simtale debugnear` | Dumps nearby blocks and entities |
| `/simtale npcstate` | State of the nearest NPC |
| `/simtale search` | Finds NPCs |
| `/simtale housecheck` | Validates the house you are aiming at, and reports what is missing |
| `/simtale chestcheck` | Describes the nearest chest |
| `/simtale camdebug` | Camera debugging |

:::tip Prefer `debugchests` over `chestcheck`
`chestcheck` only describes the nearest chest, so it cannot tell "nothing is registered" apart from
"nothing registered near where I am standing". That ambiguity cost real debugging time.
:::

## Forcing routines

| Command | What it does |
|---|---|
| `/simtale forcesleep` | Forces the nearest NPC to sleep or wake |
| `/simtale forceeat` | Zeroes hunger and sends the NPC to a chest |
| `/simtale forcework` | Forces the nearest NPC to do its job |
| `/simtale forceplant` | Gives seeds to a farmer and sends her to plant |
| `/simtale unstick` | Frees stuck NPCs **and the player** |
| `/simtale toggleai` | Enables/disables the routine AI |

## Family

| Command | What it does |
|---|---|
| `/simtale marry` | Forces marriage with the nearest NPC |
| `/simtale forcepreg` | Forces pregnancy |
| `/simtale forcebirth` | Forces birth |
| `/simtale pregnancy` | Opens the gestation panel |
| `/simtale setstage <stage>` | Sets a child's growth stage: `BABY`, `TODDLER`, `CHILD`, `TEEN`, `ADULT` |

:::caution `setstage` only sees born children
It iterates children created through the pregnancy system. An adult NPC spawned by hand never
appears there, and neither does anything if birth failed.
:::

## State

| Command | What it does |
|---|---|
| `/simtale setmood <mood> <intensity>` | Sets mood |
| `/simtale setgender` | Sets gender |
| `/simtale interact` | Opens the interaction panel |

## Known issues

| Command | Issue |
|---|---|
| `setmood` | Currently errors out |
| `debugnear` | Double-counts furniture — one door reads as two |
| `unstick` | A sleeping NPC walks off still asleep |
| `forcework` | Runs, but gives no feedback about what it found |

---

*Verified against the 28 subcommands registered in `SimTaleCommand.java`.*
