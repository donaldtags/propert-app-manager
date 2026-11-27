package com.example.estatex.analytics.market;

import com.example.estatex.analytics.market.MarketDashboardService.Bucket;
import com.example.estatex.analytics.metrics.Grain;
import com.example.estatex.common.BusinessTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Revenue analytics (docs/admin-analytics-plan.md §4.4 and Phase F). Four things are kept apart
 * and never added together:
 * <ul>
 *   <li><b>Cash collected</b>: successful payments for EstateX's own products (subscriptions,
 *       featured listings), by payment date. Client money (rent, deposits, escrow, bookings,
 *       wallet top-ups, investments) is never revenue. Renewals the system marked paid without
 *       collecting money are excluded and reported separately.</li>
 *   <li><b>Recognised revenue</b>: the same payments spread over what they pay for: a subscription
 *       over its 30 days, a featured listing over its featured period.</li>
 *   <li><b>Outstanding</b>: what past-due subscribers owe (an estimate: there are no invoices).</li>
 *   <li><b>Estimated</b>: forward-looking figures (MRR run-rate, ARR, LTV, commission share).</li>
 * </ul>
 */
@Service
public class RevenueService {
    static final int SUBSCRIPTION_DAYS = 30;
    static final String COMMISSION_SHARE_KEY = "revenue.commission_share_percent";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final List<String> CATEGORY_ORDER = List.of("DEVELOPER", "AGENT", "LANDLORD", "PRIVATE", "DIASPORA", "INVESTOR");

    private final JdbcTemplate jdbc;
    private final BusinessTime time;
    private final AnalyticsSettingRepository settings;

    RevenueService(JdbcTemplate jdbc, BusinessTime time, AnalyticsSettingRepository settings) {
        this.jdbc = jdbc;
        this.time = time;
        this.settings = settings;
    }

    public record Kpi(String key, String group, String label, String help, String unit, BigDecimal value, String note) {
    }

    public record Point(Instant start, Instant end, String label, boolean complete, Map<String, BigDecimal> values) {
    }

    public record Slice(String key, String label, BigDecimal cash, BigDecimal recognised, int payments) {
    }

    public record Spend(Long id, LocalDate month, String channel, BigDecimal amount, String currency, String note) {
    }

    public record Revenue(LocalDate from, LocalDate to, String grain, Instant subscriptionHistoryStartsAt, List<Kpi> kpis,
                          List<Point> series, List<Slice> byProduct, List<Slice> byCategory, List<Slice> byLocation,
                          List<Spend> spend, BigDecimal commissionSharePercent, List<String> notes) {
    }

    record Pay(long id, BigDecimal amount, String currency, String purpose, String status, String provider, Instant paidAt,
               Instant createdAt, String metadata, Long payerId, Long propertyId, String description) {
        boolean revenueProduct() {
            return "SUBSCRIPTION".equals(purpose) || "FEATURED_LISTING".equals(purpose);
        }

        /** Marked paid by the system with no money moving (the renewal sweep). */
        boolean selfConfirmed() {
            return "platform".equalsIgnoreCase(provider);
        }
    }

    public Revenue revenue(LocalDate from, LocalDate to, Grain grain) {
        ZoneId zone = time.zone();
        Instant now = time.clock().instant();
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant endRaw = to.plusDays(1).atStartOfDay(zone).toInstant();
        Instant end = endRaw.isAfter(now) ? now : endRaw;
        List<Bucket> buckets = MarketDashboardService.buckets(grain, start, end, zone, now);
        List<String> notes = new ArrayList<>();
        int featuredDays = featuredDays();

        // Payments that can affect the range (recognition reaches back up to a year).
        List<Pay> pays = jdbc.query("select id, amount, currency, purpose_type, status, provider, paid_at, created_at, metadata, "
                        + "payer_id, property_id, purpose from payments where (paid_at >= ? and paid_at < ?) or (created_at >= ? and created_at < ?)",
                (rs, i) -> new Pay(rs.getLong("id"), rs.getBigDecimal("amount"), rs.getString("currency"), rs.getString("purpose_type"),
                        rs.getString("status"), rs.getString("provider"),
                        rs.getTimestamp("paid_at") == null ? null : rs.getTimestamp("paid_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant(), rs.getString("metadata"),
                        rs.getObject("payer_id", Long.class), rs.getObject("property_id", Long.class), rs.getString("purpose")),
                Timestamp.from(start.minus(Duration.ofDays(366))), Timestamp.from(end), Timestamp.from(start), Timestamp.from(end));
        Map<Long, String> category = categories();
        Map<Long, String> userCity = new HashMap<>();
        jdbc.query("select id, city from users", rs -> { userCity.put(rs.getLong(1), rs.getString(2)); });
        Map<Long, String> propertyCity = new HashMap<>();
        jdbc.query("select id, city from properties", rs -> { propertyCity.put(rs.getLong(1), rs.getString(2)); });

        Map<String, SliceAcc> byProduct = new LinkedHashMap<>();
        Map<String, SliceAcc> byCategory = new LinkedHashMap<>();
        Map<String, SliceAcc> byLocation = new LinkedHashMap<>();
        BigDecimal cash = BigDecimal.ZERO, recognised = BigDecimal.ZERO, deferred = BigDecimal.ZERO, refunds = BigDecimal.ZERO,
                uncollectedValue = BigDecimal.ZERO;
        int failed = 0, uncollected = 0, uncollectedListingFees = 0, uncollectedRenewals = 0, nonUsd = 0;
        for (Pay p : pays) {
            boolean inRangeCreated = !p.createdAt().isBefore(start) && p.createdAt().isBefore(end);
            if (p.selfConfirmed() && "SUCCESSFUL".equals(p.status())) {
                // Listing fees and subscription renewals the system marks paid with no money moving.
                if (inRangeCreated) {
                    uncollected++;
                    uncollectedValue = uncollectedValue.add(p.amount());
                    if (p.description() != null && p.description().startsWith("Listing fee")) uncollectedListingFees++;
                    else uncollectedRenewals++;
                    bucket(buckets, p.createdAt()).add("uncollectedCharges", BigDecimal.ONE);
                }
                continue;
            }
            if (!p.revenueProduct()) continue;
            if ("FAILED".equals(p.status()) && inRangeCreated) {
                failed++;
                bucket(buckets, p.createdAt()).add("failedPayments", BigDecimal.ONE);
                continue;
            }
            if (p.paidAt() == null) continue;
            if (!"USD".equalsIgnoreCase(p.currency())) {
                nonUsd++;
                continue;
            }
            if ("REFUNDED".equals(p.status())) {
                if (!p.paidAt().isBefore(start) && p.paidAt().isBefore(end)) refunds = refunds.add(p.amount());
                continue;
            }
            if (!"SUCCESSFUL".equals(p.status())) continue;
            String productKey = "SUBSCRIPTION".equals(p.purpose()) ? "SUBSCRIPTION:" + plan(p.metadata()) : "FEATURED_LISTING";
            String productLabel = "SUBSCRIPTION".equals(p.purpose()) ? "Subscriptions: " + titleCase(plan(p.metadata())) : "Featured listings";
            String cat = category.getOrDefault(p.payerId(), "OTHER");
            String city = "FEATURED_LISTING".equals(p.purpose()) ? propertyCity.get(p.propertyId()) : userCity.get(p.payerId());
            String place = city == null || city.isBlank() ? "Not set" : city.trim();

            int days = "SUBSCRIPTION".equals(p.purpose()) ? SUBSCRIPTION_DAYS : featuredDays(p.metadata(), featuredDays);
            Instant serviceEnd = p.paidAt().plus(Duration.ofDays(days));
            boolean paidInRange = !p.paidAt().isBefore(start) && p.paidAt().isBefore(end);
            if (paidInRange) {
                cash = cash.add(p.amount());
                Bucket b = bucket(buckets, p.paidAt());
                b.add("cash", p.amount());
                b.add("SUBSCRIPTION".equals(p.purpose()) ? "cashSubscriptions" : "cashFeatured", p.amount());
            }
            // Recognised in each period: the share of the service period that falls inside it.
            BigDecimal recInRange = BigDecimal.ZERO;
            for (Bucket b : buckets) {
                BigDecimal share = share(p.amount(), p.paidAt(), serviceEnd, b.start, b.end);
                if (share.signum() > 0) {
                    b.add("recognised", share);
                    b.add("SUBSCRIPTION".equals(p.purpose()) ? "recognisedSubscriptions" : "recognisedFeatured", share);
                    recInRange = recInRange.add(share);
                }
            }
            recognised = recognised.add(recInRange);
            if (p.paidAt().isBefore(end)) {
                deferred = deferred.add(p.amount().subtract(share(p.amount(), p.paidAt(), serviceEnd, p.paidAt(), end)));
            }
            if (paidInRange || recInRange.signum() > 0) {
                BigDecimal c = paidInRange ? p.amount() : BigDecimal.ZERO;
                slice(byProduct, productKey, productLabel).add(c, recInRange, paidInRange);
                slice(byCategory, cat, categoryLabel(cat)).add(c, recInRange, paidInRange);
                slice(byLocation, place.toLowerCase(), place).add(c, recInRange, paidInRange);
            }
        }

        // Subscriptions: MRR, paying subscribers and movements, replayed from subscription history.
        Instant historyStart = jdbc.query("select started_at from analytics_capture_starts where source = 'subscription_events'",
                rs -> rs.next() ? rs.getTimestamp(1).toInstant() : null);
        Subs subs = subscriptions(buckets, start, end, historyStart);

        // Marketing spend, spread evenly over the days of its month.
        LocalDate firstMonth = from.withDayOfMonth(1);
        List<Spend> spend = jdbc.query("select id, spend_month, channel, amount, currency, note from marketing_spend "
                        + "where spend_month >= ? and spend_month <= ? order by spend_month, channel",
                (rs, i) -> new Spend(rs.getLong("id"), rs.getDate("spend_month").toLocalDate(), rs.getString("channel"), rs.getBigDecimal("amount"),
                        rs.getString("currency"), rs.getString("note")),
                Date.valueOf(firstMonth), Date.valueOf(to));
        BigDecimal spendInRange = BigDecimal.ZERO;
        for (Spend s : spend) {
            if (!"USD".equalsIgnoreCase(s.currency())) continue;
            Instant ms = s.month().atStartOfDay(zone).toInstant();
            Instant me = s.month().plusMonths(1).atStartOfDay(zone).toInstant();
            for (Bucket b : buckets) {
                BigDecimal part = share(s.amount(), ms, me, b.start, b.end);
                if (part.signum() > 0) {
                    b.add("marketingSpend", part);
                    spendInRange = spendInRange.add(part);
                }
            }
        }
        BigDecimal referralRewards = jdbc.query("select coalesce(sum(reward_cents), 0) from referrals where rewarded_at >= ? and rewarded_at < ? "
                + "and status in ('REWARDED', 'REVERSAL_PENDING')", rs -> rs.next() ? BigDecimal.valueOf(rs.getLong(1), 2) : BigDecimal.ZERO,
                Timestamp.from(start), Timestamp.from(end));
        BigDecimal referralDiscounts = jdbc.query("select coalesce(sum(discount_cents), 0) from referrals where qualified_at >= ? and qualified_at < ? "
                + "and status <> 'BLOCKED'", rs -> rs.next() ? BigDecimal.valueOf(rs.getLong(1), 2) : BigDecimal.ZERO,
                Timestamp.from(start), Timestamp.from(end));

        // Commission share (off unless an admin sets a rate): an estimate, since agencies aren't billed for it.
        BigDecimal sharePct = commissionSharePercent();
        BigDecimal commissionReceived = jdbc.query("select coalesce(sum(amount_cents), 0) from commissions where status = 'RECEIVED' "
                + "and received_on >= ? and received_on <= ? and currency = 'USD'", rs -> rs.next() ? BigDecimal.valueOf(rs.getLong(1), 2)
                : BigDecimal.ZERO, Date.valueOf(from), Date.valueOf(to));
        BigDecimal commissionShare = commissionReceived.multiply(sharePct).divide(HUNDRED, 2, RoundingMode.HALF_UP);

        // KPIs.
        List<Kpi> kpis = new ArrayList<>();
        kpis.add(new Kpi("cash", "CASH", "Cash collected", "Money EstateX received for subscriptions and featured listings in the range, "
                + "by payment date.", "USD", scale(cash), null));
        kpis.add(new Kpi("refunds", "CASH", "Refunds", "Revenue payments marked refunded.", "USD", scale(refunds),
                "EstateX has no refund flow yet, so this stays at $0 until one exists."));
        kpis.add(new Kpi("failed", "CASH", "Failed payments", "Subscription and featured-listing payments that failed.", "COUNT",
                BigDecimal.valueOf(failed), null));
        kpis.add(new Kpi("recognised", "RECOGNISED", "Recognised revenue", "Revenue earned in the range: each payment spread over what "
                + "it pays for (a subscription over 30 days, a featured listing over its featured period).", "USD", scale(recognised), null));
        kpis.add(new Kpi("deferred", "RECOGNISED", "Deferred revenue", "Paid but not yet earned at the end of the range (service still "
                + "to be delivered).", "USD", scale(deferred), null));
        kpis.add(new Kpi("referral_discounts", "RECOGNISED", "Referral discounts", "Discounts given to referred friends on their first "
                + "payment. Already taken off cash and revenue.", "USD", scale(referralDiscounts), null));
        kpis.add(new Kpi("past_due", "OUTSTANDING", "Owed by past-due subscribers", "Monthly price of subscriptions whose renewal hasn't "
                + "been paid. An estimate: EstateX doesn't issue invoices.", "USD", subs.pastDueMrrEnd, subs.pastDueEnd + " subscriber(s)"));
        kpis.add(new Kpi("uncollected", "OUTSTANDING", "Charges recorded without payment", "Listing fees and subscription renewals "
                + "the system marks as paid although no money is collected (there's no stored card to charge). Excluded from cash.",
                "USD", scale(uncollectedValue), uncollectedListingFees + " listing fee(s), " + uncollectedRenewals + " subscription renewal(s)"));
        kpis.add(new Kpi("mrr", "ESTIMATED", "Monthly recurring revenue", "Monthly price of active paid subscriptions at the end of the "
                + "range. What subscriptions are worth per month, not cash received.", "USD", subs.mrrEnd, subs.payingEnd + " paying"));
        kpis.add(new Kpi("arr", "ESTIMATED", "Annual run-rate", "MRR × 12. An estimate assuming nobody joins or leaves.", "USD",
                subs.mrrEnd == null ? null : subs.mrrEnd.multiply(BigDecimal.valueOf(12)), null));
        BigDecimal arpa = subs.mrrEnd != null && subs.payingEnd > 0
                ? subs.mrrEnd.divide(BigDecimal.valueOf(subs.payingEnd), 2, RoundingMode.HALF_UP) : null;
        kpis.add(new Kpi("arpa", "ESTIMATED", "Average revenue per paying subscriber", "MRR ÷ paying subscribers.", "USD", arpa, null));
        kpis.add(new Kpi("churn", "UNIT", "Subscriber churn", "Paying subscribers who stopped paying in the range, as a share of those "
                + "paying at the start plus those who started.", "PERCENT", subs.churnPercent,
                subs.churned + " of " + subs.exposed + " subscribers" + (subs.historyComplete ? "" : "; history started part-way through")));
        BigDecimal monthlyChurn = subs.monthlyChurn;
        long historyDays = historyStart == null ? 0 : Duration.between(historyStart, end).toDays();
        BigDecimal ltv = arpa != null && monthlyChurn != null && monthlyChurn.signum() > 0 && historyDays >= 60
                ? arpa.multiply(HUNDRED).divide(monthlyChurn, 2, RoundingMode.HALF_UP) : null;
        kpis.add(new Kpi("ltv", "UNIT", "Lifetime value (estimate)", "Average revenue per subscriber ÷ monthly churn: what a subscriber "
                + "is worth over their expected lifetime.", "USD", ltv,
                ltv != null ? null : historyDays < 60 ? "Needs at least 60 days of subscription history."
                        : "No churn yet in this range, so lifetime can't be estimated."));
        BigDecimal acquisition = spendInRange.add(referralRewards);
        BigDecimal cac = subs.newPaying > 0 ? acquisition.divide(BigDecimal.valueOf(subs.newPaying), 2, RoundingMode.HALF_UP) : null;
        kpis.add(new Kpi("cac", "UNIT", "Customer acquisition cost", "(Marketing spend entered + referral rewards) ÷ new paying "
                + "subscribers in the range.", "USD", cac,
                spend.isEmpty() && referralRewards.signum() == 0 ? "No marketing spend entered for this range."
                        : subs.newPaying == 0 ? "No new paying subscribers in the range." : subs.newPaying + " new paying subscriber(s)"));
        kpis.add(new Kpi("payback", "UNIT", "Months to recover acquisition cost", "Acquisition cost ÷ average revenue per subscriber.",
                "MONTHS", cac != null && arpa != null && arpa.signum() > 0 ? cac.divide(arpa, 1, RoundingMode.HALF_UP) : null, null));
        kpis.add(new Kpi("commission_share", "ESTIMATED", "Commission share (estimate)", "The EstateX share of agency commission received "
                + "in the range, at the rate an admin set. Agencies aren't billed for it yet, so it isn't cash.", "USD",
                sharePct.signum() == 0 ? null : commissionShare,
                sharePct.signum() == 0 ? "Not enabled (rate 0%)." : sharePct.stripTrailingZeros().toPlainString() + "% of "
                        + scale(commissionReceived) + " received by agencies"));

        if (uncollected > 0) {
            notes.add(uncollected + " charge(s) in this range (" + uncollectedListingFees + " listing fee(s), " + uncollectedRenewals
                    + " subscription renewal(s)) were marked paid by the system without collecting money (no stored card). They're "
                    + "excluded from cash and revenue; the listings stay live and the subscribers keep their paid plan.");
        }
        if (nonUsd > 0) notes.add(nonUsd + " payment(s) in other currencies are left out of these USD figures.");
        if (historyStart != null && historyStart.isAfter(start)) {
            notes.add("Subscription history starts on " + historyStart.atZone(zone).toLocalDate()
                    + "; MRR and churn before then can't be shown.");
        }
        notes.add("Client money (rent, deposits, escrow, bookings, wallet top-ups, investments) is never counted as revenue.");

        List<Point> series = buckets.stream().map(b -> new Point(b.start, b.end, b.label, b.complete,
                Collections.unmodifiableMap(b.values))).toList();
        return new Revenue(from, to, grain.name(), historyStart, kpis, series, slices(byProduct), slices(byCategory), slices(byLocation),
                spend, sharePct, notes);
    }

    // ---- Subscriptions --------------------------------------------------------------------

    record SubState(String status, BigDecimal price) {
        boolean paying() {
            return "ACTIVE".equals(status) && price != null && price.signum() > 0;
        }
    }

    static final class Subs {
        BigDecimal mrrEnd;
        BigDecimal pastDueMrrEnd = BigDecimal.ZERO;
        int payingEnd;
        int pastDueEnd;
        int newPaying;
        int churned;
        int exposed;
        BigDecimal churnPercent;
        BigDecimal monthlyChurn;
        boolean historyComplete = true;
    }

    private Subs subscriptions(List<Bucket> buckets, Instant start, Instant end, Instant historyStart) {
        record Ev(long sub, Instant at, String status, BigDecimal price) {
        }
        List<Ev> events = jdbc.query("select id, subscription_id, occurred_at, status, monthly_price from subscription_events "
                        + "where occurred_at < ? order by occurred_at, id",
                (rs, i) -> new Ev(rs.getLong("subscription_id"), rs.getTimestamp("occurred_at").toInstant(), rs.getString("status"),
                        rs.getBigDecimal("monthly_price")), Timestamp.from(end));
        Map<Long, SubState> state = new HashMap<>();
        int idx = 0;
        Subs out = new Subs();
        // State at the start of the range.
        while (idx < events.size() && events.get(idx).at().isBefore(start)) {
            Ev e = events.get(idx++);
            state.put(e.sub(), new SubState(e.status(), e.price()));
        }
        int payingAtStart = (int) state.values().stream().filter(SubState::paying).count();
        double payingMonths = 0;
        for (Bucket b : buckets) {
            boolean measurable = historyStart != null && !b.start.isBefore(historyStart);
            if (!measurable) out.historyComplete = false;
            Map<Long, SubState> before = new HashMap<>(state);
            while (idx < events.size() && events.get(idx).at().isBefore(b.end)) {
                Ev e = events.get(idx++);
                state.put(e.sub(), new SubState(e.status(), e.price()));
            }
            BigDecimal mrr = BigDecimal.ZERO;
            int paying = 0;
            for (SubState s : state.values()) {
                if (s.paying()) {
                    mrr = mrr.add(s.price());
                    paying++;
                }
            }
            BigDecimal newMrr = BigDecimal.ZERO, expansion = BigDecimal.ZERO, contraction = BigDecimal.ZERO, churnedMrr = BigDecimal.ZERO;
            int newSubs = 0, churnedSubs = 0;
            for (Map.Entry<Long, SubState> e : state.entrySet()) {
                SubState was = before.get(e.getKey());
                SubState is = e.getValue();
                boolean wasPaying = was != null && was.paying();
                if (!wasPaying && is.paying()) { newMrr = newMrr.add(is.price()); newSubs++; }
                else if (wasPaying && !is.paying()) { churnedMrr = churnedMrr.add(was.price()); churnedSubs++; }
                else if (wasPaying) {
                    int c = is.price().compareTo(was.price());
                    if (c > 0) expansion = expansion.add(is.price().subtract(was.price()));
                    if (c < 0) contraction = contraction.add(was.price().subtract(is.price()));
                }
            }
            if (measurable) {
                b.put("mrr", mrr);
                b.put("payingSubscribers", BigDecimal.valueOf(paying));
                b.put("newMrr", newMrr);
                b.put("expansionMrr", expansion);
                b.put("contractionMrr", contraction);
                b.put("churnedMrr", churnedMrr);
                b.put("newSubscribers", BigDecimal.valueOf(newSubs));
                b.put("churnedSubscribers", BigDecimal.valueOf(churnedSubs));
                long before0 = before.values().stream().filter(SubState::paying).count();
                if (before0 > 0) b.put("churnPercent", BigDecimal.valueOf(churnedSubs).multiply(HUNDRED)
                        .divide(BigDecimal.valueOf(before0), 1, RoundingMode.HALF_UP));
                out.newPaying += newSubs;
                out.churned += churnedSubs;
                double months = Duration.between(b.start, b.end).toHours() / (24.0 * 30.0);
                payingMonths += months * (before0 + paying) / 2.0;
            }
        }
        if (historyStart == null || historyStart.isAfter(end)) {
            out.mrrEnd = null;
            return out;
        }
        out.mrrEnd = BigDecimal.ZERO;
        for (SubState s : state.values()) {
            if (s.paying()) { out.mrrEnd = out.mrrEnd.add(s.price()); out.payingEnd++; }
            if ("PAST_DUE".equals(s.status()) && s.price() != null && s.price().signum() > 0) {
                out.pastDueMrrEnd = out.pastDueMrrEnd.add(s.price());
                out.pastDueEnd++;
            }
        }
        out.exposed = payingAtStart + out.newPaying;
        out.churnPercent = out.exposed == 0 ? null : BigDecimal.valueOf(out.churned).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(out.exposed), 1, RoundingMode.HALF_UP);
        out.monthlyChurn = payingMonths <= 0 ? null : BigDecimal.valueOf(out.churned * 100.0 / payingMonths).setScale(2, RoundingMode.HALF_UP);
        return out;
    }

    // ---- Helpers --------------------------------------------------------------------------

    /** The part of {@code amount} for the overlap of [from, to) with [a, b), pro rata by time. */
    static BigDecimal share(BigDecimal amount, Instant from, Instant to, Instant a, Instant b) {
        Instant s = from.isAfter(a) ? from : a;
        Instant e = to.isBefore(b) ? to : b;
        if (!s.isBefore(e)) return BigDecimal.ZERO;
        long total = Duration.between(from, to).getSeconds();
        if (total <= 0) return BigDecimal.ZERO;
        return amount.multiply(BigDecimal.valueOf(Duration.between(s, e).getSeconds()))
                .divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static Bucket bucket(List<Bucket> buckets, Instant t) {
        Bucket found = buckets.get(0);
        for (Bucket b : buckets) if (!t.isBefore(b.start)) found = b;
        return found;
    }

    static final class SliceAcc {
        final String key;
        final String label;
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal recognised = BigDecimal.ZERO;
        int payments;

        SliceAcc(String key, String label) {
            this.key = key;
            this.label = label;
        }

        void add(BigDecimal c, BigDecimal r, boolean counted) {
            cash = cash.add(c);
            recognised = recognised.add(r);
            if (counted) payments++;
        }
    }

    private static SliceAcc slice(Map<String, SliceAcc> map, String key, String label) {
        return map.computeIfAbsent(key, k -> new SliceAcc(key, label));
    }

    private static List<Slice> slices(Map<String, SliceAcc> map) {
        return map.values().stream().map(a -> new Slice(a.key, a.label, scale(a.cash), scale(a.recognised), a.payments))
                .sorted(Comparator.comparing(Slice::recognised).reversed()).toList();
    }

    private Map<Long, String> categories() {
        Map<Long, String> out = new HashMap<>();
        jdbc.query("select app_user_id, roles from app_user_roles", rs -> {
            long id = rs.getLong(1);
            String role = rs.getString(2);
            String current = out.get(id);
            int rank = CATEGORY_ORDER.indexOf(role);
            if (rank >= 0 && (current == null || rank < CATEGORY_ORDER.indexOf(current))) out.put(id, role);
        });
        return out;
    }

    private static String categoryLabel(String c) {
        return switch (c) {
            case "DEVELOPER" -> "Developers";
            case "AGENT" -> "Agents and agencies";
            case "LANDLORD" -> "Landlords";
            case "PRIVATE" -> "Private owners";
            case "DIASPORA" -> "Diaspora owners";
            case "INVESTOR" -> "Investors";
            default -> "Other";
        };
    }

    static String plan(String metadata) {
        if (metadata != null) {
            for (String part : metadata.split(",")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2 && kv[0].trim().equals("plan")) return kv[1].trim();
            }
        }
        return "UNKNOWN";
    }

    private static String titleCase(String s) {
        return s.isEmpty() ? s : s.charAt(0) + s.substring(1).toLowerCase();
    }

    private int featuredDays() {
        Integer d = jdbc.query("select duration_days from featured_listing_settings", rs -> rs.next() ? rs.getInt(1) : null);
        return d == null || d <= 0 ? 14 : d;
    }

    static int featuredDays(String metadata, int fallback) {
        if (metadata != null) {
            for (String part : metadata.split(",")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2 && kv[0].trim().equals("durationDays")) {
                    try {
                        return Integer.parseInt(kv[1].trim());
                    } catch (NumberFormatException ignored) {
                        return fallback;
                    }
                }
            }
        }
        return fallback;
    }

    public BigDecimal commissionSharePercent() {
        return settings.findById(COMMISSION_SHARE_KEY).map(s -> new BigDecimal(s.getValue())).orElse(BigDecimal.ZERO);
    }

    public BigDecimal setCommissionSharePercent(BigDecimal pct, Long userId) {
        AnalyticsSetting s = settings.findById(COMMISSION_SHARE_KEY).orElseGet(AnalyticsSetting::new);
        s.setKey(COMMISSION_SHARE_KEY);
        s.setValue(pct.setScale(2, RoundingMode.HALF_UP).toPlainString());
        s.setUpdatedByUserId(userId);
        s.setUpdatedAt(time.clock().instant());
        settings.save(s);
        return commissionSharePercent();
    }
}
