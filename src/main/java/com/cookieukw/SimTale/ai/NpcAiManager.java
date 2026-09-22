package com.cookieukw.SimTale.ai;

import java.util.Map;
import java.util.Set;
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
     */
    public void setDefaultProvider(String id) {
        NpcAiProvider provider = providers.get(id);
        if (provider == null) {
            return;
        }
        this.defaultProvider = provider;
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

    public CompletableFuture<AiResponse> generateAsync(AiRequest request) {
        return CompletableFuture.supplyAsync(() -> generate(request));
    }

    /**
     * Ids of providers that actually initialized (had a non-blank API key at setup), for
     * diagnostics — distinct from {@code config.provider}, which is just what was *requested*.
     */
    public Set<String> registeredProviderIds() {
        return providers.keySet();
    }

    /** The provider {@code generate(AiRequest)} actually uses, or null if none registered. */
    public String defaultProviderId() {
        return defaultProvider != null ? defaultProvider.id() : null;
    }
}
