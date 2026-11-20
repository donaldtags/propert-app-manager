package com.example.estatex.kyc.check;

import com.example.estatex.common.ApiException;
import com.example.estatex.user.AppUser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * International photo-ID check through Didit (https://docs.didit.me): the person photographs their
 * passport or ID (any of 220+ countries) and takes a selfie on Didit's hosted page; Didit checks the
 * document is genuine and the face matches, then calls our webhook with the result.
 *
 * Keys: DIDIT_API_KEY, DIDIT_WORKFLOW_ID (a KYC workflow from the Didit console) and
 * DIDIT_WEBHOOK_SECRET (the destination's shared secret). Without them this check is off.
 */
@Service
public class DiditDocumentCheck {
    private static final Logger log = LoggerFactory.getLogger(DiditDocumentCheck.class);
    static final String PROVIDER = "didit";
    private static final long MAX_WEBHOOK_AGE_SECONDS = 300;

    private final String apiKey;
    private final String workflowId;
    private final String webhookSecret;
    private final String baseUrl;
    private final String frontendBaseUrl;
    private final IdentityCheckRepository checks;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public DiditDocumentCheck(
            @Value("${app.idcheck.didit.api-key:}") String apiKey,
            @Value("${app.idcheck.didit.workflow-id:}") String workflowId,
            @Value("${app.idcheck.didit.webhook-secret:}") String webhookSecret,
            @Value("${app.idcheck.didit.base-url:https://verification.didit.me}") String baseUrl,
            @Value("${app.frontend-base-url:http://localhost:3000}") String frontendBaseUrl,
            IdentityCheckRepository checks,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.workflowId = workflowId == null ? "" : workflowId.trim();
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
        this.checks = checks;
        this.objectMapper = objectMapper;
        if (!isConfigured()) {
            log.info("Didit international ID checks are off (set DIDIT_API_KEY, DIDIT_WORKFLOW_ID and DIDIT_WEBHOOK_SECRET).");
        }
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty() && !workflowId.isEmpty() && !webhookSecret.isEmpty();
    }

    public record Session(String url, Long checkId) {}

    /** Starts a hosted check for this person and returns the page to send them to. */
    @Transactional
    public Session start(AppUser user, Long submissionId) {
        if (!isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "International ID checks aren't switched on yet");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workflow_id", workflowId);
        body.put("vendor_data", "user:" + user.getId());
        body.put("callback", frontendBaseUrl + "/verification?check=done");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v3/session/"))
                    .timeout(Duration.ofSeconds(15))
                    .header("x-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("Didit session create failed: HTTP {} {}", response.statusCode(), response.body());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Couldn't start the ID check. Please try again shortly.");
            }
            JsonNode json = objectMapper.readTree(response.body());
            IdentityCheck check = new IdentityCheck();
            check.setUserId(user.getId());
            check.setSubmissionId(submissionId);
            check.setKind(IdentityCheck.DOCUMENT);
            check.setProvider(PROVIDER);
            check.setProviderReference(json.path("session_id").asString(""));
            check.setStatus(IdentityCheck.PENDING);
            check.setSummary("Photo ID check started - waiting for the person to finish it.");
            checks.save(check);
            return new Session(json.path("url").asString(""), check.getId());
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Couldn't start the ID check. Please try again shortly.");
        } catch (Exception e) {
            log.warn("Didit session create failed: {}", e.toString());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Couldn't start the ID check. Please try again shortly.");
        }
    }

    /**
     * Didit's webhook. Trusted only when X-Signature (HMAC-SHA256 of the exact body with our shared
     * secret, hex) matches and X-Timestamp is within five minutes - otherwise anyone could post an
     * "Approved" result.
     */
    @Transactional
    public void handleWebhook(byte[] rawBody, String signature, String previousSignature, String timestamp) {
        if (!isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "International ID checks aren't switched on");
        }
        long sentAt;
        try {
            sentAt = Long.parseLong(timestamp == null ? "" : timestamp.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Missing or bad webhook timestamp");
        }
        if (Math.abs(Instant.now().getEpochSecond() - sentAt) > MAX_WEBHOOK_AGE_SECONDS) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Webhook is too old");
        }
        String expected = hmacHex(rawBody);
        if (!constantTimeEquals(expected, signature) && !constantTimeEquals(expected, previousSignature)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Webhook signature doesn't match");
        }

        JsonNode event = objectMapper.readTree(new String(rawBody, StandardCharsets.UTF_8));
        String sessionId = event.path("session_id").asString("");
        IdentityCheck check = checks.findFirstByProviderAndProviderReference(PROVIDER, sessionId).orElse(null);
        if (check == null) {
            log.info("Didit webhook for unknown session {} ignored", sessionId);
            return;
        }
        String status = switch (event.path("status").asString("")) {
            case "Approved" -> IdentityCheck.APPROVED;
            case "Declined" -> IdentityCheck.DECLINED;
            case "In Review" -> IdentityCheck.IN_REVIEW;
            case "Abandoned", "Expired" -> IdentityCheck.ERROR;
            default -> IdentityCheck.PENDING;
        };
        check.setStatus(status);
        JsonNode doc = event.path("decision").path("id_verifications").path(0);
        if (!doc.isMissingNode()) {
            Map<String, String> details = new LinkedHashMap<>();
            for (String f : new String[]{"document_type", "issuing_state", "document_number", "first_name", "last_name", "date_of_birth", "status"}) {
                String v = doc.path(f).asString("");
                if (!v.isBlank()) details.put(f, v);
            }
            check.setDetailsJson(objectMapper.writeValueAsString(details));
            check.setSummary(switch (status) {
                case IdentityCheck.APPROVED -> "Photo ID genuine and face matches: ";
                case IdentityCheck.DECLINED -> "Photo ID check failed: ";
                default -> "Photo ID check: ";
            } + String.join(" ", details.getOrDefault("first_name", ""), details.getOrDefault("last_name", "")).trim()
                    + (details.containsKey("date_of_birth") ? ", born " + details.get("date_of_birth") : "")
                    + (details.containsKey("issuing_state") ? ", " + details.get("issuing_state") : "")
                    + (details.containsKey("document_type") ? " " + details.get("document_type").toLowerCase() : "")
                    + (details.containsKey("document_number") ? " " + details.get("document_number") : ""));
        } else {
            check.setSummary("Photo ID check: " + event.path("status").asString(""));
        }
        if (!IdentityCheck.PENDING.equals(status)) check.setCompletedAt(Instant.now());
    }

    String hmacHex(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEquals(String expected, String given) {
        return given != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                given.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
