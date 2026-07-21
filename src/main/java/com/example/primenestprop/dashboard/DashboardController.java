package com.example.primenestprop.dashboard;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.common.AuditLog;
import com.example.primenestprop.common.AuditLogRepository;
import com.example.primenestprop.escrow.EscrowDtos.EscrowResponse;
import com.example.primenestprop.escrow.EscrowService;
import com.example.primenestprop.escrow.EscrowTransaction;
import com.example.primenestprop.kyc.KycService;
import com.example.primenestprop.lease.Lease;
import com.example.primenestprop.lease.LeaseDtos.LeaseResponse;
import com.example.primenestprop.lease.LeaseService;
import com.example.primenestprop.lease.LeaseStatus;
import com.example.primenestprop.maintenance.MaintenanceDtos.MaintenanceResponse;
import com.example.primenestprop.maintenance.MaintenanceService;
import com.example.primenestprop.payment.Payment;
import com.example.primenestprop.payment.PaymentDtos.PaymentResponse;
import com.example.primenestprop.payment.PaymentService;
import com.example.primenestprop.payment.PaymentStatus;
import com.example.primenestprop.payment.RentInvoiceService;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.property.PropertyDtos.PropertyResponse;
import com.example.primenestprop.property.PropertyService;
import com.example.primenestprop.property.PropertyStatus;
import com.example.primenestprop.review.LandlordRatingService;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.UserDtos.UserResponse;
import com.example.primenestprop.user.UserRole;
import com.example.primenestprop.user.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboards")
public class DashboardController {
    private static final int RECENT_LIMIT = 10;
    private static final int ACTIVITY_LIMIT = 20;
    private static final String PRIMARY_CURRENCY = "USD";

    private final PropertyService properties;
    private final LeaseService leases;
    private final EscrowService escrows;
    private final PaymentService payments;
    private final MaintenanceService maintenance;
    private final UserService users;
    private final KycService kyc;
    private final AuditLogRepository auditLogs;
    private final RentInvoiceService rentInvoices;
    private final LandlordRatingService landlordRatings;

    public DashboardController(
            PropertyService properties,
            LeaseService leases,
            EscrowService escrows,
            PaymentService payments,
            MaintenanceService maintenance,
            UserService users,
            KycService kyc,
            AuditLogRepository auditLogs,
            RentInvoiceService rentInvoices,
            LandlordRatingService landlordRatings
    ) {
        this.properties = properties;
        this.leases = leases;
        this.escrows = escrows;
        this.payments = payments;
        this.maintenance = maintenance;
        this.users = users;
        this.kyc = kyc;
        this.auditLogs = auditLogs;
        this.rentInvoices = rentInvoices;
        this.landlordRatings = landlordRatings;
    }

    @GetMapping("/landlords/{landlordId}")
    LandlordDashboard landlord(@PathVariable Long landlordId, @AuthenticationPrincipal AppUser currentUser) {
        requireSelfOrAdmin(landlordId, currentUser);
        AppUser landlordUser = users.require(landlordId);
        List<Property> ownProperties = new ArrayList<>(properties.forLandlord(landlordId));
        for (Property agentProperty : properties.forAgent(landlordId)) {
            if (ownProperties.stream().noneMatch(p -> p.getId().equals(agentProperty.getId()))) {
                ownProperties.add(agentProperty);
            }
        }
        List<Payment> recentPayments = payments.forUser(landlordId, currentUser);
        List<Lease> landlordLeases = leases.forLandlord(landlordId, currentUser);
        List<Lease> activeLeases = landlordLeases.stream().filter(l -> l.getStatus() == LeaseStatus.ACTIVE).toList();

        BigDecimal totalRentIncome = payments.totalRevenueForPayee(landlordId, PRIMARY_CURRENCY);

        Instant now = Instant.now();
        Instant monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        BigDecimal monthlyIncome = payments.revenueForPayeeBetween(landlordId, monthStart, now, PRIMARY_CURRENCY);
        BigDecimal expectedMonthlyIncome = activeLeases.stream()
                .map(Lease::getMonthlyRent)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        rentInvoices.ensureCurrentPeriodInvoices(landlordUser, activeLeases);
        rentInvoices.refreshOverdueStatuses(landlordUser);
        RentInvoiceService.OutstandingSummary outstanding = rentInvoices.outstandingSummary(landlordUser);

        Map<PropertyStatus, Long> statusCounts = ownProperties.stream()
                .collect(Collectors.groupingBy(Property::getStatus, Collectors.counting()));
        long occupiedUnits = statusCounts.getOrDefault(PropertyStatus.OCCUPIED, 0L);
        long vacantUnits = statusCounts.getOrDefault(PropertyStatus.AVAILABLE, 0L);
        double occupancyRate = (occupiedUnits + vacantUnits) == 0 ? 0.0 : (occupiedUnits * 100.0) / (occupiedUnits + vacantUnits);
        BigDecimal portfolioValue = ownProperties.stream()
                .map(Property::getPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new LandlordDashboard(
                UserResponse.from(landlordUser),
                ownProperties.stream().map(PropertyResponse::from).toList(),
                landlordLeases.stream().map(LeaseResponse::from).toList(),
                recentPayments.stream().limit(RECENT_LIMIT).map(PaymentResponse::from).toList(),
                maintenance.forProperties(ownProperties).stream().map(MaintenanceResponse::from).toList(),
                totalRentIncome,
                new LandlordFinancialSummary(
                        monthlyIncome, expectedMonthlyIncome, outstanding.totalOutstanding(),
                        outstanding.overdueCount(), portfolioValue, PRIMARY_CURRENCY
                ),
                new OccupancySummary(ownProperties.size(), occupiedUnits, vacantUnits, occupancyRate),
                escrows.landlordBalanceSummary(landlordUser, PRIMARY_CURRENCY),
                landlordRatings.satisfactionSummary(landlordId),
                incomeTrend(landlordId, monthStart)
        );
    }

    private List<MonthlyAmount> incomeTrend(Long landlordId, Instant currentMonthStart) {
        List<MonthlyAmount> trend = new ArrayList<>();
        DateTimeFormatter monthLabel = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
        LocalDate cursor = currentMonthStart.atZone(ZoneOffset.UTC).toLocalDate().minusMonths(5);
        for (int i = 0; i < 6; i++) {
            Instant from = cursor.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant to = cursor.plusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            BigDecimal amount = payments.revenueForPayeeBetween(landlordId, from, to, PRIMARY_CURRENCY);
            trend.add(new MonthlyAmount(cursor.format(monthLabel), amount));
            cursor = cursor.plusMonths(1);
        }
        return trend;
    }

    private List<MonthlyAmount> paymentTrend(Long tenantId, Instant currentMonthStart) {
        List<MonthlyAmount> trend = new ArrayList<>();
        DateTimeFormatter monthLabel = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
        LocalDate cursor = currentMonthStart.atZone(ZoneOffset.UTC).toLocalDate().minusMonths(5);
        for (int i = 0; i < 6; i++) {
            Instant from = cursor.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant to = cursor.plusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            BigDecimal amount = payments.revenueForPayerBetween(tenantId, from, to, PRIMARY_CURRENCY);
            trend.add(new MonthlyAmount(cursor.format(monthLabel), amount));
            cursor = cursor.plusMonths(1);
        }
        return trend;
    }

    @GetMapping("/tenants/{tenantId}")
    TenantDashboard tenant(@PathVariable Long tenantId, @AuthenticationPrincipal AppUser currentUser) {
        requireSelfOrAdmin(tenantId, currentUser);
        var tenantUser = users.require(tenantId);
        Instant monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new TenantDashboard(
                UserResponse.from(tenantUser),
                leases.forTenant(tenantId, currentUser).stream().map(LeaseResponse::from).toList(),
                payments.forUser(tenantId, currentUser).stream().limit(RECENT_LIMIT).map(PaymentResponse::from).toList(),
                maintenance.forRequester(tenantUser).stream().map(MaintenanceResponse::from).toList(),
                escrows.forUser(tenantId, currentUser).stream().map(EscrowResponse::from).toList(),
                paymentTrend(tenantId, monthStart)
        );
    }

    @GetMapping("/admin")
    AdminDashboardResponse admin(@AuthenticationPrincipal AppUser currentUser) {
        requireAdmin(currentUser);

        Instant now = Instant.now();
        Instant todayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        BigDecimal todayRevenue = payments.revenueBetween(todayStart, now, PRIMARY_CURRENCY);
        BigDecimal monthRevenue = payments.revenueBetween(monthStart, now, PRIMARY_CURRENCY);

        EscrowService.EscrowBalanceSummary escrowSummary = escrows.balanceSummary(PRIMARY_CURRENCY);

        Map<PropertyStatus, Long> propertyCounts = properties.statusCounts();
        long listed = propertyCounts.getOrDefault(PropertyStatus.AVAILABLE, 0L);
        long sold = propertyCounts.getOrDefault(PropertyStatus.SOLD, 0L);
        long rented = propertyCounts.getOrDefault(PropertyStatus.OCCUPIED, 0L);
        long totalProperties = propertyCounts.values().stream().mapToLong(Long::longValue).sum();
        double occupancyRate = (rented + listed) == 0 ? 0.0 : (rented * 100.0) / (rented + listed);

        return new AdminDashboardResponse(
                new RevenueSummary(todayRevenue, monthRevenue, PRIMARY_CURRENCY),
                new EscrowSummary(escrowSummary.totalBalance(), escrowSummary.activeCount(), escrowSummary.disputedCount(), PRIMARY_CURRENCY),
                new PropertySummary(listed, sold, rented, totalProperties),
                new LeaseSummary(leases.countActive(), occupancyRate, leases.countAll()),
                new MaintenanceSummary(maintenance.countOpen(), maintenance.countAll()),
                new VerificationSummary(kyc.countPending(), users.countPendingAdminRequests()),
                recentActivity()
        );
    }

    private List<ActivityItem> recentActivity() {
        List<ActivityItem> activity = new ArrayList<>();
        for (Payment payment : payments.recentSuccessful()) {
            activity.add(new ActivityItem("PAYMENT",
                    payment.getPayer().getFullName() + " paid " + payment.getAmount() + " " + payment.getCurrency(),
                    payment.getPaidAt()));
        }
        for (Lease lease : leases.recentlySigned()) {
            activity.add(new ActivityItem("LEASE",
                    "Lease signed for " + lease.getProperty().getTitle() + " by " + lease.getTenant().getFullName(),
                    lease.getSignedAt()));
        }
        for (EscrowTransaction escrow : escrows.recentlyReleased()) {
            activity.add(new ActivityItem("ESCROW",
                    "Escrow of " + escrow.getAmount() + " " + escrow.getCurrency() + " released for " + escrow.getProperty().getTitle(),
                    escrow.getReleasedAt()));
        }
        for (Property property : properties.recentlyListed()) {
            activity.add(new ActivityItem("PROPERTY",
                    "New listing: " + property.getTitle() + " in " + property.getCity(),
                    property.getCreatedAt()));
        }
        for (AuditLog log : auditLogs.findTop10ByOrderByCreatedAtDesc()) {
            activity.add(new ActivityItem(log.getAction(), log.getAction() + " (" + log.getEntityType() + " #" + log.getEntityId() + ")", log.getCreatedAt()));
        }
        return activity.stream()
                .filter(item -> item.occurredAt() != null)
                .sorted(Comparator.comparing(ActivityItem::occurredAt).reversed())
                .limit(ACTIVITY_LIMIT)
                .toList();
    }

    private void requireAdmin(AppUser currentUser) {
        if (!currentUser.getRoles().contains(UserRole.ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only admins can view the command center dashboard");
        }
    }

    public record AdminDashboardResponse(
            RevenueSummary revenue,
            EscrowSummary escrow,
            PropertySummary properties,
            LeaseSummary leases,
            MaintenanceSummary maintenance,
            VerificationSummary verification,
            List<ActivityItem> recentActivity
    ) {
    }

    public record RevenueSummary(BigDecimal todayRevenue, BigDecimal monthRevenue, String currency) {
    }

    public record EscrowSummary(BigDecimal totalBalance, long activeCount, long disputedCount, String currency) {
    }

    public record PropertySummary(long listed, long sold, long rented, long total) {
    }

    public record LeaseSummary(long active, double occupancyRatePercent, long total) {
    }

    public record MaintenanceSummary(long open, long total) {
    }

    public record VerificationSummary(long pendingKyc, long pendingAdminRequests) {
    }

    public record ActivityItem(String type, String description, Instant occurredAt) {
    }

    public record LandlordDashboard(
            UserResponse landlord,
            List<PropertyResponse> properties,
            List<LeaseResponse> activeLeases,
            List<PaymentResponse> recentPayments,
            List<MaintenanceResponse> maintenanceRequests,
            BigDecimal totalRentIncome,
            LandlordFinancialSummary financials,
            OccupancySummary occupancy,
            EscrowService.LandlordEscrowSummary escrow,
            LandlordRatingService.SatisfactionSummary satisfaction,
            List<MonthlyAmount> incomeTrend
    ) {
    }

    public record LandlordFinancialSummary(
            BigDecimal monthlyIncome,
            BigDecimal expectedMonthlyIncome,
            BigDecimal outstandingRent,
            long overdueInvoiceCount,
            BigDecimal portfolioValue,
            String currency
    ) {
    }

    public record OccupancySummary(long totalUnits, long occupiedUnits, long vacantUnits, double occupancyRatePercent) {
    }

    public record MonthlyAmount(String month, BigDecimal amount) {
    }

    public record TenantDashboard(
            UserResponse tenant,
            List<LeaseResponse> activeLeases,
            List<PaymentResponse> recentPayments,
            List<MaintenanceResponse> maintenanceRequests,
            List<EscrowResponse> escrows,
            List<MonthlyAmount> paymentTrend
    ) {
    }

    private void requireSelfOrAdmin(Long id, AppUser currentUser) {
        if (!currentUser.getId().equals(id) && !currentUser.getRoles().contains(UserRole.ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only view your own dashboard");
        }
    }
}
