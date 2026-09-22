---
sidebar_position: 3
title: Adding a command
---

# Adding a command

All commands are defined as nested classes that extend `AbstractPlayerCommand`. Depending on the purpose of the command, they are placed in one of the command classes in the root package, such as `SimTaleCommand.java` (for general `/simtale` subcommands), `DebugCommands.java`, or `SimDebugCommand.java` (for `/simdebug`).

## Skeleton

```java
private static class MyThingSubCommand extends AbstractPlayerCommand {
    public MyThingSubCommand() {
        super("mything", "What it does, shown in help");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        ctx.sendMessage(Message.raw("[SimTale] done."));
    }
}
```

Register it in the constructor:

```java
this.addSubCommand(new MyThingSubCommand());
```

Forgetting this is the most common mistake — the class compiles and the command simply does not
exist.

## Arguments

```java
private final RequiredArg<String> stageArg;

public MyThingSubCommand() {
    super("mything", "...");
    this.stageArg = this.withRequiredArg("stage", "BABY|TODDLER|CHILD", ArgTypes.STRING);
}
```

Read with `ctx.get(this.stageArg)`.

:::caution Flags, and no decimals
Hytale passes arguments as named flags (`--stage=BABY`), not positionally, and the parser rejects
decimal points. If you need a fractional value, take integer hundredths.
:::

## Structural writes

A command runs outside the tick, but the store may still be processing. Anything structural —
`addComponent`, `removeComponent`, `addEntity` — should go through:

```java
world.execute(() -> store.addComponent(ref, TYPE, value));
```

Mutating fields of an existing component is safe directly.

## Messages

Prefer `Message.translation("general.something")` over `Message.raw` for anything a player reads.
Debug commands using `raw` is acceptable.

## Opening a UI page

```java
Player player = store.getComponent(ref, Player.getComponentType());
if (player == null) return;
player.getPageManager().openCustomPage(ref, store, new MyPage(playerRef, player));
```

If the page reads from a registry that a scan populates, **run the scan first**. `debugbeds` showed
an empty list for a long time precisely because it opened the page without scanning, while
`housecheck` scanned as a side effect and looked like it worked.

## Reporting nothing found

Distinguish "nothing exists" from "nothing near you". A single-target command cannot, which is why
`chestcheck` was misleading and `debugchests` replaced it.

Include counts in your output.

## Checklist

- [ ] Class created
- [ ] `addSubCommand` in the constructor
- [ ] Structural writes deferred with `world.execute`
- [ ] Scan before reading a lazily populated registry
- [ ] Output distinguishes empty from out-of-range
