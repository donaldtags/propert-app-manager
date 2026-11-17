package com.example.primenestprop.payment.gateway;

/** Identifies which payment a webhook callback is about. Deliberately carries no asserted
 * status - the caller always re-derives status via {@link PaymentGateway#checkStatus}, which
 * goes back to the provider's own authoritative API instead of trusting the callback body. */
public record WebhookOutcome(String providerReference) {
}
