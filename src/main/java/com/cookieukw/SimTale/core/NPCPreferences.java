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

private static final List<String> ITEM_POOL = List.of(
        "Ingredient_Bar_Gold",
        "Ore_Gold",
        "Deco_Treasure_Pile_Small",
        "Plant_Leaves_Goldentree",
        "Alchemy_Cauldron",
        "Deco_Lantern",
        "Blueprint_TavernHouse",
        "simtale:wedding_ring"
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
        Profession.BUILDER
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
        Map.entry("simtale:wedding_ring", "Aliança de Casamento"),

        Map.entry("Deco_Trash", "Lixo"),
        Map.entry("Deco_Trash_Pile_Small", "Pilha de Lixo"),
        Map.entry("Deco_Bone_Pile", "Pilha de Ossos"),
        Map.entry("Deco_SpiderWeb", "Teia de Aranha"),
        Map.entry("Fluid_Poison", "Veneno")
);

private final Set<String> favoriteFoods;
private final Set<String> hatedFoods;
private final Set<String> favoriteItems;
private final Set<String> hatedItems;
private final Season favoriteSeason;
private final Weather favoriteWeather;
private final Hobby hobby;
private final Set<Profession> likedProfessions;
private final Set<Profession> dislikedProfessions;

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
    this.favoriteFoods = Set.copyOf(favoriteFoods);
    this.hatedFoods = Set.copyOf(hatedFoods);
    this.favoriteItems = Set.copyOf(favoriteItems);
    this.hatedItems = Set.copyOf(hatedItems);
    this.favoriteSeason = favoriteSeason;
    this.favoriteWeather = favoriteWeather;
    this.hobby = hobby;
    this.likedProfessions = Set.copyOf(likedProfessions);
    this.dislikedProfessions = Set.copyOf(dislikedProfessions);
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
    return favoriteFoods;
}

public Set<String> getHatedFoods() {
    return hatedFoods;
}

public Set<String> getFavoriteItems() {
    return favoriteItems;
}

public Set<String> getHatedItems() {
    return hatedItems;
}

public Season getFavoriteSeason() {
    return favoriteSeason;
}

public Weather getFavoriteWeather() {
    return favoriteWeather;
}

public Hobby getHobby() {
    return hobby;
}

public Set<Profession> getLikedProfessions() {
    return likedProfessions;
}

public Set<Profession> getDislikedProfessions() {
    return dislikedProfessions;
}

public String getSeasonDisplayName() {
    return favoriteSeason.displayName();
}

public String getWeatherDisplayName() {
    return favoriteWeather.displayName();
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
