package com.example.primenestprop.admin;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminFraudController {
    private final FraudDetectionService fraudDetectionService;

    public AdminFraudController(FraudDetectionService fraudDetectionService) {
        this.fraudDetectionService = fraudDetectionService;
    }

    @GetMapping("/fraud-signals")
    List<FraudSignal> fraudSignals() {
        return fraudDetectionService.scan();
    }
}
