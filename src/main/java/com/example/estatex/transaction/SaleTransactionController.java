package com.example.estatex.transaction;

import com.example.estatex.transaction.SaleTransactionDtos.ReportSaleRequest;
import com.example.estatex.transaction.SaleTransactionDtos.SaleTransactionResponse;
import com.example.estatex.transaction.SaleTransactionDtos.VerifySaleRequest;
import com.example.estatex.user.AppUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SaleTransactionController {
    private final SaleTransactionService service;

    SaleTransactionController(SaleTransactionService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/properties/{id}/sale-transactions")
    @ResponseStatus(HttpStatus.CREATED)
    SaleTransactionResponse report(@PathVariable Long id, @Valid @RequestBody ReportSaleRequest request,
                                   @AuthenticationPrincipal AppUser user) {
        return SaleTransactionResponse.from(service.report(id, request, user));
    }

    @GetMapping("/api/v1/properties/{id}/sale-transactions")
    List<SaleTransactionResponse> forProperty(@PathVariable Long id, @AuthenticationPrincipal AppUser user) {
        return service.forProperty(id, user).stream().map(SaleTransactionResponse::from).toList();
    }

    @GetMapping("/api/v1/admin/sale-transactions")
    List<SaleTransactionResponse> queue(@AuthenticationPrincipal AppUser user) {
        return service.queue(user).stream().map(SaleTransactionResponse::from).toList();
    }

    @PostMapping("/api/v1/admin/sale-transactions/{id}/verify")
    SaleTransactionResponse verify(@PathVariable Long id, @Valid @RequestBody VerifySaleRequest request,
                                   @AuthenticationPrincipal AppUser user) {
        return SaleTransactionResponse.from(service.verify(id, request, user));
    }
}
