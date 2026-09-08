package com.example.primenestprop.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/** Minimal request/response shapes for Google's Gemini generateContent REST endpoint. */
final class GeminiDtos {
    private GeminiDtos() {
    }

    record Part(String text) {
    }

    record Content(String role, List<Part> parts) {
    }

    record SystemInstruction(List<Part> parts) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record GenerationConfig(
            @JsonProperty("responseMimeType") String responseMimeType,
            @JsonProperty("responseSchema") Map<String, Object> responseSchema,
            Double temperature,
            @JsonProperty("maxOutputTokens") Integer maxOutputTokens
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record GenerateContentRequest(
            List<Content> contents,
            @JsonProperty("systemInstruction") SystemInstruction systemInstruction,
            @JsonProperty("generationConfig") GenerationConfig generationConfig
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Candidate(Content content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GenerateContentResponse(List<Candidate> candidates) {
    }
}
