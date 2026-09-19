package com.cookieukw.SimTale.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class NPCPreferences {

private static final List<String> FOOD_POOL = List.of(
        "Food_Pie_Apple",
        "Food_Bread",
        "Food_Pie_Meat",
        "Food_Fish_Grilled",
        "Food_Beef_Raw",
        "Food_Salad_Mushroom",
        "Food_Cheese",
        "Food_Candy_Cane",
        "Food_Popcorn",
        "Food_Salad_Berry",
        "Food_Pie_Pumpkin",
        "Food_Kebab_Fruit",
        "Food_Kebab_Meat"
);

/**
 * Items an NPC can name as a favourite.
 *
 * <p>Everything here has to be <em>giftable</em>, which is a narrower thing than "exists". An item
 * that carries its own interaction never reaches the gift path at all, so naming it a favourite
 * describes a wish the player can never grant.
 *
 * <p>Two used to be in this list and were removed for exactly that:
 * <ul>
 *   <li>{@code WeddingRing} — {@code handleGift} intercepts it and turns the gesture into a
 *       marriage proposal before any gift scoring happens. It was also handed out to children,
 *       which is worse than merely impossible.</li>
 *   <li>{@code Blueprint_TavernHouse} — a placeable construction marker, handled on placement.</li>
 * </ul>
 *
 * <p>Anything added here should be checked the same way: if the mod reacts to the item somewhere
 * else, it does not belong.
 */
private static final List<String> ITEM_POOL = List.of(
        "Ingredient_Bar_Gold",
        "Ore_Gold",
        "Deco_Treasure_Pile_Small",
        "Plant_Leaves_Goldentree",
        "Alchemy_Cauldron",
        "Deco_Lantern",
        "Ingredient_Bar_Iron",
        "Ingredient_Bar_Silver",
        "Plant_Flower_Common_Violet",
        "Deco_Trophy_Harvest"
);

private static final List<String> HATED_ITEM_POOL = List.of(
        "Deco_Trash",
        "Deco_Trash_Pile_Small",
        "Deco_Bone_Pile",
        "Deco_SpiderWeb",
        "Fluid_Poison"
);

private static final List<Profession> PROFESSION_POOL = List.of(
        Profession.MINER,
        Profession.FARMER,
        Profession.FISHERMAN,
        Profession.LUMBERJACK,
        Profession.GUARD,
        Profession.EXPLORER,
        Profession.BUILDER,
        // HUNTER was missing, so no NPC could ever like or dislike a hunter.
        Profession.HUNTER
);

private static final Map<String, String> ITEM_DISPLAY_NAMES = Map.ofEntries(
        Map.entry("Food_Pie_Apple", "Torta de Maçã"),
        Map.entry("Food_Bread", "Pão"),
        Map.entry("Food_Pie_Meat", "Torta de Carne"),
        Map.entry("Food_Fish_Raw", "Peixe Cru"),
        Map.entry("Food_Fish_Grilled", "Peixe Grelhado"),
        Map.entry("Food_Beef_Raw", "Carne Crua"),
        Map.entry("Food_Salad_Mushroom", "Salada de Cogumelo"),
        Map.entry("Food_Cheese", "Queijo"),
        Map.entry("Food_Candy_Cane", "Bengala Doce"),
        Map.entry("Food_Popcorn", "Pipoca"),
        Map.entry("Food_Salad_Berry", "Salada de Frutas"),
        Map.entry("Food_Pie_Pumpkin", "Torta de Abóbora"),
        Map.entry("Food_Kebab_Fruit", "Espetinho de Frutas"),
        Map.entry("Food_Kebab_Meat", "Espetinho de Carne"),

        Map.entry("Ingredient_Bar_Gold", "Barra de Ouro"),
        Map.entry("Ore_Gold", "Minério de Ouro"),
        Map.entry("Deco_Treasure_Pile_Small", "Pilha de Tesouro"),
        Map.entry("Plant_Leaves_Goldentree", "Folhas de Árvore Dourada"),
        Map.entry("Alchemy_Cauldron", "Caldeirão de Alquimia"),
        Map.entry("Deco_Lantern", "Lanterna"),
        Map.entry("Blueprint_TavernHouse", "Projeto de Taverna"),
        Map.entry("WeddingRing", "Aliança de Casamento"),

        Map.entry("Deco_Trash", "Lixo"),
        Map.entry("Deco_Trash_Pile_Small", "Pilha de Lixo"),
        Map.entry("Deco_Bone_Pile", "Pilha de Ossos"),
        Map.entry("Deco_SpiderWeb", "Teia de Aranha"),
        Map.entry("Fluid_Poison", "Veneno")
);

    private Set<String> favoriteFoods = Set.of();
    private Set<String> hatedFoods = Set.of();
    private Set<String> favoriteItems = Set.of();
    private Set<String> hatedItems = Set.of();
    private Season favoriteSeason = Season.SUMMER;
    private Weather favoriteWeather = Weather.CLEAR;
    private Hobby hobby = Hobby.READING;
    private Set<Profession> likedProfessions = Set.of();
    private Set<Profession> dislikedProfessions = Set.of();

    public NPCPreferences() {
    }

    public NPCPreferences(
            Set<String> favoriteFoods,
            Set<String> hatedFoods,
            Set<String> favoriteItems,
            Set<String> hatedItems,
            Season favoriteSeason,
            Weather favoriteWeather,
            Hobby hobby,
            Set<Profession> likedProfessions,
            Set<Profession> dislikedProfessions
    ) {
        this.favoriteFoods = favoriteFoods != null ? Set.copyOf(favoriteFoods) : Set.of();
        this.hatedFoods = hatedFoods != null ? Set.copyOf(hatedFoods) : Set.of();
        this.favoriteItems = favoriteItems != null ? Set.copyOf(favoriteItems) : Set.of();
        this.hatedItems = hatedItems != null ? Set.copyOf(hatedItems) : Set.of();
        this.favoriteSeason = favoriteSeason != null ? favoriteSeason : Season.SUMMER;
        this.favoriteWeather = favoriteWeather != null ? favoriteWeather : Weather.CLEAR;
        this.hobby = hobby != null ? hobby : Hobby.READING;
        this.likedProfessions = likedProfessions != null ? Set.copyOf(likedProfessions) : Set.of();
        this.dislikedProfessions = dislikedProfessions != null ? Set.copyOf(dislikedProfessions) : Set.of();
    }

public static NPCPreferences createRandom() {
    Random random = ThreadLocalRandom.current();

    Set<String> favoriteFoods = pickRandomDistinct(FOOD_POOL, randomInt(random, 2, 3), random);
    Set<String> hatedFoods = pickRandomDistinct(
            subtract(FOOD_POOL, favoriteFoods),
            randomInt(random, 2, 3),
            random
    );

    Set<String> favoriteItems = pickRandomDistinct(ITEM_POOL, randomInt(random, 2, 3), random);
    Set<String> hatedItems = pickRandomDistinct(HATED_ITEM_POOL, randomInt(random, 2, 3), random);

    Season favoriteSeason = randomEnum(Season.values(), random);
    Weather favoriteWeather = randomEnum(Weather.values(), random);
    Hobby hobby = randomEnum(Hobby.values(), random);

    List<Profession> shuffledProfessions = new ArrayList<>(PROFESSION_POOL);
    Collections.shuffle(shuffledProfessions, random);

    int likedCount = randomInt(random, 1, 2);
    int dislikedCount = randomInt(random, 1, 2);

    Set<Profession> likedProfessions = new LinkedHashSet<>();
    Set<Profession> dislikedProfessions = new LinkedHashSet<>();

    for (int i = 0; i < likedCount && i < shuffledProfessions.size(); i++) {
        likedProfessions.add(shuffledProfessions.get(i));
    }

    for (int i = likedCount; i < likedCount + dislikedCount && i < shuffledProfessions.size(); i++) {
        dislikedProfessions.add(shuffledProfessions.get(i));
    }

    return new NPCPreferences(
            favoriteFoods,
            hatedFoods,
            favoriteItems,
            hatedItems,
            favoriteSeason,
            favoriteWeather,
            hobby,
            likedProfessions,
            dislikedProfessions
    );
}

public Set<String> getFavoriteFoods() {
    return favoriteFoods != null ? favoriteFoods : Set.of();
}

public Set<String> getHatedFoods() {
    return hatedFoods != null ? hatedFoods : Set.of();
}

public Set<String> getFavoriteItems() {
    return favoriteItems != null ? favoriteItems : Set.of();
}

public Set<String> getHatedItems() {
    return hatedItems != null ? hatedItems : Set.of();
}

public Season getFavoriteSeason() {
    return favoriteSeason != null ? favoriteSeason : Season.SUMMER;
}

public Weather getFavoriteWeather() {
    return favoriteWeather != null ? favoriteWeather : Weather.CLEAR;
}

public Hobby getHobby() {
    return hobby != null ? hobby : Hobby.READING;
}

public Set<Profession> getLikedProfessions() {
    return likedProfessions != null ? likedProfessions : Set.of();
}

public Set<Profession> getDislikedProfessions() {
    return dislikedProfessions != null ? dislikedProfessions : Set.of();
}

public String getSeasonDisplayName() {
    return getFavoriteSeason().displayName();
}

public String getWeatherDisplayName() {
    return getFavoriteWeather().displayName();
}

public static String getItemDisplayName(String itemId) {
    return ITEM_DISPLAY_NAMES.getOrDefault(itemId, itemId);
}

public static String getFoodDisplayName(String itemId) {
    return getItemDisplayName(itemId);
}

private static <T> Set<T> pickRandomDistinct(List<T> source, int count, Random random) {
    if (source.isEmpty() || count <= 0) {
        return Set.of();
    }

    List<T> copy = new ArrayList<>(source);
    Collections.shuffle(copy, random);

    int limit = Math.min(count, copy.size());
    Set<T> result = new LinkedHashSet<>();
    for (int i = 0; i < limit; i++) {
        result.add(copy.get(i));
    }
    return result;
}

private static <T> T randomEnum(T[] values, Random random) {
    return values[random.nextInt(values.length)];
}

private static int randomInt(Random random, int minInclusive, int maxInclusive) {
    return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
}

private static List<String> subtract(List<String> source, Set<String> excluded) {
    List<String> result = new ArrayList<>();
    for (String value : source) {
        if (!excluded.contains(value)) {
            result.add(value);
        }
    }
    return result;
}

public enum Season {
    SPRING("Primavera"),
    SUMMER("Verão"),
    AUTUMN("Outono"),
    WINTER("Inverno");

    private final String displayName;

    Season(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}

public enum Weather {
    CLEAR("Ensolarado"),
    RAIN("Chuvoso"),
    STORM("Tempestade"),
    SNOW("Neve");

    private final String displayName;

    Weather(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}

public enum Hobby {
    FISHING("Pescaria"),
    MINING("Mineração"),
    GARDENING("Jardinagem"),
    READING("Leitura"),
    SLEEPING("Dormir");

    private final String displayName;

    Hobby(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}

}
