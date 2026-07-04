package com.cookieukw.SimTale.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class NPCPreferences {
    public List<String> favoriteFoods = new ArrayList<>();
    public List<String> hatedFoods = new ArrayList<>();
    public String favoriteSeason = "SPRING";
    public String favoriteWeather = "CLEAR";
    public String hobby = "NONE";
    public List<Profession> likedProfessions = new ArrayList<>();
    public List<Profession> dislikedProfessions = new ArrayList<>();

    public NPCPreferences() {}

    public static NPCPreferences createRandom() {
        NPCPreferences prefs = new NPCPreferences();
        Random rand = new Random();
        
        String[] foods = {"Food_Pie_Apple", "Food_Bread", "Food_Pie_Meat", "Food_Fish_Raw", "Food_Beef_Raw", "Food_Salad_Mushroom"};
        String[] seasons = {"SPRING", "SUMMER", "AUTUMN", "WINTER"};
        String[] weathers = {"CLEAR", "RAIN", "STORM", "SNOW"};
        String[] hobbies = {"PESCARIA", "MINERAÇÃO", "JARDINAGEM", "LEITURA", "DORMIR"};

        prefs.favoriteFoods.add(foods[rand.nextInt(foods.length)]);
        prefs.hatedFoods.add(foods[rand.nextInt(foods.length)]);
        
        // Ensure they don't love and hate the same food
        while (prefs.hatedFoods.getFirst().equals(prefs.favoriteFoods.getFirst())) {
            prefs.hatedFoods.set(0, foods[rand.nextInt(foods.length)]);
        }

        prefs.favoriteSeason = seasons[rand.nextInt(seasons.length)];
        prefs.favoriteWeather = weathers[rand.nextInt(weathers.length)];
        prefs.hobby = hobbies[rand.nextInt(hobbies.length)];
        
        // Generate profession preferences (1-2 liked, 1-2 disliked)
        Profession[] workProfs = {Profession.MINER, Profession.FARMER, Profession.FISHERMAN, 
                                   Profession.LUMBERJACK, Profession.GUARD, Profession.EXPLORER, Profession.BUILDER};
        List<Profession> shuffled = new ArrayList<>(java.util.Arrays.asList(workProfs));
        java.util.Collections.shuffle(shuffled, rand);
        
        int likedCount = 1 + rand.nextInt(2); // 1-2 liked
        int dislikedCount = 1 + rand.nextInt(2); // 1-2 disliked
        
        for (int i = 0; i < likedCount && i < shuffled.size(); i++) {
            prefs.likedProfessions.add(shuffled.get(i));
        }
        for (int i = likedCount; i < likedCount + dislikedCount && i < shuffled.size(); i++) {
            prefs.dislikedProfessions.add(shuffled.get(i));
        }

        return prefs;
    }
    
    public String getSeasonPtName() {
        return switch (favoriteSeason) {
            case "SPRING" -> "Primavera";
            case "SUMMER" -> "Verão";
            case "AUTUMN" -> "Outono";
            case "WINTER" -> "Inverno";
            default -> favoriteSeason;
        };
    }
    
    public String getWeatherPtName() {
        return switch (favoriteWeather) {
            case "CLEAR" -> "Ensolarado";
            case "RAIN" -> "Chuvoso";
            case "STORM" -> "Tempestade";
            case "SNOW" -> "Neve";
            default -> favoriteWeather;
        };
    }

    public static String getFoodPtName(String foodId) {
        return switch (foodId) {
            case "Food_Pie_Apple" -> "Torta de Maçã";
            case "Food_Bread" -> "Pão";
            case "Food_Pie_Meat" -> "Torta de Carne";
            case "Food_Fish_Raw" -> "Peixe Cru";
            case "Food_Beef_Raw" -> "Carne Crua";
            case "Food_Salad_Mushroom" -> "Salada de Cogumelo";
            default -> foodId;
        };
    }
}
