package com.example.primenestprop.payment;

import static com.example.primenestprop.payment.PaymentDtos.PaymentResponse;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    PaymentResponse create(@Valid @RequestBody PaymentDtos.CreatePaymentRequest request) {
        return PaymentResponse.from(service.create(request));
    }

    @GetMapping
    List<PaymentResponse> list(@RequestParam Long userId) {
        return service.forUser(userId).stream().map(PaymentResponse::from).toList();
    }

    @PatchMapping("/{id}/success")
    PaymentResponse success(@PathVariable Long id) {
        return PaymentResponse.from(service.markSuccessful(id));
    }
}
