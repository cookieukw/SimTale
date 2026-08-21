---
sidebar_position: 2
title: Beds and residents
---

# Beds and residents

## Claiming

An NPC without a bed looks for a free one nearby. When it finds one, it walks over, claims it, and
from then on that house is home.

Claiming is exclusive: one bed, one resident. Married couples are the exception — they share a home.

## Beds occupy six blocks

A bed is not one block. It spans **six**, and only one of them is the anchor.

This matters for a reason you can see in game: the sleeping pose is calculated from the anchor. When
the mod used to mount an NPC on one of the other five blocks, she slept crooked, floating beside the
bed, or lying across it like a cross.

The mod now resolves any of the six blocks back to the anchor before putting anyone to bed.

## Who is sleeping where

Use the **Innkeeper's Ledger**.

It opens a screen listing every registered bed with its coordinates and its owner.

The screen also shows the total count, so "no beds registered" is distinguishable from "the list
failed to draw".

## <img src="/img/Br.svg" alt="Br" width="38" align="absmiddle" />eaking a bed {#breaking-a-bed}

Breaking the bed of a sleeping NPC wakes her up cleanly and releases the house. She will look for
another bed.

## Sleep schedule

| Who | Sleeps |
|---|---|
| Everyone else | at night, the whole night |
| Guards | during the day |

An NPC with full energy still goes to bed at nightfall — the clock decides, not exhaustion. She
stays down until morning, so she will not pop out of bed the moment energy fills up.

Exhaustion is still a separate trigger: an NPC that runs out of energy during the day takes a nap
and wakes when rested.

:::tip Skipping the night wakes them
Running `time set day` wakes sleeping NPCs immediately, because waking follows the world clock
rather than a fixed timer.
:::
