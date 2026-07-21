package com.example.primenestprop.property;

import com.example.primenestprop.escrow.EscrowStatus;
import com.example.primenestprop.lease.LeaseStatus;
import com.example.primenestprop.maintenance.MaintenanceStatus;
import com.example.primenestprop.neighbourhood.NeighbourhoodDtos.NeighbourhoodResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PropertyPassportDtos {
    private PropertyPassportDtos() {
    }

    public record PhotoTimelineEntry(String url, Instant uploadedAt) {
    }

    public record LeaseHistoryEntry(LeaseStatus status, LocalDate startDate, LocalDate endDate, BigDecimal monthlyRent, String currency) {
    }

    public record MaintenanceHistoryEntry(String category, MaintenanceStatus status, Instant createdAt, Instant resolvedAt) {
    }

    public record EscrowHistoryEntry(EscrowStatus status, BigDecimal amount, String currency, Instant createdAt, Instant releasedAt) {
    }

    /** One dated event in the property's Digital Home Timeline - built only from events that actually happened. */
    public record TimelineEvent(Instant occurredAt, String type, String label) {
    }

    public record PropertyPassportResponse(
            Long propertyId,
            String title,
            Instant createdAt,
            VerificationStatus verificationStatus,
            Instant verifiedAt,
            boolean solarInstalled,
            boolean backupPower,
            WaterSource waterSource,
            PropertyHealthScore healthScore,
            List<PhotoTimelineEntry> photoTimeline,
            List<LeaseHistoryEntry> leaseHistory,
            List<MaintenanceHistoryEntry> maintenanceHistory,
            List<EscrowHistoryEntry> escrowHistory,
            List<TimelineEvent> timeline,
            NeighbourhoodResponse neighbourhood,
            Double averageRating,
            long ratingCount
    ) {
    }
}
