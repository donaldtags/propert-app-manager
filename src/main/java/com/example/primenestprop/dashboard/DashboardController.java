package com.example.primenestprop.dashboard;

import com.example.primenestprop.escrow.EscrowDtos.EscrowResponse;
import com.example.primenestprop.escrow.EscrowService;
import com.example.primenestprop.lease.LeaseDtos.LeaseResponse;
import com.example.primenestprop.lease.LeaseService;
import com.example.primenestprop.property.PropertyDtos.PropertyResponse;
import com.example.primenestprop.property.PropertyService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboards")
public class DashboardController {
    private final PropertyService properties;
    private final LeaseService leases;
    private final EscrowService escrows;

    public DashboardController(PropertyService properties, LeaseService leases, EscrowService escrows) {
        this.properties = properties;
        this.leases = leases;
        this.escrows = escrows;
    }

    @GetMapping("/landlords/{landlordId}")
    LandlordDashboard landlord(@PathVariable Long landlordId) {
        return new LandlordDashboard(
                properties.forLandlord(landlordId).stream().map(PropertyResponse::from).toList(),
                leases.forLandlord(landlordId).stream().map(LeaseResponse::from).toList(),
                escrows.forUser(landlordId).stream().map(EscrowResponse::from).toList()
        );
    }

    @GetMapping("/tenants/{tenantId}")
    TenantDashboard tenant(@PathVariable Long tenantId) {
        return new TenantDashboard(
                leases.forTenant(tenantId).stream().map(LeaseResponse::from).toList(),
                escrows.forUser(tenantId).stream().map(EscrowResponse::from).toList()
        );
    }

    public record LandlordDashboard(
            List<PropertyResponse> properties,
            List<LeaseResponse> leases,
            List<EscrowResponse> escrows
    ) {
    }

    public record TenantDashboard(List<LeaseResponse> leases, List<EscrowResponse> escrows) {
    }
}
