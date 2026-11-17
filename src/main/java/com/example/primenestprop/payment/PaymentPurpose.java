package com.example.primenestprop.payment;

/** What a {@link Payment} is for, driving which listener applies its post-success effect
 * when {@link PaymentSucceededEvent} fires. */
public enum PaymentPurpose {
    RENT,
    ESCROW_FUND,
    SUBSCRIPTION,
    FEATURED_LISTING,
    INVESTMENT,
    OTHER
}
