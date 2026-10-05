package com.cookieukw.SimTale.tests;

import com.cookieukw.SimTale.systems.SeasonalCostumeHelper;

/**
 * Seasonal costume ids. A costume id is always {@code <npc model id>_<event>}; reading the NPC's
 * own model back out of it is what lets a costume be removed (or swapped for the other event)
 * after a restart, when the in-memory backup map is empty.
 */
public final class CostumeTests {

    private CostumeTests() {
    }

    public static void run() {
        Assert.suite("Costume id -> event and base model", CostumeTests::ids);
    }

    private static void ids() {
        Assert.equal(SeasonalCostumeHelper.eventOf("SimTale_Human_Male_12_Christmas"), "Christmas", "christmas id");
        Assert.equal(SeasonalCostumeHelper.eventOf("SimTale_Human_Child_3_Halloween"), "Halloween", "halloween id");
        Assert.isTrue(SeasonalCostumeHelper.eventOf("SimTale_Human_Male_12") == null, "plain id has no event");
        Assert.isTrue(SeasonalCostumeHelper.eventOf(null) == null, "null id");
        Assert.equal(SeasonalCostumeHelper.baseOf("SimTale_Human_Male_12_Christmas"), "SimTale_Human_Male_12", "base of costume");
        Assert.equal(SeasonalCostumeHelper.baseOf("SimTale_Human_Female"), "SimTale_Human_Female", "base of plain id");
        // switching events must not stack suffixes
        String once = SeasonalCostumeHelper.baseOf("Doll_3_Christmas") + "_Halloween";
        Assert.equal(once, "Doll_3_Halloween", "christmas -> halloween");
    }
}
