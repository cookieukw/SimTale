package com.cookieukw.SimTale.ai;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.File;
import java.nio.file.Files;

public class AiConfigManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final File CONFIG_FILE = new File("simtale-ai.json");
    private static AiConfig currentConfig = new AiConfig();

    public static AiConfig getConfig() {
        return currentConfig;
    }

    /** Absolute path to the config file, for diagnostics — the relative name alone doesn't say
     *  which directory the server actually resolved it against. */
    public static String configFilePath() {
        return CONFIG_FILE.getAbsolutePath();
    }

    public static boolean configFileExists() {
        return CONFIG_FILE.exists();
    }

    public static void load() {
        try {
            if (!CONFIG_FILE.exists()) {
                save(); // create default config
                return;
            }

            String content = Files.readString(CONFIG_FILE.toPath());
            AiConfig config = new AiConfig();
            
            String enabledStr = JsonParser.extractPath(content, "enabled");
            if (enabledStr != null) {
                config.enabled = Boolean.parseBoolean(enabledStr);
            }

            String provider = JsonParser.extractPath(content, "provider");
            if (provider != null) config.provider = provider;
            
            String geminiKey = JsonParser.extractPath(content, "geminiKey");
            if (geminiKey != null) config.geminiKey = geminiKey;

            String openaiKey = JsonParser.extractPath(content, "openaiKey");
            if (openaiKey != null) config.openaiKey = openaiKey;

            String openrouterKey = JsonParser.extractPath(content, "openrouterKey");
            if (openrouterKey != null) config.openrouterKey = openrouterKey;

            String customUrl = JsonParser.extractPath(content, "customUrl");
            if (customUrl != null) config.customUrl = customUrl;

            String customModel = JsonParser.extractPath(content, "customModel");
            if (customModel != null) config.customModel = customModel;

            currentConfig = config;
        } catch (Exception e) {
            // printStackTrace() bypasses the server log; a bad config was effectively invisible.
            LOGGER.atWarning().log("SimTale: falha ao ler simtale-ai.json, usando a configuracao padrao: " + e);
        }
    }

    public static void save() {
        try {
            JsonBuilder builder = JsonBuilder.create().object();
            builder.key("enabled").value(currentConfig.enabled);
            builder.key("provider").value(currentConfig.provider);
            builder.key("geminiKey").value(currentConfig.geminiKey);
            builder.key("openaiKey").value(currentConfig.openaiKey);
            builder.key("openrouterKey").value(currentConfig.openrouterKey);
            builder.key("customUrl").value(currentConfig.customUrl);
            builder.key("customModel").value(currentConfig.customModel);
            String json = builder.endObject().toString();

            Files.writeString(CONFIG_FILE.toPath(), json);
        } catch (Exception e) {
            LOGGER.atWarning().log("SimTale: falha ao gravar simtale-ai.json: " + e);
        }
    }
}
