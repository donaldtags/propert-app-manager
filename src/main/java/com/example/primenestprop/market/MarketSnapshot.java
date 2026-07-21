package com.example.primenestprop.market;

import java.time.Instant;
import java.util.List;

public record MarketSnapshot(
        List<MarketQuote> quotes,
        Instant lastUpdated,
        boolean stale,
        String source
) {
}