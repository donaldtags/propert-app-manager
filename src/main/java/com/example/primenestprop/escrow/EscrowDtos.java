package com.example.primenestprop.escrow;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class EscrowDtos {
    private EscrowDtos() {
    }

    public record CreateEscrowRequest(
            @NotNull Long propertyId,
            Long leaseId,
            @DecimalMin("0.01") BigDecimal amount,
            String currency,
            String purpose
    ) {
    }

    public record FundEscrowRequest(
            @NotNull FundingMethod method,
            String provider
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
            String purpose,
            FundingMethod fundingMethod,
            String fundingProvider,
            Instant createdAt,
            int releaseApprovals,
            int releaseApprovalsRequired,
            List<Long> releaseApprovedByUserIds
    ) {
        public static EscrowResponse from(EscrowTransaction escrow) {
            List<Long> approverIds = escrow.getReleaseApprovals().stream()
                    .map(a -> a.getApprover().getId())
                    .toList();
            return new EscrowResponse(
                    escrow.getId(),
                    escrow.getProperty().getId(),
                    escrow.getLease() == null ? null : escrow.getLease().getId(),
                    escrow.getPayer().getId(),
                    escrow.getBeneficiary().getId(),
                    escrow.getStatus(),
                    escrow.getAmount(),
                    escrow.getCurrency(),
                    escrow.getPurpose(),
                    escrow.getFundingMethod(),
                    escrow.getFundingProvider(),
                    escrow.getCreatedAt(),
                    approverIds.size(),
                    2,
                    approverIds
            );
        }
    }
}
