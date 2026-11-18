package com.example.estatex.payment.gateway;

import com.example.estatex.common.ApiException;
import com.example.estatex.payment.Payment;
import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionRetrieveParams;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Stripe Checkout - primarily for international card payments (diaspora users paying in USD
 * from outside Zimbabwe). Test-mode keys are free and instant to create, unlike Paynow's. */
@Component
public class StripeGateway implements PaymentGateway {
    private final String secretKey;
    private final String webhookSecret;

    public StripeGateway(
            @Value("${app.payments.stripe.secret-key:}") String secretKey,
            @Value("${app.payments.stripe.webhook-secret:}") String webhookSecret
    ) {
        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
    }

    @Override
    public String id() {
        return "stripe";
    }

    private StripeClient client() {
        if (secretKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe is not configured yet");
        }
        return new StripeClient(secretKey);
    }

    @Override
    public CheckoutSession initiateCheckout(Payment payment, String returnUrl) {
        try {
            long unitAmount = payment.getAmount().setScale(2, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).longValueExact();
            String label = payment.getPurpose() == null || payment.getPurpose().isBlank() ? "EstateX payment" : payment.getPurpose();
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(returnUrl + (returnUrl.contains("?") ? "&" : "?") + "session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(returnUrl + (returnUrl.contains("?") ? "&" : "?") + "cancelled=1")
                    .putMetadata("paymentId", String.valueOf(payment.getId()))
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(payment.getCurrency() == null ? "usd" : payment.getCurrency().toLowerCase())
                                    .setUnitAmount(unitAmount)
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName(label)
                                            .build())
                                    .build())
                            .build())
                    .build();
            Session session = client().checkout().sessions().create(params);
            return new CheckoutSession(session.getUrl(), session.getId(), null);
        } catch (StripeException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Stripe rejected the payment: " + e.getMessage());
        }
    }

    @Override
    public GatewayStatus checkStatus(Payment payment) {
        if (payment.getProviderReference() == null || payment.getProviderReference().isBlank()) {
            return GatewayStatus.PENDING;
        }
        try {
            Session session = client().checkout().sessions()
                    .retrieve(payment.getProviderReference(), SessionRetrieveParams.builder().build());
            if ("paid".equals(session.getPaymentStatus())) {
                return GatewayStatus.PAID;
            }
            if ("expired".equals(session.getStatus())) {
                return GatewayStatus.CANCELLED;
            }
            return GatewayStatus.PENDING;
        } catch (StripeException e) {
            return GatewayStatus.PENDING;
        }
    }

    @Override
    public WebhookOutcome handleWebhook(String rawBody, Map<String, String> headers) {
        if (webhookSecret.isBlank()) {
            return null;
        }
        String signature = headers.get("stripe-signature");
        try {
            Event event = Webhook.constructEvent(rawBody, signature, webhookSecret);
            StripeObject object = event.getDataObjectDeserializer().getObject().orElse(null);
            if (object instanceof Session session) {
                return new WebhookOutcome(session.getId());
            }
            return null;
        } catch (SignatureVerificationException e) {
            return null;
        }
    }
}
