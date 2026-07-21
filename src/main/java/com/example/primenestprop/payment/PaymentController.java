package com.example.primenestprop.payment;

import static com.example.primenestprop.payment.PaymentDtos.PaymentResponse;

import com.example.primenestprop.user.AppUser;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final PaymentDocumentService documentService;

    public PaymentController(PaymentService service, PaymentDocumentService documentService) {
        this.service = service;
        this.documentService = documentService;
    }

    @PostMapping
    PaymentResponse create(@Valid @RequestBody PaymentDtos.CreatePaymentRequest request, @AuthenticationPrincipal AppUser currentUser) {
        return PaymentResponse.from(service.create(request, currentUser));
    }

    @GetMapping
    List<PaymentResponse> list(@RequestParam Long userId, @AuthenticationPrincipal AppUser currentUser) {
        return service.forUser(userId, currentUser).stream().map(PaymentResponse::from).toList();
    }

    @PatchMapping("/{id}/success")
    PaymentResponse success(@PathVariable Long id, @AuthenticationPrincipal AppUser currentUser) {
        return PaymentResponse.from(service.markSuccessful(id, currentUser));
    }

    @GetMapping("/{id}/receipt")
    ResponseEntity<String> receipt(@PathVariable Long id, @AuthenticationPrincipal AppUser currentUser) {
        String html = documentService.receiptHtml(id, currentUser);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE + "; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("receipt-" + id + ".html").build().toString())
                .body(html);
    }

    @GetMapping("/statement.csv")
    ResponseEntity<byte[]> statement(@RequestParam Long userId, @AuthenticationPrincipal AppUser currentUser) {
        String csv = documentService.statementCsv(userId, currentUser);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("statement-" + userId + ".csv").build().toString())
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
