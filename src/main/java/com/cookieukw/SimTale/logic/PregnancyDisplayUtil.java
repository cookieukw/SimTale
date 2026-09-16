package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.lifecycle.PregnancyComponent;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.cookieukw.SimTale.core.WorldUtil;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;


import java.util.UUID;

public final class PregnancyDisplayUtil {

    private static final int PROGRESS_BAR_MAX_WIDTH = 480;

    /**
     * Narrowest the fill may be drawn. @ProgressFill is a 9-slice with Border 6, so it needs room
     * for a left and a right cap; below 12px the two caps overlap and the texture renders as a
     * couple of stray marks floating in the track. That is what "Not pregnant" looked like, because
     * that path sets the width to 0 and a zero-width 9-slice still draws its corners.
     */
    private static final int PROGRESS_BAR_MIN_WIDTH = 12;

    private PregnancyDisplayUtil() {}

    /**
     * Sizes the bar's fill, hiding it outright when there is nothing to show.
     * <p>
     * Hiding is not the same as sizing it to zero: only Visible actually removes the 9-slice from
     * the draw pass.
     */
    private static void setProgressFill(UICommandBuilder cmd, int width) {
        if (width < PROGRESS_BAR_MIN_WIDTH) {
            cmd.set("#ProgressBarFill.Visible", false);
            return;
        }
        cmd.set("#ProgressBarFill.Visible", true);
        Anchor anchor = new Anchor();
        anchor.setWidth(Value.of(width));
        anchor.setHeight(Value.of(20));
        cmd.setObject("#ProgressBarFill.Anchor", anchor);
    }

    public static long getCurrentWorldTick() {
        return WorldUtil.tick();
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
        cmd.set("#SubTitle.TextSpans", Message.translation("ui.pregnancy.subtitle"));
        cmd.set("#BackButtonText.TextSpans", Message.translation("ui.pregnancy.back"));
        cmd.set("#CloseButtonText.TextSpans", Message.translation("ui.pregnancy.close"));

        if (preg == null || !preg.pregnant) {
            cmd.set("#GestationStage.TextSpans", Message.translation("ui.pregnancy.not_pregnant"));
            cmd.set("#FatherName.TextSpans", Message.translation("ui.pregnancy.father").param("name", "—"));
            cmd.set("#ElapsedDays.TextSpans", Message.translation("ui.pregnancy.elapsed").param("current", "0").param("total", "0"));
            cmd.set("#ProgressText.TextSpans", Message.translation("ui.pregnancy.progress").param("percent", "0"));
            cmd.set("#TimeRemaining.TextSpans", Message.translation("ui.pregnancy.time_none"));
            cmd.set("#Symptoms.TextSpans", Message.translation("ui.pregnancy.symptoms_none"));
            cmd.set("#TotalChildren.TextSpans", Message.translation("ui.pregnancy.children").param("count", String.valueOf(totalChildren)));
            setProgressFill(cmd, 0);
            return;
        }

        String fatherName = resolveParentName(preg.fatherId);
        cmd.set("#FatherName.TextSpans", Message.translation("ui.pregnancy.father").param("name", fatherName));

        String trimesterKey = switch (preg.trimester) {
            case 1 -> "ui.pregnancy.trimester1";
            case 2 -> "ui.pregnancy.trimester2";
            case 3 -> "ui.pregnancy.trimester3";
            default -> "ui.pregnancy.trimester_unknown";
        };
        cmd.set("#GestationStage.TextSpans", Message.translation(trimesterKey));

        float progress = preg.getProgress(currentTick);
        int percentage = Math.round(progress * 100);
        cmd.set("#ProgressText.TextSpans", Message.translation("ui.pregnancy.progress").param("percent", String.valueOf(percentage)));

        int elapsedDays = preg.getElapsedDays(currentTick);
        int totalDays = (int) (preg.durationTicks / PregnancyComponent.TICKS_PER_DAY);
        cmd.set("#ElapsedDays.TextSpans", Message.translation("ui.pregnancy.elapsed").param("current", String.valueOf(elapsedDays)).param("total", String.valueOf(totalDays)));

        long ticksRemaining = Math.max(0, (preg.startTick + preg.durationTicks) - currentTick);
        long daysRemaining = ticksRemaining / PregnancyComponent.TICKS_PER_DAY;
        long minsRemaining = (ticksRemaining / 20L) / 60L;

        if (preg.isReadyToBirth(currentTick)) {
            cmd.set("#TimeRemaining.TextSpans", Message.translation("ui.pregnancy.time_birth_imminent"));
        } else if (daysRemaining > 0) {
            cmd.set("#TimeRemaining.TextSpans", Message.translation("ui.pregnancy.time_remaining_days")
                    .param("days", String.valueOf(daysRemaining))
                    .param("minutes", String.valueOf(minsRemaining)));
        } else {
            cmd.set("#TimeRemaining.TextSpans", Message.translation("ui.pregnancy.time_remaining_minutes")
                    .param("minutes", String.valueOf(minsRemaining)));
        }

        String symptomsKey = isPlayer
                ? switch (preg.trimester) {
                    case 2 -> "ui.pregnancy.symptoms_player_t2";
                    case 3 -> "ui.pregnancy.symptoms_player_t3";
                    default -> "ui.pregnancy.symptoms_player_t1";
                }
                : switch (preg.trimester) {
                    case 2 -> "ui.pregnancy.symptoms_npc_t2";
                    case 3 -> "ui.pregnancy.symptoms_npc_t3";
                    default -> "ui.pregnancy.symptoms_npc_t1";
                };
        cmd.set("#Symptoms.TextSpans", Message.translation(symptomsKey));

        cmd.set("#TotalChildren.TextSpans", Message.translation("ui.pregnancy.children").param("count", String.valueOf(totalChildren)));

        int fillWidth = Math.clamp(Math.round(progress * PROGRESS_BAR_MAX_WIDTH), 0, PROGRESS_BAR_MAX_WIDTH);
        /* Round a sliver up to the minimum the 9-slice can draw, rather than hiding it: on day one
        of a pregnancy the bar should read as "just started", not as "no bar at all".
        */
        if (fillWidth > 0 && fillWidth < PROGRESS_BAR_MIN_WIDTH) {
            fillWidth = PROGRESS_BAR_MIN_WIDTH;
        }
        setProgressFill(cmd, fillWidth);
    }
}
