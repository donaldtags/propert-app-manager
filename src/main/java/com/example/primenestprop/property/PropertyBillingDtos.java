package com.example.primenestprop.property;

import java.math.BigDecimal;
import java.time.Instant;

public final class PropertyBillingDtos {
    private PropertyBillingDtos() {
    }

    public record PropertyBillingResponse(
            PropertyBillingStatus status,
            Instant currentPeriodEnd,
            BigDecimal monthlyFee,
            String currency
    ) {
    }
}