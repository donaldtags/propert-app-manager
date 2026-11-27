package com.example.estatex.user;

/**
 * Fine-grained capabilities a user can hold, independent of which role(s) granted them.
 * Authorization checks should test a permission, not a specific {@link UserRole}, so that
 * which roles carry a capability can change without touching call sites.
 */
public enum Permission {
    PROPERTY_CREATE,
    PROPERTY_VERIFY,
    TENANT_APPLY,
    INVESTMENT_CREATE,
    INVESTMENT_MANAGE,
    VENDOR_SERVICE_PROVIDE,
    VENDOR_MANAGE,
    USER_VERIFY,
    USER_MANAGE,
    KYC_REVIEW,
    FEATURED_LISTINGS_MANAGE,
    SUBSCRIPTION_PLANS_MANAGE,
    NEIGHBOURHOOD_MANAGE,
    ESCROW_RELEASE,
    ESCROW_ADMIN_VIEW,
    DASHBOARD_ADMIN_VIEW,
    FRAUD_SIGNALS_VIEW,
    MESSAGES_ADMIN_VIEW,
    ORGANISATION_MANAGE,
    LOCATION_MANAGE,
    ADMIN_OVERRIDE,
    /** Market analytics: listings, prices, rentals, sales, map. */
    ANALYTICS_MARKET_VIEW,
    /** Demand analytics: views, searches, enquiries, funnels. */
    ANALYTICS_DEMAND_VIEW,
    /** Revenue and payment analytics. Sensitive: every access is audited. */
    ANALYTICS_FINANCE_VIEW,
    /** Engine status, data quality, verification queues. */
    ANALYTICS_OPS_VIEW,
    /** Thresholds, rebuilds and manual runs. */
    ANALYTICS_CONFIGURE,
    REPORTS_EXPORT,
    REPORTS_SCHEDULE
}
