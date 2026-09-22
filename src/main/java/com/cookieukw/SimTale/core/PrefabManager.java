package com.cookieukw.SimTale.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import com.hypixel.hytale.logger.HytaleLogger;

public class PrefabManager {
    private static final Map<String, Prefab> cache = new HashMap<>();
    private static final Gson gson = new GsonBuilder().create();

    public static Prefab getPrefab(String name) {
        if (cache.containsKey(name)) {
            return cache.get(name);
        }

        String path = "/prefabs/" + name + ".prefab.json";
        InputStream is = PrefabManager.class.getResourceAsStream(path);

        if (is == null) {
            HytaleLogger.forEnclosingClass().atWarning().log("Prefab resource not found: " + path);
            return null;
        }

        try (InputStreamReader reader = new InputStreamReader(is)) {
            Prefab prefab = gson.fromJson(reader, Prefab.class);
            // Sort blocks by Y coordinate so they build from bottom to top
            if (prefab != null && prefab.getBlocks() != null) {
                prefab.getBlocks().sort(Comparator.comparingInt(PrefabBlock::getY));
            }
            cache.put(name, prefab);
            return prefab;
        } catch (Exception e) {
            HytaleLogger.forEnclosingClass().atSevere().withCause(e).log("Failed to load prefab: " + name);
            return null;
        }
    }
}
