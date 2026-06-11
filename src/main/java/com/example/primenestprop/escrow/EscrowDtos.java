package com.example.primenestprop.escrow;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public final class EscrowDtos {
    private EscrowDtos() {
    }

    public record CreateEscrowRequest(
            @NotNull Long propertyId,
            Long leaseId,
            @NotNull Long payerId,
            @DecimalMin("0.01") BigDecimal amount,
            String currency,
            String purpose
    ) {
    }

    public record EscrowResponse(
            Long id,
            Long propertyId,
            Long leaseId,
            Long payerId,
            Long beneficiaryId,
            EscrowStatus status,
            BigDecimal amount,
            String currency,
            String purpose
    ) {
        public static EscrowResponse from(EscrowTransaction escrow) {
            return new EscrowResponse(
                    escrow.getId(),
                    escrow.getProperty().getId(),
                    escrow.getLease() == null ? null : escrow.getLease().getId(),
                    escrow.getPayer().getId(),
                    escrow.getBeneficiary().getId(),
                    escrow.getStatus(),
                    escrow.getAmount(),
                    escrow.getCurrency(),
                    escrow.getPurpose()
            );
        }
    }
}
