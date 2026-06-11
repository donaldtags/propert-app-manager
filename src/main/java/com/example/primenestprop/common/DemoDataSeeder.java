package com.example.primenestprop.common;

import com.example.primenestprop.escrow.EscrowDtos;
import com.example.primenestprop.escrow.EscrowService;
import com.example.primenestprop.investment.InvestmentDtos;
import com.example.primenestprop.investment.InvestmentService;
import com.example.primenestprop.lease.Lease;
import com.example.primenestprop.lease.LeaseDtos;
import com.example.primenestprop.lease.LeaseService;
import com.example.primenestprop.maintenance.MaintenanceDtos;
import com.example.primenestprop.maintenance.MaintenanceService;
import com.example.primenestprop.payment.PaymentDtos;
import com.example.primenestprop.payment.PaymentService;
import com.example.primenestprop.property.ListingType;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.property.PropertyDtos;
import com.example.primenestprop.property.PropertyService;
import com.example.primenestprop.user.UserDtos;
import com.example.primenestprop.user.UserRepository;
import com.example.primenestprop.user.UserRole;
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
    private final com.example.primenestprop.user.UserService userService;
    private final PropertyService propertyService;
    private final LeaseService leaseService;
    private final EscrowService escrowService;
    private final PaymentService paymentService;
    private final MaintenanceService maintenanceService;
    private final InvestmentService investmentService;

    DemoDataSeeder(
            UserRepository users,
            com.example.primenestprop.user.UserService userService,
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

        userService.create(new UserDtos.CreateUserRequest(
                "Tariro Moyo",
                "landlord@primenest.africa",
                "+263771000001",
                DEMO_PASSWORD,
                "Zimbabwe",
                Set.of(UserRole.LANDLORD)
        ));
        userService.create(new UserDtos.CreateUserRequest(
                "Nadia Ncube",
                "tenant@primenest.africa",
                "+263771000002",
                DEMO_PASSWORD,
                "Zimbabwe",
                Set.of(UserRole.TENANT)
        ));
        userService.create(new UserDtos.CreateUserRequest(
                "Simba Dube",
                "agent@primenest.africa",
                "+263771000003",
                DEMO_PASSWORD,
                "Zimbabwe",
                Set.of(UserRole.AGENT)
        ));
        userService.create(new UserDtos.CreateUserRequest(
                "Rudo Chikowore",
                "diaspora@primenest.africa",
                "+447700900004",
                DEMO_PASSWORD,
                "Zimbabwe",
                Set.of(UserRole.DIASPORA, UserRole.INVESTOR)
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
                "Harare", null, null, "Real estate agent", "PrimeNest Verification",
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
                1L,
                3L,
                List.of("https://images.unsplash.com/photo-1560448204-e02f11c3d0e2"),
                null,
                null
        ));
        propertyService.verify(borrowdaleApartment.getId(), new PropertyDtos.VerifyPropertyRequest(3L, "Demo listing verified"));

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
                1L,
                3L,
                List.of("https://images.unsplash.com/photo-1564013799919-ab600027ffc6"),
                null,
                null
        ));

        Lease lease = leaseService.create(new LeaseDtos.CreateLeaseRequest(
                borrowdaleApartment.getId(),
                2L,
                1L,
                LocalDate.now().plusDays(7),
                LocalDate.now().plusYears(1),
                new BigDecimal("550.00"),
                new BigDecimal("550.00"),
                "USD",
                "Standard demo lease with escrow-protected deposit."
        ));
        leaseService.sign(lease.getId(), 1L);

        escrowService.fund(escrowService.create(new EscrowDtos.CreateEscrowRequest(
                borrowdaleApartment.getId(),
                lease.getId(),
                2L,
                new BigDecimal("550.00"),
                "USD",
                "Deposit protection"
        )).getId());

        paymentService.markSuccessful(paymentService.create(new PaymentDtos.CreatePaymentRequest(
                2L,
                1L,
                borrowdaleApartment.getId(),
                lease.getId(),
                new BigDecimal("550.00"),
                "USD",
                "manual",
                "First month rent"
        )).getId());

        maintenanceService.create(new MaintenanceDtos.CreateMaintenanceRequest(
                borrowdaleApartment.getId(),
                2L,
                "Plumbing",
                "NORMAL",
                "Kitchen tap needs inspection."
        ));

        var reit = investmentService.createReit(new InvestmentDtos.CreateReitRequest(
                "Harare Residential REIT",
                "Diversified residential income portfolio.",
                "Zimbabwe",
                new BigDecimal("10.00"),
                new BigDecimal("8.50"),
                "MEDIUM",
                true
        ));
        investmentService.invest(new InvestmentDtos.CreateInvestmentRequest(
                4L,
                reit.getId(),
                new BigDecimal("25.00"),
                "USD"
        ));
    }
}
