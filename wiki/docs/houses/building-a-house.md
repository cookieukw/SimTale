---
sidebar_position: 1
title: Building a house
---

# Building a house

A house is not just any build. The mod validates the structure before accepting it, and an NPC only
moves into an approved one.

## The requirements

### Structure

The space must be **enclosed**: walls and a roof with no gap for the check to leak through. Doors
count as closed wall.

The interior is capped at **512 blocks**. Past that the check gives up and rejects the house — the
cap exists so an open cave is not mistaken for a mansion.

### Mandatory furniture

| Requirement | Any block whose id contains |
|---|---|
| **Light source** | `torch`, `lantern`, `candle`, `campfire`, `glow`, `lamp`, `chandelier` |
| **Seating** | `chair`, `stool`, `bench`, `seat`, `sofa`, `couch` |
| **Surface** | `table`, `workbench`, `desk`, `counter` |

### Optional, but you will want it

| Item | Why |
|---|---|
| **Bed** | Without one, nobody lives there. The house is identified *by its bed*. |
| **Chest** | Without one, residents have nowhere to get food. |

## Checking

Point a **House Blueprint** at a registered bed.

![House Blueprint](/img/HouseBlueprint.png)

The tool will tell you whether the structure passed and, when it did not, **what is missing**. It also
reports how many interior blocks were visited, and how many doors and chests were found.

:::tip Unloaded chunks get in the way
If part of the house sits in an unloaded chunk, the check flags the result as incomplete rather
than rejecting it. Stand near the house when checking.
:::

## The house is identified by its bed

This is the most important detail and the easiest to trip over: **the house's identity comes from
the bed**.

What that means in practice:

- Two beds in the same room can become two houses.
- Breaking the resident's bed releases the house.
- Moving the bed can read as a different house.

It is a known limitation, and turning it into an identifier of its own is on the roadmap.

## Doors

A door occupies four blocks, and two doors side by side form a double door. NPCs open and close
them as they pass.

## Next

[Beds and residents](beds-and-residents.md) — how an NPC claims a bed and what happens when two of
them want the same one.
