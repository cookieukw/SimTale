---
sidebar_position: 4
title: Relationships
---

# Relationships

Every NPC keeps a separate relationship with each player and with other NPCs.

## The numbers

| Value | Meaning |
|---|---|
| **Friendship** | General closeness |
| **Romance** | Romantic interest |
| **Trust** | Willingness to accept requests |
| **Affinity** | Short-term reaction to your last actions |

## Status ladder

`UNKNOWN` → `STRANGER` → `ACQUAINTANCE` → `FRIEND` → `GOOD_FRIEND` → `BEST_FRIEND`

Romantic branch: `DATING` → `ENGAGED` → `MARRIED`.
Negative branch: `RIVAL` and `ENEMY`.
Filial branch (Children): `SON` / `DAUGHTER` (tracked via `FamilyBonds` with dedicated parental affection tiers instead of peer friendship or romantic progression).

Status changes what she says to you. The same greeting has different wording for a stranger and for
a spouse.

## Raising it

| Action | Effect |
|---|---|
| Chat | Small, reliable gain |
| Tell a joke | Depends on her sense of humour |
| Flirt | Romance, if she is receptive |
| Give a favourite gift | Large gain |
| Feed her when hungry | Large gain — bigger than an ordinary gift |
| Insult | Loss, and she remembers |

## She remembers

NPCs keep a memory of events. Insulting one has an effect that lasts beyond the moment: for a while
afterwards she greets you differently.

## NPC to NPC

NPCs talk to each other on their own when their social need drops. A conversation raises both sides'
social need and builds friendship between them, and that friendship survives a server restart.

Mood spreads through these conversations. An `AGGRESSIVE` NPC, or two who are already enemies, turn
the conversation into an argument instead: both walk away in a worse mood and like each other less.

A pleasant chat between two adult NPCs also nudges a little romance between them, on top of the
friendship it already builds. Once that romance and friendship both reach the same bar a player's
own wedding ring proposal needs (**80 Romance**, **70 Friendship**, on both sides), the two NPCs
marry each other — no ring, no player involved. A short line gets broadcast to nearby players when
it happens.

This never happens between minors, between close family (parents and children, or siblings), or
with anyone already married to someone else — SimTale does not model affairs.

:::note Nobody gets dragged out of bed
An NPC who is asleep or working is never picked as a conversation partner. And if energy runs out
mid-conversation, she abandons the chat and goes to bed — the partner left behind does not freeze.
:::

## Marriage

Give a wedding ring (`WeddingRing`) to an NPC with high romance and friendship and she
accepts. Married NPCs share a home.

If the numbers are not high enough, she turns you down.
