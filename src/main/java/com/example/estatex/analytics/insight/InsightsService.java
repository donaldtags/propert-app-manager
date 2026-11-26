package com.example.estatex.analytics.insight;

import com.example.estatex.analytics.alert.AlertService;
import com.example.estatex.analytics.metrics.Grain;
import com.example.estatex.analytics.metrics.MetricDefinition.Domain;
import com.example.estatex.user.AppUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * The "Insights &amp; alerts" read model: current observations for a grain (only the domains the
 * viewer may see), the latest AI interpretation (only items whose every cited observation is
 * visible), and the brief's analyst questions, each answered with observations or with an explicit
 * "no reliable conclusion".
 */
@Service
public class InsightsService {
    private final MarketObservationRepository observations;
    private final ObservationCoverageRepository coverage;
    private final MarketInsightRepository insights;
    private final MarketInsightService ai;
    private final ObjectMapper json;

    public InsightsService(MarketObservationRepository observations, ObservationCoverageRepository coverage,
                           MarketInsightRepository insights, MarketInsightService ai, ObjectMapper json) {
        this.observations = observations;
        this.coverage = coverage;
        this.insights = insights;
        this.ai = ai;
        this.json = json;
    }

    public record ObservationView(Long id, String kind, String metricKey, String domain, String method, String grain,
                                  Instant periodStart, Instant periodEnd, Instant comparisonStart, Instant comparisonEnd,
                                  String listingType, String city, String suburb, String category, String location,
                                  BigDecimal currentValue, BigDecimal previousValue, BigDecimal changePct, long sampleCurrent,
                                  long samplePrevious, Double statistic, Double pValue, Double qValue, String confidence,
                                  String statement, String currency, Instant firstSeenAt) {
    }

    public record CoverageView(String grain, Instant periodStart, Instant periodEnd, Instant comparisonStart, Instant comparisonEnd,
                               int tested, int reported, int belowMinimum, int historyIncomplete, Instant computedAt) {
    }

    public record AiItem(String section, String title, String interpretation, String implication, List<Long> observationIds) {
    }

    /**
     * status: OK, OFF (turned off), UNAVAILABLE (no API key), NONE (nothing generated yet),
     * FAILED or REFUSED (the latest attempt; an earlier good interpretation may still be shown).
     */
    public record AiView(String status, boolean enabled, boolean available, String model, Instant createdAt, String headline,
                         List<AiItem> items, boolean outdated, int hiddenItems, String note) {
    }

    public record Question(String key, String question, List<Long> observationIds, String answer) {
    }

    public record Minimums(int events, int medianSample, int stock, int shareEvents, double maxQValue) {
    }

    public record Insights(String grain, CoverageView coverage, List<ObservationView> observations, AiView ai,
                           List<Question> questions, Minimums minimums) {
    }

    @Transactional(readOnly = true)
    public Insights insights(AppUser user, Grain grain) {
        Set<String> domains = visibleDomains(user);
        ObservationCoverage cov = coverage.findById(grain.name()).orElse(null);
        List<MarketObservation> all = new ArrayList<>();
        for (Grain g : ObservationService.GRAINS) {
            coverage.findById(g.name()).filter(c -> c.getPeriodStart() != null)
                    .ifPresent(c -> all.addAll(observations.findByGrainAndPeriodStart(g.name(), c.getPeriodStart())));
        }
        List<MarketObservation> visible = all.stream().filter(o -> domains.contains(o.getDomain())).toList();
        List<MarketObservation> mine = visible.stream().filter(o -> o.getGrain().equals(grain.name()))
                .sorted(strongestFirst()).toList();
        AiView aiView = aiView(all, visible);
        CoverageView cv = cov == null ? null : new CoverageView(cov.getGrain(), cov.getPeriodStart(), cov.getPeriodEnd(),
                cov.getComparisonStart(), cov.getComparisonEnd(), cov.getTested(), cov.getReported(), cov.getBelowMinimum(),
                cov.getHistoryIncomplete(), cov.getComputedAt());
        return new Insights(grain.name(), cv, mine.stream().map(InsightsService::view).toList(), aiView,
                questions(mine, cov, aiView), new Minimums(ObservationService.MIN_EVENTS, ObservationService.MIN_MEDIAN_N,
                ObservationService.MIN_STOCK, ObservationService.MIN_SHARE_EVENTS, ObservationService.MAX_Q));
    }

    public static Set<String> visibleDomains(AppUser user) {
        Set<String> out = new LinkedHashSet<>();
        for (Domain d : Domain.values()) {
            if (user != null && user.hasPermission(AlertService.permissionFor(d))) out.add(d.name());
        }
        return out;
    }

    private AiView aiView(List<MarketObservation> all, List<MarketObservation> visible) {
        boolean enabled = ai.enabled();
        boolean available = ai.available();
        MarketInsight latest = insights.findFirstByOrderByCreatedAtDescIdDesc().orElse(null);
        MarketInsight good = insights.findFirstByStatusOrderByCreatedAtDescIdDesc("OK").orElse(null);
        String status = !enabled ? "OFF" : !available ? "UNAVAILABLE" : latest == null ? "NONE" : latest.getStatus();
        if (good == null || !enabled) {
            String note = !enabled ? "AI interpretations are turned off. The observations below are unaffected."
                    : !available ? "No Claude API key is configured, so there is no AI interpretation. The observations below are unaffected."
                    : latest == null ? (all.isEmpty() ? "No observations yet, so there is nothing to interpret."
                    : "An interpretation will be written on the next hourly run.")
                    : "The last attempt " + ("REFUSED".equals(latest.getStatus()) ? "was declined" : "failed") + "; it is retried hourly.";
            return new AiView(status, enabled, available, ai.model(), null, null, List.of(), false, 0, note);
        }
        Set<Long> visibleIds = visible.stream().map(MarketObservation::getId).collect(Collectors.toSet());
        List<Map<String, Object>> raw = json.readValue(good.getItemsJson() == null ? "[]" : good.getItemsJson(),
                new TypeReference<List<Map<String, Object>>>() { });
        List<AiItem> items = new ArrayList<>();
        int hidden = 0;
        for (Map<String, Object> m : raw) {
            List<Long> ids = ((List<?>) m.getOrDefault("observationIds", List.of())).stream()
                    .map(x -> ((Number) x).longValue()).toList();
            // Shown only if the viewer may see, and the platform still holds, everything it rests on.
            if (ids.isEmpty() || !visibleIds.containsAll(ids)) {
                hidden++;
                continue;
            }
            items.add(new AiItem((String) m.get("section"), (String) m.get("title"), (String) m.get("interpretation"),
                    (String) m.get("implication"), ids));
        }
        boolean outdated = !good.getInputFingerprint().equals(MarketInsightService.fingerprint(MarketInsightService.input(all)));
        String note = outdated ? "Written for an earlier set of observations; an update is due on the next hourly run." : null;
        if ("UNAVAILABLE".equals(status)) {
            note = "No Claude API key is configured now, so this won't be updated; showing the last interpretation written.";
        } else if ("FAILED".equals(status) || "REFUSED".equals(status)) {
            note = "The latest attempt " + ("REFUSED".equals(status) ? "was declined" : "failed") + "; showing the last good interpretation.";
        }
        return new AiView(status, true, available, good.getModel(), good.getCreatedAt(), good.getHeadline(), items, outdated, hidden, note);
    }

    private List<Question> questions(List<MarketObservation> obs, ObservationCoverage cov, AiView aiView) {
        List<Question> out = new ArrayList<>();
        Predicate<MarketObservation> demandUp = o -> Set.of("ENQUIRIES_UP", "VIEWING_REQUESTS_UP", "LISTING_VIEWS_UP", "SAVES_UP",
                "RENTAL_APPLICATIONS_UP", "OFFERS_UP").contains(o.getKind());
        out.add(question("changes", "What changed in the market?", obs, o -> !"DESCRIPTIVE".equals(o.getMethod()), 6, cov));
        out.add(question("rental_enquiry_suburbs", "Which suburbs had the greatest increase in rental enquiries?", obs,
                o -> "ENQUIRIES_UP".equals(o.getKind()) && "RENT".equals(o.getListingType()) && o.getSuburb() != null, 5, cov));
        out.add(question("top_supply", "Which property categories have the highest supply?", obs,
                o -> Set.of("TOP_SUPPLY_CATEGORY", "CATEGORY_OVERSUPPLIED").contains(o.getKind()), 5, cov));
        out.add(question("demand_areas", "Which areas have increasing property demand?", obs,
                o -> demandUp.test(o) && o.getCity() != null, 6, cov));
        out.add(question("prices_up", "Where are asking prices increasing?", obs, o -> "ASKING_PRICE_UP".equals(o.getKind()), 6, cov));
        out.add(question("prices_down", "Where are asking prices declining?", obs, o -> "ASKING_PRICE_DOWN".equals(o.getKind()), 6, cov));
        out.add(question("yields", "What is happening to rental yields?", obs, o -> o.getKind().startsWith("YIELD_"), 6, cov));
        out.add(question("underrepresented", "Which property categories are underrepresented?", obs,
                o -> "CATEGORY_UNDERSUPPLIED".equals(o.getKind()), 5, cov));
        out.add(question("attention", "Which market segments deserve more attention?", obs,
                o -> "CATEGORY_UNDERSUPPLIED".equals(o.getKind()) || Set.of("ACTIVE_LISTINGS_DOWN", "NEW_LISTINGS_DOWN").contains(o.getKind())
                        || (demandUp.test(o) && o.getCity() != null), 6, cov));
        Question opp = question("opportunities", "What opportunities should management investigate?", obs,
                o -> "CATEGORY_UNDERSUPPLIED".equals(o.getKind()) || ("ENQUIRIES_UP".equals(o.getKind()) && o.getCity() != null), 6, cov);
        boolean aiOpportunities = aiView.items().stream().anyMatch(i -> "OPPORTUNITY".equals(i.section()));
        if (aiOpportunities) {
            opp = new Question(opp.key(), opp.question(), opp.observationIds(),
                    (opp.answer() == null ? "" : opp.answer() + " ") + "See the opportunities in the AI interpretation.");
        }
        out.add(opp);
        return out;
    }

    private static Question question(String key, String text, List<MarketObservation> obs, Predicate<MarketObservation> match, int max,
                                     ObservationCoverage cov) {
        List<Long> ids = obs.stream().filter(match).sorted(strongestFirst()).limit(max).map(MarketObservation::getId).toList();
        return new Question(key, text, ids, ids.isEmpty() ? noConclusion(cov) : null);
    }

    static String noConclusion(ObservationCoverage cov) {
        if (cov == null || cov.getPeriodStart() == null) {
            return "No reliable conclusion can be drawn yet: no completed period has been analysed.";
        }
        if (cov.getComparisonStart() == null) {
            return "No reliable conclusion can be drawn yet: there is no earlier period to compare with.";
        }
        if (cov.getTested() == 0) {
            return "No reliable conclusion can be drawn: there was not enough data to test (" + cov.getBelowMinimum()
                    + " comparison(s) below the minimum sample, " + cov.getHistoryIncomplete() + " with history that started part-way).";
        }
        return "No reliable conclusion can be drawn: " + cov.getTested() + " comparison(s) were tested and none of the relevant ones "
                + "showed a significant, meaningful change.";
    }

    private static Comparator<MarketObservation> strongestFirst() {
        return Comparator.comparing((MarketObservation o) -> o.getQValue() == null ? 1.0 : o.getQValue())
                .thenComparing(o -> o.getChangePct() == null ? 0 : -Math.abs(o.getChangePct().doubleValue()))
                .thenComparing(MarketObservation::getId);
    }

    static ObservationView view(MarketObservation o) {
        String location = o.getSuburb() != null ? o.getSuburb() + ", " + o.getCity() : o.getCity() != null ? o.getCity() : "All areas";
        return new ObservationView(o.getId(), o.getKind(), o.getMetricKey(), o.getDomain(), o.getMethod(), o.getGrain(),
                o.getPeriodStart(), o.getPeriodEnd(), o.getComparisonStart(), o.getComparisonEnd(), o.getListingType(), o.getCity(),
                o.getSuburb(), o.getCategory(), location, o.getCurrentValue(), o.getPreviousValue(), o.getChangePct(),
                o.getSampleCurrent(), o.getSamplePrevious(), o.getStatistic(), o.getPValue(), o.getQValue(), o.getConfidence(),
                o.getStatement(), o.getCurrency(), o.getFirstSeenAt());
    }
}
