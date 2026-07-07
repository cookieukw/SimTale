package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.lifecycle.PregnancyComponent;
import com.cookieukw.SimTale.core.lifecycle.LifecycleUtils;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;
import javax.annotation.Nonnull;

@SuppressWarnings("null")
public class NPCPregnancyPage extends InteractiveCustomUIPage<String> {

    private final SimNPCComponent npc;
    private final Player player;
    private final PlayerRef playerRefComp;

    public NPCPregnancyPage(@Nonnull PlayerRef playerRefComp, Player player, SimNPCComponent npc) {
        super(playerRefComp, CustomPageLifetime.CanDismiss, BuilderCodec.builder(String.class, String::new).build());
        this.npc = npc;
        this.player = player;
        this.playerRefComp = playerRefComp;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> playerRef, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("NPCPregnancy/NPCPregnancy.ui");

        commandBuilder.set("#NpcName.Text", npc.name);

        PregnancyComponent preg = npc.pregnancy;
        if (preg == null || !preg.pregnant) {
            commandBuilder.set("#GestationStage.Text", "Estado: Não Grávida");
            commandBuilder.set("#FatherName.Text", "Pai do Bebê: —");
            commandBuilder.set("#ProgressText.Text", "Progresso: 0%");
            commandBuilder.set("#TimeRemaining.Text", "Tempo Restante: —");
        } else {
            // Get current tick to calculate progress and remaining time
            World world = Universe.get().getWorlds().values().iterator().next();
            long currentTick = world.getTick();

            // 1. Father's Name
            String fatherName = getFatherName(preg.fatherId);
            commandBuilder.set("#FatherName.Text", "Pai do Bebê: " + fatherName);

            // 2. Stage and Trimester
            String stageDescription = switch (preg.trimester) {
                case 1 -> "1º Trimestre (Início)";
                case 2 -> "2º Trimestre (Desenvolvimento)";
                case 3 -> "3º Trimestre (Reta Final)";
                default -> "Desconhecido";
            };
            commandBuilder.set("#GestationStage.Text", "Estágio: " + stageDescription);

            // 3. Progress Percentage
            float progress = preg.getProgress(currentTick);
            int percentage = Math.round(progress * 100);
            commandBuilder.set("#ProgressText.Text", "Progresso da Gestação: " + percentage + "%");

            // 4. Remaining time calculation
            long ticksRemaining = Math.max(0, (preg.startTick + preg.durationTicks) - currentTick);
            long daysRemaining = ticksRemaining / 24000L;
            long minsRemaining = (ticksRemaining / 20L) / 60L;

            String timeText;
            if (daysRemaining > 0) {
                timeText = String.format("Tempo Restante: %d dias (%d min reais)", daysRemaining, minsRemaining);
            } else {
                timeText = String.format("Tempo Restante: %d min reais", minsRemaining);
            }
            commandBuilder.set("#TimeRemaining.Text", timeText);
        }

        // 5. Total Children
        int totalChildren = npc.family.children != null ? npc.family.children.size() : 0;
        commandBuilder.set("#TotalChildren.Text", "Total de Filhos: " + totalChildren);

        // Bind Back Button
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton", new EventData().append("button", "BackButton"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> storeRef, @Nonnull Store<EntityStore> store, @Nonnull String eventData) {
        player.getPageManager().setPage(storeRef, store, Page.None);

        if (eventData.contains("BackButton")) {
            // Return to the main NPC Interaction UI
            player.getPageManager().openCustomPage(storeRef, store, new NPCInteractionPage(playerRefComp, player, npc));
        }
    }

    private String getFatherName(UUID fatherId) {
        if (fatherId == null) return "Desconhecido";
        for (SimNPCComponent other : com.cookieukw.SimTale.SimTale.ACTIVE_NPCS) {
            if (other.entityId != null && other.entityId.equals(fatherId)) {
                return other.name;
            }
        }
        for (PlayerRef pRef : Universe.get().getPlayers()) {
            if (pRef.getUuid().equals(fatherId)) {
                return pRef.getUsername();
            }
        }
        SimNPCComponent temp = new SimNPCComponent(fatherId, "Father");
        SimNPCPersistence.loadNPC(temp);
        if (!temp.name.equals("Father")) {
            return temp.name;
        }
        return "Desconhecido";
    }
}
