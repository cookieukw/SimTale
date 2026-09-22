# Screenshot shot list

What to capture, where it goes, and why it earns its place. Ordered by value.

**Guiding rule**: a screenshot has to carry information the text cannot. A picture of a paragraph is
decoration; a picture of a *spatial arrangement* is documentation.

**Format**: PNG, cropped tight, saved to `static/img/`. Dark UI on a dark site — avoid shots washed
out by daylight where possible.

---

## Priority 1 — spatial things text describes badly

These are the ones worth doing first. They are also the most stable: world geometry does not change
between builds the way a UI panel does.

### 1. A minimal valid house

**Page**: `docs/houses/building-a-house.md`
**File**: `house-minimal.png`

Interior of the smallest build that passes `housecheck`, with the light, seat, table, bed and chest
all visible in one frame. Ideally a cutaway (one wall removed) so the layout reads.

Why it matters most: "walls, roof, light, seat, table" is a list players get wrong constantly. One
picture ends the confusion.

### 2. The same house failing the check

**Page**: same
**File**: `house-invalid.png`

The chat output of `/simtale housecheck` reporting what is missing. Shows people the tool exists and
that it *tells you the answer*.

### 3. A bed occupying six blocks

**Page**: `docs/houses/beds-and-residents.md` and `dev/systems/furniture-registry.md`
**File**: `bed-six-blocks.png`

The bed with a block grid visible, or the `/inspectfiller` output. The "a bed is not one block"
point is the root of a whole class of bugs, and a picture makes it obvious.

### 4. An NPC sleeping, aligned

**Page**: `docs/houses/beds-and-residents.md`
**File**: `npc-sleeping.png`

Lying properly on the mattress. Doubles as a regression reference — if a future build breaks the
pose again, this is what "correct" looked like.

### 5. A village at night

**Page**: `docs/intro.md`
**File**: `village-night.png`

Everyone indoors, one guard awake outside. This is the single image that sells what the mod does,
and it is the header shot for the landing page.

---

## Priority 2 — proof that a feature exists

### 6. `/simtale debugchests`

**Page**: `admin/commands.md`, `docs/getting-started.md`
**File**: `debug-chests.png`

The screen with a few chests listed, showing the house link and the food count. Ideally with one row
reading "no house" so the contrast is visible.

### 7. `/simtale debugbeds`

**Page**: `docs/houses/beds-and-residents.md`
**File**: `debug-beds.png`

### 8. Plumbob showing mood

**Page**: `docs/npcs/personality-and-tastes.md`
**File**: `plumbob-moods.png`

Two NPCs side by side with different moods, so the colour difference is the subject.

---

## Priority 3 — UI, last on purpose

### 9. The interaction panel

**Page**: `docs/interacting.md`
**File**: `interaction-panel.png`

Wait until the panel settles. It changed three times in one session; a shot taken now is likely to
be wrong within a week.

When you do take it, pick an NPC with a **full** taste list — six icons, not two — so the picture
shows the feature at its most informative.

### 10. Feeding a hungry NPC

**Page**: `docs/npcs/needs.md`
**File**: `feeding.png`

The `gift.fed` line in chat with the hunger bar visibly higher afterwards. Two-frame story, so
either a before/after pair or a single frame where both are legible.

---

## Not worth shooting

| Idea | Why not |
|---|---|
| Every command's output | Text already reproduces it exactly, and it rots with every message change |
| The full command list in-game | The table on the page is better: searchable, copy-pasteable |
| Code in an editor | Copy the code into a fenced block instead |
| Decorative builds | Nice, but they document nothing |

---

## A note on GIFs

An NPC walking to a chest, taking food and eating is a **sequence** — a still cannot show it. If any
capture is worth being animated, that one is, plus a door opening and closing as an NPC passes.

Keep them short and small; a 20 MB GIF on the landing page hurts more than it helps.
