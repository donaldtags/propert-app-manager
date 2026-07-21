package com.example.primenestprop.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public final class PaymentDtos {
    private PaymentDtos() {
    }

    public record CreatePaymentRequest(
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
            String payerName,
            Long payeeId,
            String payeeName,
            Long propertyId,
            Long leaseId,
            PaymentStatus status,
            BigDecimal amount,
            String currency,
            String provider,
            String reference,
            String purpose,
            Instant createdAt,
            Instant paidAt
    ) {
        public static PaymentResponse from(Payment payment) {
            return new PaymentResponse(
                    payment.getId(),
                    payment.getPayer().getId(),
                    payment.getPayer().getFullName(),
                    payment.getPayee().getId(),
                    payment.getPayee().getFullName(),
                    payment.getProperty() == null ? null : payment.getProperty().getId(),
                    payment.getLease() == null ? null : payment.getLease().getId(),
                    payment.getStatus(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getProvider(),
                    payment.getReference(),
                    payment.getPurpose(),
                    payment.getCreatedAt(),
                    payment.getPaidAt()
            );
        }
    }
}
