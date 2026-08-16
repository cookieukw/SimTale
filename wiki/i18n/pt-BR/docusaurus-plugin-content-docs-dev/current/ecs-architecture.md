---
sidebar_position: 2
title: ECS architecture
---

# ECS architecture

## The pieces

| Concept | In practice |
|---|---|
| `Component` | Plain data on an entity — `SimNPCComponent`, `RoutineAIComponent` |
| `ComponentType` | The key used to read and write a component |
| `Store<EntityStore>` | Where entities and their components live |
| `Ref<EntityStore>` | A handle to one entity |
| `CommandBuffer` | Queues structural changes to apply after the tick |
| `ArchetypeChunk` | A batch of entities with the same component layout |

## The rule that bites everyone

**Structural writes throw during a tick.**

`addComponent`, `putComponent`, `removeComponent` and `addEntity` all fail with:

```
IllegalStateException: Store is currently processing!
```

if called while the store is ticking. This silently broke three separate features before it was
understood: auto-spawn never worked, growth transitions never ran, and visual scaling never applied.

Two ways out:

```java
// Inside a system, with a CommandBuffer at hand
commandBuffer.addComponent(ref, TYPE, value);

// Outside, or when there is no buffer
world.execute(() -> store.addComponent(ref, TYPE, value));
```

Reading and mutating fields of an existing component is fine — `store.getComponent` returns the live
instance.

## Query is optimisation; the guard is the rule

A system declares what it ticks over:

```java
public Query<EntityStore> getQuery() {
    return Query.or(
            SimTale.SIM_NPC_COMPONENT_TYPE,
            Player.getComponentType(),
            PersistentModel.getComponentType());
}
```

That is a **coarse filter for performance** — the ECS does not even call `tick` for entities that do
not match. `PlumbobSystem` previously queried `UUIDComponent`, which is every entity in the world:
dropped items, projectiles, everything, every tick.

But `Query.or` lets through more than you want, so correctness needs an explicit check inside the
tick:

```java
boolean isPlayer = chunk.getComponent(index, Player.getComponentType()) != null;
boolean isNpc = chunk.getComponent(index, SimTale.SIM_NPC_COMPONENT_TYPE) != null;
if (!isPlayer && !isNpc) return;
```

:::danger Event handlers have no query
`SimTaleEventHandler` and `SimTaleUseNPCInteraction` are event handlers, not ticking systems. There
is no query to lean on — the event fires for whatever entity the player interacted with. They depend
**entirely** on the guard, and for a long time they had none: interacting with any entity attached
`SIM_NPC_COMPONENT_TYPE` to it. Since `RoutineAISystem` queries exactly that component, cows and
players alike started running the villager routine.
:::

## Component cloning

`RoutineAIComponent.clone()` is called by the engine when replacing a component. **Every field has
to be copied by hand.**

A field forgotten there does not produce an error; it silently resets. The leash-throttling fields
were once missing, so every component replacement reset navigation and NPCs froze mid-walk.

## Registries in memory

`SimTale.ACTIVE_NPCS` is a fast lookup list, kept so tick loops never scan the world. NPCs register
on spawn and unregister on despawn.

`BedRegistry` and `ChestRegistry` are static sets holding furniture anchors. They are **global, with
no world scope** — two worlds in the same session share them. Rebuilt by the join scan.

## Persistence

Two layers, and confusing them causes bugs:

| Layer | What it stores |
|---|---|
| Native ECS codec on `SimNPCComponent` | Only `EntityId` and `Name` |
| Caskara, shell `simtale` | Everything else: needs, personality, relationships, house |

Anything not in the codec and not in the Caskara record is lost on reload. `Needs` goes through
Caskara — which is why the starvation counter lives there and survives a relog.

:::caution Use the right shell
`Caskara.load()` resolves to the `default` shell. NPC data lives in `simtale`. A re-attach path once
always returned null for exactly this reason and never ran.
:::
