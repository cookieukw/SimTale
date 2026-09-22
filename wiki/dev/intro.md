---
sidebar_position: 1
title: Contributing to SimTale
---

# Contributing to SimTale

This track is for whoever works on the code.

## The shape of the project

SimTale is a Hytale server mod built on the engine's **ECS**. There is no central game loop of our
own: behaviour lives in systems that tick over entities carrying our components.

```text
com.cookieukw.SimTale
├── SimTale.java              entry point, system registration, ACTIVE_NPCS
├── *Command.java             CLI entry points (SimTaleCommand, DebugCommands...)
├── core/                     components and data (SimNPCComponent, NeedsHelper, Profession, lifecycle...)
├── systems/                  ticking systems and helpers
├── logic/                    UI pages and interaction rules
├── ai/                       generative AI providers and config
├── engine/                   magic engine, quizzes and core systems
├── pages/                    UI pages testing (TestPage)
└── db/                       Caskara persistence
```

## Where to start reading

1. [ECS architecture](ecs-architecture.md) — how components, systems and stores fit together
2. [Build environment](build-environment.md) — building, deploying and what you cannot verify locally
3. [Lessons learned](lessons-learned.md) — **read this one before writing code**

## Why lessons learned comes early

This project has a recurring failure mode: writing code against an API that looks plausible but does
not exist. It has cost real time on at least four occasions — an asset registered with the wrong
type, 34 model paths missing a prefix, a UI component that does not exist, and item ids from the
wrong namespace.

The page documents the pattern and the habit that prevents it. It is the highest-value thing in
this track.

## House rules

**Comments in English, and only when necessary.** Explain *why*, not *what* — the code already says
what. Older files still carry Portuguese comments; leave them unless you are already editing that
line.

**Verify before you write.** Every engine API, asset id and component name gets checked against the
jar or the game assets first.

**No data migration is expected.** The mod is in testing with no public users. Breaking the save
format is acceptable right now, and that will change the day there is a release.
