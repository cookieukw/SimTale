---
sidebar_position: 7
title: Experiments
---

# Experiments

This page is different from [Implementation status](status): that page tracks features that are
part of the mod's actual design. This one tracks **prototypes** — things built fast, usually behind
a debug command, to answer a "is this even possible?" question before committing to a real design.
Nothing here should be assumed stable, and nothing here has necessarily been seen running in a live
game yet. When a prototype graduates into a real feature with its own tuning and UI, it moves out of
this page and into [Implementation status](status).

## Seasonal NPC costumes

**Question:** can an NPC's appearance be swapped for a seasonal outfit (Christmas, Halloween,
birthdays, ...) without redesigning the whole model/cosmetic system?

**Answer: yes.** Hytale's own shipped assets already do exactly this for other creatures — see
`Server/Models/Christmas/Trork_Christmas.json` and the Kweebec Sapling Christmas variants in the
base game. A `ModelAsset` can declare a `Parent` (inheriting that asset's full attachment list) and
its own `DefaultAttachments`, which **add to** the parent's list rather than replacing it. That is
enough to bolt a hat onto an existing NPC model with no engine changes and no new attachment API —
just a new JSON file per costumed variant.

SimTale already had the runtime piece needed to use this: `SimNPCFactory.applyModel(store, ref,
modelAssetId, scale, attachments)`, the same method already used to turn a dying NPC into the Grim
Reaper's `Necromancer_Void` model. Swapping a costume in is just calling it again with a different
asset id, and swapping it back is calling it a third time with the original id.

### What was built

- Six new `ModelAsset` files under `src/main/resources/Server/Models/Events/`, one per
  base/event combination: `SimTale_Human_Male_Christmas.json`, `SimTale_Human_Female_Christmas.json`,
  `SimTale_Human_Child_Christmas.json`, and the same three for `_Halloween`. Each has `Parent` set to
  the matching base (`SimTale_Human_Male`/`Female`/`Child`) and a single extra attachment: a Santa
  hat (`Colored_Cotton` / `Red`, the exact combination copied from `Trork_Christmas.json`) for
  Christmas, a straw/witch hat texture for Halloween.
- A debug command, `/simtale costume <christmas|halloween|off>`, added to `SimTaleCommand.java`.
  It finds the nearest NPC to the player, remembers that NPC's current model id in an in-memory map
  (`COSTUME_BACKUP_MODEL`), and calls `SimNPCFactory.applyModel` with the costumed asset id (picking
  the male/female/child base automatically via `InteractionManager.isNpcAChild` and the NPC's
  `Gender`). `off` looks up the backup and restores it.

This was built quickly, under time pressure, to have *something* runnable before the session ended,
so it leans entirely on values already proven by the base game's own assets rather than anything
newly invented — the goal was a working sketch, not a finished feature.

### What's confirmed vs. what still needs a play session

| | |
|---|---|
| ✅ | Confirmed working in a live game |
| 🔧 | Implemented, believed correct, **not yet confirmed in-game** |
| 🐛 | Known issue / limitation, still open |
| ⬜ | Not implemented |

| Item | Status | Notes |
|---|---|---|
| Feasibility (can a costume be added at all without engine changes) | ✅ | Confirmed by reading the base game's own shipped assets — not a guess. |
| `/simtale costume` command registers and runs without crashing | 🔧 | Never run in-game — built in the same session it's being documented in. |
| Asset id resolves correctly from the `Events/` subfolder | 🔧 | Every reference asset (`Trork_Christmas.json` etc.) lives directly under `Server/Models/<Category>/`; whether `ModelAsset.getAssetMap()` keys purely by basename or cares about the subfolder path has not been checked against the loader code. **This is the single biggest risk in the whole prototype** — if it fails, the fix is likely just renaming/relocating the files, not a logic change. |
| Santa hat renders in a sane position on a human NPC | 🔧 | Only ever confirmed on the Trork and Kweebec Sapling models in the base game. SimTale's human rig may attach differently. |
| Witch hat renders correctly (no `GradientSet`/`GradientId` set) | 🔧 | Slightly less verified than the Christmas hat — a recursive asset search for a confirmed human-compatible witch hat timed out, so this uses a texture-only attachment on the assumption (seen elsewhere in SimTale's own base JSONs) that a gradient isn't required. |
| `christmas` → `off` round trip restores the original appearance exactly | 🔧 | Logic looks right (`putIfAbsent` + `remove` on the backup map) but never run. |
| Works correctly on child NPCs | 🔧 | `SimTale_Human_Child_*` variants exist and route through `InteractionManager.isNpcAChild`, but children use a single non-gendered base — not cross-checked against every existing child model variant. |
| Costume backup survives a server restart | 🐛 | Known limitation, not a bug to "fix" so much as a design gap: `COSTUME_BACKUP_MODEL` is an in-memory `Map`, not persisted. An NPC costumed and then left costumed across a restart would have no backup to restore from if `off` is used afterward. |
| Automatic seasonal trigger (calendar-based, not a manual command) | ⬜ | Not started — this prototype is manual-only by design, to test the mechanism first. |
| Slothian / Trork NPC coverage | ⬜ | Only the three human bases (male/female/child) have costume variants so far. |

### Next steps, if this graduates into a real feature

1. Confirm the risk items above in a live game (asset resolution first — it gates everything else).
2. Persist `COSTUME_BACKUP_MODEL` (or avoid needing it at all, e.g. by deriving the "off" asset id
   from the NPC's existing gender/child data instead of caching it).
3. Wire a calendar/date trigger instead of a debug command.
4. Extend coverage to Slothian and Trork NPCs.
5. Once confirmed, move this section into [Implementation status](status) and delete it from here.

---

*Source: this page tracks fast, deliberately rough prototypes. Unlike [Implementation status](status),
an item here having no ✅ is the expected default, not a red flag — that's what makes it an
experiment.*
