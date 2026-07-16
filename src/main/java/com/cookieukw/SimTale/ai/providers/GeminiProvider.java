package com.cookieukw.SimTale.ai.providers;

import com.cookieukw.SimTale.ai.AiMessage;
import com.cookieukw.SimTale.ai.AiProviderConfig;
import com.cookieukw.SimTale.ai.AiRequest;
import com.cookieukw.SimTale.ai.AiResponse;
import com.cookieukw.SimTale.ai.GenericHttpAiProvider;
import com.cookieukw.SimTale.ai.NpcAiProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        config.model = model;
        
        config.sendMessages = false;
        config.promptField = "contents";
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
        return delegate.generate(transform(request));
    }

    /**
     * Gemini generateContent expects:
     * {
     *   "contents": [
     *     { "parts": [ { "text": "..." } ] }
     *   ],
     *   "systemInstruction": {
     *     "parts": [ { "text": "..." } ]
     *   }
     * }
     *
     * We map this dynamically into extraPayload/custom structure mapping.
     */
    private AiRequest transform(AiRequest request) {
        // Flatten system prompt + chat history into Gemini format payload config
        StringBuilder prompt = new StringBuilder();
        if (request.systemPrompt() != null) {
            prompt.append(request.systemPrompt()).append("\n\n");
        }
        if (request.messages() != null) {
            for (AiMessage m : request.messages()) {
                prompt.append(m.role()).append(": ").append(m.content()).append("\n");
            }
        }

        // We wrap this inside the "contents" expected structure using extraPayload
        List<Map<String, Object>> contents = new ArrayList<>();
        Map<String, Object> contentMap = new HashMap<>();
        List<Map<String, String>> parts = new ArrayList<>();
        parts.add(Map.of("text", prompt.toString()));
        contentMap.put("parts", parts);
        contents.add(contentMap);

        // We bypass sendMessages, promptField contains contents object list
        AiProviderConfig customConfig = new AiProviderConfig();
        customConfig.providerId = "gemini";
        customConfig.baseUrl = "https://generativelanguage.googleapis.com";
        customConfig.endpoint = delegate.id(); // Not strictly needed
        
        // Return structured format
        Map<String, Object> metadata = new HashMap<>(request.metadata());
        metadata.put("contents", contents);

        return new AiRequest(
                request.npcName(),
                request.playerName(),
                null,
                List.of(),
                metadata,
                request.playerUuid()
        );
    }
}
