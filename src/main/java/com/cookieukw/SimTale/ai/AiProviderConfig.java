package com.cookieukw.SimTale.ai;

import java.util.Map;

public class AiProviderConfig {
    public String providerId;
    public String baseUrl;
    public String endpoint;
    public String method = "POST";
    public String apiKey;
    public String authHeader = "Authorization";
    public String authPrefix = "Bearer ";
    public String model;
    public int timeoutMs = 15000;

    // Generic maps
    public Map<String, String> headers = Map.of();
    public Map<String, Object> extraPayload = Map.of();

    // Logical payload path
    public String promptField = "prompt";
    public String messagesField = "messages";
    public String modelField = "model";

    // Response dot path (e.g. choices.0.message.content)
    public String responsePath = "text";

    public boolean sendMessages = true;
}
