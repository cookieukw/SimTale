---
sidebar_position: 1
title: Adding a job
---

# Adding a job

Worked example: adding a `BAKER`.

## 1. The enum

`core/Profession.java`:

```java
BAKER("Padeiro", EnumSet.of(JobType.CRAFT), "rolling_pin", true),
```

| Argument | Meaning |
|---|---|
| `ptName` | Nome de exibição legado — a UI agora lê do `.lang`, então este é um fallback |
| `allowedJobs` | Quais `JobType`s a profissão pode executar |
| `triggerItemKeyword` | Substring do id do item que atribui o emprego |
| `safeForChildren` | `true` se crianças podem exercer o emprego, `false` caso contrário (espera até virar TEEN) |

:::caution Verify the keyword against real ids
`fromItemId` matches by substring on the item id. Check that an item containing your keyword
actually exists:

```bash
find Server/Item/Items -iname "*rolling*"
```

If nothing comes back, the job can never be assigned. This is exactly how bow and sword matching
became fragile.
:::

Order matters: `fromItemId` returns the **first** match walking the enum in declaration order. A
keyword that is a substring of another profession's will shadow it.

## 2. Translations

Both files, together:

```
# Server/Languages/en-US/ui.lang
prof.baker = Baker

# Server/Languages/pt-BR/ui.lang
prof.baker = Padeiro
```

The key is `ui.prof.` plus the enum name lowercased. A missing key renders as the raw key in game —
`prof.hunter` was missing for a long time and nobody noticed.

## 3. Showcase icon

`logic/NPCShowcaseItems.java`:

```java
case BAKER -> "Food_Bread";
```

Verify the id exists:

```bash
ls Server/Item/Items/Food/Food_Bread.json
```

Return `null` if there is no sensible item; the slot hides itself.

## 4. Behaviour (optional)

Without work behaviour the job is cosmetic: it shows in the panel and colours dialogue.

To give it work, add a `JobType` and handle it in `NPCWorkHelper`, following how `FARM` and `HUNT`
are implemented — find target, walk, timeout, act, deposit.

Do not skip the timeout. An NPC that cannot reach its target and has no timeout re-issues the same
leash point forever.

## 5. Check the preference pool

`NPCPreferences` rolls liked and disliked professions from `PROFESSION_POOL`. Add the new one there
if NPCs should be able to have an opinion about it.

## Checklist

- [ ] Enum entry, keyword verified against a real item
- [ ] `prof.<name>` in **both** language files
- [ ] Showcase icon, id verified in the assets
- [ ] `JobType` and `NPCWorkHelper` branch, if it should work
- [ ] Added to `PROFESSION_POOL` if relevant
