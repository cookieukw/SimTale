---
sidebar_position: 5
title: Family and growth
---

# Family and growth

## Pregnancy

A married NPC can become pregnant. Pregnancy runs through three trimesters, and movement speed drops
gradually as it advances.

| Trimester | Symptoms |
|---|---|
| 1st | Slightly increased hunger and energy drain |
| 2nd | Increased drain, moderate slowness |
| 3rd | Intense drain, severe slowness |

Open the **View Pregnancy** panel from the interaction screen to follow the progress: current day,
percentage, and estimated time remaining in real minutes.

Player pregnancy also exists and follows its own path.

## Birth

At the end of gestation the baby is born as an **item** that goes into the inventory. You carry the
baby around, and can hand it to the other parent.

:::warning Known bug
Birth is currently delivering a corrupted item where the baby should be. The pregnancy flow itself
works; the problem is in the item that gets created. Until it is fixed, the growth cycle cannot be
tested end to end.
:::

## Growth stages

| Stage | Notes |
|---|---|
| `BABY` | Carried in the inventory |
| `TODDLER` | |
| `CHILD` | |
| `TEEN` | |
| `ADULT` | Full routine: job, house, relationships |

Children grow over time on their own, with the model scaling up at each stage.

## Care

Babies need care. Passing the baby back and forth between parents shares the load, and there is an
offline simulation so time away from the server still counts.

## Death

NPCs die of starvation, and the SimTale death flow takes over: the body stays, the Grim Reaper
appears on its own, walks to it, performs the soul-collection ritual, leaves a gravestone and
removes the record cleanly.

:::note Hard to test on purpose
There is no command to kill an NPC, and waiting for starvation takes around fifteen in-game hours.
The reaper flow has therefore not been fully validated yet.
:::
