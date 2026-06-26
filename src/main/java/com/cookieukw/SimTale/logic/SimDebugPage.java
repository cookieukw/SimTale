package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.ai.RoutineAIComponent.TaskType;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nonnull;

@SuppressWarnings("null")
public class SimDebugPage extends InteractiveCustomUIPage<String> {

    private final Player player;
    private final PlayerRef playerRefComp;
    private int selectedIndex = 0;

    public SimDebugPage(@Nonnull PlayerRef playerRefComp, Player player) {
        super(playerRefComp, CustomPageLifetime.CanDismiss, null);
        this.player = player;
        this.playerRefComp = playerRefComp;
        this.selectedIndex = 0;
    }

    public SimDebugPage(@Nonnull PlayerRef playerRefComp, Player player, int initialIndex) {
        super(playerRefComp, CustomPageLifetime.CanDismiss, null);
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
    public void build(Ref<EntityStore> playerRef, UICommandBuilder cmd, UIEventBuilder eventBuilder, Store<EntityStore> store) {
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
        cmd.set("#StatsHunger.Text", "Fome: " + String.format("%.0f", npc.needs.hunger));
        cmd.set("#StatsEnergy.Text", "Energia: " + String.format("%.0f", npc.needs.energy));
        cmd.set("#StatsSocial.Text", "Social: " + String.format("%.0f", npc.needs.social));
        cmd.set("#StatsHygiene.Text", "Higiene: " + String.format("%.0f", npc.needs.hygiene));

        // Index display
        cmd.set("#NpcIndex.Text", (selectedIndex + 1) + " / " + SimTale.ACTIVE_NPCS.size());
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> storeRef, Store<EntityStore> store, String eventData) {
        HytaleLogger.forEnclosingClass().atInfo().log("SimDebug [EVENT]: " + eventData);

        if (eventData == null) return;

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
            forceTask(npc, TaskType.FINDING_BED, store);
            playerRefComp.sendMessage(Message.raw("[SimDebug] " + npc.name + " forcado a dormir!"));
        } else if (eventData.contains("force_bath")) {
            forceTask(npc, TaskType.FINDING_BATH, store);
            playerRefComp.sendMessage(Message.raw("[SimDebug] " + npc.name + " forcado a tomar banho!"));
        } else if (eventData.contains("force_social")) {
            forceTask(npc, TaskType.WANDERING, store);
            playerRefComp.sendMessage(Message.raw("[SimDebug] " + npc.name + " forcado a socializar/andar!"));
        } else if (eventData.contains("hunger_zero")) {
            npc.needs.hunger = 0f;
            playerRefComp.sendMessage(Message.raw("[SimDebug] Fome de " + npc.name + " zerada! (Teste de morte)"));
        } else if (eventData.contains("reset_needs")) {
            npc.needs.hunger = 100f;
            npc.needs.energy = 100f;
            npc.needs.social = 100f;
            npc.needs.fun = 100f;
            npc.needs.hygiene = 100f;
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
            for (World w : Universe.get().getWorlds().values()) { world = w; break; }
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
