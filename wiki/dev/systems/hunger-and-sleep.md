---
sidebar_position: 3
title: Hunger, sleep and death
---

# Hunger, sleep and death

## Sleep by the clock

`NPCSleepHelper.isSleepPeriod` answers whether this NPC's sleep window is open:

```java
Float hour = currentHour(world);
if (hour == null) return false;
boolean night = hour < DAY_START_HOUR || hour >= NIGHT_START_HOUR;
return isNightWatch(npc) ? !night : night;
```

The night boundary reuses the same split `InteractionManager` already used for night-time greetings,
so dialogue and routine never disagree about the time.

When the time resource cannot be read, it returns `false` — degrading to exhaustion-only behaviour
rather than trapping the village in bed.

Guards (`Profession.GUARD`) run the inverted shift. Their energy drains and refills normally, just
on the opposite schedule, which is why no special rule for zero energy was needed.

## Food classification

```java
Map<InteractionType, String> interactions = item.getInteractions();
String id = interactions.get(InteractionType.Secondary);
// Root_Secondary_Consume_Food_T1..T3
```

The tier arrives free as a raw-versus-cooked signal: raw meat, ingredients and crops inherit T1 from
their templates; anything cooked declares T2 or T3.

`scoreFor` ranks **tier → taste → distance**. Tier dominates deliberately: a hated pie beats a
beloved slab of raw beef, which is the behaviour a village sim wants.

:::note The key is an enum
`getInteractions()` returns `Map<InteractionType, String>`, not `Map<String, String>`. Using
`get("Secondary")` compiles and classifies every item as inedible.
:::

## Starvation

Hunger is not lethal, by design. Aging and disease will own death; a third cause competing with them
would make all three harder to reason about.

`NPCHungerHelper.tickStarvation` breaks a starving NPC out of whatever it was doing, sets `SAD` and
plays the crying animation. What it costs the NPC is its usefulness, not its life — job, hobby and
social life stop until someone feeds it.

Three families of task are excluded from the interruption:

| Excluded | Why |
|---|---|
| `FINDING_FOOD`, `MOVING_TO_FOOD`, `EATING` | Otherwise it finds a chest and is pulled back to `IDLE` before reaching it |
| The five sleep tasks | Yanking a sleeping NPC to `IDLE` leaves the `sleeping` flag orphaned |
| `DYING`, `DEAD`, `REAPING` | Dying is not a task to interrupt |

The `DYING → DEAD → REAPING` flow is fully wired to the engine's health system. Whenever an NPC reaches `0 HP` — through combat, fall damage, or `/simtale forcekill` — `RoutineAISystem` intercepts it and routes it into the death flow, rather than leaving a broken, not-quite-dead entity wandering around.

:::note Removed system
Earlier versions of this page documented a `Needs.starvationDamage` counter that killed at 200
accumulated damage. `Needs.java` was removed in the migration to native `EntityStats`, and the
leftover `hunger <= 0 → DYING` trigger — which killed instantly, the opposite of the intent — went
with it, along with the now-unreferenced `STARVATION_DAMAGE`, `STARVATION_INTERVAL_TICKS` and
`LETHAL_STARVATION_DAMAGE`.
:::

## Feeding by hand

`InteractionManager.tryFeed` runs before the normal gift rules. Below 50 hunger, edible gifts are
eaten on the spot: hunger and health restored by tier, starvation cancelled, larger affinity gain.

It uses the same `NPCFoodHelper` as the chest routine, so a player cannot force-feed something the
NPC would refuse on her own.

## Constants

See [Balancing](/admin/balancing) for the full table.
