# SimTale: Complete User Guide & Technical Wiki

Welcome to the official **SimTale** documentation. This wiki is designed for both players who want to understand how to interact with the mod's systems and developers looking for a comprehensive technical reference for the codebase. 

SimTale introduces deep social simulation mechanics to Hytale, turning passive NPCs into active, feeling individuals with routines, preferences, relationships, and lifecycles.

---

## Table of Contents
1. [Social & Relationship Systems](#1-social--relationship-systems)
2. [Mod Visual Variety & NPC Variants](#2-mod-visual-variety--npc-variants)
3. [Personality, Traits & Preferences](#3-personality-traits--preferences)
4. [NPC Needs & Autonomous Routine AI](#4-npc-needs--autonomous-routine-ai)
5. [Marriage, Pregnancy & Lifecycles](#5-marriage-pregnancy--lifecycles)
6. [The Baby Care & Co-Parenting System](#6-the-baby-care--co-parenting-system)
7. [Professions & Job Assignment](#7-professions--job-assignment)
8. [Interface (UI) Manual](#8-interface-ui-manual)
9. [Player Commands Reference](#9-player-commands-reference)
10. [Debug Commands Reference](#10-debug-commands-reference)

---

## 1. Social & Relationship Systems

The core of SimTale is the **Relationship Engine**. Unlike simple reputation systems, relationships in SimTale are multidimensional, separating friendship, romance, trust, and overall affinity.

### Relationship Stats
Each relationship track between an NPC and a player (or another NPC) contains the following core variables:
*   **Friendship (0 to 100):** Represents platonic bonds. Increased by friendly interactions, jokes, and liked gifts.
*   **Romance (0 to 100):** Represents romantic attraction. Can only be increased by explicit flirts and romantic gifts. Locked at 0 for children.
*   **Trust (0 to 100):** Dictates how reliable you are in the NPC's eyes. Can crash heavily if you make false promises, insult them, or treat partners badly.
*   **Affinity (-100 to 1000):** The overall emotional compatibility score. Dictates the general tone of interactions.

### Relationship Stages
Based on these stats, relationships progress through several distinct stages:
*   **Stranger / Unknown:** The default starting stage.
*   **Acquaintance:** Met briefly, minor interaction.
*   **Friend / Good Friend / Best Friend:** Achieved by high Friendship scores.
*   **Romantic Interest / Dating / Engaged / Married:** Achieved by high Romance scores and special progression items (like the wedding ring).
*   **Enemies:** Achieved by negative Affinity and high insult frequency.

### Daily Interaction Limits (Spam Control)
To prevent players from repeatedly clicking social options, SimTale enforces a **Spam Cooldown**:
*   Players can perform a maximum of **3 interactions per Hytale day** with the same NPC.
*   A Hytale day corresponds to **24,000 ticks** (approx. **20 minutes in real-time**).
*   Attempting to interact beyond this limit triggers a cooldown dialogue (e.g. *"I'm busy right now"*), yielding no relationship changes.

---

## 2. Mod Variety & NPC Variants

SimTale is shipped with a massive assortment of pre-generated cosmetic variants to ensure that every villager, spouse, and child looks completely unique in the world, with distinct genetics (hairs, skins, clothing) combined automatically.

### Total NPC Variant Counts
The mod includes the following pre-compiled cosmetic files under its server models registry:
*   **Adult Males:** **200** distinct visual variants (`SimTale_Human_Male_1` to `SimTale_Human_Male_200`).
*   **Adult Females:** **200** distinct visual variants (`SimTale_Human_Female_1` to `SimTale_Human_Female_200`).
*   **Children:** **410** distinct visual variants (`SimTale_Human_Child_1` to `SimTale_Human_Child_410`).
*   **ChibiDolls (Doll Models):** **10** distinct visual variants (`Doll_1` to `Doll_10`) generated with custom, child-proportional cosmetic scaling.

### Dialogue Variation & Contextual Dialogue
To prevent interactions from feeling repetitive, SimTale includes a dynamic dialogue engine that selects from multiple written variants based on relationship status, personality traits, and NPC moods:

*   **Greetings:**
    *   **Romantic Spouses:** 5 unique greetings.
    *   **Enemies:** 3 unique hostile greetings.
    *   **Close Friends:** 5 warm greetings.
    *   **Strangers:** 5 distant greetings.
    *   **Trait-Specific:** 3 greetings for Greedy, 3 for Paranoid, and 3 for Lazy NPCs.
    *   **Generic:** 5 standard greetings.
*   **Jokes:**
    *   **Enemies:** 3 cold joke rejections.
    *   **Bad Mood (Angry/Sad):** 5 annoyance/sadness rejections (unless Close Friend/Spouse, which pulls from 3 "Cheer Up" variants).
    *   **Funny Trait:** 5 extra funny, highly receptive joke responses.
    *   **Generic:** 5 standard joke reactions.
*   **Flirting:**
    *   **Enemies:** 3 harsh romantic rejections.
    *   **Strangers:** 3 awkward/creepy fluster rejections.
    *   **Angry Mood:** 5 annoyed rejection variants.
    *   **Married / Partner:** 5 affectionate spouse-specific lines.
    *   **Shy Trait:** 5 flustered, cute shy reactions.
    *   **Generic:** 5 standard flirt acceptances.
*   **Insults:**
    *   **Married / Partner:** 3 heartbroken/betrayed partner responses.
    *   **Aggressive Trait:** 5 hostile, retaliatory responses.
    *   **Needy Trait:** 5 highly hurt, vulnerable responses.
    *   **Generic:** 5 standard insulted lines.
*   **Marriage Proposals:**
    *   **Acceptance:** 2 romantic acceptance variants.
    *   **Rejection:** 2 rejection variants.
*   **Profession Assignment Reactions:**
    *   **Stranger/Enemy Refusals:** 3 status-refusal variants.
    *   **Job Disliked:** 4 dislike-specific responses.
    *   **Lazy Trait Refusals:** 3 lazy job refusals.
    *   **Aggressive Trait Refusals:** 3 aggressive work-dislike refusals.
    *   **Angry Mood Refusals:** 3 annoyed mood-based refusals.
    *   **Liked Job Acceptance:** 3 excited, job-liked responses.
    *   **Generic Acceptances:** 5 standard job acceptance lines.

### Social Interaction Types & Success Formula
When clicking an interaction button in the UI, the result is calculated using the NPC's Traits, Mood, and Relationship status:

#### A. Friendly (Chat)
*   **Stranger:** +8 Friendship, +1 Trust, +10 Affinity.
*   **Enemies:** +1 Friendship, +1 Trust, +1 Affinity.
*   **Other:** +5 Friendship, +1 Trust, +5 Affinity.
*   *Contextual Triggers:* NPCs will greet you differently if you are bleeding (Health <= 20) or if it is night time (Day progress < 0.25 or > 0.75). If you haven't spoken in over 3 days, they will mention missing you.

#### B. Funny (Joke)
*   **Enemy:** -2 Friendship, -5 Affinity.
*   **Angry / Sad Mood:** -2 Friendship, -5 Affinity (unless Best Friend/Partner, which triggers a "Cheer Up" event: +2 Friendship, +1 Trust, +5 Affinity).
*   **NPC has "Funny" Trait:** +5 Friendship, +2 Trust, +15 Affinity.
*   **Normal:** +3 Friendship, +1 Trust, +5 Affinity.

#### C. Romantic (Flirt)
*   **Enemy:** -5 Friendship, -15 Romance, -5 Trust, -20 Affinity.
*   **Stranger / Acquaintance:** -3 Friendship, -5 Romance, -2 Trust, -10 Affinity (creepy rejection).
*   **Angry Mood:** -10 Romance, -2 Trust, -15 Affinity (rejected).
*   **Married / Partner:** +2 Friendship, +10 Romance, +2 Trust, +10 Affinity.
*   **NPC has "Shy" Trait:** +15 Romance, +2 Trust, +10 Affinity (flustered acceptance).
*   **Normal:** +10 Romance, +1 Trust, +5 Affinity.

#### D. Mean (Insult)
*   **Married / Partner:** -15 Friendship, -20 Romance, -30 Trust, -25 Affinity (heavy marital damage).
*   **Aggressive Trait:** -10 Friendship, -15 Trust, -20 Affinity (NPC is likely to attack/retaliate).
*   **Needy Trait:** -5 Friendship, -15 Trust, -15 Affinity.
*   **Normal:** -5 Friendship, -15 Trust, -15 Affinity.

---

## 3. Personality, Traits & Preferences

NPCs are not identical copies. Each NPC is spawned with random or inherited **Traits** and **Preferences** that dictate their likes, dislikes, and behavior.

### Traits
*   **Greedy:** Loves gifts. Receives +10 Friendship and +20 Affinity on standard gifts, but hates receiving "trash" items.
*   **Paranoid:** Suspicious of everything. Rejects gifts (-5 Friendship, -10 Trust, -10 Affinity) unless very close.
*   **Lazy:** Dislikes physical labor. Refuses lumberjack/miner professions 60% of the time.
*   **Aggressive:** Has a short temper. Quickly shifts to the Angry mood. Refuses farmer/fisherman jobs 70% of the time.
*   **Shy:** Gets flustered easily. Gains extra Romance on flirts, but struggles with strangers.
*   **Funny:** Tells jokes frequently. Receives jokes very well.
*   **Needy:** Vulnerable to insults. Drains social needs faster.

### Preferences
*   **Favorite Foods:** Gifted foods matching this list give Loves bonus: +15 Friendship, +8 Trust, +25 Affinity (multiplied by 1.5x for Spouses, 0.5x for Enemies).
*   **Hated Foods:** Gifted foods matching this list give Hates penalty: -15 Friendship, -10 Trust, -20 Affinity (multiplied by status).
*   **Favorite Season / Weather:** Influences dialogue and autonomous routines.
*   **Hobby:** Dictates routine task weighting (e.g. fishing, gathering).
*   **Liked / Disliked Professions:** Dictates acceptance rates when assigning jobs.

### Memory & Recalling Events
NPCs hold a memory buffer. If you insult an NPC, they will remember it for **300,000 milliseconds (5 real minutes)**. If you try to chat during this window, they will react coldly, saying they are still upset.

---

## 4. NPC Needs & Autonomous Routine AI

Active NPCs possess a ticking **Needs System** that drives their daily routine:
*   **Hunger (0 to 100):** Decays over time. Below 30, the NPC enters the `FINDING_FOOD` state, pathfinding to food chests or crops. If Hunger hits 0, they will collapse and begin dying.
*   **Energy (0 to 100):** Decays during the day. Below 20, the NPC enters `FINDING_BED`. They will locate their registered bed and sleep, restoring energy.
*   **Social (0 to 100):** Restored by interacting with players or other NPCs. If low, they seek conversations.
*   **Hygiene (0 to 100):** Decays over time. If low, they seek bath tubs or water sources (`FINDING_BATH`).

### The Sleep & Bed Registry System
SimTale features a robust physical bed detection engine:
*   **Registry:** Coordinates of beds are stored in a thread-safe registry (`BedRegistry`). It includes deduplication algorithms (ignoring double Hytale bed blocks, merging pillows and feet blocks into one coordinates index).
*   **Home Claiming:** Adult NPCs without a bed will seek unclaimed beds within a 96-block radius. Claiming a bed sets that location as their official permanent home.
*   **Child Bed Sharing:** Children do not need or claim separate beds in the village. Upon entering `FINDING_BED`, child NPCs automatically resolve and inherit their parents' registered bed (`FamilyBonds.findParentBed`).
*   **Co-Sleeping & Mount Fallback:** Beds can be shared between family members. The bed ownership check (`isBedTakenByAnotherNpc`) specifically permits children to share beds with their parents (`FamilyBonds.isChildOf`). If a parent is already occupying the native block mount point, the child lies down alongside or on the bed with the `Sleep` pose in `TaskType.SLEEPING` without dropping or resetting their bed reference.
*   **Sleep Disturbance Protection:** While resting in bed, `GrowthTickSystem` protects children from having their rest interrupted or being pulled out of bed when a parent moves around the house during the night.

---

## 5. Marriage, Pregnancy & Lifecycles

Players can start a family with NPCs, leading to pregnancy and children who grow up dynamically.

### Marriage
*   **Requirements:** Romance >= 80, Friendship >= 70, and giving the NPC a **Wedding Ring** (`simtale:wedding_ring`).
*   **Result:** The NPC accepts the proposal, changing their relationship status to `MARRIED` and linking their family record to your UUID.

### Pregnancy
*   **Start:** Requires marriage and a Romance level of at least 50.
*   **Duration:** Gestation lasts **5 Hytale days** (120,000 ticks / 100 real minutes).
*   **Trimesters:**
    *   *Trimester 1 (0% to 33% progress):* Early stage. Normal behavior.
    *   *Trimester 2 (33% to 66% progress):* Movement speed decreased slightly (-1.5 base speed). NPC needs drain faster.
    *   *Trimester 3 (66% to 100% progress):* Heavy debuffs. Movement speed severely slowed (-3.0 base speed). Player pregnancy drains stamina rapidly.
*   **Birth:** When progress hits 100%, the mother goes into labor.
    *   If the mother is an NPC: Labor spawns the baby entity, and the mother loses 50 HP.
    *   If the mother is a Player: Labor reduces player HP by 50 and places a physical **Baby Item** (`simtale:Baby`) in the player's hotbar/inventory.

### Genetics & Naming
*   Children combine the DNA traits of both parents (skin tone gradients, hair colors, etc.).
*   First names are pulled randomly from a custom SimTale dictionary, while surnames are inherited dynamically from the parents (combining surnames or defaulting to the father's surname).

### Life Stages & Growth Timers
As the world ticks, children grow through five distinct stages:

| Stage | Duration | Visual Scale | Behavior |
| :--- | :--- | :--- | :--- |
| **BABY** | Days 0 to 3 | 0.35x | Held in inventory/arms. Relies entirely on parent care. |
| **TODDLER** | Days 4 to 8 | 0.50x | Spawns in world. Crawls/walks, follows parents. |
| **CHILD** | Days 9 to 20 | 0.70x | Moves faster, plays, gathers materials. |
| **TEEN** | Days 21 to 40 | 0.90x | Can take minor jobs, helps around the house. |
| **ADULT** | Day 41+ | 1.00x | Fully independent NPC. Leaves parents, claims a bed. |

---

## 6. The Baby Care & Co-Parenting System

SimTale implements a cooperative turn-based baby management system.

*   **Turn Duration:** **2 minutes in real time** (120,000 ms).
*   **The Rotation:** The duty to hold and care for the baby swaps between the Mother and the Father at the end of every turn.
    *   *Player Turn:* The baby is automatically placed in the player's inventory as a `simtale:Baby` item.
    *   *NPC Turn:* The item is sucked out of the player's inventory, and the NPC holds it visually, caring for it in their routine.
    *   *Offline Catch-up:* If you log off, the system calculates the time elapsed and catches up turns. If it swapped to you while offline but your inventory was full upon return, the NPC keeps it for safety.

### Baby Needs & Experiences
While a Baby, the child has three ticking needs:
*   **Hunger:** Decays by -0.0003/sec. Restored by feeding food items.
*   **Affection:** Decays by -0.0002/sec. Restored by holding or playing.
*   **Health:** Decays rapidly if Hunger drops below 20%. Restored by medicine/healing.

### Personality Development
Caring actions accumulate positive experiences. Neglect (letting needs sit below 30%) builds negative experiences.
*   Upon growing into an Adult, the child's **Wellbeing Score** is checked:
    *   **Sociable Tendency (>0.5 score):** The adult NPC gains positive traits like `Loyal`.
    *   **Withdrawn Tendency (<0.5 score):** The adult NPC gains negative/defensive traits like `Shy` or `Aggressive`.

---

## 7. Professions & Job Assignment

You can employ adult NPCs to gather resources for you.

### How to Assign
Interact with the NPC and select "Assign Profession". You must be holding the corresponding tool in your active hotbar slot:
*   **Miner:** Requires a **Pickaxe**.
*   **Farmer:** Requires a **Hoe**.
*   **Fisherman:** Requires a **Fishing Trap**.
*   **Lumberjack:** Requires a **Hatchet**.
*   **Guard:** Requires a **Sword**.
*   **Explorer:** Requires a **Map**.
*   **Builder:** Requires a **Hammer**.

### Refusal Triggers
NPCs will not always work for you:
*   **Child Age:** Children are exempt from formal labor and always spawn as `UNEMPLOYED`. Attempting to assign a profession via held-tool handoff or chat commands triggers an immediate refusal dialogue (e.g. *"{name} is just a child and cannot work yet!"*). Upon reaching the `TEEN` stage, an adult profession is automatically assigned.
*   **Child Hobbies vs. Formal Jobs:** While child NPCs cannot hold formal jobs or take work orders, they actively participate in village life through their autonomous **Hobby** (such as `GARDENING` tending farm crops or `FISHING` near water) to restore their `fun` need.
*   **Stranger / Enemy:** Will always refuse your job proposal.
*   **Disliked Jobs:** Refused with a custom dialogue.
*   **Lazy Trait:** 60% chance to refuse hard labor (Miner/Lumberjack).
*   **Aggressive Trait:** 70% chance to refuse quiet tasks (Farmer/Fisherman).
*   **Angry Mood:** 50% chance to reject any contract.

---

## 8. Interface (UI) Manual

This section explains every button, indicator, camera behavior, and field in the custom SimTale interfaces.

### NPC Interaction Page
This interface opens when right-clicking (`F` key) an active NPC.

```
+--------------------------------------------------------------+
| [NPC Interaction UI Layout]                                  |
| Header: Name, Profession / Stage, Mood                       |
| Info: Hunger, Energy, Traits, Taste Showcase (Items/Hobby)   |
| Preferences: Likes, Hates, Fav Season                        |
| Family: Parents, Children                                    |
| Bottom Well: Relationship / Filial Bond Status               |
| Action Buttons: Chat, Joke, Gift, Scold/Insult, Pick Up, etc |
+--------------------------------------------------------------+
```

*   **Dynamic Cinematic Camera:**
    *   The player camera shifts 2.0 meters back and 0.55 meters to the right, smoothly framing the NPC on the left portion of the screen.
    *   **Bounding-Box Scaling:** Target height is calculated dynamically from `BoundingBox.height() * 0.8` rather than an adult constant, ensuring babies, toddlers, and children are centered instead of having the camera aim over their heads.
    *   Restores the player's original rotation upon closing the page.
*   **NPC Freeze & Animation Safety:**
    *   Opening the interface immediately clears any pending walk destination (`clearMoveTarget`), resets task to `IDLE`, sets the NPC to face the player, and pins the animation to `Idle` while frozen (`NpcFreezeUtil`).
    *   Dismissing the menu cleanly unfreezes the NPC so it resumes its routine without gliding on ice.
*   **Header Name (#NpcName):** Displays the NPC's full name. The text color shifts dynamically (Red for Angry, Blue for Sad).
*   **Profession / Stage Indicator (#NpcProfession):** Displays the current trade for adults (e.g. *"Job: Miner"* or *"Job: Unemployed"*). For children, this line dynamically adapts to display **"Stage: Child"**, **"Stage: Toddler"**, or **"Stage: Baby"** instead of an employment title.
*   **Mood Status (#NpcMood):** Displays the current temporary emotion and emoji (e.g. *"Humor: Feliz :)"*).
*   **Traits List (#NpcTraits):** Lists the permanent personality traits (e.g. *"Traits: Greedy, Shy"*).
*   **Visual Taste Showcase (#SlotProfession, #SlotHobby, #Like0..5, #Hate0..5):**
    *   Displays visual item icons: copper-tier tools for professions, specialized items for hobbies (e.g., carrot for gardening, fishing trap for fishing).
    *   Displays up to 6 icons per row for liked and hated foods and items.
    *   Automatically hides empty slots (e.g. `#SlotProfession` is hidden for unemployed NPCs and children).
*   **Preferences (#NpcSeason):** Displays preferred season.
*   **Needs Overview:** Shows Hunger and Energy percentages with intuitive color coding.
*   **Relationship & Filial Bond (#NpcRelationship):**
    *   **General NPCs:** Displays Friendship, Affinity, and current status (`Stranger`, `Acquaintance`, `Friend`, `Good Friend`, `Best Friend`, `Dating`, `Married`, `Enemies`).
    *   **Own Children:** Automatically detects parentage via `ParentChildBond`. Replaces adult tiers with **"Son"**, **"Daughter"**, or **"Child"** accompanied by family bond metrics: `(Bond: X%, Affinity: Y%)`.
*   **Family Panel (#NpcFamilyParents / #NpcFamilyChildren):**
    *   **Parents:** Lists registered mother and father. Formatted cleanly: if only one parent is registered, omits redundant placeholder labels (e.g. shows *"kukkie"* instead of *"kukkie & Desconhecido"*).
    *   **Children:** Lists offspring and their current developmental stage.
*   **Interactions Buttons:**
    *   **Chat:** Friendly talk. Increases social need and friendship.
    *   **Joke:** Tells a joke. Risky but fun.
    *   **Flirt:** Romantic action. Automatically hidden for children.
    *   **Gift:** Opens inventory select to hand over the active hotbar item.
    *   **Insult / Scold:** Hostile dialogue for general NPCs. Dynamically switches to **Scold** (*Brigar*) when interacting with your own child.
    *   **Pick Up:** Allows lifting toddlers/children into arms.
    *   **Assign Job:** Attempts to employ the NPC using your held tool. Blocked for children.
    *   **Pregnancy Details:** Opens the pregnancy track screen. Only visible for pregnant female NPCs.

---

### NPC & Player Pregnancy Page
This page shows gestation details. It can be accessed via the NPC Interaction button or by running `/simtale pregnancy` for players.

```
+--------------------------------------------------------------+
| [Image Placeholder: Pregnancy Details UI]                    |
| (Shows father name, elapsed days, progress bar, symptoms)     |
+--------------------------------------------------------------+
```

*   **Subject Name (#SubjectName):** The name of the pregnant player or NPC.
*   **Father Name (#FatherName):** The name of the father entity.
*   **Gestation Stage (#GestationStage):** Displays the current trimester (Trimester 1, 2, or 3).
*   **Days Elapsed (#ElapsedDays):** Shows progress in days (e.g. *"Day 2 / 5"*).
*   **Progress Bar (#ProgressBarFill):** A visual bar representing progress toward birth.
*   **Time Remaining (#TimeRemaining):** Real-time countdown in minutes or days until labor starts (e.g., *"Labour imminent!"* or *"15 minutes remaining"*).
*   **Symptoms (#Symptoms):** Displays status effect notes (e.g., *"Severe fatigue, stamina drained"*).

---

### Bed Debug UI
Accessed via `/simtale debugbeds`, this window helps admins inspect and resolve bed-claiming issues.

```
+--------------------------------------------------------------+
| [Image Placeholder: Bed Debug UI]                            |
| (Grid showing active bed coordinates, owner UUIDs, and buttons)|
+--------------------------------------------------------------+
```

*   **Bed List Grid:** Lists coordinates of all registered bed blocks in loaded chunks.
*   **Claim Status:** Displays whether a bed is claimed and shows the claiming NPC's UUID/Name.
*   **Teleport Button:** Instantly teleports the player to the selected bed coordinates.
*   **Unbind Button:** Forcefully unclaims the bed, making it available for other NPCs.

---

### Sim Debug Panel
Accessed via `/simdebug`, this panel gives administrators full control over NPC simulation variables.

```
+--------------------------------------------------------------+
| [Image Placeholder: Debug Panel UI]                          |
| (Shows current selected NPC, active task, needs sliders, etc) |
+--------------------------------------------------------------+
```

*   **NPC Selectors (#BtnPrevNpc / #BtnNextNpc):** Cycles through all currently loaded active NPCs in the world.
*   **Active AI Task (#NpcTask):** Displays the current running state machine task of the NPC (e.g. *"Task: FINDING_FOOD [DEBUG]"*).
*   **Needs Status (#StatsHunger / #StatsEnergy / #StatsSocial / #StatsHygiene):** Displays real-time need float values.
*   **Debug Actions:**
    *   **Force Eat:** Forces the NPC to locate food immediately.
    *   **Force Sleep:** Sets energy to 0 and forces sleep.
    *   **Force Bath:** Pathfinds the NPC to a bath block.
    *   **Force Social:** Forces wandering/social interaction.
    *   **Set Hunger 0:** Sets hunger to 0 to test starvation warnings and Grim Reaper spawning.
    *   **Reset Needs:** Restores all needs to 100%.

---

## 9. Player Commands Reference

These commands are registered for standard game play under the `"Adventure"` permission group.

### `/simtale spawn <type>`
*   **Description:** Spawns a SimTale NPC near the player.
*   **Parameters:**
    *   `<type>`: The type of NPC to spawn. Must be: `SLOTHIAN`, `TRORK`, `HUMAN_MALE`, `HUMAN_FEMALE`, `CHILD_MALE`, `CHILD_FEMALE`.
*   **Example:** `/simtale spawn HUMAN_FEMALE`
*   **Output:** *"Successfully spawned NPC of type: HUMAN_FEMALE"*

### `/simtale interact`
*   **Description:** Opens the interaction interface for the nearest SimTale NPC.
*   **Parameters:** None.
*   **Usage Context:** Stand close to the NPC you wish to interact with and run the command.
*   **Output:** Opens the **NPC Interaction Page** and prints *"Opened interaction with [NPC Name]"*.

### `/simtale tpall`
*   **Description:** Teleports all currently active NPCs to the player's position.
*   **Parameters:** None.
*   **Output:** *"Teleported X active SimTale NPCs to your position."*

### `/simtale clearall`
*   **Description:** Permanently deletes all active NPCs from the world and database.
*   **Parameters:** None.
*   **Warning:** This deletes all relationship data, children, and stats. It cannot be undone.
*   **Output:** *"Permanently removed X NPCs from world and database."*

### `/simtale marry`
*   **Description:** Forcefully marries the nearest NPC.
*   **Parameters:** None.
*   **Output:** *"You are now married to: [NPC Name]!"*

### `/simtale pregnancy`
*   **Description:** Opens the pregnancy tracker UI page for the executing player.
*   **Parameters:** None.
*   **Usage Context:** Can be run at any time to check trimester progress.

---

## 10. Debug Commands Reference

These administrative commands are used by developers and moderators to skip simulation times and test triggers.

### `/simtale forcespawn [type]`
*   **Description:** Instantly spawns a debug NPC.
*   **Parameters:** 
    *   `[type]` *(Optional)*: `SLOTHIAN`, `TRORK`, `HUMAN_MALE`, `HUMAN_FEMALE`, `CHILD_MALE`, `CHILD_FEMALE`. If omitted, spawns a random Male/Female Human.

### `/simtale forcesleep`
*   **Description:** Forces the nearest active NPC to find a bed and sleep immediately.
*   **Parameters:** None.
*   **Under the hood:** Sets the NPC's energy to 0 and flags `forceSleep = true`.

### `/simtale forcepreg [--target=me|npc]`
*   **Description:** Forces pregnancy on the player or nearest female NPC.
*   **Parameters:**
    *   `--target=me|npc` *(Optional, defaults to me)*: Selects the target.
*   **NPC Behavior:** If `npc` is selected, the nearest female NPC is married to the player, romance is set to 100, and pregnancy starts at Tick 1.

### `/simtale forcebirth [--target=me|npc]`
*   **Description:** Forces immediate childbirth.
*   **Parameters:**
    *   `--target=me|npc` *(Optional, defaults to me)*: Selects the target.
*   **Under the hood:** Adjusts `startTick` to exceed gestation duration, spawning the baby. The player receives a physical baby item in their hotbar.

### `/simtale setstage <stage>`
*   **Description:** Changes the growth stage of the nearest child.
*   **Parameters:**
    *   `<stage>`: Must be `BABY`, `TODDLER`, `CHILD`, `TEEN`, or `ADULT`.
*   **Under the hood:** Updates `GrowthStage`, calculates the corresponding model scale, and teleports birth ticks to align with the new stage timeline.

### `/simtale setmood <mood> [intensity]`
*   **Description:** Sets the temporary emotion/expression of the nearest NPC.
*   **Parameters:**
    *   `<mood>`: `NEUTRAL`, `HAPPY`, `ANGRY`, `SAD`, `SCARED`, `SLEEPY`, `EXCITED`, `BORED`.
    *   `[intensity]` *(Optional)*: A value between `0.0` and `1.0` (defaults to `1.0`).

### `/simtale debugbeds`
*   **Description:** Forces the bed bootstrap system to scan a 96-block radius and opens the **Bed Debug UI**.
*   **Parameters:** None.

### `/simtale debugnear`
*   **Description:** Diagnoses nearby blocks and entities.
*   **Parameters:** None.
*   **Returns:** A chat summary of all active blocks in a 3x3x3 space around the player and all active NPCs within 15 blocks.

### `/simdebug`
*   **Description:** Opens the administrative **Sim Debug Panel**.
*   **Parameters:** None.
