package com.example.primenestprop.maintenance;

import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.example.primenestprop.ai.AiAnthropicConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Classifies how urgent a maintenance request is, using Claude when configured and a
 * keyword-based fallback otherwise - so urgency triage never depends on the tenant's own
 * judgement of severity. */
@Service
public class MaintenanceTriageService {
    private static final Logger log = LoggerFactory.getLogger(MaintenanceTriageService.class);

    private static final List<String> URGENT_KEYWORDS = List.of(
            "gas leak", "fire", "flood", "flooding", "no water", "sewage", "burst pipe", "break-in", "break in",
            "electrical shock", "sparking", "exposed wire"
    );
    private static final List<String> HIGH_KEYWORDS = List.of(
            "leak", "no power", "no electricity", "security", "lock broken", "ceiling", "smell of gas", "mould", "mold"
    );

    private final AiAnthropicConfig anthropicConfig;

    public MaintenanceTriageService(AiAnthropicConfig anthropicConfig) {
        this.anthropicConfig = anthropicConfig;
    }

    public record UrgencyClassification(
            @JsonPropertyDescription("One of LOW, NORMAL, HIGH, URGENT based on how urgently this needs attention.")
            String urgency
    ) {
    }

    public String classify(String category, String description) {
        if (anthropicConfig.client() != null) {
            try {
                return classifyWithClaude(category, description);
            } catch (Exception ex) {
                log.warn("Claude maintenance triage failed, falling back to keyword-based triage", ex);
            }
        }
        return classifyWithKeywords(category, description);
    }

    private String classifyWithClaude(String category, String description) {
        Model model = anthropicConfig.model();
        StructuredMessageCreateParams<UrgencyClassification> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(256L)
                .system("You triage tenant maintenance requests for a Zimbabwean rental platform. Classify urgency as "
                        + "URGENT (immediate safety/habitability risk: fire, gas, flooding, no water/power, break-in), "
                        + "HIGH (needs attention within a day or two), NORMAL (routine repair), or LOW (cosmetic/non-urgent).")
                .outputConfig(UrgencyClassification.class)
                .addUserMessage("Category: " + category + "\nDescription: " + description)
                .build();

        UrgencyClassification result = anthropicConfig.client().messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .findFirst()
                .map(structured -> structured.text())
                .orElseThrow(() -> new IllegalStateException("Claude returned no urgency classification"));

        return normalize(result.urgency());
    }

    private String classifyWithKeywords(String category, String description) {
        String normalized = (category + " " + (description == null ? "" : description)).toLowerCase(Locale.ROOT);
        if (URGENT_KEYWORDS.stream().anyMatch(normalized::contains)) {
            return "URGENT";
        }
        if (HIGH_KEYWORDS.stream().anyMatch(normalized::contains)) {
            return "HIGH";
        }
        return "NORMAL";
    }

    private String normalize(String urgency) {
        if (urgency == null) {
            return "NORMAL";
        }
        String upper = urgency.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "LOW", "NORMAL", "HIGH", "URGENT" -> upper;
            default -> "NORMAL";
        };
    }
}
