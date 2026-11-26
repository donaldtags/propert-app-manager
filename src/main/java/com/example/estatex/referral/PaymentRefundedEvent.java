package com.example.estatex.referral;

/**
 * A payment was refunded. Nothing publishes this yet (EstateX has no in-app refund flow); when
 * one is added it must publish this so referral rewards paid for that payment are taken back.
 * Until then staff reverse rewards from the admin Referrals panel.
 */
public record PaymentRefundedEvent(Long paymentId) {
}
