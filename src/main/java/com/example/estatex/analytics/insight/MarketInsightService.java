package com.example.estatex.analytics.insight;

import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.example.estatex.ai.AiAnthropicConfig;
import com.example.estatex.analytics.market.AnalyticsSetting;
import com.example.estatex.analytics.market.AnalyticsSettingRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Claude's interpretation of the current observations ("AI interpretation"). Claude only sees the
 * observations themselves, must cite the ones each item rests on, and items citing none of them
 * are dropped. It is called only when the set of observations changes, so a quiet market costs
 * nothing. If AI is off, unavailable or refuses, the observations are still shown on their own.
 */
@Service
public class MarketInsightService {
    public static final String AI_SETTING = "ai_insights_enabled";
    static final int PROMPT_VERSION = 1;
    static final int MAX_OBSERVATIONS = 40;
    static final int MAX_ITEMS = 8;
    static final Set<String> SECTIONS = Set.of("CHANGE", "OPPORTUNITY", "RISK");
    private static final Set<String> AI_DOMAINS = Set.of("MARKET", "DEMAND");
    private static final Duration RETRY_FAILED_AFTER = Duration.ofHours(1);
    private static final Logger log = LoggerFactory.getLogger(MarketInsightService.class);

    static final String SYSTEM = """
            You are the market analyst inside EstateX, a property marketplace in Zimbabwe. Administrators read your notes on \
            the analytics dashboard, right next to the statistical observations you are interpreting.

            You will receive observations the platform has already computed and tested. Each one is a fact about EstateX's own \
            data (its listings, enquiries and asking prices), not about the whole property market. Your job is to interpret them \
            for management: what they may mean, and what is worth looking into.

            Administrators make decisions from these notes, so:
            - Base every item only on the observations given, and cite the ids it rests on. Do not add figures, places or \
            periods that are not in them.
            - Asking prices are what sellers and landlords ask, not what was agreed. Say so when it matters.
            - Offer causes as possibilities to check, never as findings.
            - Observations with method THRESHOLD or DESCRIPTIVE, or LOW confidence, are not significance tests; hedge accordingly.
            - Where the observations do not support a conclusion, say that no reliable conclusion can be drawn rather than \
            stretching.

            Write a one-sentence headline on what changed. Then up to 8 items. Each item has a section (CHANGE for what \
            changed, OPPORTUNITY for something worth pursuing, RISK for something to watch), a short title, a one- or \
            two-sentence interpretation, a one-sentence business implication, and the observation ids it rests on. Plain \
            English, no markdown.""";

    /** What Claude returns (structured output). */
    public record DraftItem(String section, String title, String interpretation, String implication, List<Long> observationIds) {
    }

    public record Draft(String headline, List<DraftItem> items) {
    }

    /** Outcome of one refresh, for the run log. */
    public record Outcome(String status, String detail) {
    }

    private final AiAnthropicConfig ai;
    private final MarketInsightRepository insights;
    private final AnalyticsSettingRepository settings;
    private final ObjectMapper json;
    private final String model;

    /** Test hook: replaces the Claude call. */
    volatile Function<List<MarketObservation>, Draft> writerOverride;

    public MarketInsightService(AiAnthropicConfig ai, MarketInsightRepository insights, AnalyticsSettingRepository settings,
                                ObjectMapper json, @Value("${app.analytics.ai-model:claude-opus-5-5}") String model) {
        this.ai = ai;
        this.insights = insights;
        this.settings = settings;
        this.json = json;
        this.model = model;
    }

    public boolean enabled() {
        return settings.findById(AI_SETTING).map(s -> !"false".equalsIgnoreCase(s.getValue())).orElse(true);
    }

    public boolean available() {
        return writerOverride != null || ai.client() != null;
    }

    public String model() {
        return model;
    }

    public void setEnabled(boolean on, Long userId, Instant now) {
        AnalyticsSetting s = settings.findById(AI_SETTING).orElseGet(AnalyticsSetting::new);
        s.setKey(AI_SETTING);
        s.setValue(Boolean.toString(on));
        s.setUpdatedByUserId(userId);
        s.setUpdatedAt(now);
        settings.save(s);
    }

    /** The observations Claude is shown: market and demand findings, strongest first. */
    static List<MarketObservation> input(List<MarketObservation> current) {
        return current.stream()
                .filter(o -> AI_DOMAINS.contains(o.getDomain()))
                .sorted(Comparator.comparing((MarketObservation o) -> o.getQValue() == null ? 1.0 : o.getQValue())
                        .thenComparing(o -> o.getChangePct() == null ? 0 : -Math.abs(o.getChangePct().doubleValue()))
                        .thenComparing(MarketObservation::getId))
                .limit(MAX_OBSERVATIONS)
                .toList();
    }

    public Outcome refresh(List<MarketObservation> current, Instant now, Long runId) {
        if (!enabled()) return new Outcome("OFF", "AI interpretations are turned off; observations are still shown");
        if (!available()) return new Outcome("UNAVAILABLE", "No Claude API key is configured; observations are still shown");
        List<MarketObservation> input = input(current);
        if (input.isEmpty()) return new Outcome("NOTHING", "No market or demand observations to interpret");
        String fingerprint = fingerprint(input);
        MarketInsight latest = insights.findFirstByOrderByCreatedAtDescIdDesc().orElse(null);
        if (latest != null && latest.getInputFingerprint().equals(fingerprint)
                && (!"FAILED".equals(latest.getStatus()) || latest.getCreatedAt().isAfter(now.minus(RETRY_FAILED_AFTER)))) {
            return new Outcome("UNCHANGED", "Observations unchanged since the last interpretation");
        }
        MarketInsight row = new MarketInsight();
        row.setCreatedAt(now);
        row.setModel(writerOverride != null ? "test" : model);
        row.setPromptVersion(PROMPT_VERSION);
        row.setInputFingerprint(fingerprint);
        row.setJobRunId(runId);
        row.setObservationIdsJson(json.writeValueAsString(input.stream().map(MarketObservation::getId).toList()));
        try {
            Draft draft = writerOverride != null ? writerOverride.apply(input) : callClaude(input, row);
            List<Map<String, Object>> items = validate(draft, input);
            if (items.isEmpty()) throw new IllegalStateException("No item cited a valid observation");
            row.setStatus("OK");
            row.setHeadline(trim(draft.headline(), 500));
            row.setItemsJson(json.writeValueAsString(items));
            insights.save(row);
            return new Outcome("OK", items.size() + " interpretation(s) from " + input.size() + " observation(s)");
        } catch (Refused e) {
            row.setStatus("REFUSED");
            row.setError("Claude declined to interpret these observations");
            insights.save(row);
            return new Outcome("REFUSED", row.getError());
        } catch (RuntimeException e) {
            log.warn("AI market interpretation failed: {}", e.getMessage());
            row.setStatus("FAILED");
            row.setError(trim(e.getClass().getSimpleName() + ": " + e.getMessage(), 500));
            insights.save(row);
            return new Outcome("FAILED", row.getError());
        }
    }

    private Draft callClaude(List<MarketObservation> input, MarketInsight row) {
        StructuredMessage<Draft> message = ai.client().messages().create(params(input));
        row.setInputTokens(message.usage().inputTokens());
        row.setOutputTokens(message.usage().outputTokens());
        if (message.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) throw new Refused();
        return message.content().stream()
                .flatMap(block -> block.text().stream())
                .findFirst()
                .map(text -> text.text())
                .orElseThrow(() -> new IllegalStateException("Claude returned no interpretation"));
    }

    /** The request sent to Claude (separate so tests can check it builds without calling the API). */
    StructuredMessageCreateParams<Draft> params(List<MarketObservation> input) {
        return MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(SYSTEM)
                .outputConfig(Draft.class)
                .addUserMessage("Observations (JSON):\n" + json.writeValueAsString(input.stream().map(MarketInsightService::forPrompt).toList()))
                .build();
    }

    /** What Claude sees for one observation: the facts, nothing else. */
    static Map<String, Object> forPrompt(MarketObservation o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("statement", o.getStatement());
        m.put("kind", o.getKind());
        m.put("method", o.getMethod());
        m.put("confidence", o.getConfidence());
        if (o.getListingType() != null) m.put("listingType", o.getListingType());
        if (o.getCity() != null) m.put("city", o.getCity());
        if (o.getSuburb() != null) m.put("suburb", o.getSuburb());
        if (o.getCategory() != null) m.put("category", o.getCategory());
        m.put("period", o.getPeriodStart() + " to " + o.getPeriodEnd());
        if (o.getComparisonStart() != null) m.put("comparisonPeriod", o.getComparisonStart() + " to " + o.getComparisonEnd());
        m.put("sampleCurrent", o.getSampleCurrent());
        if (o.getComparisonStart() != null) m.put("samplePrevious", o.getSamplePrevious());
        if (o.getQValue() != null) m.put("adjustedPValue", o.getQValue());
        return m;
    }

    /** Keeps only items that cite observations Claude was shown; normalises sections and lengths. */
    static List<Map<String, Object>> validate(Draft draft, List<MarketObservation> input) {
        Set<Long> valid = input.stream().map(MarketObservation::getId).collect(Collectors.toSet());
        List<Map<String, Object>> out = new ArrayList<>();
        if (draft == null || draft.items() == null) return out;
        for (DraftItem item : draft.items()) {
            if (item == null || item.observationIds() == null || item.title() == null || item.interpretation() == null) continue;
            List<Long> ids = item.observationIds().stream().filter(valid::contains).distinct().toList();
            if (ids.isEmpty()) continue;
            String section = item.section() == null ? "CHANGE" : item.section().trim().toUpperCase(Locale.ROOT);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("section", SECTIONS.contains(section) ? section : "CHANGE");
            m.put("title", trim(item.title(), 160));
            m.put("interpretation", trim(item.interpretation(), 800));
            m.put("implication", trim(item.implication() == null ? "" : item.implication(), 500));
            m.put("observationIds", ids);
            out.add(m);
            if (out.size() == MAX_ITEMS) break;
        }
        return out;
    }

    static String fingerprint(List<MarketObservation> input) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            for (MarketObservation o : input) {
                sha.update((o.getId() + "|" + o.getStatement() + "|" + o.getConfidence() + "\n").getBytes(StandardCharsets.UTF_8));
            }
            sha.update(("v" + PROMPT_VERSION).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sha.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    static final class Refused extends RuntimeException {
        Refused() {
            super("refused");
        }
    }
}
