---
sidebar_position: 6
title: Tools and items
---

# Tools and items

SimTale adds a set of craftable tools. They exist so the village can be understood from inside the game, without typing a debug command.

## What they do

| Item | Point at | What happens |
|---|---|---|
| <img src="/img/PregnancyTest.png" width="32" align="absmiddle"/> Pregnancy Test | a villager | Says whether she is expecting, and how far along |
| <img src="/img/HouseBlueprint.png" width="32" align="absmiddle"/> House Blueprint | a bed | Reports whether that room counts as a house, and outlines it |
| <img src="/img/InnkeepersLedger.png" width="32" align="absmiddle"/> Innkeeper's Ledger | anything | Lists every registered bed and who sleeps in it. (The sacred texts!) |
| <img src="/img/QuartermastersGlass.png" width="32" align="absmiddle"/> Quartermaster's Glass | anything | Lists every village chest and what is inside. (Enhance... Enhance... Enhance) |
| <img src="/img/InspectorsJournal.png" width="32" align="absmiddle"/> Inspector's Journal | a villager | Her needs, mood, job and what she is doing right now |
| <img src="/img/ImmigrationContract.png" width="32" align="absmiddle"/> Immigration Contract | anything | Invites a new resident to settle in the village |
| <img src="/img/TownBell.png" width="32" align="absmiddle"/> Town Bell | anything | Rings the town bell, alerting nearby villagers |
| <img src="/img/WeddingRing.png" width="32" align="absmiddle"/> Wedding Ring | a villager | Proposes marriage (requires 80 Romance, 70 Friendship) |
| <img src="/img/Baby.png" width="32" align="absmiddle"/> Baby | nothing | A carried infant that will eventually spawn as a child NPC |
| <img src="/img/BirthdayCake.png" width="32" align="absmiddle"/> Birthday Cake | anything | Crafted but does nothing yet |

## Wedding Ring

<div align="center">
  <img src="/img/WeddingRing.png" width="128" style={{imageRendering: 'pixelated'}} />
</div>

The Wedding Ring is used to propose marriage to a villager. To successfully propose, you need to have a high relationship with the villager (at least 80 Romance and 70 Friendship). If they accept, they become your spouse! If they reject you, keep trying to improve your relationship before trying again.

## Baby

<div align="center">
  <img src="/img/Baby.png" width="128" style={{imageRendering: 'pixelated'}} />
</div>

The Baby is a unique item that represents a newborn child. It cannot be crafted. When a villager gives birth, a Baby is generated. After a certain amount of time passes, the Baby item will naturally "grow up" and transform into a new child NPC in the world! 

## Birthday Cake

<div align="center">
  <img src="/img/BirthdayCake.png" width="128" style={{imageRendering: 'pixelated'}} />
</div>

A festive cake that can be crafted at the Workbench. Currently, the Birthday Cake is just a decorative item and doesn't have a special function yet, but who knows what the future holds for village celebrations!

## Crafting Recipes

All items can be crafted at their respective workstations:

| Item | Bench | Ingredients |
|---|---|---|
| **Pregnancy Test** | <img src="/img/Bench_Alchemy.png" width="24" align="absmiddle"/> Alchemybench | 1x <img src="/img/Plant_Flower_Common_White.png" width="24" align="absmiddle"/> White Flower, 1x <img src="/img/Wood_Softwood_Planks.png" width="24" align="absmiddle"/> Softwood Planks, 1x <img src="/img/Ingredient_Life_Essence_Cauliflower.png" width="24" align="absmiddle"/> Cauliflower |
| **House Blueprint** | Fieldcraft | 1x <img src="/img/Deco_Map.png" width="24" align="absmiddle"/> Map, 1x <img src="/img/Deco_Inkwell.png" width="24" align="absmiddle"/> Inkwell, 1x <img src="/img/Deco_Scroll.png" width="24" align="absmiddle"/> Scroll |
| **Innkeeper's Ledger** | Fieldcraft | 1x <img src="/img/Deco_Scrap_Book_Pile_Small.png" width="24" align="absmiddle"/> Small Book Pile, 1x <img src="/img/Wood_Softwood_Planks.png" width="24" align="absmiddle"/> Softwood Planks, 1x <img src="/img/Ingredient_Leather_Light.png" width="24" align="absmiddle"/> Light Leather |
| **Quartermaster's Glass** | <img src="/img/Bench_WorkBench.png" width="24" align="absmiddle"/> Workbench | 1x <img src="/img/Rock_Crystal_White.png" width="24" align="absmiddle"/> White Crystal, 1x <img src="/img/Ingredient_Copper_Bar.png" width="24" align="absmiddle"/> Copper Bar |
| **Inspector's Journal** | Fieldcraft | 1x <img src="/img/Deco_Scrap_Book_Pile_Small.png" width="24" align="absmiddle"/> Small Book Pile, 1x <img src="/img/Deco_Inkwell.png" width="24" align="absmiddle"/> Inkwell |
| **Immigration Contract** | Fieldcraft | 1x <img src="/img/Deco_Scroll.png" width="24" align="absmiddle"/> Scroll, 1x <img src="/img/Deco_Inkwell.png" width="24" align="absmiddle"/> Inkwell, 1x <img src="/img/Ingredient_Leather_Light.png" width="24" align="absmiddle"/> Light Leather |
| **Town Bell** | <img src="/img/Bench_WorkBench.png" width="24" align="absmiddle"/> Workbench | 3x Gold Bar, 2x <img src="/img/Wood_Softwood_Planks.png" width="24" align="absmiddle"/> Softwood Planks |
| **Birthday Cake** | Workbench | 1x Apple Pie, 1x Orange Light Source |

### Item Icons

![House Blueprint](/img/HouseBlueprint.png) ![Innkeeper Ledger](/img/InnkeepersLedger.png) ![Quartermaster Glass](/img/QuartermastersGlass.png) ![Town Bell](/img/TownBell.png)

## House Blueprint

<div align="center">
  <img src="/img/HouseBlueprint.png" width="128" style={{imageRendering: 'pixelated'}} />
</div>

Right-click a **registered bed** — the bed is what makes a room a house, so anywhere else the tool has no way to know which room you mean.

You get a verdict (valid, or the list of what is missing), a summary of interior size, doors and chests, and the floor of the room lit up for about twelve seconds: green if the house is valid, red if it is not. The outline is the floor only — filling the whole interior would replace the room with a coloured brick and hide what you are looking at.

:::note It deliberately does not register anything
A tool for checking should not change what it checks. If the blueprint registered the house, you would create residences by accident while inspecting them. Houses are still created by an NPC claiming the bed.
:::

The one thing it does perfectly: it knows exactly which bed you mean. Checking proximity by yourself stops being good enough the moment two houses share a wall.

## The three lenses

<div align="center">
  <img src="/img/InnkeepersLedger.png" width="128" style={{imageRendering: 'pixelated', margin: '0 10px'}} />
  <img src="/img/QuartermastersGlass.png" width="128" style={{imageRendering: 'pixelated', margin: '0 10px'}} />
  <img src="/img/InspectorsJournal.png" width="128" style={{imageRendering: 'pixelated', margin: '0 10px'}} />
</div>

The Ledger, the Glass and the Journal are read-only views over data the mod already keeps. The first two open the bed and chest overview screens.

:::warning Why Teleport is not on them
The bed and chest registries hold every entry in the world. A craftable item with a teleport button next to each one is not a village tool, it is the fastest travel in the game. Same reasoning, less dramatically, for Unclaim and Remove: these items are lenses, never levers.
:::

The Journal does not simply dump internal debug state. A raw dump would show role state, animation slots, movement flags and search cooldowns — what you want when the AI is misbehaving, and noise when you just want to know if someone is hungry. The Journal reports the five needs, mood, job, where she lives, and what she is doing in plain words rather than the internal task name.
