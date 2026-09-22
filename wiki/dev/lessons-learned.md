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

### 2b. When you must compare an id, normalize it

Some things have no engine-side question to ask, and the id is all there is. Nine such checks had
grown across the project, each written from scratch: two used `toLowerCase()` without a locale, three
used `equalsIgnoreCase`, two stripped punctuation, and the rest used `contains`.

An id almost never arrives in the shape the asset file suggests:

| Shape | Example |
|---|---|
| State variant, prefixed | `*plant_crop_carrot_block_state_definitions_stagefinal` |
| Rotation variant | a `VariantRotation: NESW` block is not its bare asset name |
| Namespaced | `simtale:WeddingRing` |
| Re-cased | `WeddingRing` vs `wedding_ring` |

`equalsIgnoreCase` fails on all four and fails **silently** — the block simply is not recognised.
That is what made the blueprint marker do nothing, and it was still live in the fishing, lumber and
farm post checks.

`core/AssetIds` is now the single answer: lowercase with `Locale.ROOT`, drop everything that is not
a letter or a digit, then `contains`. That collapses all four shapes into one comparison.

:::warning Locale.ROOT is not decoration
The default-locale `toLowerCase()` maps `I` to a dotless `ı` under a Turkish locale. A server
started with that locale would stop recognising every id containing an uppercase I.
:::

---

## 2c. Know whether an event fires before or after the thing happens

`PlaceBlockEvent` and `BreakBlockEvent` extend `CancellableEcsEvent` and expose setters for the
target block. An event that can still be cancelled, and whose target you can still change, is by
definition delivered **before** the action.

Two separate bugs came out of ignoring that:

- The handler read `world.getBlockType(targetBlock)` to decide what had been placed. That cell still
  held air. The fix is `event.getItemInHand()` — the event carries what is being placed.
- Anything needing real geometry (multi-block anchor, bed yaw, "does this block have a container")
  has to be deferred past the placement. `WorldUtil.execute` does that, and the deferred task
  re-reads the cell first so a cancelled placement registers nothing.

A related trap in the same API: those two events are delivered through `EntityEventSystem`, not
`WorldEventSystem`, because they have an actor. Across the whole server jar, every consumer of
`PlaceBlockEvent` is an entity system. The clearest proof is one vanilla file where
`TriggerVolumeBlockEventSystems$BlockPlaced` is an entity system while its sibling
`$EnvironmentBlockBroken` — the actor-less variant — is a world system. Registering on the wrong
base is silent: the handler is simply never called.

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

## 3.5 Do not fail an interaction you do not own

`SimTaleUseNPCInteraction` is registered **over the engine's own** `UseNPCInteraction.DEFAULT_ID`:

```java
Interaction.getAssetStore().loadAssets(DefaultAssetMap.DEFAULT_PACK_KEY, List.of(
    new SimTaleUseNPCInteraction(UseNPCInteraction.DEFAULT_ID)
));
```

That means the class does not own a private interaction — it sits inside a shared pipeline.

Guarding the cow-adoption bug there with `context.getState().state = InteractionState.Failed` plus
an early `return` **removed every interaction in the game**: doors, blocks, containers, all of it.
The filter was correct about *what* to reject; it was wrong about *how*.

The fix is to decline without touching the state machine. Leaving `npc` as null is enough, because
the page only opens when it is non-null:

```java
boolean wronglyAdopted = npc != null && npc.gender == null;
if (player != null && npc != null && !wronglyAdopted) {
    // open the page
}
```

**Rule**: when overriding a shared engine hook, refuse by not acting. Setting a failure state is a
statement about the whole pipeline, not just about your feature.

Note the asymmetry with the right-click path: `SimTaleEventHandler` is a plain
`Consumer<PlayerMouseButtonEvent>`, so returning early there only stops *our* handler and is safe.
Same guard, two mechanisms, because the surrounding contracts differ.

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

---

## 10. One shared component, two unrelated meanings

`MountedComponent` is the engine's generic "attached to another entity" component. This project
reuses it for three unrelated things: an NPC asleep in a bed, an NPC sitting in a chair, and a
child riding on a player's shoulder. A tick system checked only `mounted != null` to decide "this
NPC just woke up" — true for all three cases, not just the first one.

Any child picked up outside the nighttime window satisfied that check on the very next tick, and
the carry was silently undone a few dozen milliseconds after being created — long before the
client could even render it. The success message had already been sent by the time this ran, so
the bug looked like a rendering glitch rather than a logic bug.

**A shared component is not a signal for the specific state you happen to be thinking about. Gate
on the actual state field (`currentTask == SLEEPING`), not on a side effect of that state (`has a
mount component`).**
