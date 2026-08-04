---
sidebar_position: 3
title: Jobs and hobbies
---

# Jobs and hobbies

## Jobs

| Job | What it does |
|---|---|
| `UNEMPLOYED` | Nothing in particular |
| `MINER` | Mining |
| `FARMER` | Harvesting, gathering and replanting |
| `FISHERMAN` | Fishing |
| `LUMBERJACK` | Gathering wood |
| `GUARD` | Night watch — sleeps by day |
| `EXPLORER` | Exploring |
| `BUILDER` | Building |
| `HUNTER` | Hunting animals |

### Assigning a job

Hold the matching tool and use **Assign Job** in the interaction panel.

| Job | Trigger item contains |
|---|---|
| Miner | `pickaxe` |
| Farmer | `hoe` |
| Fisherman | `fishing_trap` |
| Lumberjack | `hatchet` |
| Guard | `sword` |
| Explorer | `map` |
| Builder | `hammer` |
| Hunter | `bow` |

An NPC can refuse: each one rolls jobs she likes and jobs she dislikes.

:::caution Matching is by item name
The check looks for those words inside the item id. A sword named without "sword" in its id will
not be recognised. This is a known weak spot.
:::

### The farmer

Walks to ripe crops, harvests them, keeps the produce and 1–2 seeds, then carries everything to a
chest in her own house. If she has seeds and there is empty tilled soil nearby, she replants.

### The hunter

Finds the nearest animal, walks over, attacks until it drops, collects the raw meat and stores it
at home.

## Hobbies

| Hobby | Where she goes |
|---|---|
| `FISHING` | nearest water |
| `MINING` | nearest stone |
| `GARDENING` | nearest crops |
| `READING` | home |
| `SLEEPING` | home |

When fun drops below 40, she goes and does her hobby, and comes back happy.

If the scenery does not exist — a fisherman in the desert — she does not get stuck looking. She
gives up and relaxes at home, recovering fun more slowly.

### Hobbies matter socially

- **Gifts**: giving a fishing rod to someone whose hobby is fishing is worth far more than an
  ordinary gift.
- **Conversation**: two NPCs with the same hobby build friendship faster.
- **Work**: a farmer whose hobby is gardening *gains* fun from harvesting. One who would rather be
  reading loses a little.
