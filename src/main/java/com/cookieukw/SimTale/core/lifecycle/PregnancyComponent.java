package com.cookieukw.SimTale.core.lifecycle;

import java.util.UUID;


public class PregnancyComponent {
    public static final long TICKS_PER_DAY = 24000L;
    public static final int DEFAULT_PREGNANCY_DAYS = 5;
    public boolean pregnant = false;
    public UUID fatherId;
    public long startTick;
    public long durationTicks;
    public int trimester = 0;

    public PregnancyComponent() {}

    public void start(UUID fatherId, long worldTick) {
        this.pregnant = true;
        this.fatherId = fatherId;
        this.startTick = worldTick;
        this.durationTicks = DEFAULT_PREGNANCY_DAYS * TICKS_PER_DAY;
        this.trimester = 1;
    }

    public void start(UUID fatherId, long worldTick, int durationDays) {
        start(fatherId, worldTick);
        this.durationTicks = durationDays * TICKS_PER_DAY;
    }

    public float getProgress(long currentTick) {
        if (!pregnant || durationTicks <= 0) return 0f;
        long elapsed = currentTick - startTick;
        return Math.min(1.0f, (float) elapsed / durationTicks);
    }

    public boolean isReadyToBirth(long currentTick) {
        return pregnant && (currentTick >= startTick + durationTicks);
    }

    public void updateTrimester(long currentTick) {
        float progress = getProgress(currentTick);
        if (progress >= 0.66f) {
            trimester = 3;
        } else if (progress >= 0.33f) {
            trimester = 2;
        } else {
            trimester = 1;
        }
    }

    public void reset() {
        this.pregnant = false;
        this.fatherId = null;
        this.startTick = 0;
        this.durationTicks = 0;
        this.trimester = 0;
    }

    public int getElapsedDays(long currentTick) {
        if (!pregnant) return 0;
        return (int) ((currentTick - startTick) / TICKS_PER_DAY);
    }
}
