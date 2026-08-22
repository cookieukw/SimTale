---
sidebar_position: 4
title: Balancing
---

# Balancing

These values are **constants in the code**, not configuration. Changing them means editing and
rebuilding. They are documented here so you know what the numbers mean before you touch them.

## Need decay

Per tick, from `NeedsHelper.tickDecay` (Adultos):

| Need | Rate | With trait |
|---|---|---|
| Hunger | 0.0001 | — |
| Energy | 0.0002 | 0.0004 if `LAZY` |
| Social | 0.00015 | — |
| Fun | 0.0001 | 0.00005 if `FUNNY` |
| Hygiene | 0.0002 | — |

Per tick, from `BabyNeeds.tickDecay` (Bebês/Crianças):

| Need | Rate | Condição |
|---|---|---|
| Hunger | 0.0003 | — |
| Affection | 0.0002 | — |
| Health | 0.0001 | 0.0004 se Hunger < 20 |

At 20 ticks per second, adult hunger takes roughly 13.9 in-game hours to fall from 100 to 0.

## Hunger thresholds

| Constant | Value | Effect |
|---|---|---|
| Idle search | 70 | Looks for food when idle |
| `HUNGRY_ENOUGH_TO_EAT` | 70 | Eats a gift on the spot instead of pocketing it |
| `HUNGER_INTERRUPT_THRESHOLD` | 25 | Drops its current task to eat |
| `STARVATION_THRESHOLD` | 5 | Stops working and cries |

## Starvation

Starvation costs an NPC its usefulness, not its life. Below `STARVATION_THRESHOLD` it abandons its
job, its hobby and its social life, turns `SAD` and plays the crying animation. It stays that way
indefinitely until someone feeds it.

It still walks to a chest and eats — the "stop everything" rule excludes the tasks that lead to
food, and excludes sleep.

:::info Hunger does not kill
Aging and disease will own death. A second cause competing with them would make both harder to
reason about, so hunger was deliberately taken out of that role.

Earlier versions of this page described a `starvationDamage` counter that killed at 200 damage.
That system was removed along with `Needs.java`; the constants it named no longer exist.
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
