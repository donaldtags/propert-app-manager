package com.example.estatex.stay;

/**
 * PENDING          guest asked, host hasn't answered (request-to-book listings)
 * AWAITING_PAYMENT host accepted, or the listing is instant-book; dates are held while the guest pays
 * CONFIRMED        paid online or marked paid by the host
 * COMPLETED        the stay has ended
 * DECLINED / CANCELLED / EXPIRED  dates released
 */
public enum StayBookingStatus {
    PENDING,
    AWAITING_PAYMENT,
    CONFIRMED,
    COMPLETED,
    DECLINED,
    CANCELLED,
    EXPIRED;

    /** Statuses that keep the dates off the calendar. */
    public static final java.util.List<String> HOLDING = java.util.List.of(PENDING.name(), AWAITING_PAYMENT.name(), CONFIRMED.name());

    public boolean holdsDates() {
        return this == PENDING || this == AWAITING_PAYMENT || this == CONFIRMED;
    }
}
