package com.example.estatex.payment.gateway;

import com.example.estatex.common.ApiException;
import com.example.estatex.payment.Payment;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import zw.co.paynow.constants.TransactionStatus;
import zw.co.paynow.core.Paynow;
import zw.co.paynow.responses.StatusResponse;
import zw.co.paynow.responses.WebInitResponse;

/**
 * Zimbabwe's Paynow aggregator — one integration covers EcoCash, OneMoney, Zimswitch/bank
 * transfer, and card, since Paynow's own hosted page lets the shopper pick. Built on the
 * official {@code zw.co.paynow:java-sdk} rather than hand-rolling the hash protocol.
 *
 * Status is primarily confirmed by polling (works without a public webhook URL, which matters
 * for local/dev). The webhook path never trusts the posted fields directly - it re-polls
 * through the SDK, which independently verifies Paynow's response hash against our integration
 * key, so a forged POST to the webhook can't fake a paid status.
 */
@Component
public class PaynowGateway implements PaymentGateway {
    private final String integrationId;
    private final String integrationKey;
    private final String backendBaseUrl;

    public PaynowGateway(
            @Value("${app.payments.paynow.integration-id:}") String integrationId,
            @Value("${app.payments.paynow.integration-key:}") String integrationKey,
            @Value("${app.public-base-url:http://localhost:8081}") String backendBaseUrl
    ) {
        this.integrationId = integrationId;
        this.integrationKey = integrationKey;
        this.backendBaseUrl = backendBaseUrl.replaceAll("/+$", "");
    }

    @Override
    public String id() {
        return "paynow";
    }

    private Paynow client() {
        if (integrationId.isBlank() || integrationKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Paynow is not configured yet");
        }
        Paynow paynow = new Paynow(integrationId, integrationKey);
        paynow.setResultUrl(backendBaseUrl + "/api/v1/payments/webhooks/paynow");
        return paynow;
    }

    @Override
    public CheckoutSession initiateCheckout(Payment payment, String returnUrl) {
        Paynow paynow = client();
        paynow.setReturnUrl(returnUrl);
        try {
            zw.co.paynow.core.Payment paynowPayment = paynow.createPayment(payment.getReference());
            String label = payment.getPurpose() == null || payment.getPurpose().isBlank() ? "EstateX payment" : payment.getPurpose();
            paynowPayment.add(label, payment.getAmount().doubleValue());
            WebInitResponse response = paynow.send(paynowPayment);
            if (!response.success()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Paynow rejected the payment: " + response.errors());
            }
            return new CheckoutSession(response.redirectURL(), payment.getReference(), response.pollUrl());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not reach Paynow: " + e.getMessage());
        }
    }

    @Override
    public GatewayStatus checkStatus(Payment payment) {
        if (payment.getPollUrl() == null || payment.getPollUrl().isBlank()) {
            return GatewayStatus.PENDING;
        }
        try {
            StatusResponse status = client().pollTransaction(payment.getPollUrl());
            return map(status);
        } catch (Exception e) {
            return GatewayStatus.PENDING;
        }
    }

    @Override
    public WebhookOutcome handleWebhook(String rawBody, Map<String, String> headers) {
        Map<String, String> fields = parseFormBody(rawBody);
        String reference = fields.get("reference");
        if (reference == null || reference.isBlank()) {
            return null;
        }
        // Never trust the posted status directly - only use this callback as a signal to
        // re-poll through the SDK, which validates Paynow's hash against our integration key.
        return new WebhookOutcome(reference);
    }

    private GatewayStatus map(StatusResponse status) {
        if (status.paid()) {
            return GatewayStatus.PAID;
        }
        TransactionStatus raw = status.getStatus();
        if (raw == TransactionStatus.CANCELLED) {
            return GatewayStatus.CANCELLED;
        }
        if (raw == TransactionStatus.ERROR || raw == TransactionStatus.INVALID_ID) {
            return GatewayStatus.FAILED;
        }
        return GatewayStatus.PENDING;
    }

    private Map<String, String> parseFormBody(String rawBody) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        if (rawBody == null || rawBody.isBlank()) {
            return result;
        }
        for (String pair : rawBody.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String key = java.net.URLDecoder.decode(pair.substring(0, eq), java.nio.charset.StandardCharsets.UTF_8);
            String value = java.net.URLDecoder.decode(pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }
}
