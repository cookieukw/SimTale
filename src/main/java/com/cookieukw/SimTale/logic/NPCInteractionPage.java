package com.cookieukw.SimTale.logic;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.logger.HytaleLogger;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.SimNPCComponent;
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
import org.joml.Vector3d;
import javax.annotation.Nonnull;

public class NPCInteractionPage extends InteractiveCustomUIPage<String> {

    private final SimNPCComponent npc;
    private final Player player;
    private final PlayerRef playerRefComp;

    public NPCInteractionPage(@Nonnull PlayerRef playerRefComp, Player player, SimNPCComponent npc) {
        super(playerRefComp, CustomPageLifetime.CanDismiss, null);
        this.npc = npc;
        this.player = player;
        this.playerRefComp = playerRefComp;
    }

    @Override
    public void build(Ref<EntityStore> playerRef, UICommandBuilder commandBuilder, UIEventBuilder eventBuilder, Store<EntityStore> store) {
        commandBuilder.append("NPCInteraction/NPCInteraction.ui");
        
        commandBuilder.set("#NpcName.Text", npc.name);
        
        Mood currentMood = npc.getMood();
        commandBuilder.set("#NpcMood.Text", "Humor: " + currentMood.ptName);

        if (currentMood == Mood.ANGRY) {
            commandBuilder.set("#NpcName.Style.TextColor", "#FF0000"); 
        }

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ChatButton", new EventData().append("button", "ChatButton"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#JokeButton", new EventData().append("button", "JokeButton"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#FlirtButton", new EventData().append("button", "FlirtButton"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#InsultButton", new EventData().append("button", "InsultButton"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#GiftButton", new EventData().append("button", "GiftButton"), false);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> storeRef, Store<EntityStore> store, String eventData) {
        HytaleLogger.forEnclosingClass().atInfo().log("SimTale [DEBUG UI EVENT]: payload = " + eventData);
        
        // Fechar a pagina imediatamente para parar o "loading" no cliente
        player.getPageManager().setPage(storeRef, store, Page.None);

        if (eventData == null) return;

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
        }
    }
}
