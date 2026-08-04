---
sidebar_position: 4
title: Interacting with NPCs
---

# Interacting with NPCs

Aim at an NPC and press **F**, or right-click. The interaction panel opens.

## What the panel shows

| Section | Contents |
|---|---|
| Header | Name, job, mood |
| Needs | Hunger and energy, colour-coded by severity |
| Traits | Personality traits |
| Identity | Job and hobby, as item icons |
| Tastes | Everything she likes and everything she hates, as icons |
| Family | Parents and children |
| Status | Relationship, friendship and affinity percentages |

The colours on the hunger line follow the thresholds the routine actually uses: green above 50,
yellow below 50, orange below 25, red below 5.

## Actions

| Button | What it does |
|---|---|
| **Chat** | Conversation. Wording depends on relationship, mood and personality |
| **Tell joke** | Lands or flops depending on her humour |
| **Flirt** | Raises romance if she is receptive |
| **Give gift** | Hands over whatever you are holding |
| **Insult** | Costs affinity, and she remembers |
| **Assign job** | Sets her job from the tool you are holding |
| **View pregnancy** | Opens the gestation panel |
| **Inventory** | Opens her inventory |

## Gifts

What she thinks of a gift depends, in this order:

1. **Is it on her favourites list?** Big gain.
2. **Is it on her hated list?** Big loss.
3. **Is it hobby-related?** Solid gain — below an explicit favourite, above anything generic.
4. **Is it junk?** (dirt, stone, bones, poison, scrap) Loss.
5. **Personality**: `GREEDY` values it more; `PARANOID` reacts badly.
6. **Anything else**: small polite gain.

### Food is a special case

If her hunger is at 50 or below and the gift is edible, she eats it right there instead of putting
it away. That restores hunger and health, cancels any starvation, and is worth considerably more
goodwill than an ordinary gift.

Above 50 hunger, food goes back to being just a gift.

## Talking with AI

The mod can route conversation through a generative AI so replies are written on the fly instead of
picked from a list. It has to be enabled in the server config — see
[Generative AI](/admin/generative-ai).

:::note Only through the panel
AI replies currently work through the interaction panel. Typing in the normal chat always gives the
built-in scripted responses, even with AI enabled.
:::
