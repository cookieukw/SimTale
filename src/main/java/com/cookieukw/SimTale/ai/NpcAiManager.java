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

    /**
     * Selects the default provider by id. If the id is unknown (e.g. it is set in
     * simtale-ai.json but no API key was supplied, so the provider was never registered),
     * the previously registered default is kept instead of being wiped out.
     *
     * @return true if the provider existed and became the default
     */
    public boolean setDefaultProvider(String id) {
        NpcAiProvider provider = providers.get(id);
        if (provider == null) {
            return false;
        }
        this.defaultProvider = provider;
        return true;
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
