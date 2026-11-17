package com.example.primenestprop.commission;

import com.example.primenestprop.commission.CommissionDtos.CommissionResponse;
import com.example.primenestprop.user.AppUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orgs/{slug}/commissions")
public class CommissionController {
    private final CommissionService service;

    public CommissionController(CommissionService service) {
        this.service = service;
    }

    @GetMapping
    List<CommissionResponse> list(@PathVariable String slug, @AuthenticationPrincipal AppUser user) {
        return service.list(slug, user).stream().map(CommissionResponse::from).toList();
    }

    @PostMapping
    CommissionResponse create(@PathVariable String slug, @Valid @RequestBody CommissionDtos.CreateCommissionRequest request,
                              @AuthenticationPrincipal AppUser user) {
        return CommissionResponse.from(service.create(slug, request, user));
    }

    @PostMapping("/{id}/conveyancer-confirmation")
    CommissionResponse confirm(@PathVariable String slug, @PathVariable Long id,
                               @Valid @RequestBody CommissionDtos.ConveyancerConfirmationRequest request,
                               @AuthenticationPrincipal AppUser user) {
        return CommissionResponse.from(service.confirmTransfer(slug, id, request, user));
    }

    @PostMapping("/{id}/received")
    CommissionResponse received(@PathVariable String slug, @PathVariable Long id,
                                @Valid @RequestBody CommissionDtos.ReceivedRequest request,
                                @AuthenticationPrincipal AppUser user) {
        return CommissionResponse.from(service.markReceived(slug, id, request, user));
    }

    @PostMapping("/{id}/cancel")
    CommissionResponse cancel(@PathVariable String slug, @PathVariable Long id, @AuthenticationPrincipal AppUser user) {
        return CommissionResponse.from(service.cancel(slug, id, user));
    }
}
