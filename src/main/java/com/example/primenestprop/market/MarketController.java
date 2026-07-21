package com.example.primenestprop.market;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/market")
public class MarketController {
    private final ZimbabweReitMarketService marketService;

    public MarketController(ZimbabweReitMarketService marketService) {
        this.marketService = marketService;
    }

    @GetMapping("/reits/zw")
    MarketSnapshot zimbabweReits() {
        return marketService.snapshot();
    }
}