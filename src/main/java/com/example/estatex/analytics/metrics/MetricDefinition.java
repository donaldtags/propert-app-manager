package com.example.estatex.analytics.metrics;

/**
 * Every figure the analytics engine publishes, with its plain-English definition. Change a
 * definition → bump its version, so stored values say which rule produced them.
 * Full rules (inclusion, missing data, outliers) are in docs/admin-analytics-plan.md §4.
 *
 * <p>{@link #captureSource()} names the table that only started recording when analytics was
 * introduced. Periods before that table's capture start can't be measured, and are flagged
 * instead of being reported as zero.
 */
public enum MetricDefinition {
    // ---- Market (listing history) ----
    ACTIVE_LISTINGS(1, Kind.COUNT, Domain.MARKET, "property_events",
            "Listings live on the market at the end of the period (status Available)."),
    VERIFIED_LISTINGS(1, Kind.COUNT, Domain.MARKET, "property_events",
            "Live listings that EstateX has verified, at the end of the period."),
    NEW_LISTINGS(1, Kind.COUNT, Domain.MARKET, "property_events",
            "Listings that went on the market for the first time during the period. Listings already live when "
                    + "history began are never counted as new."),
    UPDATED_LISTINGS(1, Kind.COUNT, Domain.MARKET, "property_events",
            "Listings whose price, type, location or details changed during the period."),
    WITHDRAWN_LISTINGS(1, Kind.COUNT, Domain.MARKET, "property_events",
            "Listings the owner or agent took off the market during the period."),
    EXPIRED_LISTINGS(1, Kind.COUNT, Domain.MARKET, "property_events",
            "Listings taken off the market because their mandate ended."),
    UNDER_OFFER(1, Kind.COUNT, Domain.MARKET, "property_events",
            "Listings that moved to Under offer (an offer was accepted)."),
    REPORTED_SOLD(1, Kind.COUNT, Domain.MARKET, "property_events",
            "Listings the owner or agent marked as sold. Not independently verified."),
    PRICE_REDUCTIONS(1, Kind.DISTRIBUTION, Domain.MARKET, "property_events",
            "Price cuts during the period. Value = number of cuts; the median is the typical cut in percent."),
    PRICE_INCREASES(1, Kind.COUNT, Domain.MARKET, "property_events", "Asking-price increases during the period."),
    ASKING_PRICE(1, Kind.DISTRIBUTION, Domain.MARKET, "property_events",
            "Asking price of listings that were live at any point in the period, using each listing's latest price. "
                    + "USD only; listings with no price are excluded and counted."),
    ASKING_PRICE_PER_M2(1, Kind.DISTRIBUTION, Domain.MARKET, "property_events",
            "Asking price divided by floor area, for live listings that have a floor area. Coverage = share of live "
                    + "listings with a floor area."),
    // ---- Market (transactions; source tables hold full history) ----
    RENTAL_AGREEMENTS_SIGNED(1, Kind.COUNT, Domain.MARKET, null, "Leases signed during the period."),
    CONTRACTED_RENT(1, Kind.DISTRIBUTION, Domain.MARKET, null,
            "Monthly rent agreed in leases signed during the period (USD). This is agreed rent, not asking rent."),
    SALES_REPORTED(1, Kind.COUNT, Domain.MARKET, null,
            "Sales reported by owners or agents during the period, whatever their verification status (rejected "
                    + "ones excluded)."),
    VERIFIED_SALES(1, Kind.DISTRIBUTION, Domain.MARKET, null,
            "Sales completed in the period and verified by EstateX staff from conveyancer or Deeds Registry evidence "
                    + "(USD). Value = number of sales; sum = total value."),
    // ---- Demand ----
    LISTING_VIEWS(1, Kind.COUNT, Domain.DEMAND, "marketplace_events",
            "Listing page views by distinct visitors (once per visitor per listing per hour; owners and crawlers "
                    + "excluded). Measures interest, not confirmed demand."),
    SEARCHES(1, Kind.COUNT, Domain.DEMAND, "marketplace_events",
            "Searches run by visitors (repeats within 10 minutes count once). Measures interest, not confirmed demand."),
    SEARCHES_NO_RESULTS(1, Kind.COUNT, Domain.DEMAND, "marketplace_events",
            "Searches that found no listings: a sign of unmet interest."),
    SAVES(1, Kind.COUNT, Domain.DEMAND, "marketplace_events", "Listings saved by signed-in users."),
    ENQUIRIES(1, Kind.COUNT, Domain.DEMAND, null, "Enquiries received about listings (website and agency enquiries)."),
    VIEWING_REQUESTS(1, Kind.COUNT, Domain.DEMAND, null, "Requests to view a property."),
    RENTAL_APPLICATIONS(1, Kind.COUNT, Domain.DEMAND, null, "Rental applications submitted."),
    OFFERS(1, Kind.COUNT, Domain.DEMAND, null, "Purchase offers recorded."),
    // ---- Platform ----
    TOTAL_USERS(1, Kind.COUNT, Domain.PLATFORM, null, "Registered accounts at the end of the period."),
    NEW_REGISTRATIONS(1, Kind.COUNT, Domain.PLATFORM, null, "Accounts created during the period."),
    NEW_AGENTS(1, Kind.COUNT, Domain.PLATFORM, null, "New accounts registered as agents."),
    NEW_LANDLORDS(1, Kind.COUNT, Domain.PLATFORM, null, "New accounts registered as landlords."),
    NEW_DEVELOPERS(1, Kind.COUNT, Domain.PLATFORM, null, "New accounts registered as developers."),
    ACTIVE_USERS(1, Kind.COUNT, Domain.PLATFORM, "user_activity_days",
            "Accounts that used EstateX while signed in at least once during the period."),
    // ---- Finance ----
    PLATFORM_REVENUE(1, Kind.DISTRIBUTION, Domain.FINANCE, null,
            "Cash collected by EstateX for its own products (subscriptions, featured listings), by payment date, USD. "
                    + "Rent, deposits, escrow, bookings, wallet top-ups and investments are client money and never "
                    + "counted. Value = total collected."),
    REVENUE_SUBSCRIPTIONS(1, Kind.DISTRIBUTION, Domain.FINANCE, null, "Cash collected from subscriptions (USD)."),
    REVENUE_FEATURED_LISTINGS(1, Kind.DISTRIBUTION, Domain.FINANCE, null, "Cash collected from featured listings (USD)."),
    PAYMENT_VOLUME(1, Kind.DISTRIBUTION, Domain.FINANCE, null,
            "Client money that passed through EstateX (rent, deposits, escrow, bookings, wallet top-ups, investments), "
                    + "USD. Not EstateX revenue."),
    PAYMENTS_SUCCESSFUL(1, Kind.COUNT, Domain.FINANCE, null, "Payments that succeeded during the period (all purposes)."),
    PAYMENTS_FAILED(1, Kind.COUNT, Domain.FINANCE, null, "Payments started during the period that failed."),
    REFERRAL_SIGNUPS(1, Kind.COUNT, Domain.PLATFORM, null,
            "New accounts that signed up with a friend's referral code (self-referrals excluded)."),
    REFERRAL_DISCOUNTS(1, Kind.DISTRIBUTION, Domain.FINANCE, null,
            "Referral discounts given on first subscription payments that succeeded (USD). Already taken off the "
                    + "revenue figure; shown so the programme's cost is visible."),
    REFERRAL_REWARDS(1, Kind.DISTRIBUTION, Domain.FINANCE, null,
            "Wallet credit paid to referrers (USD), less rewards taken back. A marketing cost, not a refund.");

    public enum Kind { COUNT, DISTRIBUTION }

    /** Who may see the figure: each domain has its own analytics permission. */
    public enum Domain { MARKET, DEMAND, PLATFORM, FINANCE }

    private final int version;
    private final Kind kind;
    private final Domain domain;
    private final String captureSource;
    private final String description;

    MetricDefinition(int version, Kind kind, Domain domain, String captureSource, String description) {
        this.version = version;
        this.kind = kind;
        this.domain = domain;
        this.captureSource = captureSource;
        this.description = description;
    }

    public int version() {
        return version;
    }

    public Kind kind() {
        return kind;
    }

    public Domain domain() {
        return domain;
    }

    /** Null when the source table has always existed (complete history). */
    public String captureSource() {
        return captureSource;
    }

    public String description() {
        return description;
    }

    public String source() {
        return captureSource != null ? captureSource : switch (this) {
            case RENTAL_AGREEMENTS_SIGNED, CONTRACTED_RENT -> "leases";
            case SALES_REPORTED, VERIFIED_SALES -> "sale_transactions";
            case ENQUIRIES -> "enquiries, property_inquiries";
            case VIEWING_REQUESTS -> "viewing_requests";
            case RENTAL_APPLICATIONS -> "rental_applications";
            case OFFERS -> "offers";
            case TOTAL_USERS, NEW_REGISTRATIONS, NEW_AGENTS, NEW_LANDLORDS, NEW_DEVELOPERS -> "users";
            case REFERRAL_SIGNUPS, REFERRAL_DISCOUNTS, REFERRAL_REWARDS -> "referrals";
            default -> "payments";
        };
    }

    /** Below this many observations a median is shown but flagged as unreliable. */
    public static final int MIN_RELIABLE_SAMPLE = 5;
}
