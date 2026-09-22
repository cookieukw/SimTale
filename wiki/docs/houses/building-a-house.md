---
sidebar_position: 1
title: Building a house
---

# Building a house

A house is not just any build. The mod validates the structure before accepting it, and an NPC only
moves into an approved one.

## The requirements

### Structure

The space must be **enclosed**: walls and a roof with no gap for the check to leak through. Doors
count as closed wall.

The interior is capped at **512 blocks**. Past that the check gives up and rejects the house — the
cap exists so an open cave is not mistaken for a mansion. (Unless you are trying to build the Mines of Moria, 512 blocks is usually more than enough).

### Mandatory furniture

| Requirement | Any block whose id contains | Examples |
|---|---|---|
| **Light source** | `torch`, `lantern`, `candle`, `campfire`, `glow`, `lamp`, `chandelier` | <img src="/img/furniture/lights/Wood_Torch_Wall.png" width="22" align="absmiddle" title="Torch" /> <img src="/img/furniture/lights/Furniture_Village_Lantern.png" width="22" align="absmiddle" title="Lantern" /> <img src="/img/furniture/lights/Furniture_Tavern_Chandelier.png" width="22" align="absmiddle" title="Chandelier" /> |
| **Seating** | `chair`, `stool`, `bench`, `seat`, `sofa`, `couch` | <img src="/img/furniture/seats/Furniture_Village_Chair.png" width="22" align="absmiddle" title="Village Chair" /> <img src="/img/furniture/seats/Furniture_Tavern_Chair.png" width="22" align="absmiddle" title="Tavern Chair" /> <img src="/img/furniture/seats/Furniture_Grand_Wizard_Bench.png" width="22" align="absmiddle" title="Bench" /> |
| **Surface** | `table`, `workbench`, `desk`, `counter` | <img src="/img/furniture/tables/Furniture_Village_Table.png" width="22" align="absmiddle" title="Village Table" /> <img src="/img/furniture/tables/Furniture_Tavern_Table.png" width="22" align="absmiddle" title="Tavern Table" /> <img src="/img/furniture/tables/Furniture_Village_Counter.png" width="22" align="absmiddle" title="Counter" /> |

<details>
  <summary>View all light sources (44)</summary>
  <div className="furniture-gallery">
    <img src="/img/furniture/lights/Bench_Campfire.png" title="Bench Campfire" alt="Bench Campfire" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Campfire_New1.png" title="Campfire New1" alt="Campfire New1" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Campfire_New2.png" title="Campfire New2" alt="Campfire New2" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Campfire_New_Cartoon.png" title="Campfire New Cartoon" alt="Campfire New Cartoon" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Deco_Campfire.png" title="Deco Campfire" alt="Deco Campfire" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Deco_Campfire_Off.png" title="Deco Campfire Off" alt="Deco Campfire Off" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Deco_Lantern.png" title="Deco Lantern" alt="Deco Lantern" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Ancient_Candle.png" title="Ancient Candle" alt="Ancient Candle" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Ancient_Torch.png" title="Ancient Torch" alt="Ancient Torch" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Crude_Candle.png" title="Crude Candle" alt="Crude Candle" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Crude_Torch.png" title="Crude Torch" alt="Crude Torch" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Desert_Lantern.png" title="Desert Lantern" alt="Desert Lantern" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Desert_Lantern_Tall.png" title="Desert Lantern Tall" alt="Desert Lantern Tall" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Desert_Torch.png" title="Desert Torch" alt="Desert Torch" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Feran_Candle.png" title="Feran Candle" alt="Feran Candle" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Feran_Chandelier.png" title="Feran Chandelier" alt="Feran Chandelier" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Feran_Torch.png" title="Feran Torch" alt="Feran Torch" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Feran_Torch_Tall.png" title="Feran Torch Tall" alt="Feran Torch Tall" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Frozen_Castle_Lamp.png" title="Frozen Castle Lamp" alt="Frozen Castle Lamp" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Frozen_Castle_Secondary_Lamp.png" title="Frozen Castle Secondary Lamp" alt="Frozen Castle Secondary Lamp" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Human_Ruins_Candle.png" title="Human Ruins Candle" alt="Human Ruins Candle" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Human_Ruins_Lantern.png" title="Human Ruins Lantern" alt="Human Ruins Lantern" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Human_Ruins_Torch.png" title="Human Ruins Torch" alt="Human Ruins Torch" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Jungle_Candle.png" title="Jungle Candle" alt="Jungle Candle" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Jungle_Torch.png" title="Jungle Torch" alt="Jungle Torch" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Kweebec_Candle.png" title="Kweebec Candle" alt="Kweebec Candle" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Kweebec_Lantern.png" title="Kweebec Lantern" alt="Kweebec Lantern" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Lumberjack_Lamp.png" title="Lumberjack Lamp" alt="Lumberjack Lamp" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Lumberjack_Lantern.png" title="Lumberjack Lantern" alt="Lumberjack Lantern" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Scarak_Hive_Lamp.png" title="Scarak Hive Lamp" alt="Scarak Hive Lamp" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Scarak_Hive_Lantern.png" title="Scarak Hive Lantern" alt="Scarak Hive Lantern" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Tavern_Candle.png" title="Tavern Candle" alt="Tavern Candle" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Tavern_Chandelier.png" title="Tavern Chandelier" alt="Tavern Chandelier" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Temple_Dark_Candle.png" title="Temple Dark Candle" alt="Temple Dark Candle" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Temple_Emerald_Torch.png" title="Temple Emerald Torch" alt="Temple Emerald Torch" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Temple_Light_Lantern.png" title="Temple Light Lantern" alt="Temple Light Lantern" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Temple_Scarak_Lamp.png" title="Temple Scarak Lamp" alt="Temple Scarak Lamp" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Temple_Scarak_Lantern.png" title="Temple Scarak Lantern" alt="Temple Scarak Lantern" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Temple_Wind_Candle.png" title="Temple Wind Candle" alt="Temple Wind Candle" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Furniture_Temple_Wind_Chandelier.png" title="Temple Wind Chandelier" alt="Temple Wind Chandelier" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Prototype_NumericalState_Candle.png" title="Prototype NumericalState Candle" alt="Prototype NumericalState Candle" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Prototype_NumericalState_Candle2.png" title="Prototype NumericalState Candle2" alt="Prototype NumericalState Candle2" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Prototype_NumericalState_Candle3.png" title="Prototype NumericalState Candle3" alt="Prototype NumericalState Candle3" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/lights/Wood_Torch_Wall.png" title="Wood Torch Wall" alt="Wood Torch Wall" className="furniture-icon" loading="lazy" />
  </div>
</details>

<details>
  <summary>View all seating (49)</summary>
  <div className="furniture-gallery">
    <img src="/img/furniture/seats/Bench_Alchemy.png" title="Bench Alchemy" alt="Bench Alchemy" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Bench_Anvil.png" title="Bench Anvil" alt="Bench Anvil" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Bench_Arcane.png" title="Bench Arcane" alt="Bench Arcane" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Bench_Architects.png" title="Bench Architects" alt="Bench Architects" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Bench_Armory.png" title="Bench Armory" alt="Bench Armory" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Bench_Armour.png" title="Bench Armour" alt="Bench Armour" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Bench_Carpenter.png" title="Bench Carpenter" alt="Bench Carpenter" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Bench_Cooking.png" title="Bench Cooking" alt="Bench Cooking" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Bench_Furnace.png" title="Bench Furnace" alt="Bench Furnace" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Bench_Furnace_Simple.png" title="Bench Furnace Simple" alt="Bench Furnace Simple" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Bench_Furniture.png" title="Bench Furniture" alt="Bench Furniture" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Bench_Loom.png" title="Bench Loom" alt="Bench Loom" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Bench_Lumbermill.png" title="Bench Lumbermill" alt="Bench Lumbermill" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Bench_Memories.png" title="Bench Memories" alt="Bench Memories" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Bench_Salvage.png" title="Bench Salvage" alt="Bench Salvage" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Bench_Tannery.png" title="Bench Tannery" alt="Bench Tannery" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Bench_Trough.png" title="Bench Trough" alt="Bench Trough" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Bench_Weapon.png" title="Bench Weapon" alt="Bench Weapon" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Deco_Scrap_Chair.png" title="Deco Scrap Chair" alt="Deco Scrap Chair" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Ancient_Bench.png" title="Ancient Bench" alt="Ancient Bench" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Ancient_Chair.png" title="Ancient Chair" alt="Ancient Chair" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Castle_Bench.png" title="Castle Bench" alt="Castle Bench" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Crude_Stool.png" title="Crude Stool" alt="Crude Stool" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Desert_Chair.png" title="Desert Chair" alt="Desert Chair" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Faun_Stool.png" title="Faun Stool" alt="Faun Stool" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Feran_Bench.png" title="Feran Bench" alt="Feran Bench" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Feran_Stool.png" title="Feran Stool" alt="Feran Stool" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Frozen_Castle_Bench.png" title="Frozen Castle Bench" alt="Frozen Castle Bench" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Frozen_Castle_Chair.png" title="Frozen Castle Chair" alt="Frozen Castle Chair" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Grand_Wizard_Bench.png" title="Grand Wizard Bench" alt="Grand Wizard Bench" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Grand_Wizard_Chair.png" title="Grand Wizard Chair" alt="Grand Wizard Chair" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Human_Ruins_Bench.png" title="Human Ruins Bench" alt="Human Ruins Bench" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Human_Ruins_Chair.png" title="Human Ruins Chair" alt="Human Ruins Chair" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Human_Ruins_Stool.png" title="Human Ruins Stool" alt="Human Ruins Stool" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Jungle_Bench.png" title="Jungle Bench" alt="Jungle Bench" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Jungle_Chair.png" title="Jungle Chair" alt="Jungle Chair" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Kweebec_Stool.png" title="Kweebec Stool" alt="Kweebec Stool" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Lumberjack_Chair.png" title="Lumberjack Chair" alt="Lumberjack Chair" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Tavern_Bench.png" title="Tavern Bench" alt="Tavern Bench" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Tavern_Chair.png" title="Tavern Chair" alt="Tavern Chair" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Tavern_Stool.png" title="Tavern Stool" alt="Tavern Stool" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Temple_Dark_Stool.png" title="Temple Dark Stool" alt="Temple Dark Stool" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Temple_Emerald_Stool.png" title="Temple Emerald Stool" alt="Temple Emerald Stool" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Temple_Light_Bench.png" title="Temple Light Bench" alt="Temple Light Bench" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Temple_Light_Stool.png" title="Temple Light Stool" alt="Temple Light Stool" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Temple_Wind_Stool.png" title="Temple Wind Stool" alt="Temple Wind Stool" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Village_Bench.png" title="Village Bench" alt="Village Bench" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Village_Chair.png" title="Village Chair" alt="Village Chair" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/seats/Furniture_Village_Stool.png" title="Village Stool" alt="Village Stool" className="furniture-icon" loading="lazy" />
  </div>
</details>

<details>
  <summary>View all surfaces & tables (19)</summary>
  <div className="furniture-gallery">
    <img src="/img/furniture/tables/Food_Skewer_Vegetable.png" title="Food Skewer Vegetable" alt="Food Skewer Vegetable" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/tables/Furniture_Ancient_Table.png" title="Ancient Table" alt="Ancient Table" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/tables/Furniture_Crude_Table.png" title="Crude Table" alt="Crude Table" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/tables/Furniture_Desert_Table.png" title="Desert Table" alt="Desert Table" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/tables/Furniture_Feran_Table.png" title="Feran Table" alt="Feran Table" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/tables/Furniture_Frozen_Castle_Table.png" title="Frozen Castle Table" alt="Frozen Castle Table" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/tables/Furniture_Grand_Wizard_Table.png" title="Grand Wizard Table" alt="Grand Wizard Table" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/tables/Furniture_Human_Ruins_Desk.png" title="Human Ruins Desk" alt="Human Ruins Desk" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/tables/Furniture_Human_Ruins_Table.png" title="Human Ruins Table" alt="Human Ruins Table" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/tables/Furniture_Jungle_Table.png" title="Jungle Table" alt="Jungle Table" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/tables/Furniture_Kweebec_Table.png" title="Kweebec Table" alt="Kweebec Table" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/tables/Furniture_Lumberjack_Table.png" title="Lumberjack Table" alt="Lumberjack Table" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/tables/Furniture_Tavern_Table.png" title="Tavern Table" alt="Tavern Table" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/tables/Furniture_Temple_Dark_Table.png" title="Temple Dark Table" alt="Temple Dark Table" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/tables/Furniture_Temple_Emerald_Table.png" title="Temple Emerald Table" alt="Temple Emerald Table" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/tables/Furniture_Temple_Light_Table.png" title="Temple Light Table" alt="Temple Light Table" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/tables/Furniture_Temple_Wind_Table.png" title="Temple Wind Table" alt="Temple Wind Table" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/tables/Furniture_Village_Counter.png" title="Village Counter" alt="Village Counter" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/tables/Furniture_Village_Table.png" title="Village Table" alt="Village Table" className="furniture-icon" loading="lazy" />
  </div>
</details>

### Optional, but you will want it

| Item | Why | Examples |
|---|---|---|
| **Bed** | Without one, nobody lives there. The house is identified *by its bed*. | <img src="/img/furniture/beds/Furniture_Village_Bed.png" width="22" align="absmiddle" title="Village Bed" /> <img src="/img/furniture/beds/Furniture_Tavern_Bed.png" width="22" align="absmiddle" title="Tavern Bed" /> <img src="/img/furniture/beds/Furniture_Lumberjack_Bed.png" width="22" align="absmiddle" title="Lumberjack Bed" /> |
| **Chest** | Without one, residents have nowhere to get food. | <img src="/img/furniture/chests/Furniture_Village_Chest_Small.png" width="22" align="absmiddle" title="Village Chest" /> <img src="/img/furniture/chests/Furniture_Tavern_Chest_Large.png" width="22" align="absmiddle" title="Tavern Chest" /> <img src="/img/furniture/chests/Furniture_Tavern_Barrel.png" width="22" align="absmiddle" title="Barrel" /> |

<details>
  <summary>View all beds (16)</summary>
  <div className="furniture-gallery">
    <img src="/img/furniture/beds/Furniture_Ancient_Bed.png" title="Ancient Bed" alt="Ancient Bed" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/beds/Furniture_Crude_Bed.png" title="Crude Bed" alt="Crude Bed" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/beds/Furniture_Desert_Bed.png" title="Desert Bed" alt="Desert Bed" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/beds/Furniture_Feran_Bed.png" title="Feran Bed" alt="Feran Bed" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/beds/Furniture_Frozen_Castle_Bed.png" title="Frozen Castle Bed" alt="Frozen Castle Bed" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/beds/Furniture_Grand_Wizard_Bed.png" title="Grand Wizard Bed" alt="Grand Wizard Bed" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/beds/Furniture_Human_Ruins_Bed.png" title="Human Ruins Bed" alt="Human Ruins Bed" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/beds/Furniture_Jungle_Bed.png" title="Jungle Bed" alt="Jungle Bed" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/beds/Furniture_Kweebec_Bed.png" title="Kweebec Bed" alt="Kweebec Bed" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/beds/Furniture_Lumberjack_Bed.png" title="Lumberjack Bed" alt="Lumberjack Bed" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/beds/Furniture_Royal_Magic_Bed.png" title="Royal Magic Bed" alt="Royal Magic Bed" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/beds/Furniture_Tavern_Bed.png" title="Tavern Bed" alt="Tavern Bed" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/beds/Furniture_Temple_Dark_Bed.png" title="Temple Dark Bed" alt="Temple Dark Bed" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/beds/Furniture_Temple_Emerald_Bed.png" title="Temple Emerald Bed" alt="Temple Emerald Bed" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/beds/Furniture_Temple_Light_Bed.png" title="Temple Light Bed" alt="Temple Light Bed" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/beds/Furniture_Village_Bed.png" title="Village Bed" alt="Village Bed" className="furniture-icon" loading="lazy" />
  </div>
</details>

<details>
  <summary>View all chests & storage (48)</summary>
  <div className="furniture-gallery">
    <img src="/img/furniture/chests/Container_Kweebec_Chest_Large.png" title="Container Kweebec Chest Large" alt="Container Kweebec Chest Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Ancient_Barrel.png" title="Ancient Barrel" alt="Ancient Barrel" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Ancient_Chest_Large.png" title="Ancient Chest Large" alt="Ancient Chest Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Ancient_Chest_Small.png" title="Ancient Chest Small" alt="Ancient Chest Small" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Christmas_Chest_Small.png" title="Christmas Chest Small" alt="Christmas Chest Small" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Christmas_Chest_Small_Green.png" title="Christmas Chest Small Green" alt="Christmas Chest Small Green" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Christmas_Chest_Small_Red.png" title="Christmas Chest Small Red" alt="Christmas Chest Small Red" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Christmas_Chest_Small_RedDotted.png" title="Christmas Chest Small RedDotted" alt="Christmas Chest Small RedDotted" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Christmas_Chest_Small_White.png" title="Christmas Chest Small White" alt="Christmas Chest Small White" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Crude_Chest_Large.png" title="Crude Chest Large" alt="Crude Chest Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Crude_Chest_Small.png" title="Crude Chest Small" alt="Crude Chest Small" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Desert_Chest_Large.png" title="Desert Chest Large" alt="Desert Chest Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Desert_Chest_Small.png" title="Desert Chest Small" alt="Desert Chest Small" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Dungeon_Chest_Epic.png" title="Dungeon Chest Epic" alt="Dungeon Chest Epic" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Dungeon_Chest_Epic_Large.png" title="Dungeon Chest Epic Large" alt="Dungeon Chest Epic Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Dungeon_Chest_Legendary_Large.png" title="Dungeon Chest Legendary Large" alt="Dungeon Chest Legendary Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Feran_Chest_Large.png" title="Feran Chest Large" alt="Feran Chest Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Feran_Chest_Small.png" title="Feran Chest Small" alt="Feran Chest Small" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Frozen_Castle_Chest_Large.png" title="Frozen Castle Chest Large" alt="Frozen Castle Chest Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Frozen_Castle_Chest_Small.png" title="Frozen Castle Chest Small" alt="Frozen Castle Chest Small" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Goblin_Chest_Small.png" title="Goblin Chest Small" alt="Goblin Chest Small" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_GrandWizard_Chest_Large.png" title="GrandWizard Chest Large" alt="GrandWizard Chest Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_GrandWizard_Chest_Small.png" title="GrandWizard Chest Small" alt="GrandWizard Chest Small" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Human_Ruins_Chest_Large.png" title="Human Ruins Chest Large" alt="Human Ruins Chest Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Human_Ruins_Chest_Small.png" title="Human Ruins Chest Small" alt="Human Ruins Chest Small" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Jungle_Chest_Large.png" title="Jungle Chest Large" alt="Jungle Chest Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Jungle_Chest_Small.png" title="Jungle Chest Small" alt="Jungle Chest Small" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Kweebec_Chest_Large.png" title="Kweebec Chest Large" alt="Kweebec Chest Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Kweebec_Chest_Small.png" title="Kweebec Chest Small" alt="Kweebec Chest Small" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Lumberjack_Chest_Large.png" title="Lumberjack Chest Large" alt="Lumberjack Chest Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Lumberjack_Chest_Small.png" title="Lumberjack Chest Small" alt="Lumberjack Chest Small" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Scarak_Hive_Chest_Large.png" title="Scarak Hive Chest Large" alt="Scarak Hive Chest Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Scarak_Hive_Chest_Small.png" title="Scarak Hive Chest Small" alt="Scarak Hive Chest Small" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Tavern_Barrel.png" title="Tavern Barrel" alt="Tavern Barrel" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Tavern_Chest_Large.png" title="Tavern Chest Large" alt="Tavern Chest Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Tavern_Chest_Small.png" title="Tavern Chest Small" alt="Tavern Chest Small" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Temple_Dark_Chest_Large.png" title="Temple Dark Chest Large" alt="Temple Dark Chest Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Temple_Dark_Chest_Small.png" title="Temple Dark Chest Small" alt="Temple Dark Chest Small" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Temple_Emerald_Chest_Large.png" title="Temple Emerald Chest Large" alt="Temple Emerald Chest Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Temple_Emerald_Chest_Small.png" title="Temple Emerald Chest Small" alt="Temple Emerald Chest Small" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Temple_Light_Chest_Large.png" title="Temple Light Chest Large" alt="Temple Light Chest Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Temple_Light_Chest_Small.png" title="Temple Light Chest Small" alt="Temple Light Chest Small" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Temple_Scarak_Chest_Large.png" title="Temple Scarak Chest Large" alt="Temple Scarak Chest Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Temple_Scarak_Chest_Small.png" title="Temple Scarak Chest Small" alt="Temple Scarak Chest Small" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Temple_Wind_Chest_Large.png" title="Temple Wind Chest Large" alt="Temple Wind Chest Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Temple_Wind_Chest_Small.png" title="Temple Wind Chest Small" alt="Temple Wind Chest Small" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Village_Chest_Large.png" title="Village Chest Large" alt="Village Chest Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/chests/Furniture_Village_Chest_Small.png" title="Village Chest Small" alt="Village Chest Small" className="furniture-icon" loading="lazy" />
  </div>
</details>

## Checking

Point a **House Blueprint** at a registered bed.

![House Blueprint](/img/HouseBlueprint.png)

The tool will tell you whether the structure passed and, when it did not, **what is missing**. It also
reports how many interior blocks were visited, and how many doors and chests were found.

:::tip Unloaded chunks get in the way
If part of the house sits in an unloaded chunk, the check flags the result as incomplete rather
than rejecting it. Stand near the house when checking.
:::

## The house is identified by its bed

This is the most important detail and the easiest to trip over: **the house's identity comes from
the bed**.

What that means in practice:

- Two beds in the same room can become two houses. (Oh my god, they were roommates...)
- Breaking the resident's bed releases the house.
- Moving the bed can read as a different house.

It is a known limitation, and turning it into an identifier of its own is on the roadmap.

## Doors

A door occupies four blocks, and two doors side by side form a double door. NPCs open and close
them as they pass. (Hodor would be proud).

<details>
  <summary>View all doors & trapdoors (47)</summary>
  <div className="furniture-gallery">
    <img src="/img/furniture/doors/Furniture_Ancient_Door.png" title="Ancient Door" alt="Ancient Door" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Ancient_Trapdoor.png" title="Ancient Trapdoor" alt="Ancient Trapdoor" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Crude_Door.png" title="Crude Door" alt="Crude Door" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Crude_Trapdoor.png" title="Crude Trapdoor" alt="Crude Trapdoor" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Desert_Door.png" title="Desert Door" alt="Desert Door" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Desert_Trapdoor.png" title="Desert Trapdoor" alt="Desert Trapdoor" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Dungeon_Air_Door_Large.png" title="Dungeon Air Door Large" alt="Dungeon Air Door Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Feran_Door.png" title="Feran Door" alt="Feran Door" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Feran_Trapdoor.png" title="Feran Trapdoor" alt="Feran Trapdoor" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Frozen_Castle_Door.png" title="Frozen Castle Door" alt="Frozen Castle Door" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Frozen_Castle_Trapdoor.png" title="Frozen Castle Trapdoor" alt="Frozen Castle Trapdoor" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Grand_Wizard_Medium_Door.png" title="Grand Wizard Medium Door" alt="Grand Wizard Medium Door" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Human_Ruins_Door.png" title="Human Ruins Door" alt="Human Ruins Door" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Human_Ruins_Door_Large.png" title="Human Ruins Door Large" alt="Human Ruins Door Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Human_Ruins_Door_Medium.png" title="Human Ruins Door Medium" alt="Human Ruins Door Medium" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Human_Ruins_Trapdoor.png" title="Human Ruins Trapdoor" alt="Human Ruins Trapdoor" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Jungle_Door.png" title="Jungle Door" alt="Jungle Door" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Jungle_Trapdoor.png" title="Jungle Trapdoor" alt="Jungle Trapdoor" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Kweebec_Door.png" title="Kweebec Door" alt="Kweebec Door" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Kweebec_Trapdoor.png" title="Kweebec Trapdoor" alt="Kweebec Trapdoor" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Lumberjack_Door.png" title="Lumberjack Door" alt="Lumberjack Door" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Lumberjack_Trapdoor.png" title="Lumberjack Trapdoor" alt="Lumberjack Trapdoor" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Royal_Magic_Medium_Door.png" title="Royal Magic Medium Door" alt="Royal Magic Medium Door" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Scarak_Hive_Door_Large.png" title="Scarak Hive Door Large" alt="Scarak Hive Door Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Scarak_Hive_Door_Medium.png" title="Scarak Hive Door Medium" alt="Scarak Hive Door Medium" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Tavern_Door.png" title="Tavern Door" alt="Tavern Door" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Tavern_Trapdoor.png" title="Tavern Trapdoor" alt="Tavern Trapdoor" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Temple_Dark_Door.png" title="Temple Dark Door" alt="Temple Dark Door" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Temple_Dark_Door_Large.png" title="Temple Dark Door Large" alt="Temple Dark Door Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Temple_Dark_Door_Medium.png" title="Temple Dark Door Medium" alt="Temple Dark Door Medium" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Temple_Dark_Trapdoor.png" title="Temple Dark Trapdoor" alt="Temple Dark Trapdoor" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Temple_Earth_Door.png" title="Temple Earth Door" alt="Temple Earth Door" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Temple_Emerald_Door.png" title="Temple Emerald Door" alt="Temple Emerald Door" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Temple_Emerald_Door_Medium.png" title="Temple Emerald Door Medium" alt="Temple Emerald Door Medium" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Temple_Emerald_Trapdoor.png" title="Temple Emerald Trapdoor" alt="Temple Emerald Trapdoor" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Temple_Light_Door.png" title="Temple Light Door" alt="Temple Light Door" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Temple_Light_Door_Large.png" title="Temple Light Door Large" alt="Temple Light Door Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Temple_Light_Door_Medilum.png" title="Temple Light Door Medilum" alt="Temple Light Door Medilum" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Temple_Light_Trapdoor.png" title="Temple Light Trapdoor" alt="Temple Light Trapdoor" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Temple_Scarak_Door_Large.png" title="Temple Scarak Door Large" alt="Temple Scarak Door Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Temple_Scarak_Door_Medium.png" title="Temple Scarak Door Medium" alt="Temple Scarak Door Medium" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Temple_Wind_Door.png" title="Temple Wind Door" alt="Temple Wind Door" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Temple_Wind_Door_Large.png" title="Temple Wind Door Large" alt="Temple Wind Door Large" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Temple_Wind_Door_Medium.png" title="Temple Wind Door Medium" alt="Temple Wind Door Medium" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Temple_Wind_Trapdoor.png" title="Temple Wind Trapdoor" alt="Temple Wind Trapdoor" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Village_Door.png" title="Village Door" alt="Village Door" className="furniture-icon" loading="lazy" />
    <img src="/img/furniture/doors/Furniture_Village_Trapdoor.png" title="Village Trapdoor" alt="Village Trapdoor" className="furniture-icon" loading="lazy" />
  </div>
</details>

## Next

[Beds and residents](beds-and-residents.md) — how an NPC claims a bed and what happens when two of
them want the same one.
