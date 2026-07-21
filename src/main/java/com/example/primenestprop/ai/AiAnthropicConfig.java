package com.example.primenestprop.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds the Claude client only when an API key is configured. Without one, {@link #client()}
 * returns null and {@link AiAssistantService} falls back to keyword-based matching rather than
 * pretending to be AI-powered.
 */
@Component
public class AiAnthropicConfig {
    private static final Logger log = LoggerFactory.getLogger(AiAnthropicConfig.class);

    private final AnthropicClient client;
    private final Model model;

    public AiAnthropicConfig(
            @Value("${app.ai.anthropic-api-key:}") String apiKey,
            @Value("${app.ai.anthropic-model:claude-opus-4-8}") String modelId
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("app.ai.anthropic-api-key is not set; AI property assistant will use keyword-matching fallback only.");
            this.client = null;
            this.model = null;
        } else {
            this.client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
            this.model = Model.of(modelId);
        }
    }

    public AnthropicClient client() {
        return client;
    }

    public Model model() {
        return model;
    }
}
