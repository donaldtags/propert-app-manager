package com.example.estatex.payment.gateway;

import com.example.estatex.payment.Payment;
import java.util.Map;

/** One real payment provider (Paynow, Stripe, ...). {@link com.example.estatex.payment.PaymentService}
 * resolves the right implementation by {@link #id()} and never talks to a provider SDK directly. */
public interface PaymentGateway {
    /** Short lowercase id used as {@code Payment.provider} and in the checkout/webhook URLs, e.g. "paynow". */
    String id();

    CheckoutSession initiateCheckout(Payment payment, String returnUrl);

    /** Actively asks the provider for the current status. Works without a publicly reachable
     * webhook URL, which is what makes local/dev checkouts confirmable at all. */
    GatewayStatus checkStatus(Payment payment);

    /** Production hardening: an inbound callback from the provider's own servers. Return {@code null}
     * if the payload can't be attributed to a payment (caller then does nothing). */
    WebhookOutcome handleWebhook(String rawBody, Map<String, String> headers);
}
