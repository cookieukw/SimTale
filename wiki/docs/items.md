---
sidebar_position: 6
title: Tools and items
---

# Tools and items

SimTale adds a set of craftable tools. They exist so the village can be understood from inside the
game, without typing a debug command.

## What they do

| Item | Point at | What happens |
|---|---|---|
| Pregnancy Test | a villager | Says whether she is expecting, and how far along |
| House Blueprint | a bed | Reports whether that room counts as a house, and outlines it |
| Innkeeper's Ledger | anything | Lists every registered bed and who sleeps in it |
| Quartermaster's Glass | anything | Lists every village chest and what is inside |
| Inspector's Journal | a villager | Her needs, mood, job and what she is doing right now |

Three more are crafted but do nothing yet: the Town Bell, the Immigration Contract and the Birthday
Cake.

## House Blueprint

Right-click a **registered bed** — the bed is what makes a room a house, so anywhere else the tool
has no way to know which room you mean.

You get the same verdict `/simtale housecheck` gives (valid, or the list of what is missing), a
summary of interior size, doors and chests, and the floor of the room lit up for about twelve
seconds: green if the house is valid, red if it is not. The outline is the floor only — filling the
whole interior would replace the room with a coloured brick and hide what you are looking at.

:::note It deliberately does not register anything
A tool for checking should not change what it checks. If the blueprint registered the house, you
would create residences by accident while inspecting them. Houses are still created by an NPC
claiming the bed.
:::

The one thing it does that the command cannot: it knows exactly which bed you mean. `housecheck`
guesses by taking the nearest bed within 16 blocks, which stops being good enough the moment two
houses share a wall.

## The three lenses

The Ledger, the Glass and the Journal are read-only views over data the mod already keeps. The
first two open the same screens as `/simtale debugbeds` and `/simtale debugchests`, with the
developer buttons removed.

:::warning Why Teleport is not on them
The bed and chest registries hold every entry in the world. A craftable item with a teleport button
next to each one is not a village tool, it is the fastest travel in the game. Same reasoning, less
dramatically, for Unclaim and Remove: these items are lenses, never levers.
:::

The Journal is not simply `/simtale npcstate` in an item. That command dumps role state, animation
slots, movement flags and search cooldowns — what you want when the AI is misbehaving, and noise
when you just want to know if someone is hungry. The Journal reports the five needs, mood, job,
where she lives, and what she is doing in plain words rather than the internal task name.
