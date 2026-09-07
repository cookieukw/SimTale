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
 
| Comando | O que faz |
|---|---|
| `/simtale forcesocial [topico]` | Força os 2 NPCs mais próximos a ficarem frente a frente e conversarem em turnos. Tópicos opcionais: `hostile`, `romantic`, `hunger`, `fatigue`, `farm`, `wood`, `guard`, `fish`, `happy`, `sad`, `night`, `weather`, `village` |
| `/simtale testflirt` | Testa a reação de flerte no NPC mais próximo (expressão alegre/corada, beijo e partículas de corações) |
| `/simtale testshove` | Testa o empurrão violento de raiva com física real de repulsão (*knockback*) contra o jogador |
| `/simtale testgreet` | Testa o aceno de proximidade e cumprimento do NPC mais próximo |
| `/simtale forcesleep` | Força o NPC mais próximo a dormir ou acordar |
| `/simtale forceeat` | Zera a fome e envia o NPC até um baú |
| `/simtale forcework` | Força o NPC mais próximo a trabalhar |
| `/simtale forceplant` | Dá sementes a um fazendeiro e envia para plantar |
| `/simtale unstick` | Libera NPCs travados **e o jogador** |
| `/simtale toggleai` | Ativa/desativa a API de IA (não desativa a IA de rotina do NPC) |

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

## State

| Command | What it does |
|---|---|
| `/simtale setmood <mood> [0-100]` | Sets mood. Intensity is an **integer percentage** |
| `/simtale setgender` | Sets gender |
| `/simtale interact` | Opens the interaction panel |

---

*Verificado contra os 32 subcomandos registrados em `SimTaleCommand.java`.*
