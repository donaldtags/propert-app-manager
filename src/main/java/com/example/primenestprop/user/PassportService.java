package com.example.primenestprop.user;

import com.example.primenestprop.chat.ResponseRateService;
import com.example.primenestprop.escrow.EscrowRepository;
import com.example.primenestprop.escrow.EscrowStatus;
import com.example.primenestprop.lease.LeaseRepository;
import com.example.primenestprop.lease.LeaseStatus;
import com.example.primenestprop.maintenance.MaintenanceRepository;
import com.example.primenestprop.maintenance.MaintenanceStatus;
import com.example.primenestprop.payment.RentInvoice;
import com.example.primenestprop.payment.RentInvoiceRepository;
import com.example.primenestprop.payment.RentInvoiceStatus;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.property.PropertyRepository;
import com.example.primenestprop.review.LandlordRatingRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the Tenant Passport and Landlord Passport - aggregated reputation/history views built
 * entirely from real activity already recorded elsewhere in the system (leases, rent invoices,
 * maintenance, escrow, ratings, messages). Nothing here is a static/self-reported number.
 */
@Service
public class PassportService {
    private static final List<LeaseStatus> COMPLETED_LEASE_STATUSES =
            List.of(LeaseStatus.SIGNED, LeaseStatus.ACTIVE, LeaseStatus.ENDED);

    private final UserService users;
    private final LeaseRepository leases;
    private final RentInvoiceRepository rentInvoices;
    private final PropertyRepository properties;
    private final MaintenanceRepository maintenance;
    private final EscrowRepository escrows;
    private final LandlordRatingRepository ratings;
    private final ResponseRateService responseRateService;

    public PassportService(
            UserService users,
            LeaseRepository leases,
            RentInvoiceRepository rentInvoices,
            PropertyRepository properties,
            MaintenanceRepository maintenance,
            EscrowRepository escrows,
            LandlordRatingRepository ratings,
            ResponseRateService responseRateService
    ) {
        this.users = users;
        this.leases = leases;
        this.rentInvoices = rentInvoices;
        this.properties = properties;
        this.maintenance = maintenance;
        this.escrows = escrows;
        this.ratings = ratings;
        this.responseRateService = responseRateService;
    }

    @Transactional(readOnly = true)
    public PassportDtos.TenantPassport tenantPassport(Long tenantId) {
        AppUser tenant = users.require(tenantId);

        long completedLeases = leases.countByTenantAndStatusIn(tenant, COMPLETED_LEASE_STATUSES);
        long activeLeases = leases.countByTenantAndStatusIn(tenant, List.of(LeaseStatus.ACTIVE));

        List<RentInvoice> invoices = rentInvoices.findByTenantOrderByPeriodStartDesc(tenant);
        long paidInvoices = invoices.stream().filter(i -> i.getStatus() == RentInvoiceStatus.PAID).count();
        long onTime = invoices.stream()
                .filter(i -> i.getStatus() == RentInvoiceStatus.PAID)
                .filter(i -> i.getPaidAt() != null && i.getDueDate() != null)
                .filter(i -> !i.getPaidAt().isAfter(i.getDueDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().plus(Duration.ofDays(1))))
                .count();
        Integer onTimeRate = paidInvoices == 0 ? null : (int) Math.round((onTime * 100.0) / paidInvoices);

        var ratingsGiven = ratings.findByTenant(tenant);
        Double avgRatingGiven = ratingsGiven.isEmpty()
                ? null
                : ratingsGiven.stream().mapToInt(r -> r.getRating()).average().orElse(0);

        long yearsOnPlatform = tenant.getCreatedAt() == null
                ? 0
                : Duration.between(tenant.getCreatedAt(), Instant.now()).toDays() / 365;

        return new PassportDtos.TenantPassport(
                tenant.getId(),
                tenant.getFullName(),
                tenant.isIdentityVerified(),
                tenant.getTrustScore(),
                yearsOnPlatform,
                completedLeases,
                activeLeases,
                invoices.size(),
                onTime,
                onTimeRate,
                avgRatingGiven,
                ratingsGiven.size()
        );
    }

    @Transactional(readOnly = true)
    public PassportDtos.LandlordPassport landlordPassport(Long landlordId) {
        AppUser landlord = users.require(landlordId);

        List<Property> ownedProperties = properties.findByLandlord(landlord);

        long completedLeases = leases.countByLandlordAndStatusIn(landlord, COMPLETED_LEASE_STATUSES);
        long activeLeases = leases.countByLandlordAndStatusIn(landlord, List.of(LeaseStatus.ACTIVE));

        List<com.example.primenestprop.maintenance.MaintenanceRequest> maintenanceRequests =
                ownedProperties.isEmpty() ? List.of() : maintenance.findByPropertyIn(ownedProperties);
        long resolvedMaintenance = maintenanceRequests.stream()
                .filter(m -> m.getStatus() == MaintenanceStatus.RESOLVED)
                .count();
        Integer maintenanceRate = maintenanceRequests.isEmpty()
                ? null
                : (int) Math.round((resolvedMaintenance * 100.0) / maintenanceRequests.size());

        var beneficiaryEscrows = escrows.findByBeneficiary(landlord);
        long releasedEscrows = beneficiaryEscrows.stream().filter(e -> e.getStatus() == EscrowStatus.RELEASED).count();
        Integer escrowRate = beneficiaryEscrows.isEmpty()
                ? null
                : (int) Math.round((releasedEscrows * 100.0) / beneficiaryEscrows.size());

        var landlordRatings = ratings.findByLandlordOrderByCreatedAtDesc(landlord);
        Double avgRating = landlordRatings.isEmpty()
                ? null
                : landlordRatings.stream().mapToInt(r -> r.getRating()).average().orElse(0);

        var response = responseRateService.compute(landlord);

        long yearsOnPlatform = landlord.getCreatedAt() == null
                ? 0
                : Duration.between(landlord.getCreatedAt(), Instant.now()).toDays() / 365;

        return new PassportDtos.LandlordPassport(
                landlord.getId(),
                landlord.getFullName(),
                landlord.getCompanyName(),
                landlord.isIdentityVerified(),
                landlord.getTrustScore(),
                yearsOnPlatform,
                ownedProperties.size(),
                completedLeases,
                activeLeases,
                resolvedMaintenance,
                maintenanceRequests.size(),
                maintenanceRate,
                releasedEscrows,
                beneficiaryEscrows.size(),
                escrowRate,
                response.averageResponseHours(),
                response.responseRatePercent(),
                avgRating,
                landlordRatings.size()
        );
    }
}
