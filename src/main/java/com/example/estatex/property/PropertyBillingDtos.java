package com.example.estatex.property;

import java.math.BigDecimal;
import java.time.Instant;

public final class PropertyBillingDtos {
    private PropertyBillingDtos() {
    }

    public record PropertyBillingResponse(
            PropertyBillingStatus status,
            Instant currentPeriodEnd,
            BigDecimal monthlyFee,
            String currency,
            /** The open bill to pay (checkout or wallet), or null when nothing is due. */
            Long pendingPaymentId
    ) {
    }
}