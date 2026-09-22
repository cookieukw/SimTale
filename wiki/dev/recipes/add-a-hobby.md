---
sidebar_position: 2
title: Adding a hobby
---

# Adding a hobby

Worked example: `COOKING`.

## 1. The enum

`Hobby` is nested inside `NPCPreferences`, despite being written at zero indentation. Import it as:

```java
import com.cookieukw.SimTale.core.NPCPreferences.Hobby;
```

Add the value:

```java
COOKING("Culinária"),
```

## 2. Translation

```
# en-US/ui.lang
hobby.cooking = Cooking

# pt-BR/ui.lang
hobby.cooking = Culinária
```

## 3. Where the NPC goes

`NPCLeisureHelper` maps a hobby to a target in the world — water for fishing, stone for mining,
crops for gardening. Reading and sleeping fall back home.

Add your case. If there is no scenery to find, fall back to relaxing at home with a slower fun
recovery. **Never leave a hobby that can search forever**: a fisherman spawned in a desert must give
up, not freeze.

## 4. Hobby items

`NPCLeisureHelper.isHobbyItem` decides whether a gift is hobby-related, which is worth a large
affinity bonus and the `gift.hobby` line.

## 5. Showcase icon

`NPCShowcaseItems.forHobby`:

```java
case COOKING -> "Food_Pie_Meat";
```

The switch has no `default`, so the compiler will tell you when a new value is unhandled. Keep it
that way.

## 6. Work synergy (optional)

A farmer whose hobby is gardening gains fun from harvesting. If your hobby lines up with a job, wire
the same connection.

## Checklist

- [ ] Enum value
- [ ] `hobby.<name>` in both language files
- [ ] Target in `NPCLeisureHelper`, **with a fallback**
- [ ] Hobby items for the gift bonus
- [ ] Showcase icon, id verified
