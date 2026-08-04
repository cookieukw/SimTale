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
public static final Shell DB_SHELL = Caskara.shell("simtale");
```

`Caskara.load()` and the other static helpers resolve to the **`default`** shell. A re-attach path
once used them and always returned null, so it never ran at all.

Always go through `SimNPCPersistence`.

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

## No migration

The mod is in testing with no public users, so breaking the save format is acceptable. That changes
the day there is a release.
