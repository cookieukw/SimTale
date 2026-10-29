# Changelog

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
