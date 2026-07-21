package com.example.primenestprop.user;

public final class PassportDtos {
    private PassportDtos() {
    }

    public record TenantPassport(
            Long userId,
            String fullName,
            boolean identityVerified,
            int trustScore,
            long yearsOnPlatform,
            long completedLeaseCount,
            long activeLeaseCount,
            long totalRentInvoices,
            long onTimeRentInvoices,
            Integer onTimePaymentRatePercent,
            Double averageLandlordRatingGiven,
            long ratingsGivenCount
    ) {
    }

    public record LandlordPassport(
            Long userId,
            String fullName,
            String companyName,
            boolean identityVerified,
            int trustScore,
            long yearsOnPlatform,
            long propertyCount,
            long completedLeaseCount,
            long activeLeaseCount,
            long resolvedMaintenanceCount,
            long totalMaintenanceCount,
            Integer maintenanceResolutionRatePercent,
            long escrowUsedCount,
            long totalEscrowCount,
            Integer escrowUsageRatePercent,
            Double averageResponseHours,
            Integer responseRatePercent,
            Double averageRating,
            long ratingCount
    ) {
    }
}
