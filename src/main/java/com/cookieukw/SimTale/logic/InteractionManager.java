package com.cookieukw.SimTale.logic;

import com.cookie.caskara.Caskara;
import com.cookieukw.SimTale.core.MemoryEvent;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.RelationshipStatus;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.Trait;
import com.cookieukw.SimTale.core.lifecycle.GrowthComponent;
import com.cookieukw.SimTale.core.lifecycle.LifecycleManager;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.hypixel.hytale.component.Ref;
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
import java.util.concurrent.ThreadLocalRandom;

public class InteractionManager {

    private record InteractionOutcome(
        int friendship,
        int romance,
        int trust,
        int affinity,
        Message response,
        MemoryEvent memoryEvent,
        boolean consumeItem
    ) {
        public static InteractionOutcome of(int f, int r, int t, int a, Message msg, MemoryEvent event) {
            return new InteractionOutcome(f, r, t, a, msg, event, false);
        }
        
        public static InteractionOutcome ofItem(int f, int r, int t, int a, Message msg, MemoryEvent event, boolean consume) {
            return new InteractionOutcome(f, r, t, a, msg, event, consume);
        }

        public static InteractionOutcome error(Message msg) {
            return new InteractionOutcome(0, 0, 0, 0, msg, null, false);
        }
    }

    private record DailyState(boolean cooldownActive, boolean missedLongTime) {}

    private static final Set<String> FOOD_KEYWORDS = Set.of(
        "food", "apple", "bread", "meat", "fish", "carrot", 
        "potato", "soup", "fruit", "berry", "cookie", "pie"
    );

    private static final Set<String> TRASH_KEYWORDS_EN = Set.of(
        "dirt", "soil_sand", "rock_stone", "spiderweb", "gravel", "deco_trash"
    );
    
    private static final Set<String> TRASH_KEYWORDS_PT = Set.of(
        "terra", "pedra", "teia", "cascalho", "areia", "lixo"
    );

    public static Message performInteraction(SimNPCComponent npc, UUID playerUuid, PlayerRef playerRef, InteractionType type) {
        boolean isChild = isNpcAChild(npc);

        if (isChild && type == InteractionType.ROMANTIC) {
            return Message.translation("chat.flirt.child");
        }

        Relationship rel = npc.getRelationship(playerUuid);
        DailyState daily = refreshDailyState(rel);

        if (daily.cooldownActive()) {
            return getCooldownMessage(type, npc.name);
        }

        InteractionOutcome outcome = switch (type) {
            case FRIENDLY -> handleFriendly(npc, playerUuid, playerRef, rel, daily.missedLongTime());
            case FUNNY -> handleFunny(npc, rel);
            case ROMANTIC -> handleRomantic(npc, rel);
            case MEAN -> handleMean(npc, rel);
            case RANDOM -> handleRandom(rel);
            case GIFT -> handleGift(npc, playerUuid, playerRef, rel, isChild);
            case ASSIGN_PROFESSION -> handleProfession(npc, playerRef, rel);
        };

        if (outcome.memoryEvent() == null && outcome.friendship() == 0 && outcome.affinity() == 0) {
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
            response = Message.translation("chat.context.missed").param("name", npc.name).insert(Message.raw(" ")).insert(response);
        }
        
        int fGain = rel.status == RelationshipStatus.STRANGER ? 8 : (rel.status == RelationshipStatus.ENEMIES ? 1 : 5);
        int aGain = rel.status == RelationshipStatus.STRANGER ? 10 : 5;
        
        return InteractionOutcome.of(fGain, 0, 1, aGain, response, MemoryEvent.CHATTED);
    }

    private static InteractionOutcome handleFunny(SimNPCComponent npc, Relationship rel) {
        Mood mood = npc.getMood();
        
        if (rel.status == RelationshipStatus.ENEMIES) {
            return InteractionOutcome.of(-2, 0, 0, -5, pickRandomTranslation("chat.funny.enemy", 3, npc.name), MemoryEvent.JOKED);
        }

        if (mood == Mood.ANGRY || mood == Mood.SAD) {
            if (rel.status == RelationshipStatus.BEST_FRIEND || rel.status == RelationshipStatus.PARTNER) {
                return InteractionOutcome.of(2, 0, 1, 5, pickRandomTranslation("chat.funny.cheerup", 3, npc.name), MemoryEvent.JOKED);
            }
            return InteractionOutcome.of(-2, 0, 0, -5, pickRandomTranslation("chat.funny.angry", 5, npc.name), MemoryEvent.JOKED);
        }
        
        if (npc.personality.traits.contains(Trait.FUNNY)) {
            return InteractionOutcome.of(5, 0, 2, 15, pickRandomTranslation("chat.funny.trait", 5, npc.name), MemoryEvent.JOKED);
        }
        
        return InteractionOutcome.of(3, 0, 1, 5, pickRandomTranslation("chat.funny.normal", 5, npc.name), MemoryEvent.JOKED);
    }

    private static InteractionOutcome handleRomantic(SimNPCComponent npc, Relationship rel) {
        Mood mood = npc.getMood();

        if (rel.status == RelationshipStatus.ENEMIES) {
            return InteractionOutcome.of(-5, -15, -5, -20, pickRandomTranslation("chat.romantic.enemy", 3, npc.name), MemoryEvent.FLIRTED);
        }
        
        if (rel.status == RelationshipStatus.STRANGER || rel.status == RelationshipStatus.ACQUAINTANCE) {
            return InteractionOutcome.of(-3, -5, -2, -10, pickRandomTranslation("chat.romantic.stranger", 3, npc.name), MemoryEvent.FLIRTED);
        }

        if (mood == Mood.ANGRY) {
            return InteractionOutcome.of(0, -10, -2, -15, pickRandomTranslation("chat.romantic.reject", 5, npc.name), MemoryEvent.FLIRTED);
        }

        if (rel.status == RelationshipStatus.MARRIED || rel.status == RelationshipStatus.PARTNER) {
            return InteractionOutcome.of(2, 10, 2, 10, pickRandomTranslation("chat.romantic.partner", 5, npc.name), MemoryEvent.FLIRTED);
        }

        if (npc.personality.traits.contains(Trait.SHY)) {
            return InteractionOutcome.of(0, 15, 2, 10, pickRandomTranslation("chat.romantic.shy", 5, npc.name), MemoryEvent.FLIRTED);
        }
        
        return InteractionOutcome.of(0, 10, 1, 5, pickRandomTranslation("chat.romantic.normal", 5, npc.name), MemoryEvent.FLIRTED);
    }

    private static InteractionOutcome handleMean(SimNPCComponent npc, Relationship rel) {
        // Being mean to a partner breaks trust brutally
        if (rel.status == RelationshipStatus.MARRIED || rel.status == RelationshipStatus.PARTNER) {
            return InteractionOutcome.of(-15, -20, -30, -25, pickRandomTranslation("chat.mean.partner", 3, npc.name), MemoryEvent.INSULTED);
        }
        
        if (npc.personality.traits.contains(Trait.AGGRESSIVE)) {
            return InteractionOutcome.of(-10, 0, -15, -20, pickRandomTranslation("chat.mean.aggressive", 5, npc.name), MemoryEvent.INSULTED);
        }
        if (npc.personality.traits.contains(Trait.NEEDY)) {
            return InteractionOutcome.of(-5, 0, -15, -15, pickRandomTranslation("chat.mean.needy", 5, npc.name), MemoryEvent.INSULTED);
        }
        
        return InteractionOutcome.of(-5, 0, -15, -15, pickRandomTranslation("chat.mean.normal", 5, npc.name), MemoryEvent.INSULTED);
    }

    private static InteractionOutcome handleRandom(Relationship rel) {
        String key = rel.status == RelationshipStatus.STRANGER ? "chat.random.stranger" : "chat.random.known";
        return InteractionOutcome.of(0, 0, 0, 1, Message.translation(key), MemoryEvent.CHATTED);
    }

    private static InteractionOutcome handleGift(SimNPCComponent npc, UUID playerUuid, PlayerRef playerRef, Relationship rel, boolean isChild) {
        Optional<ItemStack> optItem = getHeldItemFromPlayer(playerRef);
        if (optItem.isEmpty()) {
            return InteractionOutcome.error(Message.translation("chat.gift.noitem"));
        }

        ItemStack heldItem = optItem.get();
        String itemName = heldItem.getDisplayName().getAnsiMessage();
        String itemId = heldItem.getItemId().toLowerCase(Locale.ROOT);

        if (isChild) {
            return handleChildGift(npc, itemId, itemName);
        }

        if (itemId.equals("simtale:wedding_ring")) {
            return handleMarriageProposal(npc, rel, playerUuid);
        }

        return calculateGiftAffinity(npc, itemId, itemName, rel);
    }

    private static InteractionOutcome handleChildGift(SimNPCComponent npc, String itemId, String itemName) {
        boolean isFood = FOOD_KEYWORDS.stream().anyMatch(itemId::contains);
        if (isFood) {
            GrowthComponent childComp = Caskara.load("child_" + npc.entityId.toString(), GrowthComponent.class);
            if (childComp != null) {
                childComp.birthTick -= 24000;
                Caskara.save("child_" + npc.entityId.toString(), childComp);
                LifecycleManager.tickGrowth(childComp, Universe.get().getWorlds().values().iterator().next().getTick());
            }
            return InteractionOutcome.ofItem(0, 0, 0, 0, 
                Message.translation("chat.gift.accelerated").param("name", npc.name).param("item", itemName), 
                MemoryEvent.GIFTED, true);
        }
        return InteractionOutcome.error(Message.translation("chat.gift.child_reject").param("name", npc.name).insert(Message.raw(". Try giving some food!")));
    }

    private static InteractionOutcome handleMarriageProposal(SimNPCComponent npc, Relationship rel, UUID playerUuid) {
        if (npc.family.isMarried) {
            return InteractionOutcome.error(Message.translation("chat.marriage.already_married").param("name", npc.name));
        }

        if (rel.romance >= 80 && rel.friendship >= 70) {
            rel.status = RelationshipStatus.MARRIED;
            npc.family.marry(playerUuid, null);
            return InteractionOutcome.ofItem(0, 0, 0, 0, 
                Message.translation("chat.marriage.accept." + ThreadLocalRandom.current().nextInt(1, 3)).param("name", npc.name), 
                MemoryEvent.GIFTED, true);
        }
        
        return InteractionOutcome.error(Message.translation("chat.marriage.reject." + ThreadLocalRandom.current().nextInt(1, 3)).param("name", npc.name));
    }

    private static InteractionOutcome calculateGiftAffinity(SimNPCComponent npc, String itemId, String itemName, Relationship rel) {
        boolean loves = npc.preferences != null && npc.preferences.favoriteFoods != null &&
                        npc.preferences.favoriteFoods.stream().anyMatch(f -> f.equalsIgnoreCase(itemName) || f.equalsIgnoreCase(itemId));
        
        boolean hates = npc.preferences != null && npc.preferences.hatedFoods != null &&
                        npc.preferences.hatedFoods.stream().anyMatch(f -> f.equalsIgnoreCase(itemName) || f.equalsIgnoreCase(itemId));

        String itemNameLower = itemName.toLowerCase(Locale.ROOT);
        boolean isTrash = TRASH_KEYWORDS_EN.stream().anyMatch(itemId::contains) || 
                          TRASH_KEYWORDS_PT.stream().anyMatch(itemNameLower::contains);

        // Multiplicador de eficácia: presentes de cônjuges/amigos valem mais, de inimigos são suspeitos
        double multiplier = rel.status == RelationshipStatus.ENEMIES ? 0.5 : (rel.status == RelationshipStatus.MARRIED ? 1.5 : 1.0);

        if (loves) {
            return InteractionOutcome.ofItem((int)(15 * multiplier), 0, (int)(8 * multiplier), (int)(25 * multiplier), Message.translation("chat.gift.loves").param("name", npc.name).param("itemName", itemName), MemoryEvent.GIFTED, true);
        } else if (hates) {
            return InteractionOutcome.ofItem((int)(-15 * multiplier), 0, (int)(-10 * multiplier), (int)(-20 * multiplier), Message.translation("chat.gift.hates").param("name", npc.name).param("itemName", itemName), MemoryEvent.GIFTED, true);
        } else if (isTrash) {
            return InteractionOutcome.ofItem(-10, 0, -5, -15, Message.translation("chat.gift.trash").param("name", npc.name).param("itemName", itemName), MemoryEvent.GIFTED, true);
        } else if (npc.personality.traits.contains(Trait.GREEDY)) {
            return InteractionOutcome.ofItem((int)(10 * multiplier), 0, 5, (int)(20 * multiplier), Message.translation("chat.gift.greedy").param("name", npc.name).param("itemName", itemName), MemoryEvent.GIFTED, true);
        } else if (npc.personality.traits.contains(Trait.PARANOID)) {
            return InteractionOutcome.ofItem(-5, 0, -10, -10, Message.translation("chat.gift.paranoid").param("name", npc.name).param("itemName", itemName), MemoryEvent.GIFTED, true);
        }
        
        return InteractionOutcome.ofItem((int)(5 * multiplier), 0, 3, (int)(10 * multiplier), Message.translation("chat.gift.normal").param("name", npc.name).param("itemName", itemName), MemoryEvent.GIFTED, true);
    }

    private static InteractionOutcome handleProfession(SimNPCComponent npc, PlayerRef playerRef, Relationship rel) {
        Optional<ItemStack> optItem = getHeldItemFromPlayer(playerRef);
        if (optItem.isEmpty()) {
            return InteractionOutcome.error(Message.translation("chat.prof.assign.noitem").param("name", npc.name));
        }

        ItemStack heldItem = optItem.get();
        String itemId = heldItem.getItemId();
        String itemName = heldItem.getDisplayName().getAnsiMessage();
        Profession targetProf = Profession.fromItemId(itemId);

        if (targetProf == null) {
            return InteractionOutcome.error(Message.translation("chat.prof.assign.unknown").param("name", npc.name).param("itemName", itemName));
        }

        String profName = targetProf.ptName;

        if (npc.profession == targetProf) {
            return InteractionOutcome.error(Message.translation("chat.prof.assign.already").param("name", npc.name).param("profName", profName));
        }

        // Se for inimigo ou desconhecido, recusa trabalhar pra você quase sempre
        if (rel.status == RelationshipStatus.ENEMIES || rel.status == RelationshipStatus.STRANGER) {
             return InteractionOutcome.of(-5, 0, -5, -10, pickRandomTranslation("chat.prof.assign.refuse_status", 3, npc.name).param("profName", profName), MemoryEvent.CHATTED);
        }

        if (npc.preferences != null && npc.preferences.dislikedProfessions.contains(targetProf)) {
            return InteractionOutcome.of(-3, 0, 0, -5, pickRandomTranslation("chat.prof.assign.dislike", 4, npc.name).param("profName", profName).param("itemName", itemName), MemoryEvent.CHATTED);
        }

        boolean isHeavyWork = (targetProf == Profession.MINER || targetProf == Profession.LUMBERJACK);
        boolean isPeacefulWork = (targetProf == Profession.FARMER || targetProf == Profession.FISHERMAN);
        double roll = ThreadLocalRandom.current().nextDouble();

        if (npc.personality.traits.contains(Trait.LAZY) && isHeavyWork && roll < 0.6) {
            return InteractionOutcome.of(-3, 0, 0, -5, pickRandomTranslation("chat.prof.assign.lazy", 3, npc.name).param("profName", profName), MemoryEvent.CHATTED);
        }
        if (npc.personality.traits.contains(Trait.AGGRESSIVE) && isPeacefulWork && roll < 0.7) {
            return InteractionOutcome.of(-3, 0, 0, -5, pickRandomTranslation("chat.prof.assign.aggressive", 3, npc.name).param("profName", profName), MemoryEvent.CHATTED);
        }
        if (npc.getMood() == Mood.ANGRY && roll < 0.5) {
            return InteractionOutcome.of(-3, 0, 0, -5, pickRandomTranslation("chat.prof.assign.angry", 3, npc.name), MemoryEvent.CHATTED);
        }

        Message prefix = Message.raw("");
        if (npc.profession != null && npc.profession != Profession.UNEMPLOYED && !npc.profession.triggerItemKeyword.isEmpty()) {
            prefix = Message.translation("chat.prof.assign.return").param("name", npc.name).param("profName", npc.profession.ptName).insert(Message.raw(" "));
        }

        npc.profession = targetProf;

        if (npc.preferences != null && npc.preferences.likedProfessions.contains(targetProf)) {
            Message reaction = pickRandomTranslation("chat.prof.assign.liked", 3, npc.name).param("profName", profName).param("itemName", itemName);
            return InteractionOutcome.ofItem(15, 0, 10, 25, prefix.insert(reaction), MemoryEvent.CHATTED, true);
        }

        Message reaction = pickRandomTranslation("chat.prof.assign.accept", 5, npc.name).param("profName", profName).param("itemName", itemName);
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
        npc.needs.social = Math.min(100, npc.needs.social + 10);
    }

    private static DailyState refreshDailyState(Relationship rel) {
        long currentDayIndex = System.currentTimeMillis() / 1200000L;
        boolean missedLongTime = false;

        if (rel.lastInteractionDayIndex > 0 && rel.lastInteractionDayIndex < currentDayIndex) {
            missedLongTime = (currentDayIndex - rel.lastInteractionDayIndex) >= 3;
            rel.interactionsToday = 0;
        } else if (rel.lastInteractionDayIndex == 0) {
            rel.interactionsToday = 0;
        }
        rel.lastInteractionDayIndex = currentDayIndex;

        return new DailyState(rel.interactionsToday >= 3, missedLongTime);
    }

    private static boolean isNpcAChild(SimNPCComponent npc) {
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
        if (playerRef != null) {
            try {
                Ref<EntityStore> pRef = playerRef.getReference();
                if (pRef != null && pRef.isValid()) {
                    EntityStatMap statMap = pRef.getStore().getComponent(pRef, EntityStatMap.getComponentType());
                    if (statMap != null) {
                        EntityStatValue healthVal = statMap.get(DefaultEntityStatTypes.getHealth());
                        if (healthVal != null && healthVal.get() > 0 && healthVal.get() <= 20f) {
                            return Message.translation("chat.context.bleeding").param("name", npc.name);
                        }
                    }
                }
            } catch (Throwable ignored) {}

            World world = Universe.get().getWorlds().values().stream().findFirst().orElse(null);
            if (world != null) {
                WorldTimeResource timeResource = world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType());
                float dayProgress = timeResource.getDayProgress();
                if (dayProgress < 0.25f || dayProgress > 0.75f) {
                    return Message.translation("chat.context.night").param("name", npc.name);
                }
            }
        }

        if (npc.memory.remembers(MemoryEvent.INSULTED, playerUuid, 300000)) {
            return Message.translation("chat.context.insulted.recent").param("name", npc.name);
        }

        // Modificações guiadas pelo RelationshipStatus
        return switch (rel.status) {
            case MARRIED, PARTNER, ENGAGED -> pickRandomTranslation("chat.greeting.romantic", 5, npc.name);
            case ENEMIES -> pickRandomTranslation("chat.greeting.enemy", 3, npc.name);
            case BEST_FRIEND -> pickRandomTranslation("chat.greeting.close_friend", 5, npc.name);
            case STRANGER, UNKNOWN -> pickRandomTranslation("chat.greeting.stranger", 5, npc.name);
            default -> getDefaultTraitGreeting(npc);
        };
    }
    
    private static Message getDefaultTraitGreeting(SimNPCComponent npc) {
        if (npc.personality.traits.contains(Trait.GREEDY)) return pickRandomTranslation("chat.greedy.greeting", 3, npc.name);
        if (npc.personality.traits.contains(Trait.PARANOID)) return pickRandomTranslation("chat.paranoid.greeting", 3, npc.name);
        if (npc.personality.traits.contains(Trait.LAZY)) return pickRandomTranslation("chat.lazy.greeting", 3, npc.name);

        return pickRandomTranslation("chat.friendly.greeting", 5, npc.name);
    }

    private static Message getCooldownMessage(InteractionType type, String npcName) {
        if (type == InteractionType.FRIENDLY) return Message.translation("chat.cooldown.friendly").param("name", npcName);
        if (type == InteractionType.GIFT) return Message.translation("chat.cooldown.gift").param("name", npcName);
        return Message.translation("chat.cooldown.general").param("name", npcName);
    }

    private static Message pickRandomTranslation(String baseKey, int optionsCount, String npcName) {
        int index = ThreadLocalRandom.current().nextInt(1, optionsCount + 1);
        return Message.translation(baseKey + "." + index).param("name", npcName);
    }
  
}