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
| Furniture (chair, table, workbench, lamp, chest…) | Recorded for validation and **traversed**, so it never walls off the room |
| Other solid | Boundary |
| Unloaded (`null` type) | Boundary, and the scan is flagged incomplete |

:::caution `null` is not air
A null block type means "not loaded", not "empty". Treating it as air made the fill pour out through
unloaded chunks until it burned the 512-block budget and the house was rejected as unenclosed.
:::

Furniture used to be treated as a wall. A workbench or table in the middle of a room stopped the
flood fill, which made valid houses report `TOO_LARGE_OR_UNENCLOSED` or `TOO_SMALL` depending on
where the furniture sat. It is now recorded and walked through: the room is the air *around* the
furniture, not the furniture itself.

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

## Map marker

`SimTaleMarkerProvider` draws one Home marker per registered house, at the **centre of the interior**
rather than the first bed. A one-house village used to put the Home and village markers on the same
pixel because both were anchored to the same bed, which read as "the house marker is missing".

Occupancy is based on `house.owners` being non-empty **or** any active NPC having a bed in the house.
It used to be based only on resolvable owner names, so an owner in an unloaded chunk turned the house
blue ("unoccupied"). Registered beds that belong to no house are drawn separately as grey
"unclaimed bed" markers.

## Village safe zone

The village boundary is also a safe zone. `NPCDamageEventSystem` cancels every damage event whose
victim is a SimTale NPC inside `VillageManager.nearest(...).contains(...)`; `/simtale forcekill` still
works because it does not go through damage.

`VillageRegenSystem` ticks players, detects the village boundary and applies
`Food_Health_Regen_Medium` through RuneCore's `RuneEffect.withNativeEffect(...)`. It refreshes every
two seconds while inside, with a six-second lifetime, so leaving the village lets the effect fade.

Both are controlled by `villageSafeZoneEnabled` and `villageRegenEnabled` in `simtale-config.json`.

## Deduplication

Claiming used to mint a fresh `UUID.randomUUID()` every time, producing 109 duplicate records in one
world. `validateAndClaimBed` now reuses the existing id via `findHouseIdForRoom` and merges owners,
and `dedupeHousesByBed()` runs from `loadAllHouses()` on boot.
