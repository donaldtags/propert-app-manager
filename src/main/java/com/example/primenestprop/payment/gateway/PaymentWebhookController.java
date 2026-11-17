package com.example.primenestprop.payment.gateway;

import com.example.primenestprop.payment.PaymentService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public (unauthenticated) callback target for provider servers. See {@link PaymentGateway#handleWebhook}
 * for why the posted body is never trusted directly. */
@RestController
@RequestMapping("/api/v1/payments/webhooks")
public class PaymentWebhookController {
    private final PaymentService service;

    public PaymentWebhookController(PaymentService service) {
        this.service = service;
    }

    @PostMapping("/{provider}")
    ResponseEntity<Void> webhook(
            @PathVariable String provider,
            @RequestBody(required = false) String rawBody,
            @RequestHeader Map<String, String> headers
    ) {
        service.handleWebhook(provider, rawBody, headers);
        return ResponseEntity.ok().build();
    }
}
