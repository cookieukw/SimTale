---
sidebar_position: 4
title: Balancing
---

# Balancing

Most values are **constants in the code**; changing them means editing and rebuilding. Hunger
pacing and the village deposit chance are exposed in `simtale-config.json`. They are documented here
so you know what the numbers mean before you touch them.

## Runtime configuration

| Key | Default | Effect |
|---|---|---|
| `hungerDecayMultiplier` | `1.0` | Multiplies the `0.0001` hunger decay. `10.0` reaches the first search in ~25 min; `0` freezes hunger |
| `villageChestDepositChance` | `0.30` | Chance a worker chooses a village/global chest even when her home chest has room |

The JSON file is read at boot; restart after editing.

## Need decay

Per tick, from `NeedsHelper.tickDecay` (Adults):

| Need | Rate | With trait |
|---|---|---|
| Hunger | 0.0001 × `hungerDecayMultiplier` | — |
| Energy | 0.0002 | 0.0004 if `LAZY` |
| Social | 0.00015 | — |
| Fun | 0.0001 | 0.00005 if `FUNNY` |
| Hygiene | 0.0002 | — |

Per tick, from `BabyNeeds.tickDecay` (Babies/Children):

| Need | Rate | Condition |
|---|---|---|
| Hunger | 0.0003 | — |
| Affection | 0.0002 | — |
| Health | 0.0001 | 0.0004 if Hunger < 20 |
| Negative Experiences | +0.001 | if Hunger, Affection, or Health < 30 |

At 20 ticks per second and the default multiplier, adult hunger takes roughly 13.9 in-game hours to
fall from 100 to 0. The first food search happens at 70 (~4h10) and the task interrupt at 25
(~10h25).

## Hunger thresholds

| Constant | Value | Effect |
|---|---|---|
| `HUNGER_SEEK_FOOD_THRESHOLD` | 70 | Looks for food when idle; also the gift-eats-on-the-spot threshold |
| `HUNGER_INTERRUPT_THRESHOLD` | 25 | Drops its current task to eat |
| `HUNGER_STARVATION_THRESHOLD` | 5 | Stops working and cries |

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
| `villageChestDepositChance` | 0.30 | Chance a worker uses a village/global chest instead of home even when home has room |
| Village link distance | 64 blocks | Houses this close form one village |
| Village radius margin | 24 blocks | Added to the furthest house when computing the radius/global-chest area |
| `FOOD_SEARCH_COOLDOWN_TICKS` | 100 | Stops a search storm when no food is reachable |
| `MOVE_TIMEOUT_TICKS` | 600 (30 s) | Gives up on an unreachable chest |
| Furniture scan radius on join | 32 blocks | Picks up pre-existing beds and chests |
| `SOCIAL_SEARCH_RADIUS` | 20 blocks | Max distance to search for a talking partner |
| `SOCIAL_COOLDOWN_TICKS` | 800–1200 (40–60 s) | Mandatory cooldown between talks for both NPCs to prevent non-stop chatting |
| `SOCIAL_SEARCH_INTERVAL_TICKS` | 300 (15 s) | Throttle for checking nearby conversationalists when idle |
| `SOCIAL_THRESHOLD` | `< 45` | Need threshold to trigger conversations (with 4% rare spontaneous chance) |
| `WANDER_RADIUS_VILLAGE` | up to 48 blocks | Village stroll radius from village center, letting villagers walk around streets and squares |
| `WANDER_RADIUS_HOME` | 20 blocks | Stroll radius around bed for isolated NPCs (expanded from 8 blocks) |
| `SEPARATION_RADIUS` | 1.15 blocks | Personal space avoidance radius to prevent NPCs from clumping on top of each other |

:::warning Do not remove the cooldowns
Without them, an NPC with no reachable bed or food re-enters the search **every tick**. One real
session logged 3447 rejections of the same bed in a few seconds, with the NPC standing still the
whole time.
:::
