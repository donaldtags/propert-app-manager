package com.example.estatex.property;

public enum PropertyBillingStatus {
    /** The first month's fee hasn't been paid yet; the listing stays unpublished until it is. */
    AWAITING_PAYMENT,
    ACTIVE,
    /** A renewal is unpaid. The listing stays live for the grace period, then is deactivated. */
    PAST_DUE
}