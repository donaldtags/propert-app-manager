package com.example.primenestprop.investment;

import static com.example.primenestprop.investment.InvestmentDtos.InvestmentResponse;
import static com.example.primenestprop.investment.InvestmentDtos.ReitResponse;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
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
    ReitResponse createReit(@Valid @RequestBody InvestmentDtos.CreateReitRequest request) {
        return ReitResponse.from(service.createReit(request));
    }

    @GetMapping("/reits")
    List<ReitResponse> reits() {
        return service.activeReits().stream().map(ReitResponse::from).toList();
    }

    @PostMapping
    InvestmentResponse invest(@Valid @RequestBody InvestmentDtos.CreateInvestmentRequest request) {
        return InvestmentResponse.from(service.invest(request));
    }

    @GetMapping
    List<InvestmentResponse> list(@RequestParam Long investorId) {
        return service.forInvestor(investorId).stream().map(InvestmentResponse::from).toList();
    }
}
