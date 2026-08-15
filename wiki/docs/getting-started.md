---
sidebar_position: 3
title: Getting started
---

# Getting started

The shortest path between "I installed the mod" and "I have a living village".

## 1. Bring in an NPC

In survival mode, you need to invite a resident. Craft an **Immigration Contract** at a Fieldcraft bench using:
- 1x Map/Scroll (`Deco_Scroll`)
- 1x Inkwell (`Deco_Inkwell`)
- 1x Light Leather (`Ingredient_Leather_Light`)

Use the contract to spawn a new resident. 

Alternatively, if you are testing or have admin privileges, you can spawn one via command:
```
/simtale spawn HUMAN_FEMALE
```
Available types: `HUMAN_MALE`, `HUMAN_FEMALE`, `CHILD_MALE`, `CHILD_FEMALE`.

She is born with a name, personality, traits, a job, a hobby, favourite foods and hated foods — all
rolled at random. No two NPCs are alike.

## 2. Build a house

An NPC without a house wanders aimlessly and never sleeps properly. The minimum that counts as a
house:

- walls and a roof enclosing the space
- a **door**
- a **light source**
- a **seat**
- a **table**
- a **bed**

To verify your build, point a **House Blueprint** at the bed.

The tool tells you whether the structure is valid and what is missing. Details in
[Building a house](houses/building-a-house.md).

## 3. Let her claim the bed

Once the house is ready, the NPC walks to the bed and registers that place as hers. From then on
she lives there: she comes back to sleep, eats from that house's chests, and opens the door on her
way in.

Use the **Innkeeper's Ledger** to check who lives where.

## 4. Put out food

Place a chest **inside the house** and leave food in it.

:::caution The chest must belong to a house
NPCs only use chests that belong to a recognised house. A chest dropped in an open field is
ignored — which is also what keeps them out of the treasure chests scattered around the world.
:::

See what they can actually reach by using the **Quartermaster's Glass**.

It lists every registered chest, the house it belongs to, and how much food is inside.

## 5. Talk to her

Aim at the NPC and press **F**, or right-click. That opens the interaction panel, with hunger,
energy, mood, traits, tastes and the available actions: chat, tell a joke, flirt, give a gift,
insult, assign a job.

See [Interacting with NPCs](interacting.md).

## 6. Let time pass

The mod is deliberately slow. Starting from 100 hunger, an NPC takes roughly seven in-game hours to
get genuinely hungry, and fifteen to die if nobody feeds her. It is not meant to be watched — it is
meant to let the village change on its own while you do something else.

---

## Common early problems

| Symptom | Likely cause |
|---|---|
| The NPC does not sleep | The bed is not registered, or there is no valid house. Check with the **Innkeeper's Ledger**. |
| The NPC does not eat | The chest does not belong to a house. Check with the **Quartermaster's Glass**. |
| No NPC appears on its own | NPCs no longer spawn automatically. You must craft and use an Immigration Contract. |
| The house is rejected | Missing furniture or the space is not enclosed. The **House Blueprint** says which. |
