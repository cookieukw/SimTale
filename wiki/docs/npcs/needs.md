---
sidebar_position: 1
title: Needs
---

# Needs

Every NPC carries five needs, all starting at 100 and decaying over time.

| Need | What it drives |
|---|---|
| **Hunger** | Looking for food; at the bottom, starvation and death |
| **Energy** | Going to bed |
| **Social** | Seeking out other NPCs to talk to |
| **Fun** | Going off to do a hobby |
| **Hygiene** | Getting into water to bathe |

Traits change the rates. A `LAZY` NPC burns energy twice as fast; a `FUNNY` one loses fun at half
speed.

## Hunger in detail

Hunger is the need with the sharpest consequences, so it has clear thresholds:

| Hunger | What happens |
|---|---|
| below 50 | looks for food **when idle** |
| below 25 | **drops whatever it is doing** to eat |
| below 5 | starts losing health |

The interruption at 25 exists because a busy NPC would otherwise starve next to a full pantry —
hunger used to be checked only while idle.

### Timeline

Starting from full hunger and 200 health:

| Milestone | Hunger | Elapsed |
|---|---|---|
| Looks for food when idle | 50 | ~6.9 h |
| Interrupts its task | 25 | ~10.4 h |
| Starts losing health | 5 | ~13.2 h |
| Dies | — | ~15.2 h |

### Death comes from health, not from the bar

Reaching 0 hunger does not kill. What kills is the health that starvation drains — about two hours
from full health to death. Feeding an NPC calls the countdown off entirely.

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

Give food to an NPC whose hunger is at 50 or below and she eats it on the spot instead of pocketing
it — restoring hunger and health, and earning you far more goodwill than an ordinary gift.

Hated food still feeds her. She eats it complaining, with a smaller gain and a hit to her mood.

## Seeing the numbers

Hunger and energy appear at the top of the interaction panel, colour-coded by severity. The colours
follow the same thresholds the routine uses, so the panel and the behaviour never disagree.
