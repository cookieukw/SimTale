---
sidebar_position: 6
title: Implementation status
---

# Implementation status

A running scorecard of what exists, what has actually been confirmed in a live game, and what
still needs a play session before anyone should rely on it. Condensed from the project's internal
testing log — the log itself is a line-by-line debug diary and is not kept in the wiki, but every
status below traces back to it or to a code-level review.

**Read the status, not just the checkmark.** ✅ means someone watched it happen in-game. 🔧 means
the code is believed correct (often because a specific root cause was found and fixed) but nobody
has confirmed it since. 🐛 marks a known, currently-open issue. ⬜ means the feature has no
behavior behind it yet — the item/asset may exist, the logic does not.

| | |
|---|---|
| ✅ | Confirmed working in a live game |
| 🔧 | Implemented, believed correct, **not yet confirmed in-game** |
| 🐛 | Known issue, still open |
| ⬜ | Not implemented |

---

## Foundations

| Area | Status | Notes |
|---|---|---|
| Atomic mod deploy | ✅ | Fixed the `ZipException: invalid LOC header` crash from the game hot-reloading a half-written jar. |
| Persistence across restarts | ✅ | NPCs keep name, needs, home and relationships. |
| Furniture scan on world join | ✅ | Beds and chests already in a house register themselves without being replaced. |
| Population growth | ✅ | No background auto-spawner; growth is a deliberate player action via `ImmigrationContract`. |

## Houses, beds & doors

| Area | Status | Notes |
|---|---|---|
| House validity check, bed/chest registration | ✅ | `/simtale housecheck`, `debugbeds`, `debugchests` all confirmed. |
| Bed assignment & shared beds for a second NPC | ✅ | No NPC has stolen another's bed in observed play. |
| Doors: open only when actually passing through | ✅ | |
| Doors: one registry entry per door, not per block | ✅ | |
| Doors: multi-door wall doesn't open every door at once | ✅ | |
| Doors: pathfinding actually routes through them | ✅ | |
| Doors: open/close race when two NPCs use the same door | 🐛 | Noticeably better after switching to a geometric "which side is the NPC headed to" check, but the close-while-opening conflict still reproduces. |
| House identified by its bed | 🐛 | Moving the bed may create a second house record. Not urgent, not yet fixed. |

## Sleep

| Area | Status | Notes |
|---|---|---|
| Sleep on a day/night schedule (not just tiredness) | ✅ | Two real regressions found and fixed this pass — see the dev log if you need the detail. |
| Sleeps through the whole night | ✅ | |
| Guard works nights, sleeps days | ⬜ | Not implemented — Guards currently share the normal day schedule. |
| Base sleep behavior (walk to bed, animation, `Frozen`, waking up) | ✅ | |

## Hunger & feeding

| Area | Status | Notes |
|---|---|---|
| World/loot chests ignored, only house chests used | ✅ | |
| Farmer deposits into house chests under the same rule | ✅ | |
| Favorite/hated food affects mood | ✅ | |
| Being handed food while hungry (eats immediately vs. banks it as a gift) | ✅ | |
| Tier-based food choice (prepared food beats raw meat even if the raw is a favorite) | 🔧 | Implemented, never confirmed in-game. |
| Tie-break by taste when tiers match | 🔧 | Implemented, never confirmed. |
| Interrupts a task when hunger drops critically low | 🔧 | Implemented, never confirmed. |
| Gives up on an unreachable chest instead of looping | 🔧 | Implemented, never confirmed — this exact shape of bug once produced thousands of searches/second over an unreachable bed. |
| Eating heals HP by food tier | 🔧 | Implemented, never confirmed. |

## Jobs

| Area | Status | Notes |
|---|---|---|
| Farmer (plant, harvest, deposit, auto-replant, seed restock) | ✅ | Long debugging session, fully resolved and confirmed in-game. |
| Hunter / Miner expedition (NPC leaves, returns with a resource) | ✅ | Redesigned away from following a real animal/ore node, for safety. |
| Fisherman / Lumberjack | ✅ | Reviewed against the farmer's already-validated design; no structural bug found. |
| Guard: detects hostiles, patrols the village perimeter when idle | ✅ | |
| Guard: weapon category (melee vs. ranged) changes engagement distance & animation | 🔧 | Implemented this session, compiles and builds clean; not yet confirmed in-game. See [Guard combat](#guard-combat) below. |
| Profession assignment false positives (maple wood → Explorer, rainbow trout → Hunter, hammerhead shark → Builder) | ✅ | Fixed by using more specific keywords instead of loose substrings. Verified by cross-referencing the game's real item list. |

### Guard combat

The Guard's "combat" is still fundamentally decorative: it walks into range, plays a stand-in
animation, waits a fixed 3 seconds, and deletes the hostile — regardless of what weapon it holds.
Weapon category (this session's addition) changes *distance and animation* correctly, but the
outcome is still the same timer. Turning that into real damage pulled from the weapon and, for
ranged Guards, an actual projectile instead of a timer is tracked as its own follow-up piece of
work, not yet started.

## Socializing, wandering & hobbies

| Area | Status | Notes |
|---|---|---|
| Spontaneous NPC-to-NPC conversation | 🐛 | Never observed in play. The trigger (low `social` + proximity) needs investigating — it may simply not be firing. |
| NPC↔NPC friendship persists across restart | 🔧 | Implemented, never confirmed. |
| Mood contagion between NPCs in conversation | 🔧 | Implemented, never confirmed. |
| Arguments (aggressive trait / enemies) hurt mood and friendship | 🔧 | Implemented, never confirmed. |
| Sleep pre-empts a conversation without stranding the other NPC | 🔧 | Implemented, never confirmed. |
| Anchored wandering (never drifts away from home indefinitely) | ✅ | |
| Hobby trip (fishing/mining/gardening/reading) and its `fun` payoff | 🔧 | User describes it as "seems to work" — not confirmed rigorously enough to close out. |
| Hobby fallback when the scenario doesn't exist nearby (e.g. fishing hobby in a desert) | 🔧 | Implemented, never confirmed. |
| Shared hobby boosts friendship gain in conversation | ✅ | Verified in code (`NPCSocialHelper.bondNpcs`), real call site confirmed. |
| Gift matching a hobby (e.g. fishing rod → NPC who fishes) | ✅ | |

## Relationships, marriage & family

| Area | Status | Notes |
|---|---|---|
| Social interaction panel, marriage, pregnancy, birth | ✅ | Birth had a real bug (baby persisted but never synced to the mother's inventory until a restart) — fixed and confirmed. |
| Wedding ring as a gift/proposal | ✅ | Id matching had to be normalized (four different casings/prefixes existed for the same file); fixed. |
| Passing a baby between parents | ✅ | |
| Player pregnancy path (`PlayerPregnancyTickSystem`) | ❓ | Separate code path from NPC pregnancy, not exercised at all yet. |

## Growth

| Area | Status | Notes |
|---|---|---|
| `/simtale setstage` (force a growth stage) | ✅ | |
| Parent/child recognize each other at birth (`FamilyBonds`) | ✅ | |
| Child cosmetic models | ✅ | 34 broken model paths fixed (missing path prefix). |
| Placing a held baby on the ground | ✅ | Root cause was two stacked bugs: an item interaction override swallowing the click, and one event listener registered with the wrong method (`.register` instead of `.registerGlobal`) — the only one in the project using the wrong call. `/simtale forceplacebaby` exists as a guaranteed fallback either way. |
| Automatic stage transitions over time (`GrowthTickSystem` actually ticking) | ❓ | Never watched through a full cycle. |
| Full baby → adult cycle observed once, start to finish | ❓ | Not done yet. |

## Death & the Grim Reaper

| Area | Status | Notes |
|---|---|---|
| Starvation never kills (by design — aging/illness are the intended causes of death, not implemented yet) | ✅ | |
| Death broadcast doesn't repeat forever | ✅ | |
| Grim Reaper spawns automatically near a body and completes the ritual | ✅ | Redesigned this pass: used to require a manual admin spawn and could get permanently stuck if none was available at the exact tick. |
| Pleading for your life with the right item cancels the reaping | 🔧 | Brand new, never tested in-game. |
| Graveyard record (dead NPC archived instead of deleted) | 🔧 | The record exists and is written; nothing yet reads it back except the revival panel below, which is itself untested end-to-end. |
| House/bed freed after death | ❓ | Not tested. |
| What happens to a surviving spouse/children | ❓ | Probably nothing yet — tracked as a future improvement, not a bug. |

### Graveyard & resurrection

Entirely new this pass and **not yet exercised at all in a live game** — every single item below is
open:

listing the graveyard panel, reviving (including the UUID remap this needs, since the engine won't
let a new entity reuse an old UUID), marriage/children/friendships surviving the remap, reviving
someone whose spouse is currently unloaded, recovering the old bed/house/chest ownership, and
guarding against reviving the same record twice.

## Construction

| Area | Status | Notes |
|---|---|---|
| Placing a blueprint item (hologram preview, rotate, `/build start`/`clear`) | 🔧 | Redesigned twice during investigation (a prefab packaging bug, then an event-registration bug identical to the baby-placing one). Needs a full retest. |
| Structure spawns facing the right way | ✅ | |
| Native engine hologram ghost (vs. placeholder blocks) | ❓ | Not confirmed. |
| NPC actually walking to a site and building | ❓ | Not confirmed. |
| Cancelling mid-build leaves nothing orphaned | ❓ | Not confirmed. |

## Mood, chat & appearance

| Area | Status | Notes |
|---|---|---|
| Plumbob reflects mood, `isMiserable` | ✅ | |
| Facial expression re-fires periodically instead of freezing after one change | 🔧 | Was broken for a while by an unrelated tuning change; fixed, needs a fresh confirmation pass. |
| Mood decay/boredom tuning | ✅ | |
| AI chat (player ↔ NPC), personality/mood/job-aware | ✅ | |
| Missing chat context (location, HP, nearby players, time, relationships) | ⬜ | Tracked as a roadmap item, not a bug. |
| Graceful fallback when the AI backend is unreachable | ❓ | Not confirmed. |
| Interaction panel visuals (needs bars, job/hobby icons, full like/dislike lists) | ✅ | |
| Female NPC mouth model, cosmetic variety | ✅ | |

## Villages

| Area | Status | Notes |
|---|---|---|
| Village forms by chaining nearby houses | ✅ | |
| `/simtale village` matches what's actually built | ✅ | |
| Homeless NPC still leaves the village to work/eat (leash isn't too tight) | ✅ | |
| Village dissolves when its houses are destroyed | ❓ | Not confirmed — nothing currently deletes a house record at all except the duplicate-cleanup scan, which is a separate gap. |
| Guard patrols the perimeter / stays put with no village | ✅ | Confirmed as part of this session's Guard work. |

## Items, recipes & models

Several craftable items exist as objects (model, texture, recipe, translated name) with **no
behavior wired to their use at all**:

| Item | Status |
|---|---|
| Wedding ring, Baby, Pregnancy test, Immigration contract | ✅ has behavior |
| House blueprint, Innkeeper's ledger, Inspector's journal | 🔧 implemented later, needs retest |
| Quartermaster's glass, Town bell, Birthday cake | ⬜ no behavior yet |

Held-item hand appearance for the newer items (fixed by matching the game's own root-node naming
convention) is ✅ visually confirmed; the behavior behind most of them is separate and tracked
above.

## Known open issues

- Fighting with your child, carrying a child, restricting child labor to safe jobs, the wood UI
  theme, and the persisted chest registry (surviving a restart even for chests far from spawn) are
  all **implemented but have zero in-game confirmation** — every item in each of those areas is
  still open.
- An NPC with no water within 15 blocks and dropping hygiene has a documented backoff fix that has
  not been re-confirmed.
- A once-seen "NPC frozen on the bed at midday, recovered on its own" report is consistent with a
  scheduled sleep window ending normally, not a bug — flagged for anyone who sees it again to check
  `/simtale npcstate` before assuming regression.

---

*Source: this page is a condensed, external view of the project's own line-by-line testing log.
When code and this page disagree, trust the code — and then come fix this page.*
