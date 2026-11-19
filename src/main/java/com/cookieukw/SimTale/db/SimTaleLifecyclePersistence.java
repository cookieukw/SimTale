package com.cookieukw.SimTale.db;

import com.cookie.caskara.Caskara;
import com.cookie.caskara.db.Core;
import com.cookie.caskara.db.Shell;
import com.cookieukw.SimTale.core.lifecycle.BabyCareData;
import com.cookieukw.SimTale.core.lifecycle.GrowthComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Caskara persistence for SimTale lifecycle records.
 *
 * <p>Do not use Caskara's static CRUD shortcuts here: those resolve to the Caskara
 * plugin's default shell, shared by every mod that uses those shortcuts. SimTale
 * lifecycle data belongs in the SimTale shell alongside its other records.
 */
public final class SimTaleLifecyclePersistence {
    private static final String MIGRATION_ID = "migration:caskara-default-lifecycle-v1";
    private static final int MIGRATION_VERSION = 1;

    private SimTaleLifecyclePersistence() {
    }

    public static <T> String save(String id, T value) {
        if (id == null || id.isBlank() || value == null) return null;
        return core(typeOf(value)).preserve(id, value);
    }

    public static <T> T load(String id, Class<T> type) {
        if (id == null || id.isBlank() || type == null) return null;
        return core(type).extract(id).sync().orElse(null);
    }

    public static <T> List<T> list(Class<T> type) {
        if (type == null) return new ArrayList<>();
        List<T> result = core(type).extractAll();
        return result != null ? result : new ArrayList<>();
    }

    public static <T> void delete(String id, Class<T> type) {
        if (id == null || id.isBlank() || type == null) return;
        core(type).discard(id);
    }

    /**
     * Copies old lifecycle records out of Caskara's shared shells into SimTale's own
     * shell. Records found in the current {@code caskara} shell are copied without
     * deleting the source. For the older {@code default.db}, Caskara's built-in
     * migration creates a safety backup before merging those type rows into the
     * {@code caskara} shell. Explicit copies skip records already in the destination,
     * so an interrupted run is safe to retry; the marker is written only after both
     * legacy shells have been checked.
     */
    public static MigrationResult migrateLegacyData() {
        // Let Caskara's own default.db migration run into its named "caskara" shell
        // first. It creates a safety backup before moving legacy rows. SimTale then
        // copies the merged named shell into its own destination.
        Shell caskaraShell = Caskara.shell("caskara");
        caskaraShell.core(GrowthComponent.class);
        caskaraShell.core(BabyCareData.class);

        Core<GrowthComponent> growthCore = core(GrowthComponent.class);
        Core<BabyCareData> babyCareCore = core(BabyCareData.class);
        Core<MigrationMarker> markerCore = core(MigrationMarker.class);
        MigrationMarker marker = markerCore.extract(MIGRATION_ID).sync().orElse(null);
        if (marker != null && marker.version >= MIGRATION_VERSION) {
            return new MigrationResult(true, 0, 0);
        }

        int growthCopied = 0;
        int babyCareCopied = 0;

        // Current Caskara initializes its default shell as "caskara". Older releases
        // used "default"; try both to preserve records from either installation.
        for (String legacyShellName : List.of("caskara", "default")) {
            Shell source = legacyShellName.equals("caskara") ? caskaraShell : Caskara.shell(legacyShellName);
            growthCopied += copyGrowth(source, growthCore);
            babyCareCopied += copyBabyCare(source, babyCareCore);
        }

        markerCore.preserve(MIGRATION_ID, new MigrationMarker(MIGRATION_VERSION));
        return new MigrationResult(false, growthCopied, babyCareCopied);
    }

    private static int copyGrowth(Shell source, Core<GrowthComponent> targetCore) {
        int copied = 0;
        for (GrowthComponent growth : source.core(GrowthComponent.class).extractAll()) {
            if (growth == null || growth.childId == null) continue;
            String id = "child_" + growth.childId;
            if (targetCore.extract(id).sync().isEmpty()) {
                targetCore.preserve(id, growth);
                copied++;
            }
        }
        return copied;
    }

    private static int copyBabyCare(Shell source, Core<BabyCareData> targetCore) {
        int copied = 0;
        for (BabyCareData care : source.core(BabyCareData.class).extractAll()) {
            if (care == null || care.childId == null || care.childId.isBlank()) continue;
            String id = "babycare_" + care.childId;
            if (targetCore.extract(id).sync().isEmpty()) {
                targetCore.preserve(id, care);
                copied++;
            }
        }
        return copied;
    }

    private static <T> Core<T> core(Class<T> type) {
        return SimNPCPersistence.DB_SHELL.core(type);
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> typeOf(T value) {
        return (Class<T>) value.getClass();
    }

    public record MigrationResult(boolean alreadyMigrated, int growthRecordsCopied, int babyCareRecordsCopied) {
    }

    public static final class MigrationMarker {
        public int version;

        public MigrationMarker() {
        }

        private MigrationMarker(int version) {
            this.version = version;
        }
    }
}
