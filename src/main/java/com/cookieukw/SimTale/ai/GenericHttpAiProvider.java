package com.cookieukw.SimTale.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class GenericHttpAiProvider implements NpcAiProvider {

    private final AiProviderConfig config;
    private final HttpClient client;

    public GenericHttpAiProvider(AiProviderConfig config) {
        this.config = config;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.timeoutMs))
                .build();
    }

    @Override
    public String id() {
        return config.providerId;
    }

    @Override
    public AiResponse generate(AiRequest request) {
        try {
            String json = buildJsonPayload(request);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl + config.endpoint))
                    .timeout(Duration.ofMillis(config.timeoutMs))
                    .header("Content-Type", "application/json");

            if (config.apiKey != null && !config.apiKey.isBlank()) {
                builder.header(config.authHeader, config.authPrefix + config.apiKey);
            }

            if (config.headers != null) {
                for (Map.Entry<String, String> entry : config.headers.entrySet()) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }

            HttpRequest httpRequest = builder
                    .method(config.method, HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return AiResponse.fail("HTTP " + response.statusCode(), response.body());
            }

            String text = JsonParser.extractPath(response.body(), config.responsePath);
            if (text == null || text.isBlank()) {
                return AiResponse.fail("Empty response or invalid return path", response.body());
            }

            return AiResponse.ok(text, response.body());

        } catch (Exception e) {
            return AiResponse.fail(e.getClass().getSimpleName() + ": " + e.getMessage(), null);
        }
    }

    private String buildJsonPayload(AiRequest request) {
        JsonBuilder builder = JsonBuilder.create().object();

        if (config.model != null && !config.model.isBlank()) {
            builder.key(config.modelField).value(config.model);
        }

        if (config.sendMessages) {
            builder.key(config.messagesField).value(request.messages());
            // For message-based APIs the system prompt is already the first message. Emitting a
            // stray top-level "systemPrompt" field made OpenAI-compatible endpoints reject the
            // request with HTTP 400.
        } else {
            // promptStructurer wraps the text in whatever shape the provider needs
            // (Gemini: contents[].parts[].text). Default is the raw string.
            builder.key(config.promptField).value(config.promptStructurer.apply(mergePrompt(request)));
        }

        // Off by default: SimTale's metadata carries ints, which both OpenAI and Gemini reject.
        if (config.sendMetadata && request.metadata() != null) {
            builder.key("metadata").value(request.metadata());
        }

        if (config.extraPayload != null) {
            for (Map.Entry<String, Object> entry : config.extraPayload.entrySet()) {
                builder.key(entry.getKey()).value(entry.getValue());
            }
        }

        return builder.endObject().toString();
    }

    private String mergePrompt(AiRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            sb.append("SYSTEM: ").append(request.systemPrompt()).append("\n\n");
        }
        if (request.messages() != null) {
            for (AiMessage msg : request.messages()) {
                sb.append(msg.role()).append(": ").append(msg.content()).append("\n");
            }
        }
        return sb.toString().trim();
    }
}
