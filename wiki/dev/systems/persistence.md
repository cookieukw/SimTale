---
sidebar_position: 5
title: Persistence
---

# Persistence

## Two layers

| Layer | Stores | Lost if you forget it |
|---|---|---|
| ECS codec on `SimNPCComponent` | `EntityId` and `Name` only | — |
| Caskara, shell `simtale` | Needs, personality, relationships, family, house, job | Everything else |

Anything not in the codec and not in the Caskara record is gone on reload.

## The shell matters

```java
public static Shell worldShell() {
    World world = WorldUtil.first();
    return world != null ? Caskara.shell(world, "simtale") : DB_SHELL;
}
```

Earlier versions used a global `Caskara.shell("simtale")`, meaning every world shared one database. A brand new world would open already "full" of the previous world's NPCs — which stopped the starting group from ever spawning.

The shell must be scoped per-world. Always go through `SimNPCPersistence.worldShell()` rather than calling raw Caskara methods on the default shell, which would return nothing.

## Saving on spawn

Newly spawned NPCs must be persisted immediately. Without it you get the classic mismatch: four NPCs
in the world, three records on disk, and the fourth vanishes on restart.

```java
simComponent.dataLoaded = true;  // this object IS the authoritative data
```

The flag exists so a freshly rolled NPC can save without first reading from the database and
overwriting itself with nothing.

## Re-attaching after a reload

`SimTaleTickSystem` re-attaches the component to entities whose record exists when their chunk
loads.

:::danger This is also an adoption vector
Because it re-attaches based on the record alone, an entity wrongly adopted once keeps coming back
after its component is stripped. `/simtale forget` therefore deletes the record too.
:::

## Adding a persisted field

1. Add the field to the class the record serialises (`Needs`, `SimNPCComponent`, …)
2. Check it is carried in `SimNPCData` and in `loadNPC`/`saveNPC`
3. Add it to `clone()` if the class has one

Step 3 is the one that gets missed, and it fails silently.

## The graveyard, and why revival changes the UUID

Death is not destructive: `archiveToGraveyard` moves the record into a separate per-world shell,
`simtale_graveyard`, instead of deleting it. `/simtale graveyard` browses that shell and
`SimNPCRevival` brings a record back.

The revived NPC gets a **new entity UUID**, and that is not a shortcut — the engine forbids the
alternative:

```
EntityStore$UUIDSystem  (a RefSystem)
  onEntityAdded -> entitiesByUuid.putIfAbsent(uuidComponent.getUuid(), ref)
                   "Removing duplicate entity with UUID: %s" -> removeEntity(newcomer)
```

Two consequences. The index is filled **when the entity is added**, so putting the old UUIDComponent
back after the spawn never reaches `entitiesByUuid` and `getRefFromUUID` would keep answering with
the wrong ref forever. And colliding on the key on purpose gets the revived body deleted by the
engine, silently.

So the id changes and `SimNPCRevival.remapReferences` rewrites everything pointing at the old one:

| Where | What |
|---|---|
| Live `SimNPCComponent` | `relationships` keys, `family.spouseId`, `family.children[].id` |
| Records in the `simtale` shell | the same two, for NPCs not loaded right now |
| `LifecycleManager.ACTIVE_CHILDREN` | `childId`, `motherId`, `fatherId`, `carriedBy` |
| `HouseManager` | `HouseData.owners` **and** `OWNER_TO_HOUSE_ID`, together |

:::danger This list is the fragile part
Anything that stores an NPC UUID and is not remapped keeps pointing at a grave, silently. If you add
a new place that holds one, add it to `remapReferences` in the same commit.
:::

The bed is deliberately excluded from the restore and handled afterwards: it is reclaimed only if it
still exists and is still free, so a revival never evicts whoever moved in while she was dead.

## No migration

The mod is in testing with no public users, so breaking the save format is acceptable. That changes
the day there is a release.
