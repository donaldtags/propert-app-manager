package com.example.primenestprop.lease;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.property.PropertyService;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.UserRole;
import com.example.primenestprop.user.UserService;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaseService {
    private final LeaseRepository leases;
    private final PropertyService properties;
    private final UserService users;

    public LeaseService(LeaseRepository leases, PropertyService properties, UserService users) {
        this.leases = leases;
        this.properties = properties;
        this.users = users;
    }

    @Transactional
    public Lease create(LeaseDtos.CreateLeaseRequest request) {
        Property property = properties.require(request.propertyId());
        AppUser tenant = users.require(request.tenantId());
        if (request.landlordId() != null && !property.getLandlord().getId().equals(request.landlordId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "landlordId must match the property landlord");
        }
        if (!tenant.getRoles().contains(UserRole.TENANT) && !tenant.getRoles().contains(UserRole.DIASPORA)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "tenantId must belong to a tenant or diaspora user");
        }
        Lease lease = new Lease();
        lease.setProperty(property);
        lease.setTenant(tenant);
        lease.setLandlord(property.getLandlord());
        lease.setStartDate(request.startDate());
        lease.setEndDate(request.endDate());
        lease.setMonthlyRent(request.monthlyRent());
        lease.setDepositAmount(request.depositAmount());
        lease.setCurrency(request.currency() == null || request.currency().isBlank() ? "USD" : request.currency());
        lease.setTerms(request.terms());
        lease.setStatus(LeaseStatus.SENT);
        return leases.save(lease);
    }

    public Lease require(Long id) {
        return leases.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Lease not found"));
    }

    public List<Lease> forTenant(Long tenantId) {
        return leases.findByTenant(users.require(tenantId));
    }

    public List<Lease> forLandlord(Long landlordId) {
        return leases.findByLandlord(users.require(landlordId));
    }

    @Transactional
    public Lease sign(Long id, Long userId) {
        Lease lease = require(id);
        AppUser signer = users.require(userId);
        if (signer.getId().equals(lease.getTenant().getId())) {
            lease.setTenantSignedAt(Instant.now());
        } else if (signer.getId().equals(lease.getLandlord().getId())) {
            lease.setLandlordSignedAt(Instant.now());
        } else {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the tenant or landlord can sign this lease");
        }
        if (lease.getTenantSignedAt() != null && lease.getLandlordSignedAt() != null) {
            lease.setStatus(LeaseStatus.SIGNED);
            if (lease.getSignedAt() == null) {
                lease.setSignedAt(Instant.now());
            }
        }
        return lease;
    }
}
