package com.cookieukw.SimTale.logic;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.logger.HytaleLogger;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.Trait;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.ai.RoutineAIComponent.TaskType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.Frozen;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.joml.Vector3d;
import javax.annotation.Nonnull;
import java.util.stream.Collectors;

@SuppressWarnings("null")
public class NPCInteractionPage extends InteractiveCustomUIPage<String> {

    private final SimNPCComponent npc;
    private final Player player;
    private final PlayerRef playerRefComp;

    public NPCInteractionPage(@Nonnull PlayerRef playerRefComp, Player player, SimNPCComponent npc) {
        BuilderCodec<String> codec = BuilderCodec.builder(String.class, String::new).build();
        super(playerRefComp, CustomPageLifetime.CanDismiss, codec);
        this.npc = npc;
        this.player = player;
        this.playerRefComp = playerRefComp;
    }

    @Override
    public void build(@NonNullDecl Ref<EntityStore> playerRef, UICommandBuilder commandBuilder, @NonNullDecl UIEventBuilder eventBuilder, @NonNullDecl Store<EntityStore> store) {
        if (npc != null && npc.entityRef != null && npc.entityRef.isValid()) {
            store.ensureComponent(npc.entityRef, Frozen.getComponentType());
        }
        commandBuilder.append("NPCInteraction/NPCInteraction.ui");
        
        // --- NPC Header Info ---
        commandBuilder.set("#NpcName.Text", npc.name);
        
        // Profession
        String profName = npc.profession != null ? npc.profession.ptName : "Desempregado";
        commandBuilder.set("#NpcProfession.Text", "Profissão: " + profName);
        
        // Mood
        Mood currentMood = npc.getMood();
        String moodEmoji = switch (currentMood) {
            case HAPPY -> " :)";
            case ANGRY -> " >:(";
            case SAD -> " :(";
            case SCARED -> " D:";
            case SLEEPY -> " -.-";
            case EXCITED -> " :D";
            default -> "";
        };
        commandBuilder.set("#NpcMood.Text", "Humor: " + currentMood.ptName + moodEmoji);

        if (currentMood == Mood.ANGRY) {
            commandBuilder.set("#NpcName.Style.TextColor", "#FF6666");
        } else if (currentMood == Mood.SAD) {
            commandBuilder.set("#NpcName.Style.TextColor", "#6688CC");
        }

        // --- Info Panel ---
        // Traits
        if (npc.personality != null && npc.personality.traits != null && !npc.personality.traits.isEmpty()) {
            String traitsStr = npc.personality.traits.stream()
                .map(t -> getTraitPtName(t))
                .collect(Collectors.joining(", "));
            commandBuilder.set("#NpcTraits.Text", "Traços: " + traitsStr);
        }
        
        // Preferences
        if (npc.preferences != null) {
            if (npc.preferences.favoriteFoods != null && !npc.preferences.favoriteFoods.isEmpty()) {
                String likesStr = npc.preferences.favoriteFoods.stream()
                    .map(com.cookieukw.SimTale.core.NPCPreferences::getFoodPtName)
                    .collect(Collectors.joining(", "));
                commandBuilder.set("#NpcLikes.Text", "Gosta de: " + likesStr);
            }
            if (npc.preferences.hatedFoods != null && !npc.preferences.hatedFoods.isEmpty()) {
                String hatesStr = npc.preferences.hatedFoods.stream()
                    .map(com.cookieukw.SimTale.core.NPCPreferences::getFoodPtName)
                    .collect(Collectors.joining(", "));
                commandBuilder.set("#NpcHates.Text", "Odeia: " + hatesStr);
            }
            commandBuilder.set("#NpcHobby.Text", "Hobby: " + npc.preferences.hobby);
            commandBuilder.set("#NpcSeason.Text", "Estação Fav.: " + npc.preferences.getSeasonPtName());
        }
        
        // Relationship
        Relationship rel = npc.getRelationship(playerRefComp.getUuid());
        commandBuilder.set("#NpcRelationship.Text", 
            rel.getStatusPtName() + " (Amizade: " + rel.friendship + " | Afinidade: " + rel.affinity + ")");

        // --- Button Event Bindings ---
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ChatButton", new EventData().append("button", "ChatButton"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#JokeButton", new EventData().append("button", "JokeButton"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#FlirtButton", new EventData().append("button", "FlirtButton"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#InsultButton", new EventData().append("button", "InsultButton"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#GiftButton", new EventData().append("button", "GiftButton"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AssignProfessionButton", new EventData().append("button", "AssignProfessionButton"), false);
    }

    @Override
    public void handleDataEvent(@NonNullDecl Ref<EntityStore> storeRef, @NonNullDecl Store<EntityStore> store, @NonNullDecl String eventData) {
        HytaleLogger.forEnclosingClass().atInfo().log("SimTale [DEBUG UI EVENT]: payload = " + eventData);
        
        // Fechar a pagina imediatamente para parar o "loading" no cliente
        player.getPageManager().setPage(storeRef, store, Page.None);

        if (npc.name.equals("Dona Morte")) {
            RoutineAIComponent reaperAi = store.getComponent(npc.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
            if (reaperAi != null && reaperAi.currentTask == TaskType.REAPING && reaperAi.dyingEntityId != null) {
                if (Math.random() < 0.5) {
                    playerRefComp.sendMessage(Message.raw("Dona Morte acenou a cabeca. A vida foi poupada... desta vez."));
                    // Heal the dying NPC
                    World w = null;
                    for (World world : Universe.get().getWorlds().values()) { w = world; break; }
                    if (w != null) {
                        Ref<EntityStore> dyingRef = w.getEntityStore().getRefFromUUID(reaperAi.dyingEntityId);
                        if (dyingRef != null) {
                            SimNPCComponent dyingNpc = store.getComponent(dyingRef, SimTale.SIM_NPC_COMPONENT_TYPE);
                            if (dyingNpc != null) {
                                dyingNpc.needs.hunger = 50f;
                                store.putComponent(dyingRef, SimTale.SIM_NPC_COMPONENT_TYPE, dyingNpc);
                            }
                            RoutineAIComponent dyingAi = store.getComponent(dyingRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
                            if (dyingAi != null) {
                                dyingAi.currentTask = TaskType.IDLE;
                                store.putComponent(dyingRef, SimTale.ROUTINE_AI_COMPONENT_TYPE, dyingAi);
                                // Play idle animation
                                AnimationUtils.playAnimation(dyingRef, AnimationSlot.Action, "Characters/Animations/Actions/Idle.blockyanim", "Idle", store);
                            }
                        }
                    }
                    if (w != null) {
                        TransformComponent t = store.getComponent(npc.entityRef, TransformComponent.getComponentType());
                        if (t != null) {
                            t.setPosition(new Vector3d(0, -1000, 0));
                            store.putComponent(npc.entityRef, TransformComponent.getComponentType(), t);
                        }
                    }
                } else {
                    playerRefComp.sendMessage(Message.raw("Dona Morte te ignorou friamente..."));
                    reaperAi.reapTimer = 0; // Force immediate reaping
                    store.putComponent(npc.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE, reaperAi);
                }
            }
            return;
        }

        if (eventData.contains("ChatButton")) {
            String resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), playerRefComp, InteractionType.FRIENDLY);
            playerRefComp.sendMessage(Message.raw(resp));
        } else if (eventData.contains("JokeButton")) {
            String resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), playerRefComp, InteractionType.FUNNY);
            playerRefComp.sendMessage(Message.raw(resp));
        } else if (eventData.contains("FlirtButton")) {
            String resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), playerRefComp, InteractionType.ROMANTIC);
            playerRefComp.sendMessage(Message.raw(resp));
        } else if (eventData.contains("InsultButton")) {
            String resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), playerRefComp, InteractionType.MEAN);
            playerRefComp.sendMessage(Message.raw(resp));
        } else if (eventData.contains("GiftButton")) {
            String resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), playerRefComp, InteractionType.GIFT);
            playerRefComp.sendMessage(Message.raw(resp));
        } else if (eventData.contains("AssignProfessionButton")) {
            String resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), playerRefComp, InteractionType.ASSIGN_PROFESSION);
            playerRefComp.sendMessage(Message.raw(resp));
        }
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        super.onDismiss(playerRef, store);
        if (npc != null && npc.entityRef != null && npc.entityRef.isValid()) {
            store.tryRemoveComponent(npc.entityRef, Frozen.getComponentType());
        }
    }
    
    private static String getTraitPtName(Trait t) {
        return switch (t) {
            case SHY -> "Tímido";
            case AGGRESSIVE -> "Agressivo";
            case NEEDY -> "Carente";
            case GREEDY -> "Ganancioso";
            case LAZY -> "Preguiçoso";
            case LOYAL -> "Leal";
            case PARANOID -> "Paranoico";
            case FUNNY -> "Engraçado";
        };
    }
}
