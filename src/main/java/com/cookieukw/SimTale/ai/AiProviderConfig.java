package com.cookieukw.SimTale.ai;

import java.util.Map;
import java.util.function.Function;

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

    /**
     * Wraps the merged prompt text into the structure the provider expects under
     * {@link #promptField}. Used when {@code sendMessages == false}: Gemini, for example,
     * needs {@code "contents": [ { "parts": [ { "text": "..." } ] } ]} rather than a bare
     * string. Defaults to identity (the raw string).
     */
    public Function<String, Object> promptStructurer = text -> text;

    /**
     * Whether the request metadata map is serialized into the payload. Off by default:
     * SimTale's metadata carries ints (friendship, romance) and both the OpenAI and Gemini
     * endpoints reject the resulting unknown/badly-typed field with HTTP 400.
     */
    public boolean sendMetadata = false;
}
