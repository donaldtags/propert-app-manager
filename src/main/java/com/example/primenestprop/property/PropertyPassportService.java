package com.example.primenestprop.property;

import com.example.primenestprop.escrow.EscrowRepository;
import com.example.primenestprop.escrow.EscrowStatus;
import com.example.primenestprop.escrow.EscrowTransaction;
import com.example.primenestprop.lease.Lease;
import com.example.primenestprop.lease.LeaseRepository;
import com.example.primenestprop.maintenance.MaintenanceRepository;
import com.example.primenestprop.maintenance.MaintenanceRequest;
import com.example.primenestprop.maintenance.MaintenanceStatus;
import com.example.primenestprop.neighbourhood.NeighbourhoodDtos.NeighbourhoodResponse;
import com.example.primenestprop.neighbourhood.NeighbourhoodProfileRepository;
import com.example.primenestprop.property.PropertyPassportDtos.EscrowHistoryEntry;
import com.example.primenestprop.property.PropertyPassportDtos.LeaseHistoryEntry;
import com.example.primenestprop.property.PropertyPassportDtos.MaintenanceHistoryEntry;
import com.example.primenestprop.property.PropertyPassportDtos.PhotoTimelineEntry;
import com.example.primenestprop.property.PropertyPassportDtos.PropertyPassportResponse;
import com.example.primenestprop.property.PropertyPassportDtos.TimelineEvent;
import com.example.primenestprop.review.LandlordRatingRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates a property's real history - photos, leases, maintenance, escrow, ratings, and a
 * computed health score - into a single "passport" view. Tenant/payer identities are
 * deliberately left out of the history entries below since this endpoint is public.
 */
@Service
public class PropertyPassportService {
    private final PropertyService properties;
    private final LeaseRepository leases;
    private final MaintenanceRepository maintenance;
    private final EscrowRepository escrows;
    private final LandlordRatingRepository ratings;
    private final PropertyHealthService health;
    private final NeighbourhoodProfileRepository neighbourhoods;

    public PropertyPassportService(
            PropertyService properties,
            LeaseRepository leases,
            MaintenanceRepository maintenance,
            EscrowRepository escrows,
            LandlordRatingRepository ratings,
            PropertyHealthService health,
            NeighbourhoodProfileRepository neighbourhoods
    ) {
        this.properties = properties;
        this.leases = leases;
        this.maintenance = maintenance;
        this.escrows = escrows;
        this.ratings = ratings;
        this.health = health;
        this.neighbourhoods = neighbourhoods;
    }

    @Transactional(readOnly = true)
    public PropertyPassportResponse build(Long propertyId) {
        Property property = properties.require(propertyId);

        var propertyLeases = leases.findByPropertyOrderByCreatedAtDesc(property);
        var maintenanceRequests = maintenance.findByProperty(property);
        var propertyEscrows = escrows.findByPropertyOrderByCreatedAtDesc(property);

        var photoTimeline = property.getPhotos().stream()
                .map(p -> new PhotoTimelineEntry(p.getPhotoUrl(), p.getCreatedAt()))
                .toList();

        var leaseHistory = propertyLeases.stream()
                .map(l -> new LeaseHistoryEntry(l.getStatus(), l.getStartDate(), l.getEndDate(), l.getMonthlyRent(), l.getCurrency()))
                .toList();

        var maintenanceHistory = maintenanceRequests.stream()
                .map(m -> new MaintenanceHistoryEntry(m.getCategory(), m.getStatus(), m.getCreatedAt(), m.getResolvedAt()))
                .toList();

        var escrowHistory = propertyEscrows.stream()
                .map(e -> new EscrowHistoryEntry(e.getStatus(), e.getAmount(), e.getCurrency(), e.getCreatedAt(), e.getReleasedAt()))
                .toList();

        var propertyRatings = ratings.findByPropertyOrderByCreatedAtDesc(property);
        Double averageRating = propertyRatings.isEmpty()
                ? null
                : propertyRatings.stream().mapToInt(r -> r.getRating()).average().orElse(0);

        return new PropertyPassportResponse(
                property.getId(),
                property.getTitle(),
                property.getCreatedAt(),
                property.getVerificationStatus(),
                property.getVerifiedAt(),
                property.isSolarInstalled(),
                property.isBackupPower(),
                property.getWaterSource(),
                health.compute(property),
                photoTimeline,
                leaseHistory,
                maintenanceHistory,
                escrowHistory,
                buildTimeline(property, propertyLeases, maintenanceRequests, propertyEscrows),
                neighbourhoods.findByCityIgnoreCaseAndSuburbIgnoreCase(property.getCity(), property.getSuburb())
                        .map(NeighbourhoodResponse::from)
                        .orElse(null),
                averageRating,
                propertyRatings.size()
        );
    }

    /** Builds the Digital Home Timeline: every dated event that actually happened, in order. */
    private List<TimelineEvent> buildTimeline(
            Property property,
            List<Lease> propertyLeases,
            List<MaintenanceRequest> maintenanceRequests,
            List<EscrowTransaction> propertyEscrows
    ) {
        List<TimelineEvent> events = new ArrayList<>();

        events.add(new TimelineEvent(property.getCreatedAt(), "LISTED", "Listed on PrimeNest"));
        if (property.getVerifiedAt() != null) {
            events.add(new TimelineEvent(property.getVerifiedAt(), "VERIFIED", "Property verified"));
        }
        for (var photo : property.getPhotos()) {
            events.add(new TimelineEvent(photo.getCreatedAt(), "PHOTO_ADDED", "Photo added"));
        }
        for (Lease lease : propertyLeases) {
            events.add(new TimelineEvent(lease.getCreatedAt(), "LEASE_CREATED", "Lease created"));
            if (lease.getSignedAt() != null) {
                events.add(new TimelineEvent(lease.getSignedAt(), "LEASE_SIGNED", "Lease signed by both parties"));
            }
        }
        for (MaintenanceRequest request : maintenanceRequests) {
            events.add(new TimelineEvent(request.getCreatedAt(), "MAINTENANCE_REQUESTED", "Maintenance requested: " + request.getCategory()));
            if (request.getStatus() == MaintenanceStatus.RESOLVED && request.getResolvedAt() != null) {
                events.add(new TimelineEvent(request.getResolvedAt(), "MAINTENANCE_RESOLVED", "Maintenance resolved: " + request.getCategory()));
            }
        }
        for (EscrowTransaction escrow : propertyEscrows) {
            events.add(new TimelineEvent(escrow.getCreatedAt(), "ESCROW_CREATED", "Escrow opened"));
            if (escrow.getStatus() == EscrowStatus.RELEASED && escrow.getReleasedAt() != null) {
                events.add(new TimelineEvent(escrow.getReleasedAt(), "ESCROW_RELEASED", "Escrow funds released"));
            }
        }

        events.sort(Comparator.comparing(TimelineEvent::occurredAt));
        return events;
    }
}
