package com.cookieukw.SimTale.ai.providers;

import com.cookieukw.SimTale.ai.AiMessage;
import com.cookieukw.SimTale.ai.AiProviderConfig;
import com.cookieukw.SimTale.ai.AiRequest;
import com.cookieukw.SimTale.ai.AiResponse;
import com.cookieukw.SimTale.ai.GenericHttpAiProvider;
import com.cookieukw.SimTale.ai.NpcAiProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OpenAIProvider implements NpcAiProvider {

    private final GenericHttpAiProvider delegate;

    public OpenAIProvider(String baseUrl, String apiKey, String model) {
        this("openai", baseUrl, apiKey, model);
    }

    /**
     * OpenAI-compatible provider registered under a custom id. Needed because OpenRouter
     * also speaks the OpenAI protocol — without a distinct id it would overwrite the
     * "openai" entry in NpcAiManager and could never be selected from the config.
     */
    public OpenAIProvider(String providerId, String baseUrl, String apiKey, String model) {
        AiProviderConfig config = new AiProviderConfig();
        config.providerId = providerId;
        config.baseUrl = baseUrl != null ? baseUrl : "https://api.openai.com";
        config.endpoint = "/v1/chat/completions";
        config.method = "POST";
        config.apiKey = apiKey;
        config.authHeader = "Authorization";
        config.authPrefix = "Bearer ";
        config.model = model;
        config.sendMessages = true;
        config.messagesField = "messages";
        config.modelField = "model";
        config.responsePath = "choices.0.message.content";
        config.extraPayload = Map.of();

        this.delegate = new GenericHttpAiProvider(config);
    }

    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public AiResponse generate(AiRequest request) {
        /* OpenAI expects:
        [
          { "role": "system", "content": systemPrompt },
          { "role": "user", "content": userMsg1 }, ...
        ]
        */
        List<AiMessage> openAiMsgs = new ArrayList<>();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            openAiMsgs.add(new AiMessage("system", request.systemPrompt()));
        }
        if (request.messages() != null) {
            openAiMsgs.addAll(request.messages());
        }

        AiRequest transformed = new AiRequest(
                request.npcName(),
                request.playerName(),
                null, // system prompt already formatted in messages list
                openAiMsgs,
                request.metadata(),
                request.playerUuid()
        );

        return delegate.generate(transformed);
    }
}
