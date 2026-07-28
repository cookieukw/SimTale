package com.cookieukw.SimTale.ai.providers;

import com.cookieukw.SimTale.ai.AiProviderConfig;
import com.cookieukw.SimTale.ai.AiRequest;
import com.cookieukw.SimTale.ai.AiResponse;
import com.cookieukw.SimTale.ai.GenericHttpAiProvider;
import com.cookieukw.SimTale.ai.NpcAiProvider;

import java.util.List;
import java.util.Map;

/**
 * Gemini {@code generateContent} adapter.
 * <p>
 * Expected body:
 * <pre>
 * {
 *   "contents": [ { "parts": [ { "text": "..." } ] } ]
 * }
 * </pre>
 * The previous implementation built a throwaway {@code AiProviderConfig} that was never
 * used, blanked the system prompt and the message list, and stuffed the real {@code contents}
 * into the metadata map. The body that actually went out was {@code {"contents": ""}} plus a
 * bogus {@code metadata} field, which Gemini always rejected — the provider could never
 * work. The prompt shape is now declared once, in the config, via {@code promptStructurer}.
 */
public class GeminiProvider implements NpcAiProvider {

    private final GenericHttpAiProvider delegate;

    public GeminiProvider(String apiKey, String model) {
        AiProviderConfig config = new AiProviderConfig();
        config.providerId = "gemini";
        config.baseUrl = "https://generativelanguage.googleapis.com";
        config.endpoint = "/v1beta/models/" + model + ":generateContent";
        config.method = "POST";
        config.apiKey = apiKey;
        config.authHeader = "x-goog-api-key";
        config.authPrefix = "";
        // The model is part of the URL for generateContent; sending it in the body as well
        // would be an unknown field.
        config.model = null;

        config.sendMessages = false;
        config.promptField = "contents";
        config.promptStructurer = text -> List.of(Map.of("parts", List.of(Map.of("text", text))));
        config.responsePath = "candidates.0.content.parts.0.text";
        config.extraPayload = Map.of();

        this.delegate = new GenericHttpAiProvider(config);
    }

    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public AiResponse generate(AiRequest request) {
        // The generic provider already merges systemPrompt + messages into a single prompt
        // string and wraps it via promptStructurer, so no request rewriting is needed here.
        return delegate.generate(request);
    }
}
