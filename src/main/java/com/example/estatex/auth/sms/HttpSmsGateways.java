package com.example.estatex.auth.sms;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** SMS providers called over plain HTTPS (no SDKs). */
public final class HttpSmsGateways {
    private static final Logger log = LoggerFactory.getLogger(HttpSmsGateways.class);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private HttpSmsGateways() {}

    /** Africa's Talking (covers Zimbabwe, Kenya, Nigeria and more). Username "sandbox" uses their test API. */
    public static final class AfricasTalking implements SmsGateway {
        private final String username;
        private final String apiKey;
        private final String senderId;

        public AfricasTalking(String username, String apiKey, String senderId) {
            this.username = username;
            this.apiKey = apiKey;
            this.senderId = senderId;
        }

        @Override
        public void send(String toE164, String message) {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("username", username);
            form.put("to", toE164);
            form.put("message", message);
            if (senderId != null && !senderId.isBlank()) form.put("from", senderId);
            String host = "sandbox".equals(username) ? "https://api.sandbox.africastalking.com" : "https://api.africastalking.com";
            post(HttpRequest.newBuilder(URI.create(host + "/version1/messaging"))
                    .header("apiKey", apiKey)
                    .header("Accept", "application/json"), form, name());
        }

        @Override public boolean deliversRealMessages() { return true; }
        @Override public String name() { return "africastalking"; }
    }

    /** Twilio Programmable Messaging. */
    public static final class Twilio implements SmsGateway {
        private final String accountSid;
        private final String authToken;
        private final String from;

        public Twilio(String accountSid, String authToken, String from) {
            this.accountSid = accountSid;
            this.authToken = authToken;
            this.from = from;
        }

        @Override
        public void send(String toE164, String message) {
            String basic = Base64.getEncoder().encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
            post(HttpRequest.newBuilder(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json"))
                    .header("Authorization", "Basic " + basic), Map.of("To", toE164, "From", from, "Body", message), name());
        }

        @Override public boolean deliversRealMessages() { return true; }
        @Override public String name() { return "twilio"; }
    }

    private static void post(HttpRequest.Builder builder, Map<String, String> form, String provider) {
        String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest request = builder.timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                // Never log the message body: it contains the sign-in code.
                log.warn("SMS via {} failed with HTTP {}", provider, response.statusCode());
                throw new SmsDeliveryException("The text message couldn't be sent. Please try again.");
            }
        } catch (java.io.IOException e) {
            log.warn("SMS via {} failed: {}", provider, e.getMessage());
            throw new SmsDeliveryException("The text message couldn't be sent. Please try again.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SmsDeliveryException("The text message couldn't be sent. Please try again.");
        }
    }

    public static class SmsDeliveryException extends com.example.estatex.common.ApiException {
        public SmsDeliveryException(String message) {
            super(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, message);
        }
    }
}
