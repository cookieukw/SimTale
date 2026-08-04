---
sidebar_position: 3
title: Hunger, sleep and death
---

# Hunger, sleep and death

## Sleep by the clock

`NPCSleepHelper.isSleepPeriod` answers whether this NPC's sleep window is open:

```java
boolean night = progress < 0.25f || progress > 0.75f;
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

## Death

```java
if (npc.needs.starvationDamage >= NPCHungerHelper.LETHAL_STARVATION_DAMAGE) {
    ai.currentTask = TaskType.DYING;
```

Death comes from accumulated starvation damage, not from the hunger bar. `hunger <= 0` used to kill
instantly, which made the whole starvation system decorative — the NPC died the moment her stomach
emptied, long before damage mattered, and healing from food changed nothing.

### Why a counter

RuneCore exposes `addHealth` and `subtractHealth` but no reliable health getter, and the stat-map
read path could not be confirmed in the bytecode. Real damage is still applied, so the health bar
reflects it; the counter drives death.

- Limit is 200, matching `MaxHealth` in the NPC roles
- Lives in `Needs`, which is persisted — so it survives a relog
- Eating resets it to zero

Cost: an NPC wounded by something else does not starve any sooner.

## Feeding by hand

`InteractionManager.tryFeed` runs before the normal gift rules. Below 50 hunger, edible gifts are
eaten on the spot: hunger and health restored by tier, starvation cancelled, larger affinity gain.

It uses the same `NPCFoodHelper` as the chest routine, so a player cannot force-feed something the
NPC would refuse on her own.

## Constants

See [Balancing](/admin/balancing) for the full table.
