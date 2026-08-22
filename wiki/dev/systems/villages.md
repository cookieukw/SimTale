---
sidebar_position: 6
title: Villages
---

# Villages

A village is not an object. It is a fact recomputed from the houses that exist right now.

Nothing about a village is persisted and nothing is placed by the player. `VillageManager` reads
`HouseManager.HOUSES_BY_ID`, groups the houses, and produces a centre and a radius. Build a house
next to the others and the village grows to include it. Break the beds and the houses cease to
exist, so the village shrinks and eventually stops being one.

:::info Different from other games on purpose
In other block-building games, the village centre is often an object that outlives the village. Level every building and the
game still treats the ruins as a village. Here the village is worked out from the houses that exist
at that moment, so there is nothing left behind to be wrong.
:::

## Grouping

Single linkage. Two houses belong to the same village when their beds are within **40 blocks** of
each other on the XZ plane, and the relation chains: A near B and B near C puts all three together
even when A and C are 80 blocks apart.

That matches how people build — outward from what is already there — and it is why the boundary is
not a circle drawn in advance. A row of houses 30 blocks apart is one village however long it gets.

| Property | Value |
|---|---|
| Centre | mean of the member beds |
| Radius | distance to the furthest member, plus a 16-block margin |
| Membership | single-linkage, 40-block threshold |

The margin exists so a one-house village has somewhere to walk. Without it the radius would be
zero and the resident would be pinned to the doorstep.

## When it is recomputed

`HouseManager` calls `VillageManager.markDirty()` on register, delete and load. The clustering
itself runs on the next query, not inside the mutation.

That indirection is deliberate: claiming a bed registers a house, and that happens inside an NPC's
tick. Recomputing there would charge the cost to whichever NPC happened to move in.

The algorithm is O(n²) over houses, which is fine — houses number in the dozens and this runs only
when they change.

## What uses it

### Homeless NPCs

The idle stroll anchors, in order of preference: the NPC's own bed, then the nearest village
centre, then its current position.

That last fallback was a real bug. Anchoring on "where I am" is not an anchor: each stroll moves
the NPC, the next one anchors on the new spot, and the result is a random walk with no restoring
force. Given time it drifts arbitrarily far, which is why homeless NPCs used to have to be fetched
back by hand.

Homeless NPCs roam the **whole** village radius rather than a private patch, so they spread out
instead of piling onto the centre tile.

The leash is soft. Work, hobbies and food searches still take an NPC outside the village — it only
stops it from wandering off for no reason.

### Guards

A guard with nothing to fight walks the perimeter. `RoutineAIComponent.patrolAngle` advances by
π/6 each leg, so twelve stops complete a circuit, and the starting angle is taken from wherever the
guard already stands so it does not march across town to reach an arbitrary first stop.

The perimeter is where threats arrive from, so patrolling it puts the guard's scan radius over the
frontier instead of overlapping every other guard in the middle of town.

A guard with no village stays put. Patrolling nothing is what the previous behaviour amounted to.

Patrol reuses `WANDERING` rather than adding a task type: from the routine's point of view it *is*
a walk to a point that ends by returning to `IDLE`, and that state already carries the timeout and
give-up handling. What makes it a patrol is that the destination advances by a fixed angle instead
of being drawn at random.

## Houses have to be able to die

Villages could only be honest once houses could stop existing, and until this system landed nothing
deleted one. `deleteHouse` was reachable only from the duplicate sweep, so a house record outlived
its own demolition: bed gone, walls gone, registry still calling it a home.

`HouseManager.deleteHouseByBed` closes that. A house is identified by its bed, so breaking the bed
ends the house — and the residents lose `bedLocation` at the same moment, otherwise they would keep
walking to a bed that is not there every night.

## Inspecting it

`/simtale village` lists every village with its centre, radius and house count, and says whether
you are standing inside each one.

Since villages are derived and never written down, this command is the only way to see them. An NPC
anchoring its walk and a guard patrolling an edge look the same whether the village is what you
intended or an artefact of two houses that happen to sit 39 blocks apart.

## Related

- [Houses](houses.md) — how a building becomes a valid house in the first place
- [Routine AI](routine-ai.md) — where the wander anchor is chosen
