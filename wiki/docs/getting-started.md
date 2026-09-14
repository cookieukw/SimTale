---
sidebar_position: 3
title: Getting started
---

# Getting started

The shortest path between "I installed the mod" and "I have a living village".
## 1. Bring in an NPC

You need to invite a resident to start your village. Craft an <img src="/img/ImmigrationContract.png" width="24" align="absmiddle"/> **Immigration Contract** at a Fieldcraft bench using:
- 1x <img src="/img/Deco_Scroll.png" width="24" align="absmiddle"/> Map/Scroll (`Deco_Scroll`)
- 1x <img src="/img/Deco_Inkwell.png" width="24" align="absmiddle"/> Inkwell (`Deco_Inkwell`)
- 1x <img src="/img/Ingredient_Leather_Light.png" width="24" align="absmiddle"/> Light Leather (`Ingredient_Leather_Light`)

Use the contract to spawn a new resident. 

The generated NPC receives one of **800 distinct visual models** and is instantiated with randomized properties for name, personality matrix, behavioral traits, occupation, hobbies, and dietary preferences.

## 2. Build a house

An NPC without a house wanders aimlessly and never sleeps properly. The minimum that counts as a house:

- walls and a roof enclosing the space
- a **door**
- a **light source**
- a **seat**
- a **table**
- a **bed**

To verify your build, point a **House Blueprint** at the bed.

![House Blueprint](/img/HouseBlueprint.png)

The tool tells you whether the structure is valid and what is missing. Details in
[Building a house](houses/building-a-house.md).

## 3. Let her claim the bed

Once the house is ready, the NPC walks to the bed and registers that place as hers. From then on she lives there: she comes back to sleep, eats from that house's chests, and opens the door on her way in.

Use the **Innkeeper's Ledger** to check who lives where.

![Innkeeper Ledger](/img/InnkeepersLedger.png)

## 4. Put out food

Place a chest **inside the house** and leave food in it. NPCs will search for a chest within a 24-block radius when hungry.

:::caution The chest must belong to a house
NPCs only use chests that belong to a recognised house. A chest dropped in an open field is ignored — which is also what keeps them out of the treasure chests scattered around the world.
:::

See what they can actually reach by using the **Quartermaster's Glass**.

![Quartermaster Glass](/img/QuartermastersGlass.png)

It lists every registered chest, the house it belongs to, and how much food is inside.

## 5. Talk to her

Aim at the NPC and press **F**, or right-click. That opens the interaction panel, with hunger, energy, mood, traits, tastes, and the available actions.

<div style={{textAlign: 'center'}}>
  <img src="/img/interacting_panel.png" alt="NPC Interaction Panel" />
</div>

The outcomes of interactions are calculated based on relationship status, mood, and traits:
- **Flirt:** Accepted by partners or shy NPCs; rejected by enemies and angry NPCs.
- **Joke:** Fails on enemies, cheers up sad/angry partners.
- **Gift:** Food given below 70 hunger will be eaten immediately, restoring health and altering fun.

See [Interacting with NPCs](interacting.md).

## 6. Let time pass

The mod is deliberately slow. Starting from 100 hunger, an NPC takes roughly seven in-game hours to get genuinely hungry. At 5 hunger, they begin to starve, crying and dropping all tasks until fed. Fifteen in-game hours without food will not kill them, but they will refuse to work.

---

## Common early problems

| Symptom | Likely cause |
|---|---|
| The NPC does not sleep | The bed is not registered, or there is no valid house. Check with the **Innkeeper's Ledger**. |
| The NPC does not eat | The chest does not belong to a house. Check with the **Quartermaster's Glass**. |
| No NPC appears on its own | NPCs no longer spawn automatically. You must craft and use an Immigration Contract. |
| The house is rejected | Missing furniture or the space is not enclosed. The **House Blueprint** says which. |
