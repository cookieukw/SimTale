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
| Miner | `Pickaxe` |
| Farmer | `Hoe` |
| Fisherman | `Tool_Fishing_Trap` |
| Lumberjack | `Hatchet` |
| Guard | any recognised weapon — melee (sword, axe, dagger, spear, mace, ...) or ranged (bow, crossbow, firearm, ...) |
| Explorer | `Tool_Map` |
| Builder | `Tool_Hammer` |
| Hunter | `Shortbow` or `Crossbow` specifically |

An NPC can refuse: each one rolls jobs she likes and jobs she dislikes.

### Guards and weapon categories

A Guard is no longer tied to swords specifically. Handing over *any* item SimTale recognises as
a weapon assigns the Guard job, and the Guard remembers whether that weapon was melee or ranged:

- **Melee** (sword, axe, dagger, spear, mace, ...): the Guard closes to about 2.5 blocks before
  fighting, same as always.
- **Ranged** (bow, crossbow, firearm, ...): the Guard stops at range (about 7 blocks) and does not
  walk into melee distance.

A bow or crossbow still makes a **Hunter**, not a Guard — that check runs first, so nothing about
Hunter changed.

Because new weapons (from other mods, or future SimTale updates) are not something SimTale can
predict by name, there are two ways to teach it about one, in order of priority: another mod
calling `WeaponCategoryRegistry.register("itemId", WeaponCategory.RANGED)` directly from its own
code, or a `simtale-weapons.json` file in the server's working directory (next to `simtale-ai.json`)
listing `{"itemId": "MELEE"}`/`{"itemId": "RANGED"}` entries by hand. Neither is required for the
weapons already built into Hytale.

### Child labor restrictions

Only NPCs at the `TEEN` or `ADULT` life stages can be assigned jobs. Young children (`BABY`, `TODDLER`, `CHILD`) are protected and will refuse to take tools or work ("I'm just a kid!"). However, children can freely engage in leisure hobbies like fishing or gardening when their fun need drops, allowing them to hang out near family crop plots or water spots without taking on economic tasks.

:::caution Matching is by item name
The check looks for those words inside the item id, so a modded item without one of the words
above in its id still needs `WeaponCategoryRegistry.register(...)` or `simtale-weapons.json` (see
above) — or, for the non-Guard jobs, simply won't be recognised.
:::

### The Farmer

<div style={{textAlign: 'center'}}>
  <img src="/img/farmer_job.png" alt="The Farmer" />
</div>

The Farmer requires a **Scarecrow** to act as their workstation. They will walk to ripe crops, harvest them (dropping 1 produce and 1-2 seeds), and then carry everything to a chest in their own house. If they have seeds and there is empty tilled soil nearby, they will replant. 
Supported crops are: Carrot, Wheat, Tomato, and Corn.

### Expeditions: Miners and Hunters

Miners and Hunters do not actively walk up to rocks or animals. Instead, they go on "Expeditions".
When their work shift begins, their model scales down (shrinks) and they conceptually disappear for about 2 real minutes. When they return, they spawn back at the Village Center or their house, carrying loot based on a weighted table.
- **Miners** return with ores (Copper, Iron, Silver, Gold, Adamantite, etc.).
- **Hunters** return with Raw Meats (Beef, Pork, Chicken).
They immediately walk to their home chest to deposit the loot.

### The Fisherman

<div style={{textAlign: 'center'}}>
  <img src="/img/fisherman_job.png" alt="The Fisherman" />
</div>

The Fisherman requires a **Fishing Post**. You create one simply by placing a **Fishing Trap** block near water (within 8 blocks).
They will walk to this post, perform their gathering animation, and then deposit their catch into their home chest.

### The Lumberjack

<div style={{textAlign: 'center'}}>
  <img src="/img/lumberjack_job.png" alt="The Lumberjack" />
</div>

The Lumberjack requires a **Lumber Post**. You create one simply by placing a **Lumbermill Bench** block near a tree trunk (within 10 blocks).
They will walk to this post, perform their gathering animation, and then deposit the gathered wood into their home chest.

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
