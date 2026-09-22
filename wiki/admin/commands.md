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
| `/simtale village` | Lists the villages derived from the houses: centre, radius, house count, and whether you are inside |
| `/simtale villagestock` | Summarizes communal storage and resources in the nearest village (food, crops, wood, ores) |
| `/simtale chestshare` | Toggles the nearest chest between **Private** (home only) and **Shared** (communal village stock) |
| `/simtale graveyard` | Screen listing every NPC the Reaper collected, with a revive button each |
| `/simtale npcstate` | State of the nearest NPC: role, animation, movement, needs, search cooldowns and the world clock |
| `/simtale search` | Finds NPCs |
| `/simtale housecheck` | Validates the house you are aiming at, and reports what is missing |
| `/simtale chestcheck` | Describes the nearest chest |
| `/simtale camdebug` | Camera debugging |

:::tip Prefer `debugchests` over `chestcheck`
`chestcheck` only describes the nearest chest, so it cannot tell "nothing is registered" apart from
"nothing registered near where I am standing". That ambiguity cost real debugging time.
:::

:::note These screens no longer rescan
`debugbeds` and `debugchests` used to sweep a 32-block radius before opening — around 139 thousand
block reads, which is why they took seconds to appear. That sweep was covering for furniture not
being registered on placement; with the placement event fixed, the registries are already current.
Use `/simtale rescan` for worlds built before that fix.
:::

## Forcing routines

| Command | What it does |
|---|---|
| `/simtale forcesocial [topic]` | Forces the 2 nearest NPCs to face each other and chat turn-by-turn. Optional topics: `hostile`, `romantic`, `hunger`, `fatigue`, `farm`, `wood`, `guard`, `fish`, `happy`, `sad`, `night`, `weather`, `village` |
| `/simtale testflirt` | Tests the flirt reaction on nearest NPC (blush expression, blow kiss, heart particles) |
| `/simtale testshove` | Tests the angry shove reaction with knockback physics against player |
| `/simtale testgreet` | Tests proximity wave and greeting on nearest NPC |
| `/simtale forcesleep` | Forces the nearest NPC to sleep or wake |
| `/simtale forceeat` | Zeroes hunger and sends the NPC to a chest |
| `/simtale forcework` | Forces the nearest NPC to do its job |
| `/simtale forceplant` | Gives seeds to a farmer and sends her to plant |
| `/simtale unstick` | Frees stuck NPCs **and the player** |
| `/simtale toggleai` | Enables/disables the AI API (does not disable the NPC's routine AI) |

## Family

| Command | What it does |
|---|---|
| `/simtale marry` | Forces marriage with the nearest NPC |
| `/simtale forcepreg` | Forces pregnancy |
| `/simtale forcebirth` | Forces birth |
| `/simtale pregnancy` | Opens the gestation panel |
| `/simtale setstage <stage>` | Sets a child's growth stage: `BABY`, `TODDLER`, `CHILD`, `TEEN`, `ADULT` |
| `/simtale growbaby` | Skips the newborn wait on the **Baby item in your hand**, so it can be placed |

:::caution `setstage` only sees born children
It iterates children created through the pregnancy system. An adult NPC spawned by hand never
appears there, and neither does anything if birth failed.

It also only sees children that already exist **as entities**. A newborn is an item in your
inventory, not a body in the world, so `setstage` cannot reach it — that is what `growbaby` is for.
Take no argument: it advances the held baby to the first stage that can be put down. Once it is on
the ground, `setstage` takes over.
:::

## State & Expressions

| Command | What it does |
|---|---|
| `/simtale setmood <mood> [0-100]` | Sets mood. Intensity is an **integer percentage** |
| `/simtale setgender` | Sets gender |
| `/simtale interact` | Opens the interaction panel |
| `/simtale thought <type>` | Displays an emote thought bubble over the nearest NPC's head (e.g. `SMUG`, `LOVE`, `PARTY`, `JOY`, `SICK`) |

## Configuration & Proximity

| Command | What it does |
|---|---|
| `/simtale proximity` | Displays the current proximity interaction configuration |
| `/simtale proximity on\|off` | Enables or disables NPC proximity greetings |
| `/simtale proximity chat [on\|off]` | Toggles chat greeting messages (if off, NPCs only wave and smile without chat text) |
| `/simtale proximity cooldown <seconds>` | Sets individual NPC greeting cooldown (default: `60`s) |
| `/simtale proximity limit <seconds>` | Sets player anti-spam window to prevent multiple NPCs greeting at once (default: `15`s) |
| `/simtale proximity radius <blocks>` | Sets player detection radius in blocks (default: `4.5`) |

:::info Persistent Server Config
All proximity settings are saved to `simtale-config.json` on the server root and loaded automatically on boot.
:::

---

*Updated with the latest subcommands registered in `SimTaleCommand.java`.*
