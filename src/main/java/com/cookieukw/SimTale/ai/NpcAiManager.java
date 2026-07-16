package com.cookieukw.SimTale.ai;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class NpcAiManager {

    private final Map<String, NpcAiProvider> providers = new ConcurrentHashMap<>();
    private NpcAiProvider defaultProvider;

    public void register(NpcAiProvider provider) {
        providers.put(provider.id(), provider);
        if (defaultProvider == null) {
            defaultProvider = provider;
        }
    }

    public void setDefaultProvider(String id) {
        this.defaultProvider = providers.get(id);
    }

    public AiResponse generate(String providerId, AiRequest request) {
        NpcAiProvider provider = providerId == null ? defaultProvider : providers.get(providerId);
        if (provider == null) {
            return AiResponse.fail("Provider not found: " + providerId, null);
        }
        return provider.generate(request);
    }

    public AiResponse generate(AiRequest request) {
        if (defaultProvider == null) {
            return AiResponse.fail("No provider registered", null);
        }
        return defaultProvider.generate(request);
    }

    public CompletableFuture<AiResponse> generateAsync(String providerId, AiRequest request) {
        return CompletableFuture.supplyAsync(() -> generate(providerId, request));
    }

    public CompletableFuture<AiResponse> generateAsync(AiRequest request) {
        return CompletableFuture.supplyAsync(() -> generate(request));
    }
}
