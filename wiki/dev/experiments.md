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
| NPC keeps its own individual look (hair/face/etc.) while costumed | 🔧 | Fixed by generating a per-NPC costume asset instead of using the generic base — see "Investigated: the 'swaps the whole model' limitation" below. Not yet confirmed in a live game. |
| Costume backup survives a server restart | 🐛 | Known limitation, not a bug to "fix" so much as a design gap: `COSTUME_BACKUP_MODEL` is an in-memory `Map`, not persisted. An NPC costumed and then left costumed across a restart would have no backup to restore from if `off` is used afterward. |
| Automatic seasonal trigger (calendar-based, not a manual command) | 🔧 | Built (13/09): `SeasonalCostumeHelper.tick()`, wired into `SimTaleTickSystem`, checks `WorldTimeResource.getGameDateTime()` at most once every 1,200 ticks (~1 real minute) and reconciles every active NPC's costume against the current date. Not yet confirmed running in a live game. |
| Child costume hat fits the head correctly (no exposed/transparent-looking faces) | 🔧 | Preventive fix built (13/09) after investigating an NPC bug report that turned out to be unrelated (a false alarm — see "Fixed (unrelated to the reported bug): child NPC costume hat clipping" below). Still a real, needed fix for when a hat costume is actually used on a child; just not the cause of that specific report. Not yet confirmed in a live game. |
| Slothian / Trork NPC coverage | ⬜ | Only the three human bases (male/female/child) have costume variants so far. |

### Research: automatic calendar trigger (13/09, verified against source)

Next step 3 below ("wire a calendar/date trigger instead of a debug command") turned out to already
have everything it needs in the engine — this is written down so nobody has to re-derive it from
scratch. **Verified by reading `WorldTimeResource.java` directly in the engine source, not just
taken on faith from a web search.**

The engine exposes `com.hypixel.hytale.server.core.modules.time.WorldTimeResource`, and it is a
real Gregorian-style calendar under the hood: `getGameDateTime()` returns a plain
`java.time.LocalDateTime`, ticking forward from `ZERO_YEAR` (`0001-01-01T00:00:00Z`) using the
JVM's own calendar math (leap years, real month lengths, all of it — this is not a custom fantasy
calendar). `DAYS_PER_YEAR` is a fixed `365` (`ChronoUnit.YEARS.getDuration().toDays()`, not
world-configurable). Also available: `getGameTime()` (`Instant`), `getCurrentHour()`,
`getDayProgress()` (0.0–1.0), and `getMoonPhase()`.

**SimTale already reads this resource** — no new access pattern to invent. See
`NPCSleepHelper.java` and `InteractionManager.java`:

```java
WorldTimeResource time = world.getEntityStore().getStore()
        .getResource(WorldTimeResource.getResourceType());
LocalDateTime date = time.getGameDateTime();
```

From there, a costume trigger is just:

```java
if (date.getMonthValue() == 12 && date.getDayOfMonth() == 25) {
    // apply the Christmas costume
}
```

Two gotchas found while verifying, worth knowing before building on this:

- **`getMoonPhase()` is not a named phase.** It returns a plain `int` index from `0` to
  `getTotalMoonPhases() - 1` (that total is configurable per world). There is no "waxing"/"full"
  enum anywhere in this class — mapping the index to a human label is on us if we ever want one.
- **`isYearWithinRange(min, max)` looks like exactly the helper we'd want for "is today inside
  this date range", but its body is commented out behind a `// TODO: Implement` and it
  unconditionally `return false`.** Do not call it expecting it to work — compare
  `getDayOfYear()`/`getMonthValue()`/`getDayOfMonth()` manually instead, as in the snippet above.

This only covers *reading* the date to decide when to fire; it does not by itself solve "don't
reapply every tick" (needs a small per-NPC or per-world "last checked day" cache) or the model-swap
limitation described below.

### Investigated: the "swaps the whole model" limitation (13/09)

This is the real cost of the current approach, and it is worse than it first looked. The costume
assets built above (`SimTale_Human_Male_Christmas.json` etc.) set `"Parent": "SimTale_Human_Male"`
— the **generic** base asset, not the specific NPC's own appearance. Reading the base asset
confirms it carries its own hardcoded look (a `"Morning"` haircut, `"BrownDark"` hair, etc.), and
reading one of the 804 per-NPC `Generated/*.json` files (e.g. `SimTale_Human_Male_95.json`)
confirms *that* file is what actually carries the specific NPC's own 9 attachments (haircut, face,
eyes, pants, shirt, shoes, mouth, ears, eyebrows) — inheriting from the generic base itself. So
today, every costumed NPC of the same gender/age currently renders identically: the base's generic
look plus a hat, not *their* look plus a hat. That is the "swapping the whole model is a pain"
problem in concrete terms — confirmed by reading the actual asset files, not assumed.

**A runtime workaround was investigated and rejected.** `ModelAsset`'s fields are `protected`
rather than `private` — the engine itself builds one ad-hoc instance this way
(`ModelAsset.DEBUG = new ModelAsset() {{ id = "Debug"; model = ...; }}`), and
`Model.createScaledModel(asset, scale, attachments)` takes a `ModelAsset` object directly, not
just an id — so building a synthetic in-memory `ModelAsset` that copies an NPC's real attachments
and adds a hat is technically possible for *rendering*. But `PersistentModel` only ever saves a
plain string id (`ModelReference.toReference()` → `{Id, Scale, RandomAttachments, Static}`), and
`ModelReference.toModel()` resolves that id by calling `ModelAsset.getAssetMap().getAsset(id)` on
every reload — **falling back to `ModelAsset.DEBUG` (a visible placeholder box) if the id isn't
found**. `DefaultAssetMap`'s only write methods (`putAll`, `remove`) are `protected`, with no public
API for a mod to register a new asset at runtime. So an unregistered synthetic asset would render
correctly right up until the next chunk reload or server restart, then break visibly. Not worth
building on.

**The fix that actually works, confirmed against the real files:** point each costume asset's
`Parent` at the specific NPC's own `Generated/*.json` id instead of the generic base. There are
exactly 804 of these today (202 male + 202 female adults, 200 male + 200 female children, counted
directly in `Generated/`), and each already inherits everything else it needs from the generic
base on its own — a costume file only has to add `Parent` + the hat attachment, identically to how
the current 6 files are built. This means:

- A one-time codegen script that lists every id in `Generated/`, and for each one writes
  `<id>_Christmas.json` / `<id>_Halloween.json` next to the existing costume assets, each just
  `{"Parent": "<that id>", "DefaultAttachments": [<the same hat as today>]}`. ~804 × 2 ≈ 1,608 tiny
  files (~150 bytes each, ~240 KB total) — mechanical, no engine changes, no new asset-loading code.
- The command's costume-id logic gets *simpler*, not more complex: `costumeId = currentId + "_" +
  event` directly, with no more branching on gender/child to pick a base — `currentId` already
  comes from `pm.getModelReference().getModelAssetId()` in the existing code.
- Needs to be re-run if the pool of `Generated/*.json` variants ever grows.

**Update (13/09, same session): built.** `scripts/generate_costume_assets.py` was written
(follows the project's existing `scripts/generate_*.py` conventions) and run once. It found **820**
ids in `Generated/` (804 of the expected human male/female/child variants, plus 10 legacy
ungendered `SimTale_Human_Child_N` variants and 10 `Doll_N` chibi-doll models picked up for free —
harmless, since the costume command only ever looks up ids that come from a live NPC's own
`PersistentModel`) and wrote **1,640** files (2 events × 820 ids) into
`Server/Models/Events/Generated/`, each just `{"Parent": "<npc id>", "DefaultAttachments": [<hat>]}`.
The script is idempotent — safe to re-run after new `Generated/` variants are added, it only fills
in what's missing. `CostumeSubCommand` in `SimTaleCommand.java` was updated to match: it now
computes `costumeId = currentId + "_" + suffix` directly, and the old gender/child branching (plus
the `InteractionManager`/`Gender` imports it needed) was deleted since it's no longer used anywhere
in that file. Not yet confirmed running in a live game — see the checklist above.

### Fixed (unrelated to the reported bug): child NPC costume hat clipping (13/09)

Reported with a screenshot: a child NPC's head looked wrong — the side of the head appeared
transparent, and the back looked "badly fitted" (user's words, translated: "provavelmente foi a
UV", i.e. "probably the UV"). **Correction, same day:** the NPC in the screenshot wasn't wearing
a costume at all — it was a perfectly normal NPC, and the odd look was just its red hair color.
False alarm; unrelated to the costume system. While chasing this report, though, a real (separate)
bug was found and is worth keeping fixed:

**Root cause, confirmed by reading the actual files:** every head cosmetic in this project
(haircuts, etc.) that's authored for an adult skeleton has to be run through a node-scaling
transform before it's usable on a child skeleton — see `docs/assets/cosmeticos-node-scales.md` and
`scripts/generate_child_variants.py`'s `NODE_SCALES` table. In short: any node named exactly
`"Head"` (a label meaning "attaches to the head", not a literal skeleton bone) gets a uniform
1.2× scale, and everything nested under it inherits that same scale (confirmed empirically by
diffing a real adult/child haircut pair, `CutePart.blockymodel` vs `CutePart_Child.blockymodel`).
The Christmas/Halloween hat models added earlier this session
(`Cosmetics/Head/SantaHat.blockymodel`, `StrawHat.blockymodel`) were the **one exception** — the
codegen script that builds costume assets (`scripts/generate_costume_assets.py`) used the same
unscaled, adult-proportioned hat for both adult and child variants.

Reading `SantaHat.blockymodel` in full confirms why that's visible as a glitch rather than just
"slightly wrong size": several of its outer nested boxes are missing `textureLayout` entries for
faces that are normally always hidden flush inside the next box (e.g. `bottom`, and one `back`) —
a totally reasonable thing to skip for a properly-nested adult-sized hat. Once the same unscaled hat
is forced onto a smaller child head socket, the nesting no longer lines up, those never-textured
faces become visible, and they render as the transparent/misplaced geometry in the report.

**Fix:** `scripts/generate_child_event_hats.py` (new file) reuses the exact same
`NODE_SCALES`/`FACE_ATTACHMENT_NAMES`/scaling logic as `generate_child_variants.py`, applied to
`SantaHat.blockymodel` and `StrawHat.blockymodel`, producing
`NPC/Player_Child/Cosmetics/Head/SantaHat_Child.blockymodel` and `StrawHat_Child.blockymodel`.
Verified after running: the scale factors on every node match the same 1.2×-compounding pattern
seen in the haircut comparison. The two generic child costume assets
(`SimTale_Human_Child_Christmas.json` / `_Halloween.json`) and all 820 individually-generated child
costume files under `Events/Generated/` were repatched to reference the `_Child` model instead of
the adult one (only `Model` changes — `Texture`/`GradientSet`/`GradientId` stay the same, which
is how every other child cosmetic in this project already works: the scaled `.blockymodel`'s
`textureLayout` still maps to the same texture pixels). `generate_costume_assets.py` itself now
has a separate `EVENTS_CHILD` dict, selected whenever `npc_id` starts with
`"SimTale_Human_Child"`, so re-running it in the future won't regenerate the bug.

**Not yet confirmed visually in game** — the fix was verified by reading/diffing JSON and geometry
(the scale factors match the expected pattern), but nobody has looked at a costumed child NPC in a
live session since the fix. That's the next thing to check — **but again, this fix does not
address the original screenshot report, which was a false alarm** (see the correction above).

**Where the real bug turned out to be (13/09, later still):** the user clarified further — it
was a red-haired boy with short hair, no costume at all. The actual bug was a corrupted node in
`Short_Child.blockymodel` itself (a pivot node merged with its child box, losing the child's own
position offset) — one of 21 child haircut models found with the same class of defect out of 112
checked. See `docs/assets/cosmeticos-node-scales.md` and `testing_checklist.md` for the full
writeup and fix; unrelated to the costume system documented on this page.

### Next steps, if this graduates into a real feature

1. Confirm the risk items above in a live game (asset resolution first — it gates everything else,
   including the newly-generated per-NPC costume ids).
2. Persist `COSTUME_BACKUP_MODEL` (or avoid needing it at all, e.g. by deriving the "off" asset id
   from the NPC's existing gender/child data instead of caching it).
3. Adjust the date windows in `SeasonalCostumeHelper.resolveEvent()` if all of December /
   Oct 25-31 isn't the desired window — it's a two-line `if`, deliberately simple to edit.
4. Extend coverage to Slothian and Trork NPCs.
5. Once confirmed, move this section into [Implementation status](status) and delete it from here.

---

*Source: this page tracks fast, deliberately rough prototypes. Unlike [Implementation status](status),
an item here having no ✅ is the expected default, not a red flag — that's what makes it an
experiment.*
