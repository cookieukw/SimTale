---
sidebar_position: 1
title: What is SimTale
slug: /
---

# SimTale

SimTale is a social simulation mod for Hytale that implements autonomous NPCs with individual state tracking for needs, personalities, schedules, and relationships.

NPC behavior is driven by internal state and environmental queries rather than fixed scripts or dialogue trees.

The mod includes **800 distinct visual variants** of NPCs:
- 400 Adults (200 Male, 200 Female)
- 400 Children (200 Male, 200 Female)

During instantiation, each NPC is assigned randomized properties for personality, traits, hobbies, and item preferences.

![NPC Variants Placeholder](/path/to/variants_collage.png)

## What an NPC does on its own

- **Sleeps at night.** When night falls, it drops whatever it is doing and heads for its own bed. NPCs with the "Lazy" trait go to sleep earlier (when energy drops below 60). Guards run the opposite shift: awake at night, asleep during the day.
- **Eats when hungry.** Looks for food in the chests of the house it lives in (within a 24-block radius), and picks the best one: cooked food beats raw meat, and it avoids what it hates.
- **Lives in a house.** Claims a bed and treats that place as its own. 
- **Belongs to a village.** Houses built near each other form one, worked out from the buildings themselves. NPCs without a house stay near the village center instead of wandering off.
- **Works.** Farmers harvest crops (Carrot, Wheat, Tomato, Corn), replant seeds, and deposit the harvest. Hunters and Miners go on expeditions and return with loot.
- **Talks.** Seeks out other NPCs within 20 blocks after too long alone, and mood is contagious.
- **Has a hobby.** Someone who likes fishing walks to the water; a reader goes home.
- **Ages.** Marries, gets pregnant, has children, and those children grow from baby to adult.
- **Starves.** If hunger drops below 5, it cries, stops working entirely, and drops all tasks until someone feeds it. It does not die of hunger — death is reserved for aging and disease.
- **Dies.** When an NPC reaches the end of their life, they enter a dying state. The Grim Reaper spawns to conduct the ceremony and collect their soul.

![Grim Reaper Ceremony Placeholder](/path/to/reaper_ceremony.png)

## Where to start

1. [Installation](installation.md)
2. [Getting started](getting-started.md) — spawn your first NPC and give it a home
3. [Building a house](houses/building-a-house.md)

## The other tracks

This section is for **players**. If you run a server or want to work on the code:

- **[Server](/admin/intro)** — commands, generative AI, balancing and troubleshooting
- **[Developer](/dev/intro)** — architecture, systems, and how to extend the mod

:::note Documentation in progress
The mod is in testing and has no public release. Behaviour described here may change between
versions, and some parts have not been validated in game yet — where that is the case, the page
says so.
:::
