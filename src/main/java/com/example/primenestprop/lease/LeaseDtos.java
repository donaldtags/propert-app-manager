package com.example.primenestprop.lease;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class LeaseDtos {
    private LeaseDtos() {
    }

    public record CreateLeaseRequest(
            @NotNull Long propertyId,
            @NotNull Long tenantId,
            Long landlordId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @DecimalMin("0.0") BigDecimal monthlyRent,
            @DecimalMin("0.0") BigDecimal depositAmount,
            String currency,
            String terms
    ) {
    }

    public record SignLeaseRequest(@NotNull Long userId) {
    }

    public record ReviewDocumentRequest(@NotNull LeaseDocumentStatus status, String reviewNote) {
    }

    public record LeaseResponse(
            Long id,
            Long propertyId,
            Long tenantId,
            Long landlordId,
            LeaseStatus status,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal monthlyRent,
            BigDecimal depositAmount,
            String currency,
            String terms
    ) {
        public static LeaseResponse from(Lease lease) {
            return new LeaseResponse(
                    lease.getId(),
                    lease.getProperty().getId(),
                    lease.getTenant().getId(),
                    lease.getLandlord().getId(),
                    lease.getStatus(),
                    lease.getStartDate(),
                    lease.getEndDate(),
                    lease.getMonthlyRent(),
                    lease.getDepositAmount(),
                    lease.getCurrency(),
                    lease.getTerms()
            );
        }
    }
}
