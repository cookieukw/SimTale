---
sidebar_position: 3
title: Generative AI
---

# Generative AI

NPC conversations can be routed through an LLM so replies are written on the fly instead of picked
from a scripted list.

## Enabling

Two switches have to agree:

1. The `enabled` flag in the AI config file.
2. The in-game toggle.

The mod ships a generic HTTP provider, so any API that accepts a chat-style request can be wired up
by configuration.

## What the NPC knows

The prompt is built from the NPC's own state: name, personality, traits, mood, job, tastes, and the
relationship with the player she is talking to.

:::note What is missing from the prompt
Location, health, house, nearby players, time of day and relationships with other NPCs are **not**
in the context yet. NPCs will therefore say things that contradict the world around them — they
cannot know it is night, or that you are standing in their kitchen. Filling these gaps is the top
item on the roadmap.
:::

## Current limitation

:::warning Chat does not use the AI
Only the **interaction panel** routes through the AI. Typing in the normal server chat always
produces the built-in scripted responses, with AI enabled or not — the chat handler never consults
the AI manager.

If you enabled the API and saw nothing change while typing in chat, this is why. Test through the
panel instead.
:::

## Failure behaviour

If the API is unreachable, the NPC falls back to scripted responses rather than hanging or spamming
errors.

## Cost

Every conversation turn is a request. On a busy server this adds up, so the toggle exists to switch
it off without restarting.
