package com.cookieukw.SimTale.engine;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MagicDataLoader {
    
    private static List<Animal> animalsCache;
    private static List<Question> questionsCache;

    public static List<Animal> getAnimals() {
        if (animalsCache == null) {
            animalsCache = loadAnimals();
        }
        return animalsCache;
    }

    public static List<Question> getQuestions() {
        if (questionsCache == null) {
            questionsCache = loadQuestions();
        }
        return questionsCache;
    }

    private static List<Animal> loadAnimals() {
        try (Reader reader = new InputStreamReader(
                MagicDataLoader.class.getResourceAsStream("/Common/UI/Custom/MagicGame/animals.json"), 
                StandardCharsets.UTF_8)) {
            Gson gson = new Gson();
            return gson.fromJson(reader, new TypeToken<List<Animal>>(){}.getType());
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    private static List<Question> loadQuestions() {
        try (Reader reader = new InputStreamReader(
                MagicDataLoader.class.getResourceAsStream("/Common/UI/Custom/MagicGame/questions.json"), 
                StandardCharsets.UTF_8)) {
            Gson gson = new Gson();
            return gson.fromJson(reader, new TypeToken<List<Question>>(){}.getType());
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }
}
