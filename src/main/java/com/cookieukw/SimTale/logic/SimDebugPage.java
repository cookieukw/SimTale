package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.ai.RoutineAIComponent.TaskType;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.NeedsHelper;
import com.cookieukw.SimTale.systems.BedWorldBootstrap;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.cookieukw.SimTale.core.WorldUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nonnull;

@SuppressWarnings("null")
public class SimDebugPage extends InteractiveCustomUIPage<String> {

    private final Player player;
    private final PlayerRef playerRefComp;
    private int selectedIndex;

    public SimDebugPage(@Nonnull PlayerRef playerRefComp, Player player) {
        BuilderCodec<String> codec = BuilderCodec.builder(String.class, String::new).build();
        super(playerRefComp, CustomPageLifetime.CanDismiss, codec);
        this.player = player;
        this.playerRefComp = playerRefComp;
        this.selectedIndex = 0;
    }

    public SimDebugPage(@Nonnull PlayerRef playerRefComp, Player player, int initialIndex) {
        BuilderCodec<String> codec = BuilderCodec.builder(String.class, String::new).build();
        super(playerRefComp, CustomPageLifetime.CanDismiss, codec);
        this.player = player;
        this.playerRefComp = playerRefComp;
        this.selectedIndex = initialIndex;
    }

    private SimNPCComponent getSelectedNPC() {
        if (SimTale.ACTIVE_NPCS.isEmpty()) return null;
        if (selectedIndex < 0) selectedIndex = 0;
        if (selectedIndex >= SimTale.ACTIVE_NPCS.size()) selectedIndex = SimTale.ACTIVE_NPCS.size() - 1;
        return SimTale.ACTIVE_NPCS.get(selectedIndex);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> playerRef, UICommandBuilder cmd, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        cmd.append("SimDebug/SimDebug.ui");

        SimNPCComponent npc = getSelectedNPC();
        if (npc != null) {
            populateNPCData(cmd, npc, store);
        }

        // Register buttons
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnForceEat", new EventData().append("action", "force_eat"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnForceSleep", new EventData().append("action", "force_sleep"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnForceBath", new EventData().append("action", "force_bath"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnForceSocial", new EventData().append("action", "force_social"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnSetHungerZero", new EventData().append("action", "hunger_zero"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnResetNeeds", new EventData().append("action", "reset_needs"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnPrevNpc", new EventData().append("action", "prev_npc"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnNextNpc", new EventData().append("action", "next_npc"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnViewBeds", new EventData().append("action", "view_beds"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnViewChests", new EventData().append("action", "view_chests"), false);
    }

    private void populateNPCData(UICommandBuilder cmd, SimNPCComponent npc, Store<EntityStore> store) {
        cmd.set("#NpcName.Text", npc.name);
        cmd.set("#NpcGender.Text", npc.gender != null ? npc.gender.getDisplayName() : "Indefinido");

        // Get current AI task
        String taskName = "IDLE";
        if (npc.entityRef != null) {
            RoutineAIComponent ai = store.getComponent(npc.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
            if (ai != null) {
                taskName = ai.currentTask.name();
                if (ai.forcedByDebug) {
                    taskName += " [DEBUG]";
                }
            }
        }
        cmd.set("#NpcTask.Text", "Task: " + taskName);

        // Needs stats
        cmd.set("#StatsHunger.Text", "Fome: " + String.format("%.0f", NeedsHelper.getNeed(null, npc.entityRef, NeedsHelper.HUNGER_ID)));
        cmd.set("#StatsEnergy.Text", "Energia: " + String.format("%.0f", NeedsHelper.getNeed(null, npc.entityRef, NeedsHelper.ENERGY_ID)));
        cmd.set("#StatsSocial.Text", "Social: " + String.format("%.0f", NeedsHelper.getNeed(null, npc.entityRef, NeedsHelper.SOCIAL_ID)));
        cmd.set("#StatsHygiene.Text", "Higiene: " + String.format("%.0f", NeedsHelper.getNeed(null, npc.entityRef, NeedsHelper.HYGIENE_ID)));

        // Index display
        cmd.set("#NpcIndex.Text", (selectedIndex + 1) + " / " + SimTale.ACTIVE_NPCS.size());
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> storeRef, @Nonnull Store<EntityStore> store, @Nonnull String eventData) {
        HytaleLogger.forEnclosingClass().atInfo().log("SimDebug [EVENT]: " + eventData);

        // Navigation
        if (eventData.contains("prev_npc")) {
            selectedIndex = Math.max(0, selectedIndex - 1);
            refreshUI(storeRef, store);
            return;
        }
        if (eventData.contains("next_npc")) {
            selectedIndex = Math.min(SimTale.ACTIVE_NPCS.size() - 1, selectedIndex + 1);
            refreshUI(storeRef, store);
            return;
        }
        if (eventData.contains("view_beds")) {
            TransformComponent tc = store.getComponent(storeRef, TransformComponent.getComponentType());
            if (tc != null) {
                BedWorldBootstrap.bootstrapLoadedRadius(store.getExternalData().getWorld(), tc.getPosition(), 96);
            }
            player.getPageManager().openCustomPage(storeRef, store, new SimBedDebugPage(playerRefComp, player));
            return;
        }
        if (eventData.contains("view_chests")) {
            // Same wide rescan the bed view does, so a chest placed before the server came up
            // shows here without the player having to walk over and replace it.
            TransformComponent tc = store.getComponent(storeRef, TransformComponent.getComponentType());
            if (tc != null) {
                BedWorldBootstrap.bootstrapLoadedRadius(store.getExternalData().getWorld(), tc.getPosition(), 96);
            }
            player.getPageManager().openCustomPage(storeRef, store, new SimChestDebugPage(playerRefComp, player));
            return;
        }

        SimNPCComponent npc = getSelectedNPC();
        if (npc == null) {
            playerRefComp.sendMessage(Message.raw("[SimDebug] Nenhum NPC disponivel."));
            player.getPageManager().setPage(storeRef, store, Page.None);
            return;
        }

        // Force actions
        if (eventData.contains("force_eat")) {
            forceTask(npc, TaskType.FINDING_FOOD, store);
            playerRefComp.sendMessage(Message.raw("[SimDebug] " + npc.name + " forcado a comer!"));
        } else if (eventData.contains("force_sleep")) {
            NeedsHelper.setNeed(null, npc.entityRef, NeedsHelper.ENERGY_ID, 0f);
            forceTask(npc, TaskType.FINDING_BED, store);
            playerRefComp.sendMessage(Message.raw("[SimDebug] " + npc.name + " forcado a dormir!"));
        } else if (eventData.contains("force_bath")) {
            forceTask(npc, TaskType.FINDING_BATH, store);
            playerRefComp.sendMessage(Message.raw("[SimDebug] " + npc.name + " forcado a tomar banho!"));
        } else if (eventData.contains("force_social")) {
            forceTask(npc, TaskType.WANDERING, store);
            playerRefComp.sendMessage(Message.raw("[SimDebug] " + npc.name + " forcado a socializar/andar!"));
        } else if (eventData.contains("hunger_zero")) {
            NeedsHelper.setNeed(null, npc.entityRef, NeedsHelper.HUNGER_ID, 0f);
            playerRefComp.sendMessage(Message.raw("[SimDebug] Fome de " + npc.name + " zerada! (Teste de morte)"));
        } else if (eventData.contains("reset_needs")) {
            NeedsHelper.setNeed(null, npc.entityRef, NeedsHelper.HUNGER_ID, 100f);
            NeedsHelper.setNeed(null, npc.entityRef, NeedsHelper.ENERGY_ID, 100f);
            NeedsHelper.setNeed(null, npc.entityRef, NeedsHelper.SOCIAL_ID, 100f);
            NeedsHelper.setNeed(null, npc.entityRef, NeedsHelper.FUN_ID, 100f);
            NeedsHelper.setNeed(null, npc.entityRef, NeedsHelper.HYGIENE_ID, 100f);
            playerRefComp.sendMessage(Message.raw("[SimDebug] Needs de " + npc.name + " resetados para 100!"));
        }

        refreshUI(storeRef, store);
    }

    private void forceTask(SimNPCComponent npc, TaskType task, Store<EntityStore> store) {
        if (npc.entityRef == null) return;
        RoutineAIComponent ai = store.getComponent(npc.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
        if (ai != null) {
            ai.currentTask = task;
            ai.targetBlockPosition = null;
            ai.forcedByDebug = true;

            World world = null;
            world = WorldUtil.first();
            if (world != null) {
                ai.taskStartTime = world.getTick();
            }

            store.putComponent(npc.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE, ai);
        }
    }

    private void refreshUI(Ref<EntityStore> storeRef, Store<EntityStore> store) {
        // Reopen the page to refresh it with updated data
        player.getPageManager().setPage(storeRef, store, Page.None);
        player.getPageManager().openCustomPage(storeRef, store, new SimDebugPage(playerRefComp, player, selectedIndex));
    }
}
