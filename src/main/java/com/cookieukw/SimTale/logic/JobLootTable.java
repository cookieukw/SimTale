package com.cookieukw.SimTale.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Handles the mapping between tasks and their respective loot returns.
 * Uses placeholder item IDs until the Hytale equivalents are finalized.
 */
public class JobLootTable {

    private static final Random random = new Random();

    public static class LootEntry {
        public final String itemId;
        public final int minQty;
        public final int maxQty;

        public LootEntry(String itemId, int minQty, int maxQty) {
            this.itemId = itemId;
            this.minQty = minQty;
            this.maxQty = maxQty;
        }

        public int rollQty() {
            return random.nextInt((maxQty - minQty) + 1) + minQty;
        }
    }

    public static List<LootEntry> getLootForJob(JobType job) {
        List<LootEntry> returnLoot = new ArrayList<>();
        
        // Native Hytale item IDs discovered in the assets repository
        switch(job) {
            case MINE:
                returnLoot.add(new LootEntry("hytale:ore_iron_stone", 2, 8));
                returnLoot.add(new LootEntry("hytale:rock_stone_cobble", 10, 32));
                returnLoot.add(new LootEntry("hytale:ingredient_charcoal", 5, 15));
                returnLoot.add(new LootEntry("hytale:ore_gold_stone", 1, 3));
                break;
            case FISH:
                returnLoot.add(new LootEntry("hytale:fish_trout_rainbow_item", 2, 6)); // Cod equivalent
                returnLoot.add(new LootEntry("hytale:fish_salmon_item", 2, 5));
                returnLoot.add(new LootEntry("hytale:fish_pufferfish_item", 0, 2));
                returnLoot.add(new LootEntry("hytale:armor_leather_soft_legs", 0, 1)); // Junk
                break;
            case GATHER:
                returnLoot.add(new LootEntry("hytale:wood_oak_trunk", 8, 24));
                returnLoot.add(new LootEntry("hytale:soil_sand", 10, 32));
                returnLoot.add(new LootEntry("hytale:rock_stone_cobble", 10, 20));
                returnLoot.add(new LootEntry("hytale:plant_reeds_water", 5, 15)); // Sugar cane equivalent
                returnLoot.add(new LootEntry("hytale:plant_flower_common_yellow", 2, 5)); // Dandelion equivalent
                break;
            case FARM:
                returnLoot.add(new LootEntry("hytale:plant_crop_wheat", 10, 20));
                returnLoot.add(new LootEntry("hytale:plant_crop_carrot", 5, 15));
                returnLoot.add(new LootEntry("hytale:plant_crop_potato", 5, 15));
                break;
            case EXPLORE:
                returnLoot.add(new LootEntry("hytale:objective_treasure_map", 1, 1));
                returnLoot.add(new LootEntry("hytale:rock_gem_emerald", 2, 5));
                break;
            case NONE:
            default:
                break;
        }

        return returnLoot;
    }
}
