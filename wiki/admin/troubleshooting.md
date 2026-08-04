---
sidebar_position: 5
title: Troubleshooting
---

# Troubleshooting

Symptoms, what causes them, and how to confirm.

## NPCs do not load at all

```
ZipException: invalid LOC header (bad signature)
FAIL: /Server/NPC/Roles/SimTale_Human_Male.json: Failed to load builder
Reloading nonexistent role ...
```

**Cause**: the jar was written while the game was running. Hytale watches the `Mods` folder and
reloads on change, so it read a half-written 11 MB file.

**Fix**: build with the game closed. The deploy task now writes to a temp file and moves it
atomically, which closes the window, but closing the game is still safest.

## No NPC spawns on its own

Wait about 40 seconds after joining. If nothing appears, use `/simtale forcespawn`.

Auto-spawn previously never worked at all — spawning inside a tick threw
`IllegalStateException: Store is currently processing!` because entity creation is a structural
write. It is now deferred to the world thread.

## NPCs do not sleep

1. `/simtale debugbeds` — is the bed listed?
2. If the list is empty, the bed was never registered. Placing a new bed registers it immediately.
3. If it is listed but nobody claims it, check the house is valid with `/simtale housecheck`.

## NPCs do not eat

1. `/simtale debugchests` — is the chest listed?
2. If the row says the chest has no house, that is the problem: `canOpenChest` refuses chests
   outside a recognised house, which is what keeps NPCs out of world-generated loot chests.
3. Make the room a valid house and check again.

## A cow opened the villager panel

Interacting with any entity used to adopt it into the mod, adding the NPC component. Since the
routine system queries exactly that component, the animal started running the villager routine.

**Fix**: `/simtale forget` releases them and deletes their records. Deleting the record matters —
otherwise the tick system re-adopts the entity the next time its chunk loads.

The bug is fixed in both interaction paths (right-click and the F key), but entities adopted before
the fix keep the component until cleared.

## The player is stuck in a bed

`/simtale unstick` frees you.

Almost certainly the same adoption bug: a player adopted as an NPC gets sent to bed by the routine,
mounted and frozen. Creative mode does not remove `MountedComponent`, which is why it did not help.

## The log is silent

SLF4J is a **no-op logger** on this server:

```
SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder"
```

Everything written through `org.slf4j` is silently discarded. The mod uses `SimLog`, which delegates
to `HytaleLogger`. If you add logging to a class, use `SimLog`.

Debug-level lines only appear after `/simtale debug on`.

## Duplicate houses

On boot, look for `N registros de casa duplicados removidos`. It should appear once and never again.

The original cause was a fresh `UUID.randomUUID()` on every bed claim, which produced 109 duplicate
records in one world.

## `State 'Sleep.null' does not exist`

Harmless. It appears while NPCs sleep and depends on registering a `BlockSet` asset from the mod —
an earlier attempt at that broke spawning entirely by creating the asset with the wrong type, so it
was reverted.
