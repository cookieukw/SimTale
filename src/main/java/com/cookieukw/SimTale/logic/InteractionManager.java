package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.core.AssetIds;

import com.cookie.caskara.Caskara;
import com.cookieukw.SimTale.SimTale;
import com.cookie.runecore.api.StatHelper;
import com.cookieukw.SimTale.ai.AiConfigManager;
import com.cookieukw.SimTale.systems.NPCFoodHelper;
import com.cookieukw.SimTale.ai.AiMessage;
import com.cookieukw.SimTale.ai.AiRequest;
import com.cookieukw.SimTale.ai.NpcContextBuilder;
import com.cookieukw.SimTale.core.NeedsHelper;
import com.cookieukw.SimTale.core.MemoryEvent;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.lifecycle.GrowthStage;
import com.cookieukw.SimTale.core.lifecycle.ParentChildBond;
import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.RelationshipStatus;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.Trait;
import com.cookieukw.SimTale.core.WorldUtil;
import com.cookieukw.SimTale.core.lifecycle.GrowthComponent;
import com.cookieukw.SimTale.core.lifecycle.LifecycleManager;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.systems.NPCLeisureHelper;
import com.cookieukw.SimTale.systems.SimTaleJuiceHelper;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Function;
import java.util.concurrent.ThreadLocalRandom;

public class InteractionManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
      * @param rejected the interaction did not happen (no item held, wrong tool, already
      *                 married, ...). Only the message is delivered; nothing is applied.
      */
    private record InteractionOutcome(
        int friendship,
        int romance,
        int trust,
        int affinity,
        Message response,
        MemoryEvent memoryEvent,
        boolean consumeItem,
        boolean rejected
    ) {
        public static InteractionOutcome of(int f, int r, int t, int a, Message msg, MemoryEvent event) {
            return new InteractionOutcome(f, r, t, a, msg, event, false, false);
        }
        
        public static InteractionOutcome ofItem(int f, int r, int t, int a, Message msg, MemoryEvent event, boolean consume) {
            return new InteractionOutcome(f, r, t, a, msg, event, consume, false);
        }

        public static InteractionOutcome error(Message msg) {
            return new InteractionOutcome(0, 0, 0, 0, msg, null, false, true);
        }
    }

    private record DailyState(boolean cooldownActive, boolean missedLongTime) {}

    private static final Set<String> FOOD_KEYWORDS = Set.of(
        "food", "apple", "bread", "meat", "fish", "carrot", 
        "potato", "soup", "fruit", "berry", "cookie", "pie"
    );

    private enum GiftCategory {
        TRASH(
            Set.of("dirt", "soil_sand", "rock_stone", "spiderweb", "gravel", "deco_trash",
                   "trash", "bone", "poison", "weed", "scrap", "sludge"),
            Set.of("terra", "pedra", "teia", "cascalho", "areia", "lixo",
                   "osso", "veneno", "ervas", "sucata")
        ),
        BASIC(
            Set.of("stone", "wood", "cobble", "gravel", "sand", "plank", "seed", "sapling",
                   "food_beef_raw", "food_chicken_raw", "food_pork_raw", "food_egg", "food_wildmeat_raw"),
            Set.of()
        );

        private final Set<String> idKeywords;
        private final Set<String> nameKeywords;

        GiftCategory(Set<String> idKeywords, Set<String> nameKeywords) {
            this.idKeywords = idKeywords;
            this.nameKeywords = nameKeywords;
        }

        boolean matches(String itemIdLower, String itemNameLower) {
            return idKeywords.stream().anyMatch(itemIdLower::contains)
                || nameKeywords.stream().anyMatch(itemNameLower::contains);
        }

        static Optional<GiftCategory> classify(String itemIdLower, String itemNameLower) {
            return Arrays.stream(values())
                .filter(c -> c.matches(itemIdLower, itemNameLower))
                .findFirst();
        }
    }

    public static Message performInteraction(SimNPCComponent npc, UUID playerUuid, PlayerRef playerRef, InteractionType type) {
        boolean isChild = isNpcAChild(npc);

        if (isChild && type == InteractionType.ROMANTIC) {
            return Message.translation("npc-dialogues.flirt.child");
        }

        Relationship rel = npc.getRelationship(playerUuid);
        DailyState daily = refreshDailyState(rel);

        if (daily.cooldownActive()) {
            return getCooldownMessage(type, npc.name);
        }

        InteractionOutcome outcome = switch (type) {
            case FRIENDLY -> handleFriendly(npc, playerUuid, playerRef, rel, daily.missedLongTime());
            case FUNNY -> handleFunny(npc, playerUuid, rel);
            case ROMANTIC -> handleRomantic(npc, rel);
            case MEAN -> handleMean(npc, rel);
            case SCOLD -> handleScold(npc, playerUuid, rel);
            case RANDOM -> handleRandom(rel);
            case GIFT -> handleGift(npc, playerUuid, playerRef, rel, isChild);
            case ASSIGN_PROFESSION -> handleProfession(npc, playerRef, rel);
        };

        // Explicit flag instead of inferring rejection from the data shape. The old check was
        // `memoryEvent == null && friendship == 0 && affinity == 0`, which silently threw away
        // any legitimate outcome that happened to have no friendship/affinity delta.
        if (outcome.rejected()) {
            return outcome.response();
        }

        rel.interactionsToday++;
        applyOutcome(npc, playerUuid, rel, outcome, isChild);

        if (outcome.consumeItem()) {
            consumeHeldItemFromPlayer(playerRef);
        }

        SimNPCPersistence.saveNPC(npc);
        return outcome.response();
    }

    private static InteractionOutcome handleFriendly(SimNPCComponent npc, UUID playerUuid, PlayerRef playerRef, Relationship rel, boolean missedLongTime) {
        Message response = getContextualGreeting(npc, playerUuid, playerRef, rel);
        if (missedLongTime) {
            response = Message.translation("npc-dialogues.context.missed").param("name", npc.name).insert(Message.raw(" ")).insert(response);
        }
        
        int fGain = rel.status == RelationshipStatus.STRANGER ? 8 : (rel.status == RelationshipStatus.ENEMIES ? 1 : 5);
        int aGain = rel.status == RelationshipStatus.STRANGER ? 10 : 5;
        
        // Trigger Generative AI async call to reply in the background
        if (SimTale.aiManager != null && AiConfigManager.getConfig().enabled) {
            AiRequest aiRequest = NpcContextBuilder.build(
                    npc,
                    playerUuid,
                    playerRef != null ? playerRef.getUsername() : "Player",
                    List.of(new AiMessage("user", "The player is starting a friendly conversation with you. Say hello or reply to them."))
            );
            SimTale.aiManager.generateAsync(aiRequest)
                .thenAccept(aiRes -> {
                    if (!aiRes.success()) {
                        LOGGER.atWarning().log("SimTale: provedor de IA falhou: " + aiRes.errorMessage());
                        return;
                    }
                    if (playerRef == null) return;

                    // The callback runs on a CompletableFuture worker. sendMessage touches
                    // engine state, so it has to be handed back to the world thread.
                    WorldUtil.execute(() ->
                            playerRef.sendMessage(Message.raw("[" + npc.name + "] " + NpcContextBuilder.stripLeadingNameTag(aiRes.text()))));
                })
                // Without this, any exception inside the callback (or the HTTP call) vanished
                // into the CompletableFuture with no trace at all.
                .exceptionally(ex -> {
                    LOGGER.atWarning().log("SimTale: erro na resposta assincrona da IA: " + ex);
                    return null;
                });
        }
        
        return InteractionOutcome.of(fGain, 0, 1, aGain, response, MemoryEvent.CHATTED);
    }

    private record FunnyContext(SimNPCComponent npc, Relationship rel, Mood mood) {}
    private record FunnyRule(Predicate<FunnyContext> condition, Function<FunnyContext, InteractionOutcome> outcome) {}

    private static final List<FunnyRule> FUNNY_RULES = List.of(
        new FunnyRule(ctx -> ctx.rel().status == RelationshipStatus.ENEMIES,
                      ctx -> InteractionOutcome.of(-2, 0, 0, -5, pickRandomTranslation("npc-dialogues.funny.enemy", 3, ctx.npc().name), MemoryEvent.JOKED)),
        new FunnyRule(ctx -> (ctx.mood() == Mood.ANGRY || ctx.mood() == Mood.SAD) && (ctx.rel().status == RelationshipStatus.BEST_FRIEND || ctx.rel().status == RelationshipStatus.PARTNER),
                      ctx -> InteractionOutcome.of(2, 0, 1, 5, pickRandomTranslation("npc-dialogues.funny.cheerup", 3, ctx.npc().name), MemoryEvent.JOKED)),
        new FunnyRule(ctx -> ctx.mood() == Mood.ANGRY || ctx.mood() == Mood.SAD,
                      ctx -> InteractionOutcome.of(-2, 0, 0, -5, pickRandomTranslation("npc-dialogues.funny.angry", 5, ctx.npc().name), MemoryEvent.JOKED)),
        new FunnyRule(ctx -> ctx.npc().personality.traits.contains(Trait.FUNNY),
                      ctx -> InteractionOutcome.of(5, 0, 2, 15, pickRandomTranslation("npc-dialogues.funny.trait", 5, ctx.npc().name), MemoryEvent.JOKED))
    );

    private static InteractionOutcome handleFunny(SimNPCComponent npc, UUID playerUuid, Relationship rel) {
        // The young voices come before the rule table on purpose. Those rules branch on mood and
        // relationship status, which are the right axes for an adult — but a nine-year-old finding
        // a bad joke hilarious is funnier and truer than the same "ENEMIES so they scoff" line
        // everyone else gets.
        String youngKey = ChildDialogue.keyFor(npc, playerUuid, "joke");
        if (youngKey != null) {
            return InteractionOutcome.of(4, 0, 2, 7,
                    pickRandomTranslation(youngKey, YOUNG_LINE_VARIANTS, npc.name), MemoryEvent.JOKED);
        }

        FunnyContext ctx = new FunnyContext(npc, rel, npc.getMood());
        return FUNNY_RULES.stream()
            .filter(r -> r.condition().test(ctx))
            .findFirst()
            .map(r -> r.outcome().apply(ctx))
            .orElseGet(() -> InteractionOutcome.of(3, 0, 1, 5, pickRandomTranslation("npc-dialogues.funny.normal", 5, npc.name), MemoryEvent.JOKED));
    }

    /** How many variants each young-voice line set ships with. */
    private static final int YOUNG_LINE_VARIANTS = 5;

    private record RomanticContext(SimNPCComponent npc, Relationship rel, Mood mood) {}
    private record RomanticRule(Predicate<RomanticContext> condition, Function<RomanticContext, InteractionOutcome> outcome) {}

    private static final List<RomanticRule> ROMANTIC_RULES = List.of(
        new RomanticRule(ctx -> ctx.rel().status == RelationshipStatus.ENEMIES,
                         ctx -> InteractionOutcome.of(-5, -15, -5, -20, pickRandomTranslation("npc-dialogues.romantic.enemy", 3, ctx.npc().name), MemoryEvent.FLIRTED)),
        new RomanticRule(ctx -> ctx.rel().status == RelationshipStatus.STRANGER || ctx.rel().status == RelationshipStatus.ACQUAINTANCE,
                         ctx -> InteractionOutcome.of(-3, -5, -2, -10, pickRandomTranslation("npc-dialogues.romantic.stranger", 3, ctx.npc().name), MemoryEvent.FLIRTED)),
        new RomanticRule(ctx -> ctx.mood() == Mood.ANGRY,
                         ctx -> InteractionOutcome.of(0, -10, -2, -15, pickRandomTranslation("npc-dialogues.romantic.reject", 5, ctx.npc().name), MemoryEvent.FLIRTED)),
        new RomanticRule(ctx -> ctx.rel().status == RelationshipStatus.MARRIED || ctx.rel().status == RelationshipStatus.PARTNER,
                         ctx -> InteractionOutcome.of(2, 10, 2, 10, pickRandomTranslation("npc-dialogues.romantic.partner", 5, ctx.npc().name), MemoryEvent.FLIRTED)),
        new RomanticRule(ctx -> ctx.npc().personality.traits.contains(Trait.SHY),
                         ctx -> InteractionOutcome.of(0, 15, 2, 10, pickRandomTranslation("npc-dialogues.romantic.shy", 5, ctx.npc().name), MemoryEvent.FLIRTED))
    );

    private static InteractionOutcome handleRomantic(SimNPCComponent npc, Relationship rel) {
        RomanticContext ctx = new RomanticContext(npc, rel, npc.getMood());
        return ROMANTIC_RULES.stream()
            .filter(r -> r.condition().test(ctx))
            .findFirst()
            .map(r -> r.outcome().apply(ctx))
            .orElseGet(() -> InteractionOutcome.of(0, 10, 1, 5, pickRandomTranslation("npc-dialogues.romantic.normal", 5, npc.name), MemoryEvent.FLIRTED));
    }

    private record MeanContext(SimNPCComponent npc, Relationship rel) {}
    private record MeanRule(Predicate<MeanContext> condition, Function<MeanContext, InteractionOutcome> outcome) {}

    private static final List<MeanRule> MEAN_RULES = List.of(
        new MeanRule(ctx -> ctx.rel().status == RelationshipStatus.MARRIED || ctx.rel().status == RelationshipStatus.PARTNER,
                     ctx -> InteractionOutcome.of(-15, -20, -30, -25, pickRandomTranslation("npc-dialogues.mean.partner", 3, ctx.npc().name), MemoryEvent.INSULTED)),
        new MeanRule(ctx -> ctx.npc().personality.traits.contains(Trait.AGGRESSIVE),
                     ctx -> InteractionOutcome.of(-10, 0, -15, -20, pickRandomTranslation("npc-dialogues.mean.aggressive", 5, ctx.npc().name), MemoryEvent.INSULTED)),
        new MeanRule(ctx -> ctx.npc().personality.traits.contains(Trait.NEEDY),
                     ctx -> InteractionOutcome.of(-5, 0, -15, -15, pickRandomTranslation("npc-dialogues.mean.needy", 5, ctx.npc().name), MemoryEvent.INSULTED))
    );

    private static InteractionOutcome handleMean(SimNPCComponent npc, Relationship rel) {
        MeanContext ctx = new MeanContext(npc, rel);
        return MEAN_RULES.stream()
            .filter(r -> r.condition().test(ctx))
            .findFirst()
            .map(r -> r.outcome().apply(ctx))
            .orElseGet(() -> InteractionOutcome.of(-5, 0, -15, -15, pickRandomTranslation("npc-dialogues.mean.normal", 5, npc.name), MemoryEvent.INSULTED));
    }

    /**
     * How many scoldings in one day stop being a bad moment and start being a pattern.
     * <p>
     * Below it only the mood takes the hit and the relationship is untouched: a parent raising
     * their voice once should not cost affinity, or every player who ever clicks the button is
     * quietly punished for roleplaying.
     */
    private static final int SCOLDING_PATIENCE = 3;

    /**
     * Telling off your own child.
     *
     * <p>The reaction is chosen by life stage, because that is the whole point of separating this
     * from an insult:
     * <ul>
     *   <li><b>Child</b> — goes sad. No answering back, and the mood hit is the largest.</li>
     *   <li><b>Teen</b> — goes angry. Same telling-off, opposite reaction.</li>
     *   <li><b>Adult</b> — barely moves. They are grown and you are still their parent, so it
     *       lands as an awkward moment rather than a wound.</li>
     * </ul>
     *
     * <p>Nothing here is permanent. Repeated scoldings in the same day start costing trust and
     * affinity, and both recover with time and ordinary kindness — this is a consequence, not a
     * trap the player can fall into without a way back.
     */
    private static InteractionOutcome handleScold(SimNPCComponent npc, UUID playerUuid, Relationship rel) {
        GrowthStage stage = ParentChildBond.stageOf(npc, playerUuid);
        if (stage == null) {
            // Not this player's child after all — the page should not have offered the button, so
            // fall back rather than inventing a parental reaction between strangers.
            return handleMean(npc, rel);
        }

        rel.scoldingsToday++;
        boolean excessive = rel.scoldingsToday > SCOLDING_PATIENCE;
        long tick = WorldUtil.tick();

        Mood reaction;
        float intensity;
        int trustHit;
        int affinityHit;
        String key;

        switch (stage) {
            case TEEN -> {
                reaction = Mood.ANGRY;
                intensity = excessive ? 0.9f : 0.6f;
                trustHit = excessive ? -8 : 0;
                affinityHit = excessive ? -10 : -2;
                key = excessive ? "npc-dialogues.scold.teen_excessive" : "npc-dialogues.scold.teen";
            }
            case ADULT -> {
                reaction = Mood.BORED;
                intensity = 0.3f;
                trustHit = excessive ? -3 : 0;
                affinityHit = excessive ? -4 : 0;
                key = excessive ? "npc-dialogues.scold.adult_excessive" : "npc-dialogues.scold.adult";
            }
            // BABY and TODDLER are cared for, not argued with; they read as CHILD here.
            default -> {
                reaction = Mood.SAD;
                intensity = excessive ? 1.0f : 0.7f;
                trustHit = excessive ? -10 : 0;
                affinityHit = excessive ? -12 : -3;
                key = excessive ? "npc-dialogues.scold.child_excessive" : "npc-dialogues.scold.child";
            }
        }

        npc.setEmotion(reaction, intensity, "scold", tick);

        return InteractionOutcome.of(0, 0, trustHit, affinityHit,
                Message.translation(key).param("name", npc.name),
                excessive ? MemoryEvent.INSULTED : null);
    }

    private static InteractionOutcome handleRandom(Relationship rel) {
        String key = rel.status == RelationshipStatus.STRANGER ? "npc-dialogues.random.stranger" : "npc-dialogues.random.known";
        return InteractionOutcome.of(0, 0, 0, 1, Message.translation(key), MemoryEvent.CHATTED);
    }

    private static InteractionOutcome handleGift(SimNPCComponent npc, UUID playerUuid, PlayerRef playerRef, Relationship rel, boolean isChild) {
        Optional<ItemStack> optItem = getHeldItemFromPlayer(playerRef);
        if (optItem.isEmpty()) {
            return InteractionOutcome.error(Message.translation("npc-dialogues.gift.noitem"));
        }

        ItemStack heldItem = optItem.get();
        String itemName = heldItem.getDisplayName().getAnsiMessage();
        String itemId = heldItem.getItemId().toLowerCase(Locale.ROOT);

        // The real id has been guessed wrong three times over (with and without a "simtale:"
        // prefix, CamelCase and snake_case), so log it once and stop guessing.
        LOGGER.atInfo().log("SimTale: presente recebido, itemId bruto = '%s'", heldItem.getItemId());

        // "Baby" must never fall into the generic gift path: it would silently delete the item
        // (consumeHeldItemFromPlayer) and score a normal gift affinity without BabyCareManager
        // ever seeing it, orphaning the child's custody record. Custody transfer only happens
        // through the spouse's inventory (BabyCareManager.registerInventoryListener) or the
        // automatic proximity swap (BabyCareTickSystem) — both points-neutral by design.
        if (heldItem.getItemId().equals("Baby")) {
            return InteractionOutcome.error(Message.translation("npc-dialogues.gift.baby_reject"));
        }

        if (isChild) {
            return handleChildGift(npc, itemId, itemName);
        }

        if (isWeddingRing(heldItem.getItemId())) {
            return handleMarriageProposal(npc, rel, playerUuid);
        }

        InteractionOutcome meal = tryFeed(npc, heldItem, itemName);
        if (meal != null) return meal;

        return calculateGiftAffinity(npc, itemId, itemName, rel);
    }

    /**
     * Whether this item is the wedding ring, whatever form its id happens to take.
     *
     * <p>Matching used to be a single {@code equals} against a hardcoded string, and it was wrong
     * every time: the asset id could be {@code WeddingRing}, {@code wedding_ring}, with or without
     * a {@code simtale:} prefix, and the caller had already lowercased the id — so a CamelCase
     * literal could never match anything. Stripping everything that is not a letter or digit and
     * comparing the tail covers all four shapes at once.
     */
    private static boolean isWeddingRing(String rawItemId) {
        return AssetIds.matchesAsset(rawItemId, "WeddingRing");
    }

    /** Hunger level at or below which a gift of food is eaten on the spot instead of pocketed. */
    private static final float HUNGRY_ENOUGH_TO_EAT = 70f;


    /**
     * Feeds the NPC directly when it is hungry and the gift is edible.
     *
     * <p>Returns null when this is not a meal, so the normal affinity rules take over — a cake
     * handed to someone who just ate is still a nice present, just not dinner.
     *
     * <p>Uses the same {@link NPCFoodHelper} the chest routine uses, so a player cannot feed an NPC
     * something it would refuse to eat on its own, and the restored amounts match tier for tier.
     */
    private static InteractionOutcome tryFeed(SimNPCComponent npc, ItemStack heldItem, String itemName) {
        if (NeedsHelper.getNeed(null, npc.entityRef, NeedsHelper.HUNGER_ID) > HUNGRY_ENOUGH_TO_EAT) return null;

        int tier = NPCFoodHelper.tierOf(heldItem);
        if (tier == NPCFoodHelper.NOT_FOOD) return null;

        boolean hated = NPCFoodHelper.isHated(heldItem, npc.preferences);
        boolean favorite = NPCFoodHelper.isFavorite(heldItem, npc.preferences);

        NeedsHelper.setNeed(null, npc.entityRef, NeedsHelper.HUNGER_ID, NeedsHelper.getNeed(null, npc.entityRef, NeedsHelper.HUNGER_ID) + NPCFoodHelper.hungerRestored(tier));
        // Starvation damage has been removed, so we no longer reset it here.

        float healed = NPCFoodHelper.healthRestored(tier);
        if (healed > 0f) {
            healNpc(npc, healed);
        }

        if (favorite) {
            NeedsHelper.setNeed(null, npc.entityRef, NeedsHelper.FUN_ID, NeedsHelper.getNeed(null, npc.entityRef, NeedsHelper.FUN_ID) + 10f);
        } else if (hated) {
            NeedsHelper.setNeed(null, npc.entityRef, NeedsHelper.FUN_ID, NeedsHelper.getNeed(null, npc.entityRef, NeedsHelper.FUN_ID) - 10f);
        }

        // Feeding someone who is starving lands harder than handing over a trinket, and a hated
        // food still helps the body while souring the mood — hence the reduced, not negative, gain.
        int friendship = hated ? 6 : (favorite ? 25 : 15);
        int trust = hated ? 4 : (favorite ? 15 : 10);
        int affinity = hated ? 5 : (favorite ? 30 : 18);

        String key = hated ? "fed_hated" : (favorite ? "fed_favorite" : "fed");
        return InteractionOutcome.ofItem(friendship, 0, trust, affinity,
                Message.translation("npc-dialogues.gift." + key)
                        .param("name", npc.name).param("itemName", itemName),
                MemoryEvent.GIFTED, true);
    }

    /** Best-effort heal: the NPC entity may not be resolvable, and a missed heal is not fatal. */
    private static void healNpc(SimNPCComponent npc, float amount) {
        try {
            World world = WorldUtil.first();
            if (world == null || npc.entityId == null) return;
            Ref<EntityStore> npcRef = world.getEntityStore().getRefFromUUID(npc.entityId);
            if (npcRef != null && npcRef.isValid()) {
                StatHelper.addHealth(npcRef, amount);
            }
        } catch (RuntimeException ignored) {
            // Stat handling is best-effort; the hunger restore above already happened.
        }
    }

    private static InteractionOutcome handleChildGift(SimNPCComponent npc, String itemId, String itemName) {
        boolean isFood = FOOD_KEYWORDS.stream().anyMatch(itemId::contains);
        if (isFood) {
            GrowthComponent childComp = Caskara.load("child_" + npc.entityId.toString(), GrowthComponent.class);
            World world = WorldUtil.first();
            if (childComp != null && world != null) {
                childComp.birthTick -= 24000;
                Caskara.save("child_" + npc.entityId.toString(), childComp);
                LifecycleManager.tickGrowth(childComp, world.getTick());
            }
            return InteractionOutcome.ofItem(0, 0, 0, 0, 
                Message.translation("npc-dialogues.gift.accelerated").param("name", npc.name).param("item", itemName), 
                MemoryEvent.GIFTED, true);
        }
        return InteractionOutcome.error(Message.translation("npc-dialogues.gift.child_reject").param("name", npc.name).insert(Message.raw(". Try giving some food!")));
    }

    private static InteractionOutcome handleMarriageProposal(SimNPCComponent npc, Relationship rel, UUID playerUuid) {
        if (npc.family.isMarried) {
            return InteractionOutcome.error(Message.translation("npc-dialogues.marriage.already_married").param("name", npc.name));
        }

        if (rel.romance >= 80 && rel.friendship >= 70) {
            rel.status = RelationshipStatus.MARRIED;
            npc.family.marry(playerUuid, null);
            return InteractionOutcome.ofItem(0, 0, 0, 0, 
                Message.translation("npc-dialogues.marriage.accept." + ThreadLocalRandom.current().nextInt(1, 3)).param("name", npc.name), 
                MemoryEvent.GIFTED, true);
        }
        
        return InteractionOutcome.error(Message.translation("npc-dialogues.marriage.reject." + ThreadLocalRandom.current().nextInt(1, 3)).param("name", npc.name));
    }

    private record GiftContext(SimNPCComponent npc, Relationship rel, String itemId, String itemName, double multiplier) {}

    private record GiftRule(Predicate<GiftContext> condition, Function<GiftContext, InteractionOutcome> outcome) {}

    private static final List<GiftRule> GIFT_RULES = List.of(
        new GiftRule(ctx -> isLoved(ctx), ctx -> giftOutcome(20, 12, 30, "loves", ctx)),
        new GiftRule(ctx -> isHated(ctx), ctx -> giftOutcome(-20, -15, -25, "hates", ctx)),
        // Ranked below the explicit favorite/hated lists (those are personal and beat a generic
        // interest) but above trash/basic, so a gardener reads seeds as a thoughtful gift
        // instead of as filler.
        new GiftRule(ctx -> isHobbyRelated(ctx), ctx -> giftOutcome(14, 8, 22, "hobby", ctx)),
        new GiftRule(ctx -> isTrash(ctx), ctx -> giftOutcomeFlat(-15, -10, -20, "trash", ctx)),
        new GiftRule(ctx -> ctx.npc().personality.traits.contains(Trait.GREEDY),
                     ctx -> giftOutcome(15, 5, 25, "greedy", ctx)),
        new GiftRule(ctx -> ctx.npc().personality.traits.contains(Trait.PARANOID),
                     ctx -> giftOutcomeFlat(-5, -12, -10, "paranoid", ctx)),
        new GiftRule(ctx -> isBasic(ctx), ctx -> giftOutcome(2, 1, 3, "basic", ctx))
    );

    private static boolean isLoved(GiftContext ctx) {
        return ctx.npc().preferences != null && (
            (ctx.npc().preferences.getFavoriteFoods() != null && ctx.npc().preferences.getFavoriteFoods().stream().anyMatch(f -> f.equalsIgnoreCase(ctx.itemName()) || f.equalsIgnoreCase(ctx.itemId()))) ||
            (ctx.npc().preferences.getFavoriteItems() != null && ctx.npc().preferences.getFavoriteItems().stream().anyMatch(i -> i.equalsIgnoreCase(ctx.itemName()) || i.equalsIgnoreCase(ctx.itemId())))
        );
    }

    /** Gift that lines up with whatever the NPC does for fun. */
    private static boolean isHobbyRelated(GiftContext ctx) {
        if (ctx.npc().preferences == null) {
            return false;
        }
        String idLower = ctx.itemId().toLowerCase(Locale.ROOT);
        String nameLower = ctx.itemName().toLowerCase(Locale.ROOT);
        var hobby = NPCLeisureHelper.hobbyOf(ctx.npc());
        return NPCLeisureHelper.isHobbyItem(hobby, idLower)
                || NPCLeisureHelper.isHobbyItem(hobby, nameLower);
    }

    private static boolean isHated(GiftContext ctx) {
        return ctx.npc().preferences != null && (
            (ctx.npc().preferences.getHatedFoods() != null && ctx.npc().preferences.getHatedFoods().stream().anyMatch(f -> f.equalsIgnoreCase(ctx.itemName()) || f.equalsIgnoreCase(ctx.itemId()))) ||
            (ctx.npc().preferences.getHatedItems() != null && ctx.npc().preferences.getHatedItems().stream().anyMatch(i -> i.equalsIgnoreCase(ctx.itemName()) || i.equalsIgnoreCase(ctx.itemId())))
        );
    }

    private static boolean isTrash(GiftContext ctx) {
        String itemIdLower = ctx.itemId().toLowerCase(Locale.ROOT);
        String itemNameLower = ctx.itemName().toLowerCase(Locale.ROOT);
        return GiftCategory.classify(itemIdLower, itemNameLower).map(c -> c == GiftCategory.TRASH).orElse(false);
    }

    private static boolean isBasic(GiftContext ctx) {
        String itemIdLower = ctx.itemId().toLowerCase(Locale.ROOT);
        String itemNameLower = ctx.itemName().toLowerCase(Locale.ROOT);
        return GiftCategory.classify(itemIdLower, itemNameLower).map(c -> c == GiftCategory.BASIC).orElse(false);
    }

    private static InteractionOutcome giftOutcome(int f, int t, int a, String key, GiftContext ctx) {
        return InteractionOutcome.ofItem(
            (int) (f * ctx.multiplier()), 0, (int) (t * ctx.multiplier()), (int) (a * ctx.multiplier()),
            Message.translation("npc-dialogues.gift." + key).param("name", ctx.npc().name).param("itemName", ctx.itemName()),
            MemoryEvent.GIFTED, true
        );
    }

    private static InteractionOutcome giftOutcomeFlat(int f, int t, int a, String key, GiftContext ctx) {
        return InteractionOutcome.ofItem(
            f, 0, t, a,
            Message.translation("npc-dialogues.gift." + key).param("name", ctx.npc().name).param("itemName", ctx.itemName()),
            MemoryEvent.GIFTED, true
        );
    }

    private static InteractionOutcome calculateGiftAffinity(SimNPCComponent npc, String itemId, String itemName, Relationship rel) {
        double multiplier = rel.status == RelationshipStatus.ENEMIES ? 0.5 : (rel.status == RelationshipStatus.MARRIED ? 1.5 : 1.0);
        GiftContext ctx = new GiftContext(npc, rel, itemId, itemName, multiplier);

        return GIFT_RULES.stream()
            .filter(r -> r.condition().test(ctx))
            .findFirst()
            .map(r -> r.outcome().apply(ctx))
            .orElseGet(() -> giftOutcome(8, 4, 15, "normal", ctx));
    }

    // profName is a Message, not a String: it is a localized profession name and must render in
    // the player's language rather than carry the enum's Portuguese label into the sentence.
    private record ProfessionContext(SimNPCComponent npc, Relationship rel, Profession targetProf, Message profName, String itemName, double roll) {}
    private record ProfessionRule(Predicate<ProfessionContext> condition, Function<ProfessionContext, InteractionOutcome> outcome) {}

    private static final List<ProfessionRule> PROFESSION_RULES = List.of(
        new ProfessionRule(ctx -> ctx.rel().status == RelationshipStatus.ENEMIES || ctx.rel().status == RelationshipStatus.STRANGER,
                           ctx -> InteractionOutcome.of(-5, 0, -5, -10, pickRandomTranslation("npc-dialogues.prof.assign.refuse_status", 3, ctx.npc().name).param("profName", ctx.profName()), MemoryEvent.CHATTED)),
        new ProfessionRule(ctx -> ctx.npc().preferences != null && ctx.npc().preferences.getDislikedProfessions().contains(ctx.targetProf()),
                           ctx -> InteractionOutcome.of(-3, 0, 0, -5, pickRandomTranslation("npc-dialogues.prof.assign.dislike", 4, ctx.npc().name).param("profName", ctx.profName()).param("itemName", ctx.itemName()), MemoryEvent.CHATTED)),
        new ProfessionRule(ctx -> ctx.npc().personality.traits.contains(Trait.LAZY) && isHeavyWork(ctx.targetProf()) && ctx.roll() < 0.6,
                           ctx -> InteractionOutcome.of(-3, 0, 0, -5, pickRandomTranslation("npc-dialogues.prof.assign.lazy", 3, ctx.npc().name).param("profName", ctx.profName()), MemoryEvent.CHATTED)),
        new ProfessionRule(ctx -> ctx.npc().personality.traits.contains(Trait.AGGRESSIVE) && isPeacefulWork(ctx.targetProf()) && ctx.roll() < 0.7,
                           ctx -> InteractionOutcome.of(-3, 0, 0, -5, pickRandomTranslation("npc-dialogues.prof.assign.aggressive", 3, ctx.npc().name).param("profName", ctx.profName()), MemoryEvent.CHATTED)),
        new ProfessionRule(ctx -> ctx.npc().getMood() == Mood.ANGRY && ctx.roll() < 0.5,
                           ctx -> InteractionOutcome.of(-3, 0, 0, -5, pickRandomTranslation("npc-dialogues.prof.assign.angry", 3, ctx.npc().name), MemoryEvent.CHATTED))
    );

    private static boolean isHeavyWork(Profession prof) {
        return prof == Profession.MINER || prof == Profession.LUMBERJACK;
    }

    private static boolean isPeacefulWork(Profession prof) {
        return prof == Profession.FARMER || prof == Profession.FISHERMAN;
    }

    private static InteractionOutcome handleProfession(SimNPCComponent npc, PlayerRef playerRef, Relationship rel) {
        if (isNpcAChild(npc)) {
            return InteractionOutcome.error(Message.translation("npc-dialogues.prof.assign.child").param("name", npc.name));
        }

        Optional<ItemStack> optItem = getHeldItemFromPlayer(playerRef);
        if (optItem.isEmpty()) {
            return InteractionOutcome.error(Message.translation("npc-dialogues.prof.assign.noitem").param("name", npc.name));
        }

        ItemStack heldItem = optItem.get();
        String itemId = heldItem.getItemId();
        String itemName = heldItem.getDisplayName().getAnsiMessage();
        Profession targetProf = Profession.fromItemId(itemId);

        if (targetProf == null) {
            return InteractionOutcome.error(Message.translation("npc-dialogues.prof.assign.unknown").param("name", npc.name).param("itemName", itemName));
        }

        Message profName = Message.translation(targetProf.translationKey());

        if (npc.profession == targetProf) {
            return InteractionOutcome.error(Message.translation("npc-dialogues.prof.assign.already").param("name", npc.name).param("profName", profName));
        }

        double roll = ThreadLocalRandom.current().nextDouble();
        ProfessionContext ctx = new ProfessionContext(npc, rel, targetProf, profName, itemName, roll);

        Optional<InteractionOutcome> refusal = PROFESSION_RULES.stream()
            .filter(r -> r.condition().test(ctx))
            .findFirst()
            .map(r -> r.outcome().apply(ctx));

        if (refusal.isPresent()) {
            return refusal.get();
        }

        // Success path: perform the assignment
        Message prefix = Message.raw("");
        if (npc.profession != null && npc.profession != Profession.UNEMPLOYED && !npc.profession.triggerItemKeyword.isEmpty()) {
            prefix = Message.translation("npc-dialogues.prof.assign.return").param("name", npc.name).param("profName", Message.translation(npc.profession.translationKey())).insert(Message.raw(" "));
        }

        npc.profession = targetProf;

        if (npc.preferences != null && npc.preferences.getLikedProfessions().contains(targetProf)) {
            Message reaction = pickRandomTranslation("npc-dialogues.prof.assign.liked", 3, npc.name).param("profName", profName).param("itemName", itemName);
            return InteractionOutcome.ofItem(15, 0, 10, 25, prefix.insert(reaction), MemoryEvent.CHATTED, true);
        }

        Message reaction = pickRandomTranslation("npc-dialogues.prof.assign.accept", 5, npc.name).param("profName", profName).param("itemName", itemName);
        return InteractionOutcome.ofItem(8, 0, 5, 15, prefix.insert(reaction), MemoryEvent.CHATTED, true);
    }

    // --- Helpers Lógicos ---

    private static void applyOutcome(SimNPCComponent npc, UUID playerUuid, Relationship rel, InteractionOutcome outcome, boolean isChild) {
        if (outcome.memoryEvent() != null) {
            npc.memory.addMemory(outcome.memoryEvent(), playerUuid);
        }

        rel.addAffinity(outcome.affinity());
        rel.addFriendship(outcome.friendship());
        rel.addTrust(outcome.trust());
        
        if (!isChild) {
            rel.addRomance(outcome.romance());
        } else {
            rel.romance = 0;
        }

        npc.stats.addXP(Math.abs(outcome.affinity()) * 10);
        NeedsHelper.setNeed(null, npc.entityRef, NeedsHelper.SOCIAL_ID, NeedsHelper.getNeed(null, npc.entityRef, NeedsHelper.SOCIAL_ID) + 10f);

        // Dynamic emotion trigger based on interaction outcome
        long tick = 0;
        World world = WorldUtil.first();
        if (world != null) {
            tick = world.getTick();
        }
        
        if (outcome.memoryEvent() == MemoryEvent.GIFTED) {
            if (outcome.affinity() >= 20) {
                npc.setEmotion(Mood.HAPPY, 1.0f, "gift_loves", tick);
            } else if (outcome.affinity() < 0) {
                if (npc.personality.traits.contains(Trait.AGGRESSIVE)) {
                    npc.setEmotion(Mood.ANGRY, 1.0f, "gift_hates", tick);
                } else {
                    npc.setEmotion(Mood.SAD, 1.0f, "gift_hates", tick);
                }
            } else if (outcome.affinity() > 0 && outcome.affinity() <= 4) {
                // Basic item: do not alter mood
            } else {
                if (ThreadLocalRandom.current().nextDouble() < 0.5) {
                    npc.setEmotion(Mood.HAPPY, 0.6f, "gift_normal", tick);
                }
            }
        } else {
            if (outcome.affinity() > 0) {
                npc.setEmotion(Mood.HAPPY, 0.6f, "interaction", tick);
            } else if (outcome.affinity() < 0) {
                if (npc.personality.traits.contains(Trait.AGGRESSIVE)) {
                    npc.setEmotion(Mood.ANGRY, 0.8f, "interaction", tick);
                } else {
                    npc.setEmotion(Mood.SAD, 0.6f, "interaction", tick);
                }
            }
        }
    }

    /**
     * Length of an interaction "day". Despite the name this is 20 real minutes, not a game
     * day — the interaction budget and the "haven't seen you in ages" greeting both key off it.
     */
    private static final long INTERACTION_DAY_MILLIS = 20L * 60L * 1000L;
    /** Interactions allowed per player per interaction-day before the NPC starts declining. */
    private static final int INTERACTIONS_PER_DAY = 3;
    /** Days apart before the NPC greets the player with "long time no see". */
    private static final int MISSED_DAYS_THRESHOLD = 3;

    private static DailyState refreshDailyState(Relationship rel) {
        long currentDayIndex = System.currentTimeMillis() / INTERACTION_DAY_MILLIS;
        boolean missedLongTime = false;

        if (rel.lastInteractionDayIndex > 0 && rel.lastInteractionDayIndex < currentDayIndex) {
            missedLongTime = (currentDayIndex - rel.lastInteractionDayIndex) >= MISSED_DAYS_THRESHOLD;
            rel.interactionsToday = 0;
            rel.scoldingsToday = 0;
        } else if (rel.lastInteractionDayIndex == 0) {
            rel.interactionsToday = 0;
            rel.scoldingsToday = 0;
        }
        rel.lastInteractionDayIndex = currentDayIndex;

        return new DailyState(rel.interactionsToday >= INTERACTIONS_PER_DAY, missedLongTime);
    }

    /**
     * Whether this NPC is a minor.
     *
     * <p>Public because the pregnancy test needs the same answer, and a second copy of this check
     * is how two callers end up disagreeing about who is a child.
     */
    public static boolean isNpcAChild(SimNPCComponent npc) {
        if (npc.entityRef != null) {
            NPCEntity npcEntity = npc.entityRef.getStore().getComponent(npc.entityRef, Objects.requireNonNull(NPCEntity.getComponentType()));
            if (npcEntity != null && npcEntity.getRoleName() != null && 
                npcEntity.getRoleName().toLowerCase(Locale.ROOT).contains("child")) {
                return true;
            }
        }
        for (GrowthComponent child : LifecycleManager.ACTIVE_CHILDREN) {
            if (npc.entityId != null && npc.entityId.equals(child.childId) && !child.isAdult()) {
                return true;
            }
        }
        return false;
    }

    // --- Inventory Helpers  ---

    private static Optional<ItemStack> getHeldItemFromPlayer(PlayerRef playerRef) {
        if (playerRef == null || playerRef.getReference() == null || !playerRef.getReference().isValid()) return Optional.empty();
        Ref<EntityStore> pRef = playerRef.getReference();
        InventoryComponent.Hotbar hotbar = pRef.getStore().getComponent(pRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) return Optional.empty();
        
        ItemStack heldItem = hotbar.getActiveItem();
        if (heldItem == null || heldItem.isEmpty()) return Optional.empty();
        
        return Optional.of(heldItem);
    }

    private static void consumeHeldItemFromPlayer(PlayerRef playerRef) {
        if (playerRef == null || playerRef.getReference() == null || !playerRef.getReference().isValid()) return;
        Ref<EntityStore> pRef = playerRef.getReference();
        InventoryComponent.Hotbar hotbar = pRef.getStore().getComponent(pRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbar != null) {
            hotbar.getInventory().removeItemStackFromSlot(hotbar.getActiveSlot(), 1);
        }
    }

    // --- Helpers de UI e Mensagens ---

    private static Message getContextualGreeting(SimNPCComponent npc, UUID playerUuid, PlayerRef playerRef, Relationship rel) {
        // Ahead of every contextual rule below, which are all written for adults: they check the
        // player's health, recent insults, what happened to a friend. A small child does not open
        // with any of that — she opens with whatever she is looking at.
        String youngKey = ChildDialogue.keyFor(npc, playerUuid, "chat");
        if (youngKey != null) {
            return pickRandomTranslation(youngKey, YOUNG_LINE_VARIANTS, npc.name);
        }

        if (playerRef != null) {
            try {
                Ref<EntityStore> pRef = playerRef.getReference();
                if (pRef != null && pRef.isValid()) {
                    EntityStatMap statMap = pRef.getStore().getComponent(pRef, EntityStatMap.getComponentType());
                    if (statMap != null) {
                        EntityStatValue healthVal = statMap.get(DefaultEntityStatTypes.getHealth());
                        if (healthVal != null && healthVal.get() > 0 && healthVal.get() <= 20f) {
                            return Message.translation("npc-dialogues.context.bleeding").param("name", npc.name);
                        }
                    }
                }
            } catch (RuntimeException ignored) {
                // Stat lookup is best-effort; catching Throwable here also swallowed
                // OutOfMemoryError/StackOverflowError and any Error thrown by the engine.
            }

            World world = WorldUtil.first();
            if (world != null) {
                WorldTimeResource timeResource = world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType());
                float dayProgress = timeResource.getDayProgress();
                if (dayProgress < 0.25f || dayProgress > 0.75f) {
                    return Message.translation("npc-dialogues.context.night").param("name", npc.name);
                }
            }
        }

        if (npc.memory.remembers(MemoryEvent.INSULTED, playerUuid, 300000)) {
            return Message.translation("npc-dialogues.context.insulted.recent").param("name", npc.name);
        }

        // Modificações guiadas pelo RelationshipStatus
        return switch (rel.status) {
            case MARRIED, PARTNER, ENGAGED -> pickRandomTranslation("npc-dialogues.greeting.romantic", 5, npc.name);
            case ENEMIES -> pickRandomTranslation("npc-dialogues.greeting.enemy", 3, npc.name);
            case BEST_FRIEND -> pickRandomTranslation("npc-dialogues.greeting.close_friend", 5, npc.name);
            case STRANGER, UNKNOWN -> pickRandomTranslation("npc-dialogues.greeting.stranger", 5, npc.name);
            default -> getDefaultTraitGreeting(npc);
        };
    }
    
    private record TraitGreeting(Trait trait, String key, int options) {}

    private static final List<TraitGreeting> TRAIT_GREETINGS = List.of(
        new TraitGreeting(Trait.GREEDY, "npc-dialogues.greedy.greeting", 3),
        new TraitGreeting(Trait.PARANOID, "npc-dialogues.paranoid.greeting", 3),
        new TraitGreeting(Trait.LAZY, "npc-dialogues.lazy.greeting", 3)
    );

    private static Message getDefaultTraitGreeting(SimNPCComponent npc) {
        return TRAIT_GREETINGS.stream()
            .filter(tg -> npc.personality.traits.contains(tg.trait()))
            .findFirst()
            .map(tg -> pickRandomTranslation(tg.key(), tg.options(), npc.name))
            .orElseGet(() -> pickRandomTranslation("npc-dialogues.friendly.greeting", 5, npc.name));
    }

    private static Message getCooldownMessage(InteractionType type, String npcName) {
        if (type == InteractionType.FRIENDLY) return Message.translation("npc-dialogues.cooldown.friendly").param("name", npcName);
        if (type == InteractionType.GIFT) return Message.translation("npc-dialogues.cooldown.gift").param("name", npcName);
        return Message.translation("npc-dialogues.cooldown.general").param("name", npcName);
    }

    private static Message pickRandomTranslation(String baseKey, int optionsCount, String npcName) {
        int index = ThreadLocalRandom.current().nextInt(1, optionsCount + 1);
        return Message.translation(baseKey + "." + index).param("name", npcName);
    }
  
}