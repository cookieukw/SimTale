---
sidebar_position: 3
title: Jobs and hobbies
---

# Jobs and hobbies

## Jobs

| Job | What it does |
|---|---|
| `UNEMPLOYED` | Nothing in particular |
| `MINER` | Goes on mining expeditions and returns with ores |
| `FARMER` | Claims a Scarecrow, harvests, and replants specific crops |
| `FISHERMAN` | Claims a Fishing Post and gathers fish |
| `LUMBERJACK` | Claims a Lumber Post and gathers wood. (He is a lumberjack, but very calm. No rage please) |
| `GUARD` | Night watch — sleeps by day |
| `EXPLORER` | Exploring |
| `BUILDER` | Walks to construction sites |
| `HUNTER` | Goes on hunting expeditions and returns with raw meat |

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
The check looks for those words inside the item id. A sword named without "sword" in its id will not be recognised. This is a known weak spot.
:::

### The Farmer

The Farmer requires a **Scarecrow** to act as their workstation. They will walk to ripe crops, harvest them (dropping 1 produce and 1-2 seeds), and then carry everything to a chest in their own house. If they have seeds and there is empty tilled soil nearby, they will replant. 
Supported crops are: Carrot, Wheat, Tomato, and Corn.

### Expeditions: Miners and Hunters

Miners and Hunters do not actively walk up to rocks or animals. Instead, they go on "Expeditions".
When their work shift begins, their model scales down (shrinks) and they conceptually disappear for about 2 real minutes. When they return, they spawn back at the Village Center or their house, carrying loot based on a weighted table.
- **Miners** return with ores (Copper, Iron, Silver, Gold, Adamantite, etc.).
- **Hunters** return with Raw Meats (Beef, Pork, Chicken).
They immediately walk to their home chest to deposit the loot.

### Fisherman and Lumberjack

Both professions require specific workstations to function:
- Fisherman requires a **Fishing Post**.
- Lumberjack requires a **Lumber Post**.
They will walk to these posts, perform their gathering animation, and then deposit their respective resources (Fish or Wood) into their home chest.

## Hobbies

| Hobby | Where she goes |
|---|---|
| `FISHING` | nearest water |
| `MINING` | nearest stone |
| `GARDENING` | nearest crops |
| `READING` | home |
| `SLEEPING` | home |

When fun drops below 40, she goes and does her hobby, and comes back happy.

If the scenery does not exist — a fisherman in the desert — she does not get stuck looking. She gives up and relaxes at home, recovering fun more slowly.

### Hobbies matter socially

- **Gifts**: giving a fishing rod to someone whose hobby is fishing is worth far more than an ordinary gift.
- **Conversation**: two NPCs with the same hobby build friendship faster.
- **Work**: a farmer whose hobby is gardening *gains* fun from harvesting. One who would rather be reading loses a little.
