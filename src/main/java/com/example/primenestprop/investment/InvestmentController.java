package com.example.primenestprop.investment;

import static com.example.primenestprop.investment.InvestmentDtos.InvestmentResponse;
import static com.example.primenestprop.investment.InvestmentDtos.ReitResponse;

import com.example.primenestprop.payment.PaymentDtos;
import com.example.primenestprop.user.AppUser;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/v1/investments")
public class InvestmentController {
    private final InvestmentService service;

    public InvestmentController(InvestmentService service) {
        this.service = service;
    }

    @PostMapping("/reits")
    ReitResponse createReit(@Valid @RequestBody InvestmentDtos.CreateReitRequest request, @AuthenticationPrincipal AppUser currentUser) {
        return service.createReit(request, currentUser);
    }

    @GetMapping("/reits")
    List<ReitResponse> reits() {
        return service.activeReits();
    }

    @PatchMapping("/reits/{id}/properties")
    ReitResponse updateReitProperties(
            @PathVariable Long id,
            @Valid @RequestBody InvestmentDtos.UpdateReitPropertiesRequest request,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        return service.updateReitProperties(id, request, currentUser);
    }

    @PostMapping("/checkout")
    PaymentDtos.CheckoutResponse checkout(@Valid @RequestBody InvestmentDtos.CreateInvestmentRequest request, @AuthenticationPrincipal AppUser currentUser) {
        return service.initiateInvestmentCheckout(request, currentUser, request.provider());
    }

    @PatchMapping("/sell")
    List<InvestmentResponse> sell(@Valid @RequestBody InvestmentDtos.SellInvestmentRequest request, @AuthenticationPrincipal AppUser currentUser) {
        return service.sell(request, currentUser).stream().map(InvestmentResponse::from).toList();
    }

    @GetMapping
    List<InvestmentResponse> list(@RequestParam Long investorId, @AuthenticationPrincipal AppUser currentUser) {
        return service.forInvestor(investorId, currentUser).stream().map(InvestmentResponse::from).toList();
    }
}
