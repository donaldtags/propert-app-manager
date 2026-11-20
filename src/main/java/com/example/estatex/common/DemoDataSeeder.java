package com.example.estatex.common;

import com.example.estatex.escrow.EscrowDtos;
import com.example.estatex.escrow.EscrowService;
import com.example.estatex.escrow.FundingMethod;
import com.example.estatex.investment.InvestmentDtos;
import com.example.estatex.investment.InvestmentService;
import com.example.estatex.lease.Lease;
import com.example.estatex.lease.LeaseDtos;
import com.example.estatex.lease.LeaseService;
import com.example.estatex.maintenance.MaintenanceDtos;
import com.example.estatex.maintenance.MaintenanceService;
import com.example.estatex.payment.PaymentDtos;
import com.example.estatex.payment.PaymentService;
import com.example.estatex.property.ListingType;
import com.example.estatex.property.Property;
import com.example.estatex.property.PropertyDtos;
import com.example.estatex.property.PropertyService;
import com.example.estatex.property.WaterSource;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.UserDtos;
import com.example.estatex.user.UserRepository;
import com.example.estatex.user.UserRole;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.demo.seed-data", havingValue = "true")
class DemoDataSeeder implements CommandLineRunner {
    private static final String DEMO_PASSWORD = "AfricaProp123!";

    private final UserRepository users;
    private final com.example.estatex.user.UserService userService;
    private final PropertyService propertyService;
    private final LeaseService leaseService;
    private final EscrowService escrowService;
    private final PaymentService paymentService;
    private final MaintenanceService maintenanceService;
    private final InvestmentService investmentService;

    DemoDataSeeder(
            UserRepository users,
            com.example.estatex.user.UserService userService,
            PropertyService propertyService,
            LeaseService leaseService,
            EscrowService escrowService,
            PaymentService paymentService,
            MaintenanceService maintenanceService,
            InvestmentService investmentService
    ) {
        this.users = users;
        this.userService = userService;
        this.propertyService = propertyService;
        this.leaseService = leaseService;
        this.escrowService = escrowService;
        this.paymentService = paymentService;
        this.maintenanceService = maintenanceService;
        this.investmentService = investmentService;
    }

    @Override
    public void run(String... args) {
        if (users.count() > 0) {
            return;
        }

        AppUser landlord = userService.create(new UserDtos.CreateUserRequest(
                "Tariro Moyo",
                "landlord@estatex.africa",
                "+263771000001",
                DEMO_PASSWORD,
                "Zimbabwe",
                Set.of(UserRole.LANDLORD)
        ));
        AppUser tenant = userService.create(new UserDtos.CreateUserRequest(
                "Nadia Ncube",
                "tenant@estatex.africa",
                "+263771000002",
                DEMO_PASSWORD,
                "Zimbabwe",
                Set.of(UserRole.TENANT)
        ));
        AppUser agent = userService.create(new UserDtos.CreateUserRequest(
                "Simba Dube",
                "agent@estatex.africa",
                "+263771000003",
                DEMO_PASSWORD,
                "Zimbabwe",
                Set.of(UserRole.AGENT)
        ));
        AppUser diasporaInvestor = userService.create(new UserDtos.CreateUserRequest(
                "Rudo Chikowore",
                "diaspora@estatex.africa",
                "+447700900004",
                DEMO_PASSWORD,
                "Zimbabwe",
                Set.of(UserRole.DIASPORA, UserRole.INVESTOR)
        ));
        AppUser admin = userService.create(new UserDtos.CreateUserRequest(
                "EstateX Ops",
                "admin@estatex.africa",
                "+263771000005",
                DEMO_PASSWORD,
                "Zimbabwe",
                Set.of(UserRole.ADMIN)
        ));

        userService.updateProfile(1L, new UserDtos.UpdateProfileRequest(
                null, null, null, null, null,
                "Verified landlord managing rentals in Harare.",
                "Harare", null, null, "Property owner", null,
                null, null, true, true, false
        ));
        userService.updateProfile(2L, new UserDtos.UpdateProfileRequest(
                null, null, null, null, null,
                "Tenant looking for verified rentals with escrow protection.",
                "Harare", null, null, "Software developer", null,
                "Tariro Ncube", "+263772000000", true, true, false
        ));
        userService.updateProfile(3L, new UserDtos.UpdateProfileRequest(
                null, null, null, null, null,
                "Licensed agent supporting property verification.",
                "Harare", null, null, "Real estate agent", "EstateX Verification",
                null, null, true, true, false
        ));
        userService.updateProfile(4L, new UserDtos.UpdateProfileRequest(
                null, null, null, null, null,
                "Diaspora investor managing Zimbabwe property remotely.",
                "London", "United Kingdom", null, "Healthcare consultant", null,
                null, null, true, true, true
        ));
        userService.verify(1L);
        userService.verify(3L);

        Property borrowdaleApartment = propertyService.create(new PropertyDtos.CreatePropertyRequest(
                "Borrowdale Garden Apartment",
                "Two-bedroom apartment near schools with escrow-protected deposit support.",
                ListingType.RENT,
                "Harare",
                "Borrowdale",
                "Borrowdale Road",
                "Zimbabwe",
                2,
                2,
                new BigDecimal("550.00"),
                "USD",
                null,
                null,
                true,
                true,
                true,
                true,
                WaterSource.MUNICIPAL,
                true,
                true,
                true,
                true,
                false,
                1L,
                3L,
                List.of("https://images.unsplash.com/photo-1560448204-e02f11c3d0e2"),
                null,
                null,
                null
        ));
        propertyService.markVerifiedBySystem(borrowdaleApartment.getId(), "Demo listing verified");

        propertyService.create(new PropertyDtos.CreatePropertyRequest(
                "Avondale Townhouse",
                "Family townhouse with secure parking and diaspora-friendly management.",
                ListingType.SALE,
                "Harare",
                "Avondale",
                "King George Road",
                "Zimbabwe",
                3,
                2,
                new BigDecimal("125000.00"),
                "USD",
                null,
                null,
                true,
                false,
                false,
                false,
                WaterSource.BOREHOLE,
                false,
                false,
                true,
                true,
                false,
                1L,
                3L,
                List.of("https://images.unsplash.com/photo-1564013799919-ab600027ffc6"),
                null,
                null,
                null
        ));

        Lease lease = leaseService.create(new LeaseDtos.CreateLeaseRequest(
                borrowdaleApartment.getId(),
                tenant.getId(),
                landlord.getId(),
                LocalDate.now().plusDays(7),
                LocalDate.now().plusYears(1),
                new BigDecimal("550.00"),
                new BigDecimal("550.00"),
                "USD",
                "Standard demo lease with escrow-protected deposit."
        ), landlord);
        leaseService.sign(lease.getId(), landlord);
        leaseService.sign(lease.getId(), tenant);

        escrowService.fund(escrowService.create(new EscrowDtos.CreateEscrowRequest(
                borrowdaleApartment.getId(),
                lease.getId(),
                new BigDecimal("550.00"),
                "USD",
                "Deposit protection"
        ), tenant).getId(), new EscrowDtos.FundEscrowRequest(FundingMethod.BANK_TRANSFER, "CBZ Bank"), tenant);

        paymentService.markSuccessful(paymentService.create(new PaymentDtos.CreatePaymentRequest(
                landlord.getId(),
                borrowdaleApartment.getId(),
                lease.getId(),
                new BigDecimal("550.00"),
                "USD",
                "manual",
                "First month rent"
        ), tenant).getId(), landlord);

        maintenanceService.create(new MaintenanceDtos.CreateMaintenanceRequest(
                borrowdaleApartment.getId(),
                "Plumbing",
                "NORMAL",
                "Kitchen tap needs inspection."
        ), tenant);

        var reit = investmentService.createReit(new InvestmentDtos.CreateReitRequest(
                "Harare Residential REIT",
                "Diversified residential income portfolio.",
                "Zimbabwe",
                new BigDecimal("10.00"),
                new BigDecimal("8.50"),
                "MEDIUM",
                true,
                new BigDecimal("50000"),
                "RESIDENTIAL",
                null,
                null,
                null
        ), admin);
        investmentService.seedActiveInvestment(reit.id(), new BigDecimal("25.00"), "USD", diasporaInvestor);
    }
}
