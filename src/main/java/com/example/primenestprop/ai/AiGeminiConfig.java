package com.example.primenestprop.ai;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Builds a Google Gemini client when a free Gemini API key (from Google AI Studio) is
 * configured. Gemini is tried before Anthropic in {@link AiAssistantService} since it costs
 * nothing to use; without either key configured, the service falls back to keyword matching.
 */
@Component
public class AiGeminiConfig {
    private static final Logger log = LoggerFactory.getLogger(AiGeminiConfig.class);

    private final RestClient client;
    private final String model;

    public AiGeminiConfig(
            @Value("${app.ai.gemini-api-key:}") String apiKey,
            @Value("${app.ai.gemini-model:gemini-3.5-flash-lite}") String model
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("app.ai.gemini-api-key is not set; AI property assistant will try Anthropic next, or "
                    + "fall back to keyword-matching if that isn't set either.");
            this.client = null;
            this.model = null;
        } else {
            this.client = RestClient.builder()
                    .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                    .requestInterceptor((request, body, execution) -> {
                        URI uri = UriComponentsBuilder.fromUri(request.getURI())
                                .queryParam("key", apiKey)
                                .build(true)
                                .toUri();
                        return execution.execute(new HttpRequestWrapper(request) {
                            @Override
                            public URI getURI() {
                                return uri;
                            }
                        }, body);
                    })
                    .build();
            this.model = model;
            log.info("Gemini AI client configured with model {}", model);
        }
    }

    public RestClient client() {
        return client;
    }

    public String model() {
        return model;
    }
}
