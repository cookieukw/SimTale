package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.lifecycle.PregnancyComponent;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;


import java.util.UUID;

public final class PregnancyDisplayUtil {

    private static final int PROGRESS_BAR_MAX_WIDTH = 480;

    private PregnancyDisplayUtil() {}

    public static long getCurrentWorldTick() {
        for (World world : Universe.get().getWorlds().values()) {
            return world.getTick();
        }
        return 0L;
    }

    public static String resolveParentName(UUID parentId) {
        if (parentId == null) {
            return "—";
        }
        for (SimNPCComponent other : SimTale.ACTIVE_NPCS) {
            if (other.entityId != null && other.entityId.equals(parentId)) {
                return other.name;
            }
        }
        for (PlayerRef pRef : Universe.get().getPlayers()) {
            if (pRef.getUuid().equals(parentId)) {
                return pRef.getUsername();
            }
        }
        SimNPCComponent temp = new SimNPCComponent(parentId, "Parent");
        SimNPCPersistence.loadNPC(temp);
        if (!temp.name.equals("Parent")) {
            return temp.name;
        }
        return "—";
    }

    public static void populatePregnancyUI(
            UICommandBuilder cmd,
            PregnancyComponent preg,
            long currentTick,
            String subjectName,
            int totalChildren,
            boolean isPlayer) {

        cmd.set("#SubjectName.Text", subjectName);
        cmd.set("#SubTitle.TextSpans", Message.translation("simtale.ui.pregnancy.subtitle"));
        cmd.set("#BackButtonText.TextSpans", Message.translation("simtale.ui.pregnancy.back"));
        cmd.set("#CloseButtonText.TextSpans", Message.translation("simtale.ui.pregnancy.close"));

        if (preg == null || !preg.pregnant) {
            cmd.set("#GestationStage.TextSpans", Message.translation("simtale.ui.pregnancy.not_pregnant"));
            cmd.set("#FatherName.TextSpans", Message.translation("simtale.ui.pregnancy.father").param("name", "—"));
            cmd.set("#ElapsedDays.TextSpans", Message.translation("simtale.ui.pregnancy.elapsed").param("current", "0").param("total", "0"));
            cmd.set("#ProgressText.TextSpans", Message.translation("simtale.ui.pregnancy.progress").param("percent", "0"));
            cmd.set("#TimeRemaining.TextSpans", Message.translation("simtale.ui.pregnancy.time_none"));
            cmd.set("#Symptoms.TextSpans", Message.translation("simtale.ui.pregnancy.symptoms_none"));
            cmd.set("#TotalChildren.TextSpans", Message.translation("simtale.ui.pregnancy.children").param("count", String.valueOf(totalChildren)));
            Anchor anchor = new Anchor();
            anchor.setWidth(Value.of(0));
            anchor.setHeight(.Value.of(20));
            cmd.setObject("#ProgressBarFill.Anchor", anchor); 
            return;
        }

        String fatherName = resolveParentName(preg.fatherId);
        cmd.set("#FatherName.TextSpans", Message.translation("simtale.ui.pregnancy.father").param("name", fatherName));

        String trimesterKey = switch (preg.trimester) {
            case 1 -> "simtale.ui.pregnancy.trimester1";
            case 2 -> "simtale.ui.pregnancy.trimester2";
            case 3 -> "simtale.ui.pregnancy.trimester3";
            default -> "simtale.ui.pregnancy.trimester_unknown";
        };
        cmd.set("#GestationStage.TextSpans", Message.translation("simtale.ui.pregnancy.stage").insert(Message.raw(" ")).insert(Message.translation(trimesterKey)));

        float progress = preg.getProgress(currentTick);
        int percentage = Math.round(progress * 100);
        cmd.set("#ProgressText.TextSpans", Message.translation("simtale.ui.pregnancy.progress").param("percent", String.valueOf(percentage)));

        int elapsedDays = preg.getElapsedDays(currentTick);
        int totalDays = (int) (preg.durationTicks / PregnancyComponent.TICKS_PER_DAY);
        cmd.set("#ElapsedDays.TextSpans", Message.translation("simtale.ui.pregnancy.elapsed").param("current", String.valueOf(elapsedDays)).param("total", String.valueOf(totalDays)));

        long ticksRemaining = Math.max(0, (preg.startTick + preg.durationTicks) - currentTick);
        long daysRemaining = ticksRemaining / PregnancyComponent.TICKS_PER_DAY;
        long minsRemaining = (ticksRemaining / 20L) / 60L;

        if (preg.isReadyToBirth(currentTick)) {
            cmd.set("#TimeRemaining.TextSpans", Message.translation("simtale.ui.pregnancy.time_birth_imminent"));
        } else if (daysRemaining > 0) {
            cmd.set("#TimeRemaining.TextSpans", Message.translation("simtale.ui.pregnancy.time_remaining_days")
                    .param("days", String.valueOf(daysRemaining))
                    .param("minutes", String.valueOf(minsRemaining)));
        } else {
            cmd.set("#TimeRemaining.TextSpans", Message.translation("simtale.ui.pregnancy.time_remaining_minutes")
                    .param("minutes", String.valueOf(minsRemaining)));
        }

        String symptomsKey = isPlayer
                ? switch (preg.trimester) {
                    case 2 -> "simtale.ui.pregnancy.symptoms_player_t2";
                    case 3 -> "simtale.ui.pregnancy.symptoms_player_t3";
                    default -> "simtale.ui.pregnancy.symptoms_player_t1";
                }
                : switch (preg.trimester) {
                    case 2 -> "simtale.ui.pregnancy.symptoms_npc_t2";
                    case 3 -> "simtale.ui.pregnancy.symptoms_npc_t3";
                    default -> "simtale.ui.pregnancy.symptoms_npc_t1";
                };
        cmd.set("#Symptoms.TextSpans", Message.translation(symptomsKey));

        cmd.set("#TotalChildren.TextSpans", Message.translation("simtale.ui.pregnancy.children").param("count", String.valueOf(totalChildren)));

        int fillWidth = Math.max(0, Math.min(PROGRESS_BAR_MAX_WIDTH, Math.round(progress * PROGRESS_BAR_MAX_WIDTH)));
        Anchor anchor = new Anchor();
        anchor.setWidth(Value.of(fillWidth));
        anchor.setHeight(Value.of(20));
        cmd.setObject("#ProgressBarFill.Anchor", anchor);
    }
}
