package com.example.estatex.payment;

/** What a {@link Payment} is for, driving which listener applies its post-success effect
 * when {@link PaymentSucceededEvent} fires. */
public enum PaymentPurpose {
    RENT,
    ESCROW_FUND,
    SUBSCRIPTION,
    FEATURED_LISTING,
    INVESTMENT,
    /** Short-stay booking paid online; metadata "stayBookingId=<id>". */
    STAY_BOOKING,
    /** Money added to the payer's own EstateX wallet; credited only once a gateway confirms it. */
    WALLET_TOP_UP,
    OTHER
}
