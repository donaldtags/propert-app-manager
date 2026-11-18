package com.example.estatex.payment.gateway;

/** @param pollUrl gateway-specific status-check handle (Paynow's poll URL); null for gateways
 *                 (like Stripe) whose {@link PaymentGateway#checkStatus} needs only the stored
 *                 {@code providerReference}. */
public record CheckoutSession(String redirectUrl, String providerReference, String pollUrl) {
}
