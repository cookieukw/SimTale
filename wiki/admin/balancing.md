---
sidebar_position: 4
title: Balancing
---

# Balancing

These values are **constants in the code**, not configuration. Changing them means editing and
rebuilding. They are documented here so you know what the numbers mean before you touch them.

## Need decay

Per tick, from `Needs.tickDecay`:

| Need | Rate | With trait |
|---|---|---|
| Hunger | 0.0001 | — |
| Energy | 0.0002 | 0.0004 if `LAZY` |
| Social | 0.00015 | — |
| Fun | 0.0001 | 0.00005 if `FUNNY` |
| Hygiene | 0.0002 | — |

At 20 ticks per second, hunger takes roughly 13.9 in-game hours to fall from 100 to 0.

## Hunger thresholds

| Constant | Value | Effect |
|---|---|---|
| Idle search | 50 | Looks for food when idle |
| `HUNGER_INTERRUPT_THRESHOLD` | 25 | Drops its current task to eat |
| `STARVATION_THRESHOLD` | 5 | Starts losing health |

## Starvation

| Constant | Value | Meaning |
|---|---|---|
| `STARVATION_DAMAGE` | 4 | Health lost per hit |
| `STARVATION_INTERVAL_TICKS` | 2880 | One hit every 2.4 minutes |
| `LETHAL_STARVATION_DAMAGE` | 200 | Total damage that kills |

200 damage at 4 per hit is 50 hits, spread over 144000 ticks — **two hours** from full health to
death, matching the `MaxHealth: 200` the NPC roles declare.

Damage is staggered by entity id so a starving village does not take damage in lockstep.

:::info Why a counter and not real health
RuneCore exposes `addHealth` and `subtractHealth` but no reliable health getter, so death is driven
by accumulated starvation damage stored in `Needs.starvationDamage`. Real damage is still applied,
so the health bar reflects it. The trade-off: an NPC already wounded by something else does not
starve to death any sooner.
:::

## Food values

| Tier | Hunger | Health |
|---|---|---|
| 1 | +25 | +6 |
| 2 | +45 | +14 |
| 3 | +65 | +24 |

Tiers come from the item's own `Root_Secondary_Consume_Food_T1..T3` interaction, not from its name.

## Sleep

| Constant | Value |
|---|---|
| Night window | day progress `< 0.25` or `> 0.75` |
| Tired threshold | 30 energy, or 60 if `LAZY` |
| Energy recovered while asleep | 0.045 per tick |

Guards invert the window: they sleep during the day.

## Search radii and cooldowns

| Constant | Value | Why it exists |
|---|---|---|
| `CHEST_SEARCH_RADIUS` | 24 blocks | How far an NPC walks for a meal |
| `FOOD_SEARCH_COOLDOWN_TICKS` | 100 | Stops a search storm when no food is reachable |
| `MOVE_TIMEOUT_TICKS` | 600 (30 s) | Gives up on an unreachable chest |
| Furniture scan radius on join | 32 blocks | Picks up pre-existing beds and chests |

:::warning Do not remove the cooldowns
Without them, an NPC with no reachable bed or food re-enters the search **every tick**. One real
session logged 3447 rejections of the same bed in a few seconds, with the NPC standing still the
whole time.
:::
