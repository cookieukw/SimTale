---
sidebar_position: 5
title: Lessons learned
---

# Lessons learned

Failure patterns that have already cost this project real time. Read before writing code.

---

## 1. Plausible format, unverified existence

The most expensive pattern in the project, four times over.

| Case | What happened |
|---|---|
| `SimTale_Beds` | A `BlockSet` asset created in `Server/BlockTypeList/` with type `BlockTypeListAsset`, when the validator looks up `asset.type.blockset.config.BlockSet`. Broke spawning entirely: *The block set with the name "SimTale_Beds" does not exist*. |
| Child model paths | 34 models written as `Cosmetics/Undertops/X_Child.blockymodel` instead of `NPC/Player_Child/Cosmetics/...`. The format was validated; the existence was not. |
| `ItemPreviewComponent` | A UI component documented with convincing examples and properties. Absent from all 135 shipped `.ui` files and from the API. |
| `hytale:sword_iron` | An invented namespace. Real ids look like `Weapon_Sword_Copper`. |

**The habit that prevents it**: before writing against any engine API, asset id or component name,
find a real usage — in the game assets, or in the server jar's constant pool. See
[Build environment](build-environment.md) for how.

A convincing example is not evidence. Neither is a plausible file format.

---

## 2. Classifying by name fails silently

Three times, the same shape:

| Where | The heuristic | What it missed |
|---|---|---|
| Food | id contains `food_` | Accepted `Plant_Crop_Mushroom_Cap_Brown` (inedible), rejected `Ingredient_Dough` (edible) |
| Chests | id contains `chest`/`barrel`/`cupboard`/`cabinet` | Every storage block named anything else — NPCs stood next to full chests and starved |
| Job tools | id contains `sword`, `bow`, `hoe` | Any weapon whose id does not spell the word |

Each was fixed by asking the engine instead:

```java
// food: the item's own consume interaction, resolved through its Parent chain
item.getInteractions().get(InteractionType.Secondary)  // Root_Secondary_Consume_Food_T1..T3

// chests: does the block actually have a container?
BlockModule.getComponent(ItemContainerBlock.getComponentType(), world, x, y, z) != null
```

The chest fix has a second benefit worth copying: it uses **the same component NPCs already read**
when looking for food, so registration and consumption cannot disagree.

Job tools are still name-based. Known debt.

---

## 3. Duplicated code means duplicated fixes

The entity adoption bug lived in **two** handlers: `SimTaleEventHandler` (right-click) and
`SimTaleUseNPCInteraction` (the F key). Fixing the first changed nothing observable, because the
test was done with F.

The search that actually resolved it:

```bash
grep -rn "addComponent.*SIM_NPC_COMPONENT_TYPE" --include=*.java .
```

**Before calling a bug fixed, grep for every occurrence of the pattern.**

---

## 4. A diagnostic that also repairs hides the fault

`bootstrapLoadedRadius` — the furniture scan — was only ever called from inside `/simtale housecheck`
and the debug page, as a side effect.

The result: beds registered themselves in old worlds (where someone had run the command) and not in
new ones. Because the registries are `static`, the state even survived across worlds in the same
JVM. The bug looked intermittent and environmental for a long time.

**Diagnostics observe. Repair belongs to the code path that owns the state.**

---

## 5. The UI can lie, and it reads as a logic bug

The interaction panel showed only the **first** entry of an NPC's tastes. An NPC rolls two to three
hated foods plus two to three hated items — up to six.

So giving popcorn to an NPC whose panel showed something else as "hated" made her angry, and it
looked like the gift rules were broken. They were correct; the screen was hiding five sixths of the
list.

**When behaviour contradicts the UI, suspect the UI first.** It is cheaper to check.

---

## 6. Interrupts must respect terminal states

The sleep and hunger interrupts did not exclude `DYING`, `DEAD` or `REAPING`. A starving NPC entered
`DYING`, got yanked out on the same tick, the death check fired again next tick, and the "is dying"
broadcast repeated forever without her ever dying.

**Any state machine with an escape hatch needs a list of states the hatch does not apply to.**

---

## 7. Interrupts need cooldowns

The tired interrupt reset `taskStartTime` to bypass the search cooldown. When the bed it found could
not be claimed, the task fell back to `IDLE` and the interrupt fired again on the very next tick.

One session logged **3447 rejections of the same bed in a few seconds**, with the NPC standing still
from exhaustion the entire time.

The fix is a separate field (`nextBedSearchTick`, `nextFoodSearchTick`) that survives task changes,
precisely because the interrupt is what consults it.

**Any interrupt that can fire from any state needs a cooldown independent of the task timer.**

---

## 8. Multi-block furniture has an anchor

A bed spans six blocks; a door, four. Only one is the anchor, and the mount point of the asset is
measured from it. Mounting on a filler offsets the sleeping pose by the distance from that filler to
the anchor.

Four attempts at fixing bed alignment failed — geometric centring, teleport offsets, attachment
offsets, tuning constants — because all of them treated a placement bug as a maths problem.

What solved it was an observation from testing: `/simtale debugnear` reported **twelve beds where
there were two**.

**Resolve to the anchor with `FurnitureAnchorHelper.anchorOf` before doing anything positional.**

And: when a fix needs a fourth attempt, the model of the problem is probably wrong.

---

## 9. Logging that goes nowhere

SLF4J is a no-op on this server:

```
SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder"
```

Fourteen classes were logging into the void, which made several bugs invisible. Use `SimLog`, which
delegates to `HytaleLogger`.
