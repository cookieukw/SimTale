package com.cookieukw.SimTale.logic;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.logger.HytaleLogger;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.Trait;
import com.cookieukw.SimTale.core.Profession;
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
    public void build(@NonNullDecl Ref<EntityStore> playerRef, @NonNullDecl UICommandBuilder commandBuilder, @NonNullDecl UIEventBuilder eventBuilder, @NonNullDecl Store<EntityStore> store) {
        if (npc != null && npc.entityRef != null && npc.entityRef.isValid()) {
            store.ensureComponent(npc.entityRef, Frozen.getComponentType());
        }
        commandBuilder.append("NPCInteraction/NPCInteraction.ui");

        assert npc != null;
        commandBuilder.set("#NpcName.Text", npc.name);
        
        Message profMsg = npc.profession != null && npc.profession != Profession.UNEMPLOYED 
            ? Message.translation("simtale.prof." + npc.profession.name().toLowerCase()) 
            : Message.translation("simtale.prof.unemployed");
        commandBuilder.set("#NpcProfession.TextSpans", Message.translation("simtale.ui.job").insert(Message.raw(" ")).insert(profMsg));
        
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
        // Mood isn't fully translated yet, fallback to raw text but we can add it later
        commandBuilder.set("#NpcMood.Text", "Humor: " + currentMood.ptName + moodEmoji);

        if (currentMood == Mood.ANGRY) {
            commandBuilder.set("#NpcName.Style.TextColor", "#FF6666");
        } else if (currentMood == Mood.SAD) {
            commandBuilder.set("#NpcName.Style.TextColor", "#6688CC");
        }

        // --- Info Panel Static UI Overrides ---
        commandBuilder.set("#InfoHeader.TextSpans", Message.translation("simtale.ui.info"));
        commandBuilder.set("#ChatButtonText.TextSpans", Message.translation("simtale.ui.button.chat"));
        commandBuilder.set("#JokeButtonText.TextSpans", Message.translation("simtale.ui.button.joke"));
        commandBuilder.set("#FlirtButtonText.TextSpans", Message.translation("simtale.ui.button.flirt"));
        commandBuilder.set("#GiftButtonText.TextSpans", Message.translation("simtale.ui.button.gift"));
        commandBuilder.set("#InsultButtonText.TextSpans", Message.translation("simtale.ui.button.insult"));
        commandBuilder.set("#AssignProfessionButtonText.TextSpans", Message.translation("simtale.ui.button.prof"));

        // Traits
        if (npc.personality != null && npc.personality.traits != null && !npc.personality.traits.isEmpty()) {
            Message traitsMsg = Message.raw("");
            boolean first = true;
            for (Trait t : npc.personality.traits) {
                if (!first) {
                    traitsMsg = traitsMsg.insert(Message.raw(", "));
                }
                traitsMsg = traitsMsg.insert(Message.translation("simtale.trait." + t.name().toLowerCase()));
                first = false;
            }
            commandBuilder.set("#NpcTraits.TextSpans", Message.translation("simtale.ui.traits").insert(Message.raw(" ")).insert(traitsMsg));
        }
        
        // Preferences
        if (npc.preferences != null) {
            if (npc.preferences.favoriteFoods != null && !npc.preferences.favoriteFoods.isEmpty()) {
                Message likesMsg = Message.raw("");
                boolean first = true;
                for (String foodId : npc.preferences.favoriteFoods) {
                    if (!first) likesMsg = likesMsg.insert(Message.raw(", "));
                    likesMsg = likesMsg.insert(Message.translation("simtale." + foodId));
                    first = false;
                }
                commandBuilder.set("#NpcLikes.TextSpans", Message.translation("simtale.ui.likes").insert(Message.raw(" ")).insert(likesMsg));
            }
            if (npc.preferences.hatedFoods != null && !npc.preferences.hatedFoods.isEmpty()) {
                Message hatesMsg = Message.raw("");
                boolean first = true;
                for (String foodId : npc.preferences.hatedFoods) {
                    if (!first) hatesMsg = hatesMsg.insert(Message.raw(", "));
                    hatesMsg = hatesMsg.insert(Message.translation("simtale." + foodId));
                    first = false;
                }
                commandBuilder.set("#NpcHates.TextSpans", Message.translation("simtale.ui.hates").insert(Message.raw(" ")).insert(hatesMsg));
            }
            commandBuilder.set("#NpcHobby.TextSpans", Message.translation("simtale.ui.hobby").insert(Message.raw(" " + npc.preferences.hobby)));
            
            String seasonKey = "season." + (npc.preferences.favoriteSeason != null ? npc.preferences.favoriteSeason.toLowerCase() : "spring");
            commandBuilder.set("#NpcSeason.TextSpans", Message.translation("simtale.ui.season").insert(Message.raw(" ")).insert(Message.translation("simtale." + seasonKey)));
        }
        
        // Relationship
        Relationship rel = npc.getRelationship(playerRefComp.getUuid());
        Message statusMsg = Message.translation("simtale.rel." + rel.getStatusName().toLowerCase());
        commandBuilder.set("#NpcRelationship.TextSpans", 
            Message.translation("simtale.ui.relationship").insert(Message.raw(" "))
            .insert(statusMsg)
            .insert(Message.raw(" (" + rel.friendship + " | " + rel.affinity + ")")));

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
        
        player.getPageManager().setPage(storeRef, store, Page.None);

        if (npc.name.equals("Dona Morte")) {
            RoutineAIComponent reaperAi = store.getComponent(npc.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
            if (reaperAi != null && reaperAi.currentTask == TaskType.REAPING && reaperAi.dyingEntityId != null) {
                if (Math.random() < 0.5) {
                    playerRefComp.sendMessage(Message.raw("Dona Morte acenou a cabeca. A vida foi poupada... desta vez."));
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
                    reaperAi.reapTimer = 0;
                    store.putComponent(npc.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE, reaperAi);
                }
            }
            return;
        }

        if (eventData.contains("ChatButton")) {
            Message resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), playerRefComp, InteractionType.FRIENDLY);
            playerRefComp.sendMessage(resp);
        } else if (eventData.contains("JokeButton")) {
            Message resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), playerRefComp, InteractionType.FUNNY);
            playerRefComp.sendMessage(resp);
        } else if (eventData.contains("FlirtButton")) {
            Message resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), playerRefComp, InteractionType.ROMANTIC);
            playerRefComp.sendMessage(resp);
        } else if (eventData.contains("InsultButton")) {
            Message resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), playerRefComp, InteractionType.MEAN);
            playerRefComp.sendMessage(resp);
        } else if (eventData.contains("GiftButton")) {
            Message resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), playerRefComp, InteractionType.GIFT);
            playerRefComp.sendMessage(resp);
        } else if (eventData.contains("AssignProfessionButton")) {
            Message resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), playerRefComp, InteractionType.ASSIGN_PROFESSION);
            playerRefComp.sendMessage(resp);
        }
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        super.onDismiss(playerRef, store);
        if (npc != null && npc.entityRef != null && npc.entityRef.isValid()) {
            store.tryRemoveComponent(npc.entityRef, Frozen.getComponentType());
        }
    }
}
