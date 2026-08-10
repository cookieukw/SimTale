package com.cookieukw.SimTale.tests;

import com.cookieukw.SimTale.core.AssetIds;
import com.cookieukw.SimTale.systems.BedRegistry;
import com.cookieukw.SimTale.systems.ChestRegistry;
import com.cookieukw.SimTale.systems.CropRegistry;
import com.cookieukw.SimTale.systems.FarmPostRegistry;
import com.cookieukw.SimTale.systems.FarmlandRegistry;
import com.cookieukw.SimTale.systems.FishingPostRegistry;
import com.cookieukw.SimTale.systems.LumberPostRegistry;

/**
 * Id matching — the single largest source of silent bugs in this project.
 *
 * <p>Every case here is a shape that actually came out of the engine and broke something. They are
 * cheap to test because the predicates are pure string logic, and expensive to debug in game
 * because a failed match produces no error at all: the block is just never recognised.
 */
public final class AssetIdsTests {

    private AssetIdsTests() {
    }

    /** Runtime id of a fully grown carrot, copied from real /simtale debugnear output. */
    private static final String GROWN_CARROT =
            "*plant_crop_carrot_block_state_definitions_stagefinal";

    /** Runtime id of watered tilled soil, same source. */
    private static final String WATERED_SOIL =
            "*soil_dirt_tilled_state_definitions_watered";

    public static void run() {
        Assert.suite("AssetIds.normalize", AssetIdsTests::normalize);
        Assert.suite("AssetIds.containsAny / containsNone", AssetIdsTests::contains);
        Assert.suite("AssetIds.matchesAsset tolerates decoration", AssetIdsTests::matchesAsset);
        Assert.suite("Registry predicates", AssetIdsTests::registryPredicates);
        Assert.suite("Null and empty are never a match", AssetIdsTests::nullSafety);
    }

    private static void normalize() {
        Assert.equal(AssetIds.normalize("Blueprint_TavernHouse"), "blueprinttavernhouse",
                "underscores and case are dropped");
        Assert.equal(AssetIds.normalize("Plant_Seeds_Carrot"), "plantseedscarrot",
                "seed ids normalize the same way — the Farmer's deposit rule depends on it");
        Assert.equal(AssetIds.normalize("ÁÉÍ"), "",
                "non-ASCII is dropped, as the old character class did");
        Assert.equal(AssetIds.normalize(""), "", "empty stays empty");
        Assert.equal(AssetIds.normalize("simtale:WeddingRing"), "simtaleweddingring",
                "namespace separator is dropped");
        Assert.equal(AssetIds.normalize(GROWN_CARROT),
                "plantcropcarrotblockstatedefinitionsstagefinal",
                "the leading state-variant marker is dropped");
        Assert.equal(AssetIds.normalize(null), "", "null normalizes to empty, never null");
        Assert.equal(AssetIds.normalize("***"), "", "punctuation only normalizes to empty");
    }

    private static void contains() {
        Assert.isTrue(AssetIds.containsAny(WATERED_SOIL, "soil_dirt"),
                "keyword with underscore matches an id whose underscores were stripped");
        Assert.isTrue(AssetIds.containsAny("Furniture_Village_Bed", "chest", "bed"),
                "any one keyword is enough");
        Assert.isFalse(AssetIds.containsAny("Furniture_Village_Bed", "chest", "barrel"),
                "no keyword matches");
        Assert.isTrue(AssetIds.containsNone("Furniture_Village_Bed", "bedrock"),
                "containsNone is the negation");
    }

    private static void matchesAsset() {
        // The wedding ring id was guessed wrong three times in four different shapes.
        Assert.isTrue(AssetIds.matchesAsset("WeddingRing", "WeddingRing"), "bare asset name");
        Assert.isTrue(AssetIds.matchesAsset("wedding_ring", "WeddingRing"), "snake_case");
        Assert.isTrue(AssetIds.matchesAsset("simtale:WeddingRing", "WeddingRing"), "namespaced");
        Assert.isTrue(AssetIds.matchesAsset("SIMTALE:WEDDING_RING", "WeddingRing"), "shouted");

        // The blueprint marker declares VariantRotation: NESW, so it never arrives bare.
        Assert.isTrue(AssetIds.matchesAsset("Blueprint_TavernHouse", "Blueprint_TavernHouse"),
                "bare marker");
        Assert.isTrue(
                AssetIds.matchesAsset("*Blueprint_TavernHouse_State_Definitions_North",
                        "Blueprint_TavernHouse"),
                "rotation variant still matches — this is what equalsIgnoreCase missed");

        Assert.isFalse(AssetIds.matchesAsset("Blueprint_Cottage", "Blueprint_TavernHouse"),
                "a different blueprint must not match");
    }

    private static void registryPredicates() {
        Assert.isTrue(BedRegistry.isBedId("Furniture_Village_Bed"), "bed");
        Assert.isFalse(BedRegistry.isBedId("Rock_Bedrock"), "bedrock is excluded, not a bed");

        Assert.isTrue(ChestRegistry.isChestId("Furniture_Village_Chest"), "chest by name");
        Assert.isTrue(ChestRegistry.isChestId("Deco_Barrel"), "barrel by name");
        // Pins a known limitation rather than pretending it works: this is exactly why
        // isContainerAt exists and why the name check is only a fallback.
        Assert.isFalse(ChestRegistry.isChestId("Furniture_Storage_Crate"),
                "known gap — a storage block named nothing on the list is missed by name alone");

        Assert.isTrue(CropRegistry.isCropId(GROWN_CARROT), "grown crop, state-variant id");
        Assert.isTrue(CropRegistry.isCropId("Plant_Crop_Carrot_Block"), "freshly planted crop");
        Assert.isFalse(CropRegistry.isCropId("Plant_Crop_Eternal_Block"),
                "eternal crops are decorative and must not be harvested");
        Assert.isTrue(CropRegistry.isReadyToHarvest(GROWN_CARROT), "stagefinal is ripe");
        Assert.isFalse(CropRegistry.isReadyToHarvest("Plant_Crop_Carrot_Block"),
                "a seedling is not ripe — this is what caused the plant/harvest loop");

        Assert.isTrue(FarmlandRegistry.isFarmlandId(WATERED_SOIL), "watered tilled soil");
        Assert.isTrue(FarmlandRegistry.isFarmlandId("Soil_Grass"), "plain grass is plantable");
        Assert.isFalse(FarmlandRegistry.isFarmlandId("Rock_Stone"), "stone is not farmland");

        // These three were equalsIgnoreCase until the AssetIds unification. The variant cases are
        // the regression guard.
        Assert.isTrue(FishingPostRegistry.isFishingPostId("Tool_Fishing_Trap"), "fishing post");
        Assert.isTrue(FishingPostRegistry.isFishingPostId("*Tool_Fishing_Trap_State_Definitions_Full"),
                "fishing post as a state variant");
        Assert.isTrue(LumberPostRegistry.isLumberPostId("Bench_Lumbermill"), "lumber post");
        Assert.isTrue(FarmPostRegistry.isFarmPostId("Deco_Scarecrow"), "farm post");
        Assert.isTrue(FarmPostRegistry.isFarmPostId("*Deco_Scarecrow_State_Definitions_Middle"),
                "scarecrow is 3 blocks tall — the non-anchor blocks arrive decorated");
    }

    private static void nullSafety() {
        Assert.isFalse(AssetIds.containsAny(null, "bed"), "null id");
        Assert.isFalse(AssetIds.containsAny("Furniture_Village_Bed", (String[]) null), "null keywords");
        Assert.isFalse(AssetIds.matchesAsset(null, "WeddingRing"), "null id, matchesAsset");
        Assert.isFalse(AssetIds.matchesAsset("WeddingRing", null), "null asset name");

        Assert.isFalse(BedRegistry.isBedId(null), "null is not a bed");
        Assert.isFalse(ChestRegistry.isChestId(null), "null is not a chest");
        Assert.isFalse(CropRegistry.isCropId(null), "null is not a crop");
        Assert.isFalse(FarmlandRegistry.isFarmlandId(null), "null is not farmland");
        Assert.isFalse(FishingPostRegistry.isFishingPostId(null), "null is not a fishing post");
        Assert.isFalse(LumberPostRegistry.isLumberPostId(null), "null is not a lumber post");
        Assert.isFalse(FarmPostRegistry.isFarmPostId(null), "null is not a farm post");
    }
}
