---
sidebar_position: 5
title: Family and growth
---

# Family and growth

## Pregnancy

A married NPC can become pregnant. Pregnancy runs through three trimesters, and movement speed drops
gradually as it advances.

| Trimester | Symptoms |
|---|---|
| 1st | Slightly increased hunger and energy drain |
| 2nd | Increased drain, moderate slowness |
| 3rd | Intense drain, severe slowness |

Open the **View Pregnancy** panel from the interaction screen to follow the progress: current day,
percentage, and estimated time remaining in real minutes.

Player pregnancy also exists and follows its own path.

NPCs can also marry each other now, entirely on their own — see [Relationships](./relationships)
for how a courtship turns into a wedding without any player involved. Once married, an NPC couple
goes through this exact same pregnancy, birth, and growth path.

:::note Not every NPC wants a big family
Each NPC quietly decides, the first time it matters, how many children they personally want —
anywhere from zero to four — and that number sticks for good. When two NPCs are married to each
other, a natural pregnancy only happens while both of them still want another child; if one is done
having kids and the other isn't, that couple simply stops there. A player's own marriage isn't
affected by this at all — it works exactly as it always has.
:::

## Birth

At the end of gestation the baby is born as an **item** that goes into the inventory. You carry the
baby around, and can hand it to the other parent.

![Baby Item](/img/Baby.png)

## Growth stages

| Stage | Notes |
|---|---|
| `BABY` | Carried as an item in inventory or placed in care |
| `TODDLER` | Walks around, plays, inherits parents' bed for co-sleeping |
| `CHILD` | Model scaled down, explores, co-sleeps with parents or uses own bed, participates in hobbies |
| `TEEN` | Model slightly reduced, can take on formal jobs |
| `ADULT` | Full adult routine: careers, house claiming, independent relationships |

Children grow over time on their own, with their model scaling up smoothly across stages.

## Family Bonds & Co-Sleeping

Through the `FamilyBonds` system, NPCs retain hereditary connections with their biological parents and siblings:

- **Bed Sharing / Co-Sleeping**: Toddlers and Children do not require separate houses or beds to stay rested. When their bedtime arrives (`FINDING_BED`), they locate their parents' registered bed (`FamilyBonds.findParentBed`). If the parent is already sleeping, the child co-sleeps in the same bed without displacing the parent.
- **Sleep Protection**: A growing child inside a bed remains safe during lifecycle ticks without getting frozen or kicked out of routine animations.
- **Parental Recognition**: Interacting with your child opens an interface customized for filial bonds, displaying their life stage and parental relationship title.
- **What they call you**: A child addresses their mother as "mom" (or the warmer "mommy") and their father as "dad" (or "daddy"), picked fresh from how well the two of you are getting along right now — not decided once and locked in. Let the bond sour badly enough and the title disappears too: the child just uses your name instead. (So far this shows up in a couple of young child dialogue lines.)

## Work Eligibility & Hobbies

SimTale implements child protection rules:
- **Formal Job Immunity**: `BABY`, `TODDLER`, and `CHILD` stages cannot be assigned adult jobs. Trying to assign a profession with a tool will prompt a gentle refusal ("I'm just a kid!").
- **Hobbies & Assistance**: Children can still enjoy leisure activities like fishing or gardening when their fun need drops, allowing them to hang out near family crop plots or water spots without taking on economic burdens.

## Care

Babies need care. Passing the baby back and forth between parents shares the load, and there is an
offline simulation so time away from the server still counts. You can pick up and carry toddlers or children when needed. You are not limited to carrying one at a time, either — up to ten children can be stacked on your shoulders at once.

![Carrying several children at once](/img/child_carry_stack.png)

## Death

When an NPC dies, the SimTale death flow takes over: the body stays, and starts bleeding visually. The Grim Reaper appears on its
own, walks to it, performs the soul-collection ritual, leaves a gravestone and removes the record
cleanly. Interacting with the Reaper mid-ritual while holding an <img src="/img/Ingredient_Voidheart.png" width="20" style={{verticalAlign: "middle"}} /> `Ingredient_Voidheart` cancels the
collection and revives the NPC.

![NPC bleeding after death](/img/npc_bleeding.png)

![The Grim Reaper](/img/grim_reaper.png)

![Grim Reaper Ceremony](/img/reaper_ceremony.png)

:::note Nothing kills an NPC yet
Hunger deliberately does not kill. Starving NPCs simply cry and stop working. Aging and disease are not implemented yet.
The death flow is currently only reachable by administrative testing commands, which exists so the Reaper can be tested without waiting for a cause of death that does not exist.
:::
