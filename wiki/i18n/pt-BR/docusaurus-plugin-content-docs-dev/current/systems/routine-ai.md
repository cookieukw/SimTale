---
sidebar_position: 1
title: RoutineAISystem
---

# RoutineAISystem

The autonomy engine. One tick per NPC, three phases.

```
RoutineAISystem (tick per NPC)
├── Evaluation  — self-heal stuck flags, then sleep/hunger interrupts
├── Decision    — pick the task
└── Action      — delegate to a helper
    ├── NPCHungerHelper  → FINDING_FOOD, MOVING_TO_FOOD, EATING
    ├── NPCWorkHelper    → MOVING_TO_WORK, FARMING, HUNTING, PLANTING, MOVING_TO_DEPOSIT
    ├── NPCSocialHelper  → MOVING_TO_SOCIALIZE, SOCIALIZING, WANDERING
    └── NPCLeisureHelper → FINDING_LEISURE, MOVING_TO_LEISURE, DOING_HOBBY
```

Query: `SimTale.SIM_NPC_COMPONENT_TYPE`.

## Interrupts

Two conditions can drop whatever the NPC is doing.

### Sleep

```java
boolean sleepWindowOpen = NPCSleepHelper.isSleepPeriod(npc, world);
boolean exhausted = NeedsHelper.getNeed(store, npc.entityRef, NeedsHelper.ENERGY_ID) < sleepThreshold;

if ((sleepWindowOpen || exhausted)
        && world.getTick() >= ai.nextBedSearchTick
        && !alreadyHeadedToBed && !inDeathFlow) { ... }
```

`sleepingOnSchedule` records *why* she went to bed, because the two cases wake differently:
scheduled sleepers stay down until the window closes; naps end when energy fills.

### Hunger

Same shape, threshold 25, guarded by `nextFoodSearchTick`.

## Three guards every interrupt needs

| Guard | Without it |
|---|---|
| Not already in that task chain | Restarts the task every tick |
| `nextXSearchTick` cooldown | Search storm when the target is unreachable — 3447/sec observed |
| `!inDeathFlow` | Yanks the NPC out of `DYING`; the death broadcast repeats forever |

## Movement

Movement uses a leash point, updated with throttling via `lastLeashPos` and `lastLeashTick`.

:::danger Copy every field in clone()
Those two fields were once missing from `RoutineAIComponent.clone()`. Every component replacement
reset them to null, navigation stopped updating, and NPCs froze mid-walk. No error, no log.
:::

## Sleeping

1. Resolve the bed anchor with `FurnitureAnchorHelper.anchorOf`
2. `BlockMountAPI.mountOnBlock` — sets position **and** rotation synchronously
3. `setSleepingState` plus the animation *is* the pose
4. `pinLeashAt` at the resulting position

There is no manual teleport. Every attempt to position the body by hand made it worse; the mount API
already does it, provided it is given the anchor.

## Related

- [Furniture registry](furniture-registry.md) — anchors and how beds get registered
- [Hunger, sleep and death](hunger-and-sleep.md) — thresholds and the death rule
