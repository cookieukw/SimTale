---
sidebar_position: 1
title: Running a SimTale server
---

# Running a SimTale server

This track is for whoever operates the server: commands, configuration, tuning and diagnosing
problems from the log.

## What you should know up front

**The mod is in testing.** There is no public release, and there is no migration path between
versions. Treat worlds as disposable for now.

**Everything is persisted through Caskara**, in a shell named `simtale`. NPC data, houses and
relationships live there. Removing the mod does not delete it.

**Two registries live in memory only**: beds and chests. They are rebuilt when a player joins a
world, by a scan with a 32-block radius around the join point. Furniture far from where players
enter only registers once someone gets close.

## Quick health check

After starting the server and joining a world, look for these lines:

```
[SimTale] Scan found N new beds and M new chests. Totals: ...
```

The furniture scan worked. Only appears when it finds something new.

```
N registros de casa duplicados removidos
```

House deduplication on boot. Should appear once and never again.

Then run `/simtale debug on` and confirm NPCs are ticking. **Turn it off afterwards** — the debug
logs are very noisy.

## Pages

- [Commands](commands.md) — the full list
- [Generative AI](generative-ai.md) — hooking up an LLM for conversations
- [Balancing](balancing.md) — the constants that control hunger, sleep and death
- [Troubleshooting](troubleshooting.md) — symptoms, causes and fixes
