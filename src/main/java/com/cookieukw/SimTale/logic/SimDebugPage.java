package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.ai.RoutineAIComponent.TaskType;
import com.cookieukw.SimTale.core.DebugAccess;
import com.cookieukw.SimTale.core.Gender;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.NeedsHelper;
import com.hypixel.hytale.codec.builder.BuilderCodec;
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

        /* The whole action panel is a set of cheats — forcing a routine, and killing an NPC
        outright. Outside creative the screen stays open as an inspector: paging and the two
        registry views are reading, not editing.
        */
        boolean canEdit = DebugAccess.canEdit(player);
        cmd.set("#ActionPanel.Visible", canEdit);

        if (canEdit) {
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnForceEat", new EventData().append("action", "force_eat"), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnForceSleep", new EventData().append("action", "force_sleep"), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnForceBath", new EventData().append("action", "force_bath"), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnForceSocial", new EventData().append("action", "force_social"), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnSetHungerZero", new EventData().append("action", "hunger_zero"), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnResetNeeds", new EventData().append("action", "reset_needs"), false);
        }

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnPrevNpc", new EventData().append("action", "prev_npc"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnNextNpc", new EventData().append("action", "next_npc"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnViewBeds", new EventData().append("action", "view_beds"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnViewChests", new EventData().append("action", "view_chests"), false);
    }

    private void populateNPCData(UICommandBuilder cmd, SimNPCComponent npc, Store<EntityStore> store) {
        cmd.set("#NpcName.TextSpans", Message.raw(npc.name));

        String genderKey = "ui.simdebug.genderUnknown";
        if (npc.gender == Gender.MALE) {
            genderKey = "ui.simdebug.genderMale";
        } else if (npc.gender == Gender.FEMALE) {
            genderKey = "ui.simdebug.genderFemale";
        }
        cmd.set("#NpcGender.TextSpans", Message.translation(genderKey));

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
        cmd.set("#NpcTask.TextSpans", Message.translation("ui.simdebug.taskLine").param("task", taskName));

        // Needs stats
        cmd.set("#StatsHunger.TextSpans", Message.translation("ui.simdebug.statHunger")
                .param("value", formatNeed(npc, NeedsHelper.HUNGER_ID)));
        cmd.set("#StatsEnergy.TextSpans", Message.translation("ui.simdebug.statEnergy")
                .param("value", formatNeed(npc, NeedsHelper.ENERGY_ID)));
        cmd.set("#StatsSocial.TextSpans", Message.translation("ui.simdebug.statSocial")
                .param("value", formatNeed(npc, NeedsHelper.SOCIAL_ID)));
        cmd.set("#StatsHygiene.TextSpans", Message.translation("ui.simdebug.statHygiene")
                .param("value", formatNeed(npc, NeedsHelper.HYGIENE_ID)));

        // Index display
        cmd.set("#NpcIndex.TextSpans", Message.translation("ui.simdebug.pageIndex")
                .param("current", selectedIndex + 1)
                .param("total", SimTale.ACTIVE_NPCS.size()));
    }

    private String formatNeed(SimNPCComponent npc, String needId) {
        return String.format("%.0f", NeedsHelper.getNeed(null, npc.entityRef, needId));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> storeRef, @Nonnull Store<EntityStore> store, @Nonnull String eventData) {
        HytaleLogger.forEnclosingClass().atInfo().log("SimDebug [EVENT]: " + eventData);

        // Navigation
        if (eventData.contains("prev_npc")) {
            selectedIndex = Math.max(0, selectedIndex - 1);
            refreshUI();
            return;
        }
        if (eventData.contains("next_npc")) {
            selectedIndex = Math.min(SimTale.ACTIVE_NPCS.size() - 1, selectedIndex + 1);
            refreshUI();
            return;
        }
        /* Neither view rescans any more.

        Both ran bootstrapLoadedRadius at radius 96, which is a 193x193x33 box: about 1.2
        million getBlockType calls, each followed by an ItemContainerBlock component lookup,
        synchronously on a button press. That is nine times the radius-32 scan that already made
        /simtale debugchests take seconds to answer.

        Like that one, it was a workaround for furniture not being registered on placement. With
        the placement event actually reaching its handler, the registries are current and the
        sweep buys nothing. '/simtale rescan' remains for worlds built before the fix.
        */
        if (eventData.contains("view_beds")) {
            player.getPageManager().openCustomPage(storeRef, store,
                    new SimBedDebugPage(playerRefComp, player, 0, false, true));
            return;
        }
        if (eventData.contains("view_chests")) {
            player.getPageManager().openCustomPage(storeRef, store,
                    new SimChestDebugPage(playerRefComp, player, 0, false, true));
            return;
        }

        SimNPCComponent npc = getSelectedNPC();
        if (npc == null) {
            playerRefComp.sendMessage(Message.translation("ui.simdebug.msgNoNpc"));
            player.getPageManager().setPage(storeRef, store, Page.None);
            return;
        }

        /* Force actions, creative only — the bindings are withheld above, but the client still
        sends whatever action string it likes.
        */
        if (!DebugAccess.canEdit(player)) {
            return;
        }

        if (eventData.contains("force_eat")) {
            forceTask(npc, TaskType.FINDING_FOOD, store);
            playerRefComp.sendMessage(Message.translation("ui.simdebug.msgForcedEat").param("name", npc.name));
        } else if (eventData.contains("force_sleep")) {
            NeedsHelper.setNeed(null, npc.entityRef, NeedsHelper.ENERGY_ID, 0f);
            forceTask(npc, TaskType.FINDING_BED, store);
            playerRefComp.sendMessage(Message.translation("ui.simdebug.msgForcedSleep").param("name", npc.name));
        } else if (eventData.contains("force_bath")) {
            forceTask(npc, TaskType.FINDING_BATH, store);
            playerRefComp.sendMessage(Message.translation("ui.simdebug.msgForcedBath").param("name", npc.name));
        } else if (eventData.contains("force_social")) {
            forceTask(npc, TaskType.WANDERING, store);
            playerRefComp.sendMessage(Message.translation("ui.simdebug.msgForcedSocial").param("name", npc.name));
        } else if (eventData.contains("hunger_zero")) {
            NeedsHelper.setNeed(null, npc.entityRef, NeedsHelper.HUNGER_ID, 0f);
            playerRefComp.sendMessage(Message.translation("ui.simdebug.msgHungerZero").param("name", npc.name));
        } else if (eventData.contains("reset_needs")) {
            NeedsHelper.setNeed(null, npc.entityRef, NeedsHelper.HUNGER_ID, 100f);
            NeedsHelper.setNeed(null, npc.entityRef, NeedsHelper.ENERGY_ID, 100f);
            NeedsHelper.setNeed(null, npc.entityRef, NeedsHelper.SOCIAL_ID, 100f);
            NeedsHelper.setNeed(null, npc.entityRef, NeedsHelper.FUN_ID, 100f);
            NeedsHelper.setNeed(null, npc.entityRef, NeedsHelper.HYGIENE_ID, 100f);
            playerRefComp.sendMessage(Message.translation("ui.simdebug.msgResetNeeds").param("name", npc.name));
        }

        refreshUI();
    }

    private void forceTask(SimNPCComponent npc, TaskType task, Store<EntityStore> store) {
        if (npc.entityRef == null) return;
        RoutineAIComponent ai = store.getComponent(npc.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
        if (ai != null) {
            ai.currentTask = task;
            ai.targetBlockPosition = null;
            ai.forcedByDebug = true;

            World world;
            world = WorldUtil.first();
            if (world != null) {
                ai.taskStartTime = world.getTick();
            }

            store.putComponent(npc.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE, ai);
        }
    }

    private void refreshUI() {
        player.getPageManager().clearCustomPageAcknowledgements();
        rebuild();
    }
}
