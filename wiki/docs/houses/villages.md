---
sidebar_position: 3
title: Villages
---

# Villages

Build houses close together and they become a village. You do not place anything to make it happen,
and there is no marker to lose.

## How one forms

Two houses belong to the same village when their beds are within about **40 blocks** of each other,
and that chains: if A is near B and B is near C, all three are one village, even when A and C are
far apart.

So a village grows the way you build — outward from what is already there. A long street of houses
30 blocks apart is one village, however long the street gets.

The centre sits at the middle of the beds, and the village reaches from there to its furthest house
plus a little margin.

## It disappears if you tear it down

A house exists because of its bed. Break the bed and the house is gone, and the village recalculates
without it. Break every bed and there is no village left.

:::info Different from Minecraft on purpose
In Minecraft the village centre is a thing that stays put. Flatten every building and the game still
treats the ruins as a village. Here the village is worked out from the houses that exist at that
moment, so there is nothing left behind to be wrong.
:::

## What it changes

**NPCs without a house stop wandering off.** Previously a homeless NPC drifted — each stroll started
from wherever the last one ended, so it got further away indefinitely and you had to go and find it.
Now it strolls around the village instead.

The limit is loose on purpose: an NPC still leaves the village to work, to fish, or to fetch food.
It just will not wander away for no reason.

**Guards patrol the edge.** A guard with nothing to fight walks a circuit around the village
boundary, which is where trouble comes from. A guard with no village stays where it is.

## Seeing it

```
/simtale village
```

Lists every village with its centre, radius and number of houses, and tells you whether you are
standing inside one. Since nothing about a village is written down, this is the only way to check
what the mod currently thinks.

If two clusters you consider separate show up as one village, they are within the 40-block chain —
move the next house further out, or accept the merge.
