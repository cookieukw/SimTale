# SimTale

*Read this in [Portuguese](README-pt-BR.md)*



SimTale is a social simulation mod for Hytale that implements autonomous NPCs with individual state tracking for needs, personalities, schedules, and relationships.

NPC behavior is driven by internal state and environmental queries rather than fixed scripts or dialogue trees.

The mod includes **800 distinct visual variants** of NPCs:
- 400 Adults (200 Male, 200 Female)
- 400 Children (200 Male, 200 Female)

During instantiation, each NPC is assigned randomized properties for personality, traits, hobbies, and item preferences.

<p align="center">
  <img src="wiki/static/img/variant_1.png" width="49%" />
  <img src="wiki/static/img/variant_2.png" width="49%" />
  <br />
  <img src="wiki/static/img/variant_3.png" width="49%" />
  <img src="wiki/static/img/variant_4.png" width="49%" />
</p>

## What an NPC does on its own

- **Sleeps at night.** When night falls, it drops whatever it is doing and heads for its own bed. NPCs with the "Lazy" trait go to sleep earlier (when energy drops below 60). Guards run the opposite shift: awake at night, asleep during the day.
- **Eats when hungry.** Looks for food in the chests of the house it lives in (within a 24-block radius), and picks the best one: cooked food beats raw meat, and it avoids what it hates.
- **Lives in a house.** Claims a bed and treats that place as its own. 
- **Belongs to a village.** Houses built near each other form one, worked out from the buildings themselves. NPCs without a house stay near the village center instead of wandering off.
- **Works.** Farmers harvest crops (Carrot, Wheat, Tomato, Corn), replant seeds, and deposit the harvest. Hunters and Miners go on expeditions and return with loot.
- **Talks.** Seeks out other NPCs within 20 blocks after too long alone, and mood is contagious.
- **Has a hobby.** Someone who likes fishing walks to the water; a reader goes home.
- **Ages.** Marries, gets pregnant, has children, and those children grow from baby to adult.
- **Starves.** If hunger drops below 5, it cries, stops working entirely, and drops all tasks until someone feeds it. It does not die of hunger — death is reserved for aging and disease.
- **Dies.** When an NPC reaches the end of their life, they enter a dying state. The Grim Reaper spawns to conduct the ceremony and collect their soul.

![Grim Reaper Ceremony Placeholder](wiki/static/img/reaper_ceremony.png)

## Where to start

1. [Installation](#installation)
2. [Getting started](#getting-started) — spawn your first NPC and give it a home
3. [Building a house](#building-a-house)

## The other tracks

This section is for **players**. If you run a server or want to work on the code:

- **[Server](https://simtale.kukkie.org/admin/intro)** — commands, generative AI, balancing and troubleshooting
- **[Developer](https://simtale.kukkie.org/dev/intro)** — architecture, systems, and how to extend the mod

> **Nota:** Documentation in progress
> The mod is in testing and has no public release. Behaviour described here may change between
> versions, and some parts have not been validated in game yet — where that is the case, the page
> says so.
>

---

### Installation

### Requirements

| Item | Version | Required |
|---|---|---|
| Hytale server | `>= 0.5.7` | yes |
| Caskara (database) | `>= 3.0.0` | yes |
| RuneCore | 1.0.12 | yes — used to apply damage and healing to NPCs |
| Java | 21+ | only to build |

> **Nota:** Where these numbers come from
> `ServerVersion` and `Dependencies` in the mod's `manifest.json`. The RuneCore dependency is not
> declared there, but the code calls `com.cookie.runecore.api.StatHelper` — without that jar, hunger
> cannot take or restore health.
> 

### Install

1. Drop `SimTale-1.0.0.jar`, `Caskara.jar` and `RuneCore-1.0.12.jar` into the server's `Mods/` folder.
2. Start the server.
3. Join a world and craft your first Immigration Contract.

```
[SimTale] Scan found 2 new beds and 1 new chests. Totals: 2 beds, 1 chests
```

That line is the furniture scan that runs when you join a world. It only appears when it finds
something new.

### Building from source

```bash
./gradlew deploy
```

The `deploy` task builds the jar and copies it into Hytale's `Mods` folder.

> **Caution:** Close the game before building
> The copy is atomic precisely to avoid this, but building with the game closed is still safer.
> Hytale watches the `Mods` folder and reloads the mod by itself when the file changes — with an
> 11 MB jar, a reload fired mid-write produced `ZipException: invalid LOC header` and no NPC loaded
> at all.
> 

### Verifying it works

Join the game, craft an **Immigration Contract**, and use it.

If an NPC shows up with a name of its own and a diamond floating above its head (the *plumbob*,
which shows mood), the mod is live.

### Uninstalling

Remove the jar from `Mods`. NPC data stays in the Caskara database; reinstalling brings everyone
back.

---

### Getting started

The shortest path between "I installed the mod" and "I have a living village".
### 1. Bring in an NPC

You need to invite a resident to start your village. Craft an **Immigration Contract** at a Fieldcraft bench using:
- 1x Map/Scroll (`Deco_Scroll`)
- 1x Inkwell (`Deco_Inkwell`)
- 1x Light Leather (`Ingredient_Leather_Light`)

Use the contract to spawn a new resident. 

The generated NPC receives one of **800 distinct visual models** and is instantiated with randomized properties for name, personality matrix, behavioral traits, occupation, hobbies, and dietary preferences.

### 2. Build a house

An NPC without a house wanders aimlessly and never sleeps properly. The minimum that counts as a house:

- walls and a roof enclosing the space
- a **door**
- a **light source**
- a **seat**
- a **table**
- a **bed**

To verify your build, point a **House Blueprint** at the bed.

![House Blueprint](wiki/static/img/HouseBlueprint.png)

The tool tells you whether the structure is valid and what is missing. Details in
[Building a house](#building-a-house).

### 3. Let her claim the bed

Once the house is ready, the NPC walks to the bed and registers that place as hers. From then on she lives there: she comes back to sleep, eats from that house's chests, and opens the door on her way in.

Use the **Innkeeper's Ledger** to check who lives where.

![Innkeeper Ledger](wiki/static/img/InnkeepersLedger.png)

### 4. Put out food

Place a chest **inside the house** and leave food in it. NPCs will search for a chest within a 24-block radius when hungry.

> **Caution:** The chest must belong to a house
> NPCs only use chests that belong to a recognised house. A chest dropped in an open field is ignored — which is also what keeps them out of the treasure chests scattered around the world.
> 

See what they can actually reach by using the **Quartermaster's Glass**.

![Quartermaster Glass](wiki/static/img/QuartermastersGlass.png)

It lists every registered chest, the house it belongs to, and how much food is inside.

### 5. Talk to her

Aim at the NPC and press **F**, or right-click. That opens the interaction panel, with hunger, energy, mood, traits, tastes, and the available actions.

<div style="text-align: center;">
  <img src="wiki/static/img/interacting_panel.png" alt="NPC Interaction Panel" />
</div>

The outcomes of interactions are calculated based on relationship status, mood, and traits:
- **Flirt:** Accepted by partners or shy NPCs; rejected by enemies and angry NPCs.
- **Joke:** Fails on enemies, cheers up sad/angry partners.
- **Gift:** Food given below 70 hunger will be eaten immediately, restoring health and altering fun.

See [Interacting with NPCs](#interacting).

### 6. Let time pass

The mod is deliberately slow. Starting from 100 hunger, an NPC takes roughly seven in-game hours to get genuinely hungry. At 5 hunger, they begin to starve, crying and dropping all tasks until fed. Fifteen in-game hours without food will not kill them, but they will refuse to work.

---

### Common early problems

| Symptom | Likely cause |
|---|---|
| The NPC does not sleep | The bed is not registered, or there is no valid house. Check with the **Innkeeper's Ledger**. |
| The NPC does not eat | The chest does not belong to a house. Check with the **Quartermaster's Glass**. |
| No NPC appears on its own | NPCs no longer spawn automatically. You must craft and use an Immigration Contract. |
| The house is rejected | Missing furniture or the space is not enclosed. The **House Blueprint** says which. |

---

### Building a house

A house is not just any build. The mod validates the structure before accepting it, and an NPC only
moves into an approved one.

### The requirements

#### Structure

The space must be **enclosed**: walls and a roof with no gap for the check to leak through. Doors
count as closed wall.

The interior is capped at **512 blocks**. Past that the check gives up and rejects the house — the
cap exists so an open cave is not mistaken for a mansion. (Unless you are trying to build the Mines of Moria, 512 blocks is usually more than enough).

#### Mandatory furniture

| Requirement | Any block whose id contains |
|---|---|
| **Light source** | `torch`, `lantern`, `candle`, `campfire`, `glow`, `lamp`, `chandelier` |
| **Seating** | `chair`, `stool`, `bench`, `seat`, `sofa`, `couch` |
| **Surface** | `table`, `workbench`, `desk`, `counter` |

#### Optional, but you will want it

| Item | Why |
|---|---|
| **Bed** | Without one, nobody lives there. The house is identified *by its bed*. |
| **Chest** | Without one, residents have nowhere to get food. |

### Checking

Point a **House Blueprint** at a registered bed.

![House Blueprint](wiki/static/img/HouseBlueprint.png)

The tool will tell you whether the structure passed and, when it did not, **what is missing**. It also
reports how many interior blocks were visited, and how many doors and chests were found.

> **Tip:** Unloaded chunks get in the way
> If part of the house sits in an unloaded chunk, the check flags the result as incomplete rather
> than rejecting it. Stand near the house when checking.
> 

### The house is identified by its bed

This is the most important detail and the easiest to trip over: **the house's identity comes from
the bed**.

What that means in practice:

- Two beds in the same room can become two houses. (Oh my god, they were roommates...)
- Breaking the resident's bed releases the house.
- Moving the bed can read as a different house.

It is a known limitation, and turning it into an identifier of its own is on the roadmap.

### Doors

A door occupies four blocks, and two doors side by side form a double door. NPCs open and close
them as they pass. (Hodor would be proud).

### Next

[Beds and residents](#beds-and-residents) — how an NPC claims a bed and what happens when two of
them want the same one.

---

### Beds and residents

### Claiming

An NPC without a bed looks for a free one nearby. When it finds one, it walks over, claims it, and
from then on that house is home.

Claiming is exclusive: one bed, one resident. Married couples are the exception — they share a home.

### Beds occupy six blocks

A bed is not one block. It spans **six**, and only one of them is the anchor.

This matters for a reason you can see in game: the sleeping pose is calculated from the anchor. When
the mod used to mount an NPC on one of the other five blocks, she slept crooked, floating beside the
bed, or lying across it like a cross.

The mod now resolves any of the six blocks back to the anchor before putting anyone to bed.

### Who is sleeping where

Use the **Innkeeper's Ledger**.

It opens a screen listing every registered bed with its coordinates and its owner.

The screen also shows the total count, so "no beds registered" is distinguishable from "the list
failed to draw".

### <img src="wiki/static/img/Br.svg" alt="Br" width="38" align="absmiddle" />eaking a bed 

Breaking the bed of a sleeping NPC wakes her up cleanly and releases the house. She will look for
another bed.

### Sleep schedule

| Who | Sleeps |
|---|---|
| Everyone else | at night, the whole night |
| Guards | during the day |

An NPC with full energy still goes to bed at nightfall — the clock decides, not exhaustion. She
stays down until morning, so she will not pop out of bed the moment energy fills up.

Exhaustion is still a separate trigger: an NPC that runs out of energy during the day takes a nap
and wakes when rested.

> **Tip:** Skipping the night wakes them
> Running `time set day` wakes sleeping NPCs immediately, because waking follows the world clock
> rather than a fixed timer.
>

---

### Villages

Build houses close together and they become a village. You do not place anything to make it happen,
and there is no marker to lose.

### How one forms

Two houses belong to the same village when their beds are within about **40 blocks** of each other,
and that chains: if A is near B and B is near C, all three are one village, even when A and C are
far apart.

So a village grows the way you build — outward from what is already there. A long street of houses
30 blocks apart is one village, however long the street gets.

The centre sits at the middle of the beds, and the village reaches from there to its furthest house
plus a little margin.

### It disappears if you tear it down

A house exists because of its bed. Break the bed and the house is gone, and the village recalculates
without it. Break every bed and there is no village left.

> **Nota:** Different from other games on purpose
> In other block-building games, the village centre is often a thing that stays put. Flatten every building and the game still
> treats the ruins as a village. Here the village is worked out from the houses that exist at that
> moment, so there is nothing left behind to be wrong.
> 

### What it changes

**NPCs without a house stop wandering off.** Previously a homeless NPC drifted — each stroll started
from wherever the last one ended, so it got further away indefinitely and you had to go and find it.
Now it strolls around the village instead.

The limit is loose on purpose: an NPC still leaves the village to work, to fish, or to fetch food.
It just will not wander away for no reason.

**Guards patrol the edge.** A guard with nothing to fight walks a circuit around the village
boundary, which is where trouble comes from. A guard with no village stays where it is.

---

### Interacting with NPCs

Aim at an NPC and press **F**, or right-click. The interaction panel opens.

<div style="text-align: center;">
  <img src="wiki/static/img/interacting_panel.png" alt="Painel de Interação do NPC" />
</div>

### What the panel shows

| Section | Contents |
|---|---|
| Header | Name, job, mood |
| Needs | Hunger and energy, colour-coded by severity |
| Traits | Personality traits |
| Identity | Job and hobby, as item icons |
| Tastes | Everything she likes and everything she hates, as icons |
| Family | Parents and children |
| Status | Relationship, friendship and affinity percentages |

The colours on the hunger line follow the thresholds the routine actually uses: green above 50,
yellow below 50, orange below 25, red below 5. At 5, the NPC cries and abandons all tasks.

### Actions

| Button | What it does |
|---|---|
| **Chat** | Conversation. Grants a small boost to Friendship (+5) and Affinity (+5 to +10). If Generative AI is enabled, the NPC will actively write a response back. |
| **Tell joke** | Lands or flops depending on her humour. Fails entirely if enemies. Cheers up sad/angry partners. NPCs with the `FUNNY` trait give a massive +15 affinity boost. |
| **Flirt** | Requires a good baseline relationship. Unlike a certain life simulator game, you can't just spam this 50 times in a row until they marry you. Guaranteed to fail and drop relationship points if enemies, strangers, or angry. Readily accepted by partners or NPCs with the `SHY` trait. |
| **Give gift** | Hands over whatever you are holding. (See below for gift logic) |
| **Insult** | Costs up to -30 trust and affinity, and she remembers it. (Clementine will remember that). Partners will react very poorly. |
| **Scold** | Specific to your children. Reactions vary by age: Teens become angry, Adults become bored, and Babies/Toddlers become sad. Repeated scolding drops trust and affinity. |
| **Assign job** | Sets her job from the tool you are holding (e.g., holding a hoe assigns Farmer). |
| **View pregnancy** | Opens the gestation panel |
| **Inventory** | Opens her inventory |

### Gifts

What she thinks of a gift depends, in this order:

1. **Is it on her favourites list?** Big gain (+30 affinity).
2. **Is it on her hated list?** Big loss (-25 affinity).
3. **Is it hobby-related?** Solid gain (+22 affinity) — below an explicit favourite, above anything generic.
4. **Is it junk?** (dirt, sand, stone, cobweb, bones, poison, scrap) Loss (-20 affinity).
5. **Personality**: `GREEDY` values it more (+25 affinity); `PARANOID` reacts badly (-10 affinity).
6. **Anything else**: small polite gain (+15 affinity).

#### Food is a special case

If her hunger is at 70 or below and the gift is edible, she eats it right there instead of putting it away. That restores hunger and health, cancels any starvation, and alters her fun based on her tastes.

Above 70 hunger, food goes back to being just a gift.

#### The Baby Item

<div style="text-align: center;">
  <img src="wiki/static/img/baby_care.png" alt="Baby Item" />
</div>

The <img src="wiki/static/img/Baby.png" width="24" align="absmiddle"/> **Baby** is initially an item. After some time, it will transform and spawn into a child NPC. (Just make sure you don't leave it inside a chest, unless you want a very confused child spawning in your storage!)

### Marriage

To propose to an NPC, you must gift them a **Wedding Ring**. (One Ring to rule them all... wait, wrong franchise).
The proposal will only be accepted if your relationship with them is at least **80 Romance** and **70 Friendship**. If accepted, the NPC will become your spouse.

![Wedding Ring](wiki/static/img/WeddingRing.png)

### Talking with AI

The mod can route conversation through a generative AI so replies are written on the fly instead of picked from a list. It has to be enabled in the server config — see
[Generative AI](https://simtale.kukkie.org/admin/generative-ai).

> **Nota:** Only through the panel
> AI replies currently work through the interaction panel. Typing in the normal chat always gives the built-in scripted responses, even with AI enabled.
>

---

### Needs

Every NPC carries five needs, all starting at 100 and decaying over time.

| Need | What it drives |
|---|---|
| **Hunger** | Looking for food; at the bottom, it stops doing anything else. (The cake may be a lie, but it fills the bar) |
| **Energy** | Going to bed |
| **Social** | Seeking out other NPCs to talk to |
| **Fun** | Going off to do a hobby. (All work and no play makes the NPC a dull villager) |
| **Hygiene** | Getting into water to bathe. (Removing the pool ladder won't actually trap them) |

Traits change the rates. A `LAZY` NPC burns energy twice as fast; a `FUNNY` one loses fun at half
speed.

### Hunger in detail

Hunger is the need with the sharpest consequences, so it has clear thresholds:

| Hunger | What happens |
|---|---|
| below 50 | looks for food **when idle** |
| below 25 | **drops whatever it is doing** to eat |
| below 5 | starts losing health |

The interruption at 25 exists because a busy NPC would otherwise starve next to a full pantry —
hunger used to be checked only while idle.

#### Timeline

Starting from full hunger:

| Milestone | Hunger | Elapsed |
|---|---|---|
| Looks for food when idle | 70 | ~4.2 h |
| Interrupts its task | 25 | ~10.4 h |
| Stops working and cries | 5 | ~13.2 h |

#### Hunger does not kill

An NPC that runs out of food does not die. It becomes miserable and useless: it drops its job, its
hobby and its social life, and stays that way until someone feeds it. Death is reserved for aging
and disease, which are not implemented yet.

It can still reach food on its own — being starving does not stop it from walking to a chest, and
it does not interrupt sleep.

### What food restores

Food is graded by the game's own item data into three tiers. Raw meat, ingredients and harvested
crops are tier 1; anything cooked or assembled is tier 2 or 3.

| Tier | Hunger | Health |
|---|---|---|
| 1 (raw) | +25 | +6 |
| 2 | +45 | +14 |
| 3 (cooked) | +65 | +24 |

When choosing from a chest, tier wins over taste: a hated pie still beats a beloved slab of raw
beef.

### Feeding by hand

Give food to an NPC whose hunger is at 50 or below and she eats it on the spot instead of pocketing
it — restoring hunger and health, and earning you far more goodwill than an ordinary gift.

Hated food still feeds her. She eats it complaining, with a smaller gain and a hit to her mood.

### Seeing the numbers

Hunger and energy appear at the top of the interaction panel, colour-coded by severity. The colours
follow the same thresholds the routine uses, so the panel and the behaviour never disagree.

---

### Personality and tastes

Every NPC is rolled at spawn and never changes. This is what makes two villagers with the same job
behave differently.

### Traits

| Trait | Effect |
|---|---|
| `AGGRESSIVE` | Conversations can turn into arguments |
| `NEEDY` | Takes insults very heavily, losing massive affinity |
| `SHY` | Unique dialogue and reactions to romantic flirting |
| `LAZY` | Loses energy twice as fast |
| `GREEDY` | Values gifts more highly |
| `PARANOID` | Reacts badly to gifts |
| `FUNNY` | Loses fun at half speed |
| `LOYAL` | Friendships do not decay over time (Planned) |

### Tastes

Each NPC rolls:

- 2–3 **favourite foods** and 2–3 **hated foods**
- 2–3 **favourite items** and 2–3 **hated items**

That is up to six things she dislikes. The interaction panel shows the **whole list** as icons, not
a sample — showing only the first one made NPCs look like they did not hate something they very
much did.

Giving a favourite lands well. Giving something hated costs you affinity and sours her mood.

> **Tip:** Tastes collide
> The pool of foods is small, so two NPCs hating the same thing is common. If a gift goes badly with
> someone you did not expect, open the panel and check her actual list before assuming a bug.
> 

### Favourite season and weather

Cosmetic for now — shown in the panel, used to colour dialogue.

### Mood

Mood is the plumbob above the head: `NEUTRAL`, `HAPPY`, `ANGRY`, `SAD`, `SCARED`, `SLEEPY`,
`EXCITED`, `BORED`.

It reacts to what happens: eating a favourite dish, being insulted, a good conversation, doing a
hobby. Mood is **contagious** — a happy NPC talking to a sad one can lift her.

Any need dropping below 10 makes her miserable regardless of everything else.

---

### Relationships

Every NPC keeps a separate relationship with each player and with other NPCs.

### The numbers

| Value | Meaning |
|---|---|
| **Friendship** | General closeness |
| **Romance** | Romantic interest |
| **Trust** | Willingness to accept requests |
| **Affinity** | Short-term reaction to your last actions |

### Status ladder

`UNKNOWN` → `STRANGER` → `ACQUAINTANCE` → `FRIEND` → `GOOD_FRIEND` → `BEST_FRIEND`

Romantic branch: `DATING` → `ENGAGED` → `MARRIED`.
Negative branch: `RIVAL` and `ENEMY`.

Status changes what she says to you. The same greeting has different wording for a stranger and for
a spouse.

### Raising it

| Action | Effect |
|---|---|
| Chat | Small, reliable gain |
| Tell a joke | Depends on her sense of humour |
| Flirt | Romance, if she is receptive |
| Give a favourite gift | Large gain |
| Feed her when hungry | Large gain — bigger than an ordinary gift |
| Insult | Loss, and she remembers |

### She remembers

NPCs keep a memory of events. Insulting one has an effect that lasts beyond the moment: for a while
afterwards she greets you differently.

### NPC to NPC

NPCs talk to each other on their own when their social need drops. A conversation raises both sides'
social need and builds friendship between them, and that friendship survives a server restart.

Mood spreads through these conversations. An `AGGRESSIVE` NPC, or two who are already enemies, turn
the conversation into an argument instead: both walk away in a worse mood and like each other less.

> **Nota:** Nobody gets dragged out of bed
> An NPC who is asleep or working is never picked as a conversation partner. And if energy runs out
> mid-conversation, she abandons the chat and goes to bed — the partner left behind does not freeze.
> 

### Marriage

Give a wedding ring (`WeddingRing`) to an NPC with high romance and friendship and she
accepts. Married NPCs share a home.

If the numbers are not high enough, she turns you down.

---

### Family and growth

### Pregnancy

A married NPC can become pregnant. Pregnancy runs through three trimesters, and movement speed drops
gradually as it advances.

| Trimester | Symptoms |
|---|---|
| 1st | Slightly increased hunger and energy drain |
| 2nd | Increased drain, moderate slowness |
| 3rd | Intense drain, severe slowness |

Open the **View Pregnancy** panel from the interaction screen to follow the progress: current day,
percentage, and estimated time remaining in real minutes.

Player pregnancy also exists and follows its own path.

### Birth

At the end of gestation the baby is born as an **item** that goes into the inventory. You carry the
baby around, and can hand it to the other parent.

![Baby Item](wiki/static/img/Baby.png)

### Growth stages

| Stage | Notes |
|---|---|
| `BABY` | Carried in the inventory |
| `TODDLER` | |
| `CHILD` | Model scaled down. |
| `TEEN` | Model scaled down slightly. |
| `ADULT` | Full routine: job, house, relationships |

Children grow over time on their own, with the model scaling up at each stage.

### Care

Babies need care. Passing the baby back and forth between parents shares the load, and there is an
offline simulation so time away from the server still counts.

### Death

When an NPC dies, the SimTale death flow takes over: the body stays, and starts bleeding visually. The Grim Reaper appears on its
own, walks to it, performs the soul-collection ritual, leaves a gravestone and removes the record
cleanly. Interacting with the Reaper mid-ritual while holding an <img src="wiki/static/img/Ingredient_Voidheart.png" width="20" align="absmiddle" /> `Ingredient_Voidheart` cancels the
collection and revives the NPC.

![Grim Reaper Ceremony Placeholder](wiki/static/img/reaper_ceremony.png)

> **Nota:** Nothing kills an NPC yet
> Hunger deliberately does not kill. Starving NPCs simply cry and stop working. Aging and disease are not implemented yet.
> The death flow is currently only reachable by administrative testing commands, which exists so the Reaper can be tested without waiting for a cause of death that does not exist.
>

---

### Jobs and hobbies

### Jobs

| Job | What it does |
|---|---|
| `UNEMPLOYED` | Nothing in particular |
| `MINER` | Goes on mining expeditions and returns with ores |
| `FARMER` | Claims a Scarecrow, harvests, and replants specific crops |
| `FISHERMAN` | Claims a Fishing Post and gathers fish |
| `LUMBERJACK` | Claims a Lumber Post and gathers wood. (He is a lumberjack, but very calm. No rage please) |
| `GUARD` | Night watch — sleeps by day |
| `EXPLORER` | Exploring |
| `BUILDER` | Walks to construction sites |
| `HUNTER` | Goes on hunting expeditions and returns with raw meat |

#### Assigning a job

Hold the matching tool and use **Assign Job** in the interaction panel.

| Job | Trigger item contains |
|---|---|
| Miner | `Pickaxe` |
| Farmer | `Hoe` |
| Fisherman | `Tool_Fishing_Trap` |
| Lumberjack | `Hatchet` |
| Guard | `Sword` |
| Explorer | `Map` |
| Builder | `Hammer` |
| Hunter | `Bow` |

An NPC can refuse: each one rolls jobs she likes and jobs she dislikes.

> **Caution:** Matching is by item name
> The check looks for those words inside the item id. A sword named without "sword" in its id will not be recognised. This is a known weak spot.
> 

#### The Farmer

<div style="text-align: center;">
  <img src="wiki/static/img/farmer_job.png" alt="The Farmer" />
</div>

The Farmer requires a **Scarecrow** to act as their workstation. They will walk to ripe crops, harvest them (dropping 1 produce and 1-2 seeds), and then carry everything to a chest in their own house. If they have seeds and there is empty tilled soil nearby, they will replant. 
Supported crops are: Carrot, Wheat, Tomato, and Corn.

#### Expeditions: Miners and Hunters

Miners and Hunters do not actively walk up to rocks or animals. Instead, they go on "Expeditions".
When their work shift begins, their model scales down (shrinks) and they conceptually disappear for about 2 real minutes. When they return, they spawn back at the Village Center or their house, carrying loot based on a weighted table.
- **Miners** return with ores (Copper, Iron, Silver, Gold, Adamantite, etc.).
- **Hunters** return with Raw Meats (Beef, Pork, Chicken).
They immediately walk to their home chest to deposit the loot.

#### The Fisherman

<div style="text-align: center;">
  <img src="wiki/static/img/fisherman_job.png" alt="The Fisherman" />
</div>

The Fisherman requires a **Fishing Post**. You create one simply by placing a **Fishing Trap** block near water (within 8 blocks).
They will walk to this post, perform their gathering animation, and then deposit their catch into their home chest.

#### The Lumberjack

<div style="text-align: center;">
  <img src="wiki/static/img/lumberjack_job.png" alt="The Lumberjack" />
</div>

The Lumberjack requires a **Lumber Post**. You create one simply by placing a **Lumbermill Bench** block near a tree trunk (within 10 blocks).
They will walk to this post, perform their gathering animation, and then deposit the gathered wood into their home chest.

### Hobbies

| Hobby | Where she goes |
|---|---|
| `FISHING` | nearest water |
| `MINING` | nearest stone |
| `GARDENING` | nearest crops |
| `READING` | home |
| `SLEEPING` | home |

When fun drops below 40, she goes and does her hobby, and comes back happy.

If the scenery does not exist — a fisherman in the desert — she does not get stuck looking. She gives up and relaxes at home, recovering fun more slowly.

#### Hobbies matter socially

- **Gifts**: giving a fishing rod to someone whose hobby is fishing is worth far more than an ordinary gift.
- **Conversation**: two NPCs with the same hobby build friendship faster.
- **Work**: a farmer whose hobby is gardening *gains* fun from harvesting. One who would rather be reading loses a little.

---

### Tools and items

SimTale adds a set of craftable tools. They exist so the village can be understood from inside the game, without typing a debug command.

### What they do

| Item | Point at | What happens |
|---|---|---|
| <img src="wiki/static/img/PregnancyTest.png" width="32" align="absmiddle"/> Pregnancy Test | a female villager or female player | Says whether she is expecting, and how far along |
| <img src="wiki/static/img/HouseBlueprint.png" width="32" align="absmiddle"/> House Blueprint | a bed | Reports whether that room counts as a house, and outlines it |
| <img src="wiki/static/img/InnkeepersLedger.png" width="32" align="absmiddle"/> Innkeeper's Ledger | anything | Lists every registered bed and who sleeps in it. (The sacred texts!) |
| <img src="wiki/static/img/QuartermastersGlass.png" width="32" align="absmiddle"/> Quartermaster's Glass | anything | Lists every village chest and what is inside. (Enhance... Enhance... Enhance) |
| <img src="wiki/static/img/InspectorsJournal.png" width="32" align="absmiddle"/> Inspector's Journal | a villager | Her needs, mood, job and what she is doing right now |
| <img src="wiki/static/img/ImmigrationContract.png" width="32" align="absmiddle"/> Immigration Contract | anything | Invites a new resident to settle in the village |
| <img src="wiki/static/img/TownBell.png" width="32" align="absmiddle"/> Town Bell | anything | Rings the town bell, alerting nearby villagers |
| <img src="wiki/static/img/WeddingRing.png" width="32" align="absmiddle"/> Wedding Ring | a villager | Proposes marriage (requires 80 Romance, 70 Friendship) |
| <img src="wiki/static/img/Baby.png" width="32" align="absmiddle"/> Baby | nothing | A carried infant that will eventually spawn as a child NPC |
| <img src="wiki/static/img/BirthdayCake.png" width="32" align="absmiddle"/> Birthday Cake | anything | Crafted but does nothing yet |

### Wedding Ring

<div align="center">
  <img src="wiki/static/img/WeddingRing.png" width="128" style="image-rendering: pixelated;" />
</div>

The Wedding Ring is used to propose marriage to a villager. To successfully propose, you need to have a high relationship with the villager (at least 80 Romance and 70 Friendship). If they accept, they become your spouse! If they reject you, keep trying to improve your relationship before trying again.

### Baby

<div align="center">
  <img src="wiki/static/img/Baby.png" width="128" style="image-rendering: pixelated;" />
</div>

The Baby is a unique item that represents a newborn child. It cannot be crafted. When a villager gives birth, a Baby is generated. After a certain amount of time passes, the Baby item will naturally "grow up" and transform into a new child NPC in the world! 

### Birthday Cake

<div align="center">
  <img src="wiki/static/img/BirthdayCake.png" width="128" style="image-rendering: pixelated;" />
</div>

A festive cake that can be crafted at the Workbench. Currently, the Birthday Cake is just a decorative item and doesn't have a special function yet, but who knows what the future holds for village celebrations!

### Crafting Recipes

All items can be crafted at their respective workstations:

| Item | Bench | Ingredients |
|---|---|---|
| **Pregnancy Test** | <img src="wiki/static/img/Bench_Alchemy.png" width="24" align="absmiddle"/> Alchemybench | 1x <img src="wiki/static/img/Plant_Flower_Common_White.png" width="24" align="absmiddle"/> White Flower, 1x <img src="wiki/static/img/Wood_Softwood_Planks.png" width="24" align="absmiddle"/> Softwood Planks, 1x <img src="wiki/static/img/Ingredient_Life_Essence_Cauliflower.png" width="24" align="absmiddle"/> Cauliflower |
| **House Blueprint** | Inventory (Fieldcraft) | 1x <img src="wiki/static/img/Deco_Map.png" width="24" align="absmiddle"/> Map, 1x <img src="wiki/static/img/Deco_Inkwell.png" width="24" align="absmiddle"/> Inkwell, 1x <img src="wiki/static/img/Deco_Scroll.png" width="24" align="absmiddle"/> Scroll |
| **Innkeeper's Ledger** | Inventory (Fieldcraft) | 1x <img src="wiki/static/img/Deco_Scrap_Book_Pile_Small.png" width="24" align="absmiddle"/> Small Book Pile, 1x <img src="wiki/static/img/Wood_Softwood_Planks.png" width="24" align="absmiddle"/> Softwood Planks, 1x <img src="wiki/static/img/Ingredient_Leather_Light.png" width="24" align="absmiddle"/> Light Leather |
| **Quartermaster's Glass** | <img src="wiki/static/img/Bench_WorkBench.png" width="24" align="absmiddle"/> Workbench | 1x <img src="wiki/static/img/Rock_Crystal_White.png" width="24" align="absmiddle"/> White Crystal, 1x <img src="wiki/static/img/Ingredient_Copper_Bar.png" width="24" align="absmiddle"/> Copper Bar |
| **Inspector's Journal** | Inventory (Fieldcraft) | 1x <img src="wiki/static/img/Deco_Scrap_Book_Pile_Small.png" width="24" align="absmiddle"/> Small Book Pile, 1x <img src="wiki/static/img/Deco_Inkwell.png" width="24" align="absmiddle"/> Inkwell |
| **Immigration Contract** | Inventory (Fieldcraft) | 1x <img src="wiki/static/img/Deco_Scroll.png" width="24" align="absmiddle"/> Scroll, 1x <img src="wiki/static/img/Deco_Inkwell.png" width="24" align="absmiddle"/> Inkwell, 1x <img src="wiki/static/img/Ingredient_Leather_Light.png" width="24" align="absmiddle"/> Light Leather |
| **Town Bell** | <img src="wiki/static/img/Bench_WorkBench.png" width="24" align="absmiddle"/> Workbench | 3x Gold Bar, 2x <img src="wiki/static/img/Wood_Softwood_Planks.png" width="24" align="absmiddle"/> Softwood Planks |
| **Birthday Cake** | Workbench | 1x Apple Pie, 1x Orange Light Source |

#### Item Icons

![Pregnancy Test](wiki/static/img/PregnancyTest.png) ![House Blueprint](wiki/static/img/HouseBlueprint.png) ![Innkeeper Ledger](wiki/static/img/InnkeepersLedger.png) ![Quartermaster Glass](wiki/static/img/QuartermastersGlass.png) ![Inspector's Journal](wiki/static/img/InspectorsJournal.png) ![Immigration Contract](wiki/static/img/ImmigrationContract.png) ![Town Bell](wiki/static/img/TownBell.png) ![Wedding Ring](wiki/static/img/WeddingRing.png) ![Baby](wiki/static/img/Baby.png) ![Birthday Cake](wiki/static/img/BirthdayCake.png)

### House Blueprint

<div align="center">
  <img src="wiki/static/img/HouseBlueprint.png" width="128" style="image-rendering: pixelated;" />
</div>

Right-click a **registered bed** — the bed is what makes a room a house, so anywhere else the tool has no way to know which room you mean.

You get a verdict (valid, or the list of what is missing), a summary of interior size, doors and chests, and the floor of the room lit up for about twelve seconds: green if the house is valid, red if it is not. The outline is the floor only — filling the whole interior would replace the room with a coloured brick and hide what you are looking at.

> **Nota:** It deliberately does not register anything
> A tool for checking should not change what it checks. If the blueprint registered the house, you would create residences by accident while inspecting them. Houses are still created by an NPC claiming the bed.
> 

The one thing it does perfectly: it knows exactly which bed you mean. Checking proximity by yourself stops being good enough the moment two houses share a wall.

### The three lenses

<div align="center">
  <img src="wiki/static/img/InnkeepersLedger.png" width="128" style="image-rendering: pixelated; margin: 0 10px;" />
  <img src="wiki/static/img/QuartermastersGlass.png" width="128" style="image-rendering: pixelated; margin: 0 10px;" />
  <img src="wiki/static/img/InspectorsJournal.png" width="128" style="image-rendering: pixelated; margin: 0 10px;" />
</div>

The Ledger, the Glass and the Journal are read-only views over data the mod already keeps. The first two open the bed and chest overview screens.

> **Caution:** Why Teleport is not on them
> The bed and chest registries hold every entry in the world. A craftable item with a teleport button next to each one is not a village tool, it is the fastest travel in the game. Same reasoning, less dramatically, for Unclaim and Remove: these items are lenses, never levers.
> 

The Journal does not simply dump internal debug state. A raw dump would show role state, animation slots, movement flags and search cooldowns — what you want when the AI is misbehaving, and noise when you just want to know if someone is hungry. The Journal reports the five needs, mood, job, where she lives, and what she is doing in plain words rather than the internal task name.

---

### FAQ

#### Why won't my NPC sleep?

Most likely there is no registered bed. Use the **Innkeeper's Ledger** — if the list is empty, the bed was
never picked up. Placing a new bed registers it immediately.

Also check that she actually claimed a house: an NPC with no home has no bed to go back to.

#### Why won't she eat, with a chest full of food right there?

The chest has to belong to a **recognised house**. Chests in the open, and chests generated by the
world, are ignored on purpose.

Use the **Quartermaster's Glass**: if the row says "no house", that is the problem. Make the room around it
a valid house.

#### She got angry at a gift I thought she liked

Open the panel and read her **hated** list. Each NPC hates up to six different things, and the pool
is small enough that collisions between NPCs are common.

#### Why is everything so slow?

By design. An NPC takes around four in-game hours to start looking for food and thirteen to hit
rock bottom. The mod is built to run in the background while you play, not to be supervised.

#### Do guards ever sleep?

Yes — during the **day**. They hold the night watch, so their whole routine is inverted. Their
energy drains like everyone else's and refills in bed, just on the opposite schedule.

#### Can NPCs open doors?

Yes. They open on the way through and close behind them.

#### Do NPCs survive a server restart?

Yes. Names, needs, relationships, houses and jobs are all persisted.

#### Can I change how fast they get hungry?

Not from a config file yet — the values are constants in the code. See
[Balancing](https://simtale.kukkie.org/admin/balancing).

#### How do I make NPCs do hobbies?

You don't need to order them. Just place blocks that contain `leisure_fishing`, `leisure_mining`, or `leisure_gardening` in the world. If the NPC has that hobby and their fun need is low, they will interact with the block on their own.

#### How do I create a village? Do I need a center block?

No. A village forms automatically when you build houses close to each other (around 40 blocks between beds). The village expands naturally as you build more houses in the same area.

#### How do NPCs have babies and how do I care for them?

Married NPCs that share a home can have babies. Babies need care, and you (or the parents) can carry them on your shoulders or back while performing other tasks.

#### How do I put a child down?

Crouch and right-click on any block (you must actually click a block, right-clicking the air will not work). If for any reason that fails, you can always use the command `/simtale putdown` in the chat.

#### An NPC died. Can I bring them back?

Yes. When the Grim Reaper appears to collect their soul, interact with her during the ritual while holding a Void Heart (`Ingredient_Voidheart`) to cancel the collection and revive the NPC.