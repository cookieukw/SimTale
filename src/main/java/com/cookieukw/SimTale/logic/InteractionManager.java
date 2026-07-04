package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.MemoryEvent;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.Trait;
import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.db.SimNPCPersistence;

import java.util.UUID;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.Message;
import java.util.Random;

public class InteractionManager {

    public static Message performInteraction(SimNPCComponent npc, UUID playerUuid, PlayerRef playerRef, InteractionType type) {
        Mood mood = npc.getMood();
        int friendshipChange = 0;
        int romanceChange = 0;
        int trustChange = 0;
        int affinityChange = 0;
        Message response = Message.raw("");
        MemoryEvent memEvent = MemoryEvent.CHATTED;

        Relationship rel = npc.getRelationship(playerUuid);
        long currentDayIndex = System.currentTimeMillis() / 1200000L;
        
        boolean missedLongTime = false;
        if (rel.lastInteractionDayIndex > 0 && rel.lastInteractionDayIndex < currentDayIndex) {
            long daysMissed = currentDayIndex - rel.lastInteractionDayIndex;
            if (daysMissed >= 3) {
                missedLongTime = true;
            }
            rel.interactionsToday = 0;
        } else if (rel.lastInteractionDayIndex == 0) {
            rel.interactionsToday = 0;
        }
        rel.lastInteractionDayIndex = currentDayIndex;

        boolean cooldownActive = rel.interactionsToday >= 3;
        rel.interactionsToday++;

        switch (type) {
            case FRIENDLY -> {
                friendshipChange = 5;
                affinityChange = 5;
                trustChange = 1;
                response = getContextualGreeting(npc, playerUuid, playerRef);
            }
            case FUNNY -> {
                memEvent = MemoryEvent.JOKED;
                if (mood == Mood.ANGRY || mood == Mood.SAD) {
                    friendshipChange = -2;
                    affinityChange = -5;
                    response = getRandomOption(
                        Message.translation("chat.funny.angry.1").param("name", npc.name),
                        Message.translation("chat.funny.angry.2").param("name", npc.name),
                        Message.translation("chat.funny.angry.3").param("name", npc.name),
                        Message.translation("chat.funny.angry.4").param("name", npc.name),
                        Message.translation("chat.funny.angry.5").param("name", npc.name)
                    );
                } else if (npc.personality.traits.contains(Trait.FUNNY)) {
                    friendshipChange = 5;
                    affinityChange = 15;
                    trustChange = 2;
                    response = getRandomOption(
                        Message.translation("chat.funny.trait.1").param("name", npc.name),
                        Message.translation("chat.funny.trait.2").param("name", npc.name),
                        Message.translation("chat.funny.trait.3").param("name", npc.name),
                        Message.translation("chat.funny.trait.4").param("name", npc.name),
                        Message.translation("chat.funny.trait.5").param("name", npc.name)
                    );
                } else {
                    friendshipChange = 3;
                    affinityChange = 5;
                    trustChange = 1;
                    response = getRandomOption(
                        Message.translation("chat.funny.normal.1").param("name", npc.name),
                        Message.translation("chat.funny.normal.2").param("name", npc.name),
                        Message.translation("chat.funny.normal.3").param("name", npc.name),
                        Message.translation("chat.funny.normal.4").param("name", npc.name),
                        Message.translation("chat.funny.normal.5").param("name", npc.name)
                    );
                }
            }
            case ROMANTIC -> {
                memEvent = MemoryEvent.FLIRTED;
                int affinity = npc.getRelationship(playerUuid).affinity;
                if (affinity < 20 || mood == Mood.ANGRY) {
                    romanceChange = -10;
                    affinityChange = -15;
                    trustChange = -2;
                    response = getRandomOption(
                        Message.translation("chat.romantic.reject.1").param("name", npc.name),
                        Message.translation("chat.romantic.reject.2").param("name", npc.name),
                        Message.translation("chat.romantic.reject.3").param("name", npc.name),
                        Message.translation("chat.romantic.reject.4").param("name", npc.name),
                        Message.translation("chat.romantic.reject.5").param("name", npc.name)
                    );
                } else if (npc.personality.traits.contains(Trait.SHY)) {
                    romanceChange = 15;
                    affinityChange = 10;
                    trustChange = 2;
                    response = getRandomOption(
                        Message.translation("chat.romantic.shy.1").param("name", npc.name),
                        Message.translation("chat.romantic.shy.2").param("name", npc.name),
                        Message.translation("chat.romantic.shy.3").param("name", npc.name),
                        Message.translation("chat.romantic.shy.4").param("name", npc.name),
                        Message.translation("chat.romantic.shy.5").param("name", npc.name)
                    );
                } else {
                    romanceChange = 10;
                    affinityChange = 5;
                    trustChange = 1;
                    response = getRandomOption(
                        Message.translation("chat.romantic.normal.1").param("name", npc.name),
                        Message.translation("chat.romantic.normal.2").param("name", npc.name),
                        Message.translation("chat.romantic.normal.3").param("name", npc.name),
                        Message.translation("chat.romantic.normal.4").param("name", npc.name),
                        Message.translation("chat.romantic.normal.5").param("name", npc.name)
                    );
                }
            }
            case MEAN -> {
                memEvent = MemoryEvent.INSULTED;
                trustChange = -15;
                if (npc.personality.traits.contains(Trait.AGGRESSIVE)) {
                    friendshipChange = -10;
                    affinityChange = -20;
                    response = getRandomOption(
                        Message.translation("chat.mean.aggressive.1").param("name", npc.name),
                        Message.translation("chat.mean.aggressive.2").param("name", npc.name),
                        Message.translation("chat.mean.aggressive.3").param("name", npc.name),
                        Message.translation("chat.mean.aggressive.4").param("name", npc.name),
                        Message.translation("chat.mean.aggressive.5").param("name", npc.name)
                    );
                } else if (npc.personality.traits.contains(Trait.NEEDY)) {
                    friendshipChange = -5;
                    affinityChange = -15;
                    response = getRandomOption(
                        Message.translation("chat.mean.needy.1").param("name", npc.name),
                        Message.translation("chat.mean.needy.2").param("name", npc.name),
                        Message.translation("chat.mean.needy.3").param("name", npc.name),
                        Message.translation("chat.mean.needy.4").param("name", npc.name),
                        Message.translation("chat.mean.needy.5").param("name", npc.name)
                    );
                } else {
                    friendshipChange = -5;
                    affinityChange = -15;
                    response = getRandomOption(
                        Message.translation("chat.mean.normal.1").param("name", npc.name),
                        Message.translation("chat.mean.normal.2").param("name", npc.name),
                        Message.translation("chat.mean.normal.3").param("name", npc.name),
                        Message.translation("chat.mean.normal.4").param("name", npc.name),
                        Message.translation("chat.mean.normal.5").param("name", npc.name)
                    );
                }
            }
            case RANDOM -> {
                affinityChange = 1;
                response = Message.translation("chat.random");
            }
            case GIFT -> {
                Ref<EntityStore> pRef = playerRef != null ? playerRef.getReference() : null;
                if (pRef == null || !pRef.isValid()) {
                    rel.interactionsToday = Math.max(0, rel.interactionsToday - 1);
                    return Message.raw("Error: Invalid player.");
                }
                
                InventoryComponent.Hotbar hotbar = 
                    pRef.getStore().getComponent(pRef, InventoryComponent.Hotbar.getComponentType());
                if (hotbar == null) {
                    rel.interactionsToday = Math.max(0, rel.interactionsToday - 1);
                    return Message.raw("Error: Inventory unavailable.");
                }
                
                byte activeSlot = hotbar.getActiveSlot();
                ItemStack heldItem = hotbar.getActiveItem();
                if (heldItem == null || heldItem.isEmpty()) {
                    rel.interactionsToday = Math.max(0, rel.interactionsToday - 1);
                    return Message.raw("You are not holding any item to give as a gift!");
                }
                
                String itemName = heldItem.getDisplayName().getAnsiMessage();
                String itemId = heldItem.getItemId();
                
                hotbar.getInventory().removeItemStackFromSlot(activeSlot, 1);
                
                memEvent = MemoryEvent.GIFTED;
                
                boolean loves = false;
                boolean hates = false;
                
                if (npc.preferences != null) {
                    if (npc.preferences.favoriteFoods != null) {
                        for (String fav : npc.preferences.favoriteFoods) {
                            if (fav.equalsIgnoreCase(itemName) || fav.equalsIgnoreCase(itemId)) {
                                loves = true;
                                break;
                            }
                        }
                    }
                    if (npc.preferences.hatedFoods != null) {
                        for (String hate : npc.preferences.hatedFoods) {
                            if (hate.equalsIgnoreCase(itemName) || hate.equalsIgnoreCase(itemId)) {
                                hates = true;
                                break;
                            }
                        }
                    }
                }
                
                boolean isTrash = itemId.contains("dirt") || itemId.contains("soil_sand") || itemId.contains("rock_stone") || itemId.contains("spiderweb") || itemId.contains("gravel") || itemId.contains("deco_trash") ||
                                  itemName.toLowerCase().contains("terra") || itemName.toLowerCase().contains("pedra") || itemName.toLowerCase().contains("teia") || itemName.toLowerCase().contains("cascalho") || itemName.toLowerCase().contains("areia") || itemName.toLowerCase().contains("lixo");
                
                if (loves) {
                    friendshipChange = 15;
                    affinityChange = 25;
                    trustChange = 8;
                    response = Message.translation("chat.gift.loves").param("name", npc.name).param("itemName", itemName);
                } else if (hates) {
                    friendshipChange = -15;
                    affinityChange = -20;
                    trustChange = -10;
                    response = Message.translation("chat.gift.hates").param("name", npc.name).param("itemName", itemName);
                } else if (isTrash) {
                    friendshipChange = -10;
                    affinityChange = -15;
                    trustChange = -5;
                    response = Message.translation("chat.gift.trash").param("name", npc.name).param("itemName", itemName);
                } else {
                    if (npc.personality.traits.contains(Trait.GREEDY)) {
                        friendshipChange = 10;
                        affinityChange = 20;
                        trustChange = 5;
                        response = Message.translation("chat.gift.greedy").param("name", npc.name).param("itemName", itemName);
                    } else if (npc.personality.traits.contains(Trait.PARANOID)) {
                        friendshipChange = -5;
                        affinityChange = -10;
                        trustChange = -10;
                        response = Message.translation("chat.gift.paranoid").param("name", npc.name).param("itemName", itemName);
                    } else {
                        friendshipChange = 5;
                        affinityChange = 10;
                        trustChange = 3;
                        response = Message.translation("chat.gift.normal").param("name", npc.name).param("itemName", itemName);
                    }
                }
            }
            case ASSIGN_PROFESSION -> {
                Ref<EntityStore> pRef = playerRef != null ? playerRef.getReference() : null;
                if (pRef == null || !pRef.isValid()) {
                    rel.interactionsToday = Math.max(0, rel.interactionsToday - 1);
                    return Message.raw("Error: Invalid player.");
                }
                
                InventoryComponent.Hotbar hotbar = 
                    pRef.getStore().getComponent(pRef, InventoryComponent.Hotbar.getComponentType());
                if (hotbar == null) {
                    rel.interactionsToday = Math.max(0, rel.interactionsToday - 1);
                    return Message.raw("Error: Inventory unavailable.");
                }
                
                byte activeSlot = hotbar.getActiveSlot();
                ItemStack heldItem = hotbar.getActiveItem();
                if (heldItem == null || heldItem.isEmpty()) {
                    rel.interactionsToday = Math.max(0, rel.interactionsToday - 1);
                    return Message.raw("Hold a representative item (pickaxe, sword, hoe, etc) to assign a job!");
                }
                
                String itemId = heldItem.getItemId();
                String itemName = heldItem.getDisplayName().getAnsiMessage();
                Profession targetProf = Profession.fromItemId(itemId);
                
                if (targetProf == null) {
                    rel.interactionsToday = Math.max(0, rel.interactionsToday - 1);
                    return Message.translation("chat.prof.assign.unknown").param("name", npc.name).param("itemName", itemName);
                }
                
                String profName = targetProf.ptName; // Will be localized later if needed or we can rely on UI translation for now
                
                if (npc.profession == targetProf) {
                    rel.interactionsToday = Math.max(0, rel.interactionsToday - 1);
                    return Message.translation("chat.prof.assign.already").param("name", npc.name).param("profName", profName);
                }
                
                boolean refuses = false;
                Message refuseReason = Message.raw("");
                
                if (npc.preferences != null && npc.preferences.dislikedProfessions.contains(targetProf)) {
                    refuses = true;
                    refuseReason = getRandomOption(
                        Message.translation("chat.prof.assign.dislike.1").param("name", npc.name).param("profName", profName).param("itemName", itemName),
                        Message.translation("chat.prof.assign.dislike.2").param("name", npc.name).param("profName", profName).param("itemName", itemName),
                        Message.translation("chat.prof.assign.dislike.3").param("name", npc.name).param("profName", profName).param("itemName", itemName),
                        Message.translation("chat.prof.assign.dislike.4").param("name", npc.name).param("profName", profName).param("itemName", itemName)
                    );
                }
                
                if (!refuses) {
                    boolean isHeavyWork = (targetProf == Profession.MINER || targetProf == Profession.LUMBERJACK);
                    boolean isPeacefulWork = (targetProf == Profession.FARMER || targetProf == Profession.FISHERMAN);
                    
                    if (npc.personality.traits.contains(Trait.LAZY) && isHeavyWork && Math.random() < 0.6) {
                        refuses = true;
                        refuseReason = getRandomOption(
                            Message.translation("chat.prof.assign.lazy.1").param("name", npc.name).param("profName", profName),
                            Message.translation("chat.prof.assign.lazy.2").param("name", npc.name).param("profName", profName),
                            Message.translation("chat.prof.assign.lazy.3").param("name", npc.name).param("profName", profName)
                        );
                    } else if (npc.personality.traits.contains(Trait.AGGRESSIVE) && isPeacefulWork && Math.random() < 0.7) {
                        refuses = true;
                        refuseReason = getRandomOption(
                            Message.translation("chat.prof.assign.aggressive.1").param("name", npc.name).param("profName", profName),
                            Message.translation("chat.prof.assign.aggressive.2").param("name", npc.name).param("profName", profName),
                            Message.translation("chat.prof.assign.aggressive.3").param("name", npc.name).param("profName", profName)
                        );
                    } else if (mood == Mood.ANGRY && Math.random() < 0.5) {
                        refuses = true;
                        refuseReason = getRandomOption(
                            Message.translation("chat.prof.assign.angry.1").param("name", npc.name),
                            Message.translation("chat.prof.assign.angry.2").param("name", npc.name),
                            Message.translation("chat.prof.assign.angry.3").param("name", npc.name)
                        );
                    }
                }
                
                if (refuses) {
                    rel.interactionsToday = Math.max(0, rel.interactionsToday - 1);
                    affinityChange = -5;
                    friendshipChange = -3;
                    response = refuseReason;
                } else {
                    Message prefix = Message.raw("");
                    if (npc.profession != null && npc.profession != Profession.UNEMPLOYED 
                        && !npc.profession.triggerItemKeyword.isEmpty()) {
                        prefix = Message.translation("chat.prof.assign.return").param("name", npc.name).param("profName", npc.profession.ptName).insert(Message.raw(" "));
                    }
                    
                    Profession oldProf = npc.profession;
                    npc.profession = targetProf;
                    
                    hotbar.getInventory().removeItemStackFromSlot(activeSlot, 1);
                    
                    friendshipChange = 8;
                    affinityChange = 15;
                    trustChange = 5;
                    
                    Message reaction;
                    if (npc.preferences != null && npc.preferences.likedProfessions.contains(targetProf)) {
                        friendshipChange = 15;
                        affinityChange = 25;
                        trustChange = 10;
                        reaction = getRandomOption(
                            Message.translation("chat.prof.assign.liked.1").param("name", npc.name).param("profName", profName).param("itemName", itemName),
                            Message.translation("chat.prof.assign.liked.2").param("name", npc.name).param("profName", profName).param("itemName", itemName),
                            Message.translation("chat.prof.assign.liked.3").param("name", npc.name).param("profName", profName).param("itemName", itemName)
                        );
                    } else {
                        reaction = getRandomOption(
                            Message.translation("chat.prof.assign.accept.1").param("name", npc.name).param("profName", profName).param("itemName", itemName),
                            Message.translation("chat.prof.assign.accept.2").param("name", npc.name).param("profName", profName).param("itemName", itemName),
                            Message.translation("chat.prof.assign.accept.3").param("name", npc.name).param("profName", profName).param("itemName", itemName),
                            Message.translation("chat.prof.assign.accept.4").param("name", npc.name).param("profName", profName).param("itemName", itemName),
                            Message.translation("chat.prof.assign.accept.5").param("name", npc.name).param("profName", profName).param("itemName", itemName)
                        );
                    }
                    response = prefix.insert(reaction);
                }
            }
        }
        
        if (cooldownActive) {
            affinityChange = 0;
            friendshipChange = 0;
            romanceChange = 0;
            trustChange = 0;
            if (type == InteractionType.FRIENDLY) {
                response = Message.translation("chat.cooldown.friendly").param("name", npc.name);
            } else if (type == InteractionType.GIFT) {
                response = Message.translation("chat.cooldown.gift").param("name", npc.name);
            } else {
                response = Message.translation("chat.cooldown.general").param("name", npc.name);
            }
        } else if (missedLongTime && type == InteractionType.FRIENDLY) {
            response = Message.translation("chat.context.missed").param("name", npc.name).insert(Message.raw(" ")).insert(response);
        }

        if (memEvent != null) {
            npc.memory.addMemory(memEvent, playerUuid);
        }
        npc.getRelationship(playerUuid).addAffinity(affinityChange);
        npc.getRelationship(playerUuid).addFriendship(friendshipChange);
        npc.getRelationship(playerUuid).addRomance(romanceChange);
        npc.getRelationship(playerUuid).addTrust(trustChange);

        npc.stats.addXP(Math.abs(affinityChange) * 10);

        npc.needs.social = Math.min(100, npc.needs.social + 10);

        SimNPCPersistence.saveNPC(npc);
        
        return response;
    }

    private static Message getContextualGreeting(SimNPCComponent npc, UUID playerUuid, PlayerRef playerRef) {
        if (playerRef != null) {
            try {
                Ref<EntityStore> pRef = playerRef.getReference();
                if (pRef != null && pRef.isValid()) {
                    EntityStatMap statMap = pRef.getStore().getComponent(pRef, EntityStatMap.getComponentType());
                    if (statMap != null) {
                        EntityStatValue healthVal = statMap.get(DefaultEntityStatTypes.getHealth());
                        if (healthVal != null) {
                            float health = healthVal.get();
                            if (health > 0 && health <= 20f) {
                                return Message.translation("chat.context.bleeding").param("name", npc.name);
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}

            World world = null;
            for (World w : Universe.get().getWorlds().values()) {
                world = w;
                break;
            }
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

        for (SimNPCComponent otherNpc : SimTale.ACTIVE_NPCS) {
            if (otherNpc != npc && otherNpc.memory.remembers(MemoryEvent.INSULTED, playerUuid, 600000)) {
                return Message.translation("chat.context.insulted.other").param("name", npc.name).param("otherName", otherNpc.name);
            }
        }

        if (npc.personality.traits.contains(Trait.GREEDY)) {
            return getRandomOption(
                Message.translation("chat.greedy.greeting.1").param("name", npc.name),
                Message.translation("chat.greedy.greeting.2").param("name", npc.name),
                Message.translation("chat.greedy.greeting.3").param("name", npc.name)
            );
        } else if (npc.personality.traits.contains(Trait.PARANOID)) {
            return getRandomOption(
                Message.translation("chat.paranoid.greeting.1").param("name", npc.name),
                Message.translation("chat.paranoid.greeting.2").param("name", npc.name),
                Message.translation("chat.paranoid.greeting.3").param("name", npc.name)
            );
        } else if (npc.personality.traits.contains(Trait.LAZY)) {
            return getRandomOption(
                Message.translation("chat.lazy.greeting.1").param("name", npc.name),
                Message.translation("chat.lazy.greeting.2").param("name", npc.name),
                Message.translation("chat.lazy.greeting.3").param("name", npc.name)
            );
        }

        return getRandomOption(
            Message.translation("chat.friendly.greeting.1").param("name", npc.name),
            Message.translation("chat.friendly.greeting.2").param("name", npc.name),
            Message.translation("chat.friendly.greeting.3").param("name", npc.name),
            Message.translation("chat.friendly.greeting.4").param("name", npc.name),
            Message.translation("chat.friendly.greeting.5").param("name", npc.name)
        );
    }

    private static final Random rand = new Random();
    private static Message getRandomOption(Message... options) {
        if (options == null || options.length == 0) return Message.raw("");
        return options[rand.nextInt(options.length)];
    }
}
