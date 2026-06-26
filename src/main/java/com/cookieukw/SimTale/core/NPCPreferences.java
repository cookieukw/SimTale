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

    public NPCPreferences() {}

    public static NPCPreferences createRandom() {
        NPCPreferences prefs = new NPCPreferences();
        Random rand = new Random();
        
        String[] foods = {"Torta de Maçã", "Pão Doce", "Sopa de Pedra", "Peixe Cru", "Carne Assada", "Sopa de Legumes"};
        String[] seasons = {"SPRING", "SUMMER", "AUTUMN", "WINTER"};
        String[] weathers = {"CLEAR", "RAIN", "STORM", "SNOW"};
        String[] hobbies = {"PESCARIA", "MINERAÇÃO", "JARDINAGEM", "LEITURA", "DORMIR"};

        prefs.favoriteFoods.add(foods[rand.nextInt(foods.length)]);
        prefs.hatedFoods.add(foods[rand.nextInt(foods.length)]);
        
        // Ensure they don't love and hate the same food
        while (prefs.hatedFoods.get(0).equals(prefs.favoriteFoods.get(0))) {
            prefs.hatedFoods.set(0, foods[rand.nextInt(foods.length)]);
        }

        prefs.favoriteSeason = seasons[rand.nextInt(seasons.length)];
        prefs.favoriteWeather = weathers[rand.nextInt(weathers.length)];
        prefs.hobby = hobbies[rand.nextInt(hobbies.length)];
        
        return prefs;
    }
}
