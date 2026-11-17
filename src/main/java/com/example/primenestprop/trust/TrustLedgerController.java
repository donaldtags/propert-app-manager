package com.example.primenestprop.trust;

import com.example.primenestprop.user.AppUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Append-only: there is deliberately no update or delete endpoint. */
@RestController
@RequestMapping("/api/v1/orgs/{slug}/trust-ledger")
public class TrustLedgerController {
    private final TrustLedgerService service;

    public TrustLedgerController(TrustLedgerService service) {
        this.service = service;
    }

    @GetMapping
    TrustLedgerDtos.LedgerResponse ledger(@PathVariable String slug, @AuthenticationPrincipal AppUser user) {
        return service.ledger(slug, user);
    }

    @PostMapping
    TrustLedgerDtos.EntryResponse record(@PathVariable String slug, @Valid @RequestBody TrustLedgerDtos.RecordEntryRequest request,
                                         @AuthenticationPrincipal AppUser user) {
        return TrustLedgerDtos.EntryResponse.from(service.record(slug, request, user));
    }
}
