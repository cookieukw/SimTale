# Changelog

## [1.4.1] — 2026-11-09

- Moved growth and baby-care data into SimTale's own Caskara shell, with repeatable migration of
  legacy records and retention of Caskara's `default.db` safety backup.
- Fixed unscoped chest keys and added migration for the legacy `_x_y_z` keys.
- Updated recipes to use `TimeSeconds`, accepted by the current Hytale schema.
- Updated the compatibility target to release 0.7.x and pre-release 0.8.0-pre.2.1.

## [1.4.0] — 2026-11-06

This release collects everything added after 1.3.1. Version 1.3.2 was an internal build used
while the travelling merchant was being developed; its changes ship here.

### NPC following

- Added `/simtale follow` to make the nearest NPC follow the player.
- Added `/simtale follow stop` to release NPCs following the player.
- Added **Follow** / **Stop Following** to the NPC interaction menu.
- Normal NPCs show the action within 6 blocks; guards can always be ordered to follow.
- Following does not require friendship, romance or intimacy.
- Followers use native pathfinding instead of teleporting, stop at a natural distance and resume
  following after sleep, hunger or another temporary task.
- Followers walk when close and run when they fall behind; walking and running animations now
  switch correctly while they move.
- Pinned animation slots are cleared when following starts, so the NPC cannot get stuck in the
  wrong pose.
- Follow messages and menu labels are localized in English, Portuguese and Spanish.

### Houses and house validation

- Increased the supported house interior limit from 512 to 2,048 blocks.
- House validation now distinguishes large houses, exposed roofs/openings and unloaded chunks.
- Small openings of up to two exposed blocks are tolerated, allowing minor balcony access or
  decorative gaps without treating the whole exterior as the house interior.
- Open rooms no longer flood the scan into the surrounding world until the old hard limit.
- The House Blueprint now reports clearer diagnostics for oversized or open houses.
- The House Blueprint and inspection items use the player's current world instead of assuming the
  first loaded world.

### Professional work posts

- Added world-scoped records for Fishing Posts, Lumber Posts and Farm Posts.
- Post claims, releases, removal and boot-time discovery are now isolated per world.
- Post markers are unique per world and show whether the post is available or occupied.
- Fishing Posts revalidate their water target before a fisherman claims them.
- A Fishing Post automatically retargets nearby water when its original water block is removed.
- A Fishing Post with no remaining water target is released instead of sending an NPC to stale air.
- Professional post marker labels are localized in English, Portuguese and Spanish.
- Fixed a condition that prevented workers without an active social reservation from starting a
  job.

### World isolation and persistence

- Added explicit per-world persistence APIs for SimTale data and graveyards.
- Bed registries, house indexes, chest registrations, professional posts and village snapshots now
  support world-aware access.
- NPC routines, growth, pregnancy, baby care, construction, doors, guard behavior, stockpiles,
  map markers and debug tools now resolve their world from the relevant entity or player.
- Map snapshots and village overlays are isolated per world.
- Village safe-zone checks now use the village belonging to the NPC's current world.
- Shared chest discovery and village stock summaries no longer select chests from another world.
- NPC home, family and revival lookups use world-scoped house indexes.

### Village safety, guards and civilian defense

- NPCs inside a village safe zone are protected from normal damage, including damage from players,
  mobs, fire and falls.
- If a direct or debug damage path still reduces an NPC inside the safe zone to 0 HP, SimTale
  restores the NPC's health instead of entering the death flow.
- External NPC removal continues to be logged with the NPC name, task and last known damage cause.
- Missing residents can be recovered with `/simtale respawnmissing [name]`.
- Guards now recognize hostile animals in addition to the undead and creatures already covered:
  spiders, bears, cave spiders, dire wolves and wild boars.
- Ordinary residents also react to nearby hostiles while idle or wandering. They use the civilian
  combat path, with lower damage (about 10% of maximum health per hit) and without the guard's
  health, armour and damage-reduction bonuses.

### NPC appearance and clothing

- Added a neutral base undertop beneath overtop clothing layers.
- Repaired adult, child, Christmas and Halloween model variants that could leave the upper torso
  visually uncovered.
- Updated the model generation scripts so future variants keep the base undertop automatically.
- Repaired 1,089 existing generated model variants.

### Map and interface

- Map marker snapshots refresh immediately after houses, villages, beds, posts and recognized
  furniture change.
- House markers are read from the current world's house index.
- Nearby global chests are grouped into a single map marker with a count, and the label is
  localized.
- Map chunk invalidation now targets only the world that changed, fixing chunks that appeared blue
  or unloaded in other worlds.
- NPCs away on expeditions or work journeys no longer leave a plumbob or map marker behind.
- NPC interaction panels were reorganized with clearer sections for needs, relationships,
  preferences, details and actions.
- NPC interaction fallback labels now use translation keys instead of hardcoded text.
- Added complete Follow and professional-post translations for `pt-BR` and `es-ES`.
- Added strict locale validation for key parity, placeholders, duplicate entries, empty values,
  BOMs and UTF-8 encoding.

### Travelling merchant

- Added a temporary travelling merchant that visits a village for about 30 minutes and then leaves.
- Arrival checks run every 15 minutes, require at least three residents and have a 25% chance, so
  visits feel like an event instead of constant traffic.
- The merchant arrives on ground already used by villagers, at least 28 blocks away from the
  player, inside the nearest village.
- Each visit rolls its own name, body variant, gender and specialty: general goods, provisions,
  ore, remedies, curiosities or seeds.
- Merchants now wear the classic merchant outfit (beret, tunic, belted pants and mittens), with
  five male and five female appearances generated from existing resident faces.
- Each visit rolls five offers from a broad pool of roughly 400 vanilla items (ores, bars, gems,
  food, potions, seeds, tools, weapons, armour, decoration and ingredients), rather than three
  fixed staples. A premium entry is guaranteed when the specialty has one.
- Stock, prices and quantities stay stable while the merchant is present and after a reload.
- The trade screen shows the real item icon for every offer, uses ItemSlot quality backgrounds,
  and lists five stock entries per specialty.
- Sold-out offers hide the **Buy** button and show **Sold Out** in its place.
- Added Buy and Sell actions. Selling returns goods to the merchant's stock and pays the player
  in coins.
- Interacting with a merchant opens the trade counter directly, with no resident sheet, needs
  panel, chat, flirt or scold actions.
- Merchants no longer roll a random resident profession at spawn, so they never hold a pickaxe,
  hoe, bow or another profession tool.
- Opening and closing the trade counter clears the movement animation slot and releases the
  merchant cleanly, so arriving and leaving use the normal walk animation.
- Closing the counter explicitly releases the merchant pose too, so selling and then leaving the
  shop no longer leaves the walk clip pinned in idle.
- Visits, remaining stock and the merchant's identity are persisted with Caskara, so a restart
  does not duplicate the merchant or reset what was already sold.
- The trade screen is localized in English, Portuguese and Spanish.
- Added `/simtale merchant` to spawn a test visit.

### Economy: coins, piggy bank and gold

- Added `SimTale_Coin` (copper), `SimTale_Coin_Thorium` and `SimTale_Coin_Gold`, with 3D models
  and icons. One hundred copper coins equal one thorium coin; one hundred thorium coins equal one
  gold coin.
- Bar conversion recipes: one copper bar becomes 100 copper coins, one thorium bar becomes 100
  thorium coins, and one gold bar becomes 100 gold coins. The exchange recipes turn 100 copper
  into one thorium and 100 thorium into one gold.
- Added the Piggy Bank item and its deposit screen. Coins deposited there are used automatically
  by the merchant when the inventory does not carry enough, and sale proceeds overflow into
  savings when the inventory is full.
- Decorative gold blocks can now be broken back down at a Workbench: each `Rock_Gold_Brick` or
  variant (wall, beam, pillar, roof, stairs, smooth, ornate or half-block) yields one Gold Bar.

### Stability

- Fixed a `NullPointerException` when looking up a child's bed after the parent record no longer
  existed.

### Tooling and validation

- Added Crowdin configuration and GitHub Actions synchronization for English, Portuguese and
  Spanish resources.
- Added automated checks for UI files, locale consistency and generated resources.
- The full test suite, strict locale validation and UI validation pass after these changes.

## [1.3.1] — 2026-10-28

This is the first release on the 1.3.1 branch and includes the complete 1.3.x feature set.

### Interaction fixes

- Chests placed **inside a village and outside any house** are now **global chests**. They have
  no owner and any resident can take food from them.
- NPCs still prioritize their own house chests. When the house pantry has no food — or when the
  NPC has no house — they search the global/shared chests.
- Private chests outside a house are still ignored, so NPCs do not loot world loot chests.
- `/simtale forceeat` now starts the chest search on the same tick and is not interrupted by
  night sleep before the NPC reaches the chest.
- NPCs no longer sit on workbenches, furnaces, anvils or other crafting stations. Only real
  seating counts: chairs, stools, sofas, armchairs and furniture benches.
- A dead NPC's house is released immediately instead of remaining marked as "occupied" with an
  owner who no longer exists.

### NPC behavior

- Needs now **persist across server restarts** (hunger, energy, social, fun and hygiene).
- Food search starts when hunger is below **70** (idle), and tasks are interrupted below **25**.
  The pace can be accelerated through configuration.
- Workers have a **30% chance** to take surplus to a village global chest even when the house
  chest still has space. Previously the village chest was only used when the house chest filled.
- Homeless NPCs are automatically assigned to registered empty houses when they enter the world.
- Village regeneration also considers the interior of registered houses, even when the house is
  slightly outside the calculated village radius.
- Work now has priority over leisure, chairs, conversations and idle strolling. Before this,
  Miners and Hunters could go through an entire session without ever starting an expedition.
- Every profession now visibly holds its tool: pickaxe, shortbow, hoe, hatchet, fishing trap,
  hammer or map. The tool uses an empty hotbar slot so gathered items are not overwritten, and
  Guards keep the exact weapon they were given.
- Miner/Hunter expedition departures and returns are now written to the server log.

### Guards

- Guards receive **double maximum health**, **+50 armor**, **+10 physical damage** and **25%
  damage reduction**. The bonuses are applied on spawn, when the job is assigned and
  periodically; they are removed if the NPC changes profession.
- When a hostile enters a village, the **two closest guards from that village** leave their patrol
  and go to it, even from far away. They respond from anywhere inside the village radius plus a
  16-block margin. Guards that are sleeping, busy or already fighting another target do not
  consume one of the two response slots.
- Detection covers skeletons, zombies, ghouls, wraiths, werewolves, bleached hounds, shadow
  knights, trorks, goblins, scaraks, outlanders, hederas and fen stalkers.
- Guards now **run** while closing on a hostile, forcing the run movement state and temporarily
  increasing their movement speed.
- Combat is no longer a delayed `removeEntity`. Each swing applies a real damage event scaled to
  roughly a third of the target's maximum health, so the hostile reacts, plays its own death
  animation and dies after about three hits. The guard returns to the chase if the mob backs
  away, and a timeout still removes targets that cannot be damaged.
- Attack animations are taken from the held weapon's own animation set (`SwingRight` for melee,
  `Shoot` for bows), so the guard visibly swings instead of standing still.

### Houses and villages

- Fixed the bug where **air was treated as an unloaded chunk**. This made every house scan fail
  with `TOO_LARGE_OR_UNENCLOSED` and prevented new beds from becoming houses.
- Furniture such as tables, workbenches, chairs, lamps and chests is now **traversed** by the
  house flood fill. It is registered for requirements but does not block the room.
- Workbench now counts as a **surface** (`SURFACE`), not as a seat.
- A valid bed placed near a village immediately registers an **ownerless house**. The first valid
  property in a world can also start a village.
- Empty houses are assigned to the nearest homeless adult; houses whose owner died are released
  and can be reoccupied.
- The Blueprint now distinguishes an open/too-large house from an unloaded chunk.

### Map and UI

- The house marker moved to the **interior center**, not the first bed.
- An occupied house appears green and with a name; an empty house appears blue; an ownerless bed
  appears gray.
- A house with an owner but no resolved name no longer appears blue: it shows as
  "House (Occupied)".
- Taste/dislike icons grow with **affinity**:
  - 0–19: 2 likes, 0 dislikes;
  - 20–39: 3 likes, 1 dislike;
  - 40–59: 4 likes, 2 dislikes;
  - 60–79: 5 likes, 4 dislikes;
  - 80–100: all.
- A `+N` at the end of the row shows how many icons are still hidden.
- Preference icons increased from 24 to 36 px; profession/hobby icons from 28 to 34 px.

### Village safe zone and regeneration

- SimTale NPCs inside the village boundary cannot take damage (mobs, falls, fire or player).
  `/simtale forcekill` still works.
- When entering the village, the player receives a **regeneration** effect through RuneCore
  (`Food_Health_Regen_Medium`), refreshed while remaining in the area.

### Death, missing NPCs and recovery

- The cause of death is saved in the NPC record and shown in the graveyard.
- When entering the world, the player is told how many NPCs are buried.
- The server log records the death and its cause.
- The deceased NPC's house is released and can be assigned to another resident.
- New command `/simtale respawnmissing`:
  - without arguments, lists saved residents whose entity is missing from the world;
  - with a name or partial name, recreates that NPC near the player;
  - relationships, family and house references are remapped;
  - the NPC reclaims her old bed when it is still valid;
  - if revival fails, the record remains in the graveyard for another attempt.
- NPCs removed externally now produce a warning with their name, current task and last damage,
  making clear that the removal did not go through SimTale's death flow.

### Stability and mod compatibility

- SimTale NPCs now remove any `SpawnMarkerReference` on spawn and periodically. Hytale's
  Spawning system was despawning residents 30 seconds after a marker disappeared
  (`NPCEntity despawning due to lost marker`), which made them vanish without a death cause.
- Plumbobs and thought bubbles now have a `Velocity` component. Its absence could crash the world
  with a `NullPointerException` in Hytale's NPC avoidance system.
- NPCs no longer die from drowning or suffocation caused by getting stuck in water/blocks: that
  damage is cancelled and the NPC is moved back to its bed or home. External-removal warnings now
  show the real cause (`Drowning`, `Suffocation`, etc.) instead of an empty `por `.
- The 0 HP check now also runs during Miner/Hunter expeditions, so an external-damage death cannot
  bypass the death flow.
- The session log also showed unrelated compatibility warnings from the following mods:
  - **RuneCore** targets `>=0.6.8 <0.7.0` while the server is `0.7.4`;
  - **Automatic Slab Placement** targets `0.7.2`;
  - **Arm Cannons** targets `<0.7.0`;
  - **Composter Table** targets `<0.6.0`;
  - **BetterSigns** does not declare a target version;
  - **BetterFood** references the missing role `Spectre_Void`;
  - **Patchwork** fails to generate files under `/mnt` (`Operation not supported`).

  None of these are caused by SimTale, but they should be updated or disabled.

### Configuration

New options in `simtale-config.json`:

```json
{
  "hungerDecayMultiplier": 1.0,
  "villageChestDepositChance": 0.3,
  "villageSafeZoneEnabled": true,
  "villageRegenEnabled": true
}
```

- `hungerDecayMultiplier`: multiplies hunger speed. `1.0` keeps the original pace, `10.0`
  accelerates it significantly and `0` freezes hunger.
- `villageChestDepositChance`: chance from 0 to 1 of using the village chest even when there is
  space at home.
- `villageSafeZoneEnabled`: enables/disables NPC protection inside the village.
- `villageRegenEnabled`: enables/disables regeneration when entering the village.

### Documentation

- The project's `docs/` folder was removed.
- The wiki is the canonical documentation, with English and PT-BR pages updated for the changes
  above, including `/simtale respawnmissing` in the FAQ.
- Old comments and references to `docs/` now point to the wiki.

### Tests

```text
All tests passed (1881 assertions)
Wiki build: en + pt-BR OK
```
