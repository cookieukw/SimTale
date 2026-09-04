---
sidebar_position: 4
title: Interacting with NPCs
---

# Interacting with NPCs

Aim at an NPC and press **F**, or right-click. The interaction panel opens.

<div style={{textAlign: 'center'}}>
  <img src="/img/interacting_panel.png" alt="Painel de Interação do NPC" />
</div>

## What the panel shows

| Section | Contents |
|---|---|
| Header | Name, job, mood, and **Life Stage** (`BABY`, `TODDLER`, `CHILD`, `TEEN`, `ADULT`) |
| Needs | Hunger and energy, colour-coded by severity |
| Traits | Personality traits |
| Identity | Job and hobby, as item icons |
| Tastes | Showcase of items they love and hate, as interactive icons |
| Family | Parents, spouses, and children |
| Status | Relationship, friendship, affinity, or **Filial Kinship** (for your children, showing son/daughter bond tiers instead of romantic levels) |

The colors on the hunger line follow the thresholds the routine actually uses: green above 50, yellow below 50, orange below 25, red below 5. At 5, the NPC cries and abandons all tasks.

### Dynamic Camera Framing
When the interaction panel opens, the camera dynamically adjusts its distance, height offset, and pitch based on the NPC's actual bounding box and growth stage scale. This ensures toddlers and children are centered perfectly in frame without pointing awkwardly at the empty air above them.

## Actions

| Button | What it does |
|---|---|
| **Chat** | Conversation. Grants a small boost to Friendship (+5) and Affinity (+5 to +10). If Generative AI is enabled, the NPC will actively write a response back. |
| **Tell joke** | Lands or flops depending on her humour. Fails entirely if enemies. Cheers up sad/angry partners. NPCs with the `FUNNY` trait give a massive +15 affinity boost. |
| **Flirt** | Requires a good baseline relationship (hidden/disabled for children). Unlike a certain life simulator game, you can't just spam this 50 times in a row until they marry you. Guaranteed to fail and drop relationship points if enemies, strangers, or angry. Readily accepted by partners or NPCs with the `SHY` trait. |
| **Give gift** | Hands over whatever you are holding. (See below for gift logic) |
| **Insult** | Costs up to -30 trust and affinity, and she remembers it. (Clementine will remember that). Partners will react very poorly. |
| **Scold** | Specific to your children. Reactions vary by age: Teens become angry, Adults become bored, and Babies/Toddlers become sad. Repeated scolding drops trust and affinity. |
| **Pick Up / Carry** | Allows carrying toddlers or children on your back or shoulders. Release by crouching and right-clicking a block or typing `/simtale putdown`. |
| **Assign job** | Sets her job from the tool you are holding (e.g., holding a hoe assigns Farmer). Protected: young children will refuse to work. |
| **View pregnancy** | Opens the gestation panel |
| **Inventory** | Opens her inventory |

## Gifts

What she thinks of a gift depends, in this order:

1. **Is it on her favourites list?** Big gain (+30 affinity).
2. **Is it on her hated list?** Big loss (-25 affinity).
3. **Is it hobby-related?** Solid gain (+22 affinity) — below an explicit favourite, above anything generic.
4. **Is it junk?** (dirt, sand, stone, cobweb, bones, poison, scrap) Loss (-20 affinity).
5. **Personality**: `GREEDY` values it more (+25 affinity); `PARANOID` reacts badly (-10 affinity).
6. **Anything else**: small polite gain (+15 affinity).

### Food is a special case

If her hunger is at 70 or below and the gift is edible, she eats it right there instead of putting it away. That restores hunger and health, cancels any starvation, and alters her fun based on her tastes.

Above 70 hunger, food goes back to being just a gift.

### The Baby Item

<div style={{textAlign: 'center'}}>
  <img src="/img/baby_care.png" alt="Baby Item" />
</div>

The <img src="/img/Baby.png" width="24" align="absmiddle"/> **Baby** is initially an item. After some time, it will transform and spawn into a child NPC. (Just make sure you don't leave it inside a chest, unless you want a very confused child spawning in your storage!)

## Marriage

To propose to an NPC, you must gift them a **Wedding Ring**. (One Ring to rule them all... wait, wrong franchise).
The proposal will only be accepted if your relationship with them is at least **80 Romance** and **70 Friendship**. If accepted, the NPC will become your spouse.

![Wedding Ring](/img/WeddingRing.png)

## Talking with AI

The mod can route conversation through a generative AI so replies are written on the fly instead of picked from a list. It has to be enabled in the server config — see
[Generative AI](/admin/generative-ai).

:::note Only through the panel
AI replies currently work through the interaction panel. Typing in the normal chat always gives the built-in scripted responses, even with AI enabled.
:::
