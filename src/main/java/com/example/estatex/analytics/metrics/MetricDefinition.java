package com.example.estatex.analytics.metrics;

/**
 * Every figure the analytics engine publishes, with its plain-English definition. Change a
 * definition → bump its version, so stored values say which rule produced them.
 * Full rules (inclusion, missing data, outliers) are in docs/admin-analytics-plan.md §4.
 */
public enum MetricDefinition {
    ACTIVE_LISTINGS(1, Kind.COUNT, "Listings live on the market at the end of the period (status Available).",
            "property_events"),
    NEW_LISTINGS(1, Kind.COUNT, "Listings that went on the market for the first time during the period. "
            + "Listings already live when history began are never counted as new.", "property_events"),
    WITHDRAWN_LISTINGS(1, Kind.COUNT, "Listings the owner or agent took off the market during the period.", "property_events"),
    EXPIRED_LISTINGS(1, Kind.COUNT, "Listings taken off the market because their mandate ended.", "property_events"),
    UNDER_OFFER(1, Kind.COUNT, "Listings that moved to Under offer (an offer was accepted).", "property_events"),
    REPORTED_SOLD(1, Kind.COUNT, "Listings the owner or agent marked as sold. Not independently verified.", "property_events"),
    PRICE_REDUCTIONS(1, Kind.DISTRIBUTION, "Price cuts during the period. Value = number of cuts; the median is "
            + "the typical cut in percent.", "property_events"),
    PRICE_INCREASES(1, Kind.COUNT, "Asking-price increases during the period.", "property_events"),
    ASKING_PRICE(1, Kind.DISTRIBUTION, "Asking price of listings that were live at any point in the period, using "
            + "each listing's latest price. USD only; listings with no price are excluded and counted.", "property_events"),
    ASKING_PRICE_PER_M2(1, Kind.DISTRIBUTION, "Asking price divided by floor area, for live listings that have a "
            + "floor area. Coverage = share of live listings with a floor area.", "property_events"),
    RENTAL_AGREEMENTS_SIGNED(1, Kind.COUNT, "Leases signed during the period.", "leases"),
    CONTRACTED_RENT(1, Kind.DISTRIBUTION, "Monthly rent agreed in leases signed during the period (USD). "
            + "This is agreed rent, not asking rent.", "leases"),
    SALES_REPORTED(1, Kind.COUNT, "Sales reported by owners or agents during the period, whatever their "
            + "verification status (rejected ones excluded).", "sale_transactions"),
    VERIFIED_SALES(1, Kind.DISTRIBUTION, "Sales completed in the period and verified by EstateX staff from "
            + "conveyancer or Deeds Registry evidence (USD). Value = number of sales; sum = total value.", "sale_transactions"),
    LISTING_VIEWS(1, Kind.COUNT, "Listing page views by distinct visitors (once per visitor per listing per hour; "
            + "owners and crawlers excluded). Measures interest, not confirmed demand.", "marketplace_events"),
    SEARCHES(1, Kind.COUNT, "Searches run by visitors (repeats within 10 minutes count once). Measures interest, "
            + "not confirmed demand.", "marketplace_events"),
    SEARCHES_NO_RESULTS(1, Kind.COUNT, "Searches that found no listings: a sign of unmet interest.", "marketplace_events"),
    SAVES(1, Kind.COUNT, "Listings saved by signed-in users.", "marketplace_events"),
    ENQUIRIES(1, Kind.COUNT, "Enquiries received about listings (website and agency enquiries).",
            "enquiries, property_inquiries"),
    VIEWING_REQUESTS(1, Kind.COUNT, "Requests to view a property.", "viewing_requests"),
    RENTAL_APPLICATIONS(1, Kind.COUNT, "Rental applications submitted.", "rental_applications"),
    OFFERS(1, Kind.COUNT, "Purchase offers recorded.", "offers");

    public enum Kind { COUNT, DISTRIBUTION }

    private final int version;
    private final Kind kind;
    private final String description;
    private final String source;

    MetricDefinition(int version, Kind kind, String description, String source) {
        this.version = version;
        this.kind = kind;
        this.description = description;
        this.source = source;
    }

    public int version() {
        return version;
    }

    public Kind kind() {
        return kind;
    }

    public String description() {
        return description;
    }

    public String source() {
        return source;
    }

    /** Below this many observations a median is shown but flagged as unreliable. */
    public static final int MIN_RELIABLE_SAMPLE = 5;
}
