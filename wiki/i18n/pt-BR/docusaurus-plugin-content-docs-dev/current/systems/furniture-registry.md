---
sidebar_position: 2
title: Furniture registry
---

# Furniture registry

## Multi-block furniture

A bed spans **six** blocks, a door **four**. One is the anchor; the rest are fillers that store the
offset back to it.

`BlockMountAPI.mountOnBlock` measures the sleeping position from the anchor, so mounting on a filler
offsets the pose by exactly that filler's distance.

```java
int filler = section.getFiller(x, y, z);
int dx = FillerBlockUtil.unpackX(filler);
```

The **sign** of the offset is undocumented, so `FurnitureAnchorHelper.anchorOf` tries both
directions and validates the one landing on a block with the same id and `filler == 0`. This is the
same data the game's `/inspectfiller` reads.

Neighbourhood heuristics were tried first and are wrong: the anchor does not reliably sit in any
particular direction.

## The registries

| Registry | Holds | Key |
|---|---|---|
| `BedRegistry.BEDS` | `BedPos` (position + lying-axis yaw) | anchor |
| `ChestRegistry.CHESTS` | `HouseBlockPos` | anchor |
| `BathRegistry.BATHS` | `HouseBlockPos` | anchor |
| `CropRegistry.CROPS` | `HouseBlockPos` | anchor |
| `FarmlandRegistry.FARMLAND` | `HouseBlockPos` | anchor |
| `FishingPostRegistry.POSTS` | `FishingPost` (post pos + water pos) | anchor |
| `LumberPostRegistry.POSTS` | `HouseBlockPos` | anchor |
| `FarmPostRegistry.POSTS` | `HouseBlockPos` | anchor |
| `LeisureRegistry.LEISURES` | `LeisurePos` (pos + hobby type) | anchor |

Todos são estáticos e globais em memória. Enquanto a maioria é reconstruída exclusivamente pela varredura ao entrar no mundo, o `ChestRegistry` persiste explicitamente seus dados no Caskara por mundo para evitar amnésia quando as chunks descarregam.

## Who populates them

| Path | When |
|---|---|
| `BedPlaceBlockEventSystem` | On the place event — beds, chests, crops, farmland |
| `PlayerJoinHandler` | Radius-32 scan on join, delayed 2 s for chunks to load |
| `BedEntityRegistrySystem` | Beds that are entities rather than blocks |
| `SimNPCPersistence` | Restoring an NPC that already had a bed |

Place events and the scan share `BedWorldBootstrap.registerBedAt`, so a bed placed by hand and one
found by the scan produce identical records.

:::caution The historical bug
The place event did not register beds at all, and the only scan was called from inside
`/simtale housecheck` as a side effect. Old worlds worked because someone had run the command there,
and static registries kept the state alive. See [Lessons learned](../lessons-learned.md).
:::

## Classification comes from the engine

```java
BlockModule.getComponent(ItemContainerBlock.getComponentType(), world, x, y, z) != null
```

The same component NPCs read when looking for food, so registration and consumption cannot diverge.

`ChestRegistry.isChestId` (name-based) remains only as a fallback and is marked unreliable.

`BedRegistry.isBedId` is still name-based (`contains "bed"`, excluding `bedrock`). Known debt.

## House scope and global chests

`HouseManager.canOpenChest` refuses a private chest that belongs to no house. That is what keeps NPCs
out of world-generated loot chests, since the scan registers any container in range.

There is one deliberate exception: a chest **inside a village and outside every house** is a
**village-global chest**. It has no owner and any resident may take food from it, including
homeless NPCs. `ChestRegistry.isVillageGlobal` computes this live from
`HouseManager.findHouseIdForChest` + `VillageManager.nearest`, so moving the chest into a house makes
it private again, and destroying the village removes the global status with it. The status is never
written to Caskara.

A chest outside the village in open field is still ignored. `/simtale chestshare` can mark a chest
shared explicitly regardless of location.
