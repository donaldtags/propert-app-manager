package com.example.primenestprop.market;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketQuote(
        String ticker,
        String name,
        String exchange,
        String currency,
        BigDecimal price,
        BigDecimal changeAmount,
        BigDecimal changePercent,
        Long volume,
        Instant asOf
) {
}