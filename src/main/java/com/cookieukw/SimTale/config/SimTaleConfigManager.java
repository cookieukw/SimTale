package com.cookieukw.SimTale.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Manages loading and saving the general SimTale configuration (simtale-config.json).
 */
public class SimTaleConfigManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final File CONFIG_FILE = new File("simtale-config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static SimTaleConfig currentConfig = new SimTaleConfig();

    public static SimTaleConfig getConfig() {
        return currentConfig;
    }

    public static void setConfig(SimTaleConfig config) {
        if (config != null) {
            currentConfig = config;
        }
    }

    public static String configFilePath() {
        return CONFIG_FILE.getAbsolutePath();
    }

    public static boolean configFileExists() {
        return CONFIG_FILE.exists();
    }

    public static void load() {
        if (!CONFIG_FILE.exists()) {
            save(); // Create default config file
            return;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            SimTaleConfig loaded = GSON.fromJson(reader, SimTaleConfig.class);
            if (loaded != null) {
                currentConfig = loaded;
                if (loaded.debugMode) {
                    LOGGER.atInfo().log("[SimTale] Configuracao carregada de " + CONFIG_FILE.getName());
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[SimTale] Falha ao ler simtale-config.json, mantendo padroes: " + e);
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(currentConfig, writer);
        } catch (IOException e) {
            LOGGER.atWarning().log("[SimTale] Falha ao salvar simtale-config.json: " + e);
        }
    }
}
