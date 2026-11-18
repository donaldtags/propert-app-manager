package com.example.estatex.listing;

/**
 * DRAFT -> AWAITING_SIGN_OFF (owner signed, document attached) -> ACTIVE (PREA signed off).
 * ACTIVE ends as EXPIRED (expiry date passed), CANCELLED, or SUPERSEDED (a newer mandate was
 * signed off for the same listing).
 */
public enum MandateStatus {
    DRAFT,
    AWAITING_SIGN_OFF,
    ACTIVE,
    EXPIRED,
    CANCELLED,
    SUPERSEDED;

    public boolean isOpen() {
        return this == DRAFT || this == AWAITING_SIGN_OFF || this == ACTIVE;
    }
}
