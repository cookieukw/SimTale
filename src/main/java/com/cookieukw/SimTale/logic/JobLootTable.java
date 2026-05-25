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
                returnLoot.add(new LootEntry("Ore_Iron_Stone", 2, 8));
                returnLoot.add(new LootEntry("Rock_Stone_Cobble", 10, 32));
                returnLoot.add(new LootEntry("Ingredient_Charcoal", 5, 15));
                returnLoot.add(new LootEntry("Ore_Gold_Stone", 1, 3));
                break;
            case FISH:
                returnLoot.add(new LootEntry("Fish_Catfish_Item", 2, 6));
                returnLoot.add(new LootEntry("Fish_Clownfish_Item", 2, 5));
                returnLoot.add(new LootEntry("Fish_Pufferfish_Item", 0, 2));
                returnLoot.add(new LootEntry("Deco_Starfish", 0, 1)); 
                break;
            case GATHER:
                returnLoot.add(new LootEntry("Wood_Oak_Trunk", 8, 24));
                returnLoot.add(new LootEntry("Soil_Sand", 10, 32));
                returnLoot.add(new LootEntry("Rock_Stone_Cobble", 10, 20));
                returnLoot.add(new LootEntry("Plant_Reeds_Water", 5, 15)); // Sugar cane equivalent
                returnLoot.add(new LootEntry("Plant_Flower_Common_Yellow", 2, 5)); // Dandelion equivalent
                break;
            case FARM:
                returnLoot.add(new LootEntry("Plant_Crop_Wheat_Item", 10, 20));
                returnLoot.add(new LootEntry("Plant_Crop_Carrot_Item", 5, 15));
                returnLoot.add(new LootEntry("Plant_Crop_Potato_Item", 5, 15));
                break;
            case EXPLORE:
                returnLoot.add(new LootEntry("Objective_Treasure_Map", 1, 1));
                returnLoot.add(new LootEntry("Rock_Gem_Emerald", 2, 5));
                break;
            case NONE:
            default:
                break;
        }

        return returnLoot;
    }
}
