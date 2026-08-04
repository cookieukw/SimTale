# SimTale Wiki Blueprint

Plan for the mod's public documentation site. This file is the **map**: stack, folder layout, the
three content tracks, and the editorial rule the wiki is built on. The pages themselves live in
`docs/`, `admin/` and `dev/`.

---

## 1. Stack

**Docusaurus 3** (React, Node 18+). Site language: **English**.

| Reason | Detail |
|---|---|
| Three distinct audiences | Each track gets its own sidebar and its own navbar entry. A player never trips over ECS documentation. |
| Doc versioning | Once the mod has releases, `docusaurus docs:version 1.0` freezes that version's wiki. A game mod breaks saves between versions; the docs have to follow. |
| i18n | English is the base. pt-BR can be added later without restructuring. |
| MDX | Allows React components in pages: filterable command tables, a hunger-timeline calculator, styled callouts. |

**Accepted cost**: needs Node and a build step, unlike a markdown-only generator. Given the wiki will
carry interactive reference tables, it pays for itself.

---

## 2. Folder layout

```
wiki/
├── BLUEPRINT.md              ← this file
├── package.json
├── docusaurus.config.js
├── sidebars.js
├── docs/                     ← PLAYER track (route /)
│   ├── intro.md
│   ├── installation.md
│   ├── getting-started.md
│   ├── houses/
│   │   ├── building-a-house.md
│   │   └── beds-and-residents.md
│   ├── npcs/
│   │   ├── needs.md
│   │   ├── personality-and-tastes.md
│   │   ├── jobs-and-hobbies.md
│   │   ├── relationships.md
│   │   └── family-and-growth.md
│   ├── interacting.md
│   └── faq.md
├── admin/                    ← SERVER track (route /admin)
│   ├── intro.md
│   ├── commands.md
│   ├── generative-ai.md
│   ├── balancing.md
│   └── troubleshooting.md
├── dev/                      ← DEVELOPER track (route /dev)
│   ├── intro.md
│   ├── ecs-architecture.md
│   ├── build-environment.md
│   ├── systems/
│   │   ├── routine-ai.md
│   │   ├── furniture-registry.md
│   │   ├── hunger-and-sleep.md
│   │   ├── houses.md
│   │   └── persistence.md
│   ├── recipes/
│   │   ├── add-a-job.md
│   │   ├── add-a-hobby.md
│   │   └── add-a-command.md
│   └── lessons-learned.md
├── src/css/custom.css
└── static/img/
```

`admin/` and `dev/` are extra `content-docs` plugin instances. The player track uses the default
`docs/` folder mounted at the site root.

---

## 3. The three tracks

### 🎮 Player — "how does this behave in game"

No class names, no ECS. Talks about **observable behaviour**: why the NPC will not sleep, what makes
a house valid, what happens when food runs out.

Writing rule: if a sentence needs a `.java` filename to make sense, it belongs in the dev track.

### 🛠️ Server admin — "how do I operate this"

Full command reference, generative AI setup, the constants that can be tuned, and troubleshooting
tied to **real log lines**.

### 🧩 Developer — "how is this built"

Architecture, systems, extension recipes. Reuses what already exists in the repository's
`docs/sistemas/` rather than rewriting it.

Includes a page most projects skip and this one needs: **lessons learned**, documenting the failure
patterns that have already cost time here.

---

## 4. Editorial rule: nothing unverified

Rule number one, drawn from real mistakes in this project:

> Every command, item id, asset name and filename cited in the wiki must be checked against the code
> or the game assets before it is published.

The history behind the rule:

| Case | What happened |
|---|---|
| `SimTale_Beds` | Asset created with the wrong type. Plausible format, unverified existence. Broke spawning. |
| Child model paths | 34 models written without the `NPC/Player_Child/` prefix. Format validated, existence not. |
| `ItemPreviewComponent` | A UI component documented with convincing examples. Absent from all 135 shipped `.ui` files. |
| `hytale:sword_iron` | Invented namespace. Real ids are `Weapon_Sword_Copper`. |

Reference pages therefore state **where the data was verified**.

---

## 5. Writing order

| Phase | Pages | Why first |
|---|---|---|
| 1 | `intro`, `installation`, `getting-started` | Without these, nobody uses the mod |
| 2 | `admin/commands` | Most consulted reference, and the easiest to verify |
| 3 | `houses/*`, `npcs/needs` | The two systems that generate the most confusion |
| 4 | `dev/ecs-architecture`, `dev/lessons-learned` | Brings contributors in |
| 5 | The rest | Fill-in |

All five phases are drafted. What remains is validating them against a running build.

---

## 6. Working commands

```bash
cd wiki
npm install
npm run start      # http://localhost:3000, hot reload
npm run build      # static output in build/
```

Suggested publishing: GitHub Pages via an Actions workflow triggered on push to the main branch.

`onBrokenLinks: 'throw'` is set deliberately — a dead link fails the build rather than shipping.

---

## 7. What this blueprint does NOT decide

- **Custom domain**: GitHub Pages is enough for now.
- **Portuguese translation**: the structure supports it, but writing two languages before the content
  settles doubles the rework. Revisit at public release.
- **Algolia search**: DocSearch requires a public site. Until then, Docusaurus local search serves.
- **Screenshots**: `static/img/` is empty. The house-building and interaction-panel pages are the
  ones that would benefit most.
