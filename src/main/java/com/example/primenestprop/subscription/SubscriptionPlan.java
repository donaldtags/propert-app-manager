package com.example.primenestprop.subscription;

/** Landlord/agent subscription tiers. ENTERPRISE is intentionally excluded from self-serve
 * signup - the PRD prices it as "custom", which implies a sales conversation, not a checkout flow. */
public enum SubscriptionPlan {
    STARTER, GROWTH, PROFESSIONAL
}