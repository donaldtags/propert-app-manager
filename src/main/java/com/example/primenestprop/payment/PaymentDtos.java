package com.example.primenestprop.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public final class PaymentDtos {
    private PaymentDtos() {
    }

    public record CreatePaymentRequest(
            @NotNull Long payerId,
            @NotNull Long payeeId,
            Long propertyId,
            Long leaseId,
            @DecimalMin("0.01") BigDecimal amount,
            String currency,
            String provider,
            String purpose
    ) {
    }

    public record PaymentResponse(
            Long id,
            Long payerId,
            Long payeeId,
            Long propertyId,
            Long leaseId,
            PaymentStatus status,
            BigDecimal amount,
            String currency,
            String provider,
            String reference,
            String purpose
    ) {
        public static PaymentResponse from(Payment payment) {
            return new PaymentResponse(
                    payment.getId(),
                    payment.getPayer().getId(),
                    payment.getPayee().getId(),
                    payment.getProperty() == null ? null : payment.getProperty().getId(),
                    payment.getLease() == null ? null : payment.getLease().getId(),
                    payment.getStatus(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getProvider(),
                    payment.getReference(),
                    payment.getPurpose()
            );
        }
    }
}
