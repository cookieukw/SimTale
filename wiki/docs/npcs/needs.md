---
sidebar_position: 1
title: Needs
---

# Needs

Every NPC carries five needs, all starting at 100 and decaying over time. They are saved with the
NPC and restored when the server starts again, so hunger is cumulative across sessions.

| Need | What it drives |
|---|---|
| <img src="/img/need_hunger.svg" width="22" height="22" align="absmiddle" /> **Hunger** | Looking for food; at the bottom, it stops doing anything else. (The cake may be a lie, but it fills the bar) |
| <img src="/img/need_energy.svg" width="22" height="22" align="absmiddle" /> **Energy** | Going to bed |
| <img src="/img/need_social.svg" width="22" height="22" align="absmiddle" /> **Social** | Seeking out other NPCs to talk to |
| <img src="/img/need_fun.svg" width="22" height="22" align="absmiddle" /> **Fun** | Going off to do a hobby. (All work and no play makes the NPC a dull villager) |
| <img src="/img/need_hygiene.svg" width="22" height="22" align="absmiddle" /> **Hygiene** | Getting into water to bathe. (Removing the pool ladder won't actually trap them) |

Traits change the rates. A `LAZY` NPC burns energy twice as fast; a `FUNNY` one loses fun at half
speed.

## Hunger in detail

Hunger is the need with the sharpest consequences, so it has clear thresholds:

| Hunger | What happens |
|---|---|
| below 70 | looks for food **when idle** |
| below 25 | **drops whatever it is doing** to eat |
| below 5 | stops working, turns sad and cries |

The interruption at 25 exists because a busy NPC would otherwise starve next to a full pantry —
hunger used to be checked only while idle.

### Timeline

Starting from full hunger:

| Milestone | Hunger | Elapsed |
|---|---|---|
| Looks for food when idle | 70 | ~4.2 h |
| Interrupts its task | 25 | ~10.4 h |
| Stops working and cries | 5 | ~13.2 h |

### Hunger does not kill

An NPC that runs out of food does not die. It becomes miserable and useless: it drops its job, its
hobby and its social life, and stays that way until someone feeds it. Death is reserved for aging
and disease, which are not implemented yet.

It can still reach food on its own — being starving does not stop it from walking to a chest, and
normal hunger does not interrupt sleep. `/simtale forceeat` is the exception: it keeps the NPC on
the feeding task until she reaches the chest, even at night.

### Where the food comes from

An NPC first looks for a chest in her own house. If the house has no food (or she has no house),
she can use a **global chest**: a chest placed inside the village and outside every house. Those
belong to nobody and any resident may take food from them. Chests explicitly marked with
`/simtale chestshare` also work this way.

A chest outside the village, or a private chest inside someone else's house, is ignored.

### Adjusting the pace

`simtale-config.json` has `hungerDecayMultiplier` (default `1.0`). Raise it to make hunger arrive
sooner; `10.0` reaches the first threshold in roughly 25 minutes, while `0` freezes hunger. The
file is read at server boot, so restart after editing.

## What food restores

Food is graded by the game's own item data into three tiers. Raw meat, ingredients and harvested
crops are tier 1; anything cooked or assembled is tier 2 or 3.

| Tier | Hunger | Health |
|---|---|---|
| 1 (raw) | +25 | +6 |
| 2 | +45 | +14 |
| 3 (cooked) | +65 | +24 |

When choosing from a chest, tier wins over taste: a hated pie still beats a beloved slab of raw
beef.

## Feeding by hand

Give food to an NPC whose hunger is at 70 or below and she eats it on the spot instead of pocketing
it — restoring hunger and health, and earning you far more goodwill than an ordinary gift.

Hated food still feeds her. She eats it complaining, with a smaller gain and a hit to her mood.

## Seeing the numbers

Hunger and energy appear at the top of the interaction panel, colour-coded by severity. The colours
follow the same thresholds the routine uses, so the panel and the behaviour never disagree.
