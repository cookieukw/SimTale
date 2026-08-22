---
sidebar_position: 4
title: House recognition
---

# House recognition

## Flood fill

`HouseManager.scanHouseFromBed` runs a 3D flood fill from the bed, capped at **512 interior
blocks**.

Along the way it classifies what it meets:

| Kind | Effect |
|---|---|
| Door | Recorded (bottom block only, to avoid double counting) and treated as boundary |
| Bed | Recorded as another bed if not the origin, and **traversed** (no longer blocks its own scan) |
| Chest | Recorded and traversed (its position joins the interior) |
| Solid | Boundary. If it matches a furniture requirement (e.g. chair, table), it is also recorded as furniture to satisfy validation. |
| Unloaded (`null` type) | Boundary, and the scan is flagged incomplete |

:::caution `null` is not air
A null block type means "not loaded", not "empty". Treating it as air made the fill pour out through
unloaded chunks until it burned the 512-block budget and the house was rejected as unenclosed.
:::

## Furniture requirements

```java
LIGHT_SOURCE("torch", "lantern", "candle", "campfire", "glow", "lamp", "chandelier"),
SEATING("chair", "stool", "bench", "seat", "sofa", "couch"),
SURFACE("table", "workbench", "desk", "counter"),
STORAGE_OPTIONAL("chest", "barrel", "cupboard", "cabinet");
```

The first three are mandatory. Storage is optional structurally, but without it residents have
nowhere to find food.

Chest detection during the scan uses `ChestRegistry.isContainerAt` — the engine component — with the
keyword list only as fallback. When it was name-only, chests never entered `interior`, so
`BLOCK_TO_HOUSE_ID` had no entry for them and `canOpenChest` refused every one.

## Indexes

| Map | Purpose |
|---|---|
| `HOUSES_BY_ID` | House id → data |
| `BLOCK_TO_HOUSE_ID` | Every interior block → house id |
| `OWNER_TO_HOUSE_ID` | Owner uuid → house id |

`BLOCK_TO_HOUSE_ID` is populated from `house.interior`, which is why chests must be part of the
interior for ownership checks to work.

## Identity comes from the bed

Structural limitation. Consequences:

- Two beds in one room can become two houses
- Breaking the bed releases the house
- Moving the bed can read as a different house

Giving houses an id of their own is on the roadmap.

## Deduplication

Claiming used to mint a fresh `UUID.randomUUID()` every time, producing 109 duplicate records in one
world. `validateAndClaimBed` now reuses the existing id via `findHouseIdForRoom` and merges owners,
and `dedupeHousesByBed()` runs from `loadAllHouses()` on boot.
