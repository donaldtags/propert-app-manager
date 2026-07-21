package com.example.primenestprop.lease;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.property.PropertyService;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.TrustScoreService;
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
    private final TrustScoreService trustScoreService;

    public LeaseService(
            LeaseRepository leases,
            PropertyService properties,
            UserService users,
            TrustScoreService trustScoreService
    ) {
        this.leases = leases;
        this.properties = properties;
        this.users = users;
        this.trustScoreService = trustScoreService;
    }

    @Transactional
    public Lease create(LeaseDtos.CreateLeaseRequest request, AppUser currentUser) {
        Property property = properties.require(request.propertyId());
        AppUser tenant = users.require(request.tenantId());
        if (!isLandlordOrRepresentingAgent(property, currentUser) && !currentUser.getRoles().contains(UserRole.ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Only the property landlord or the agent representing this property can create a lease for it");
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

    @Transactional(readOnly = true)
    public Lease require(Long id) {
        return leases.findWithDetailsById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Lease not found"));
    }

    @Transactional(readOnly = true)
    public List<Lease> forTenant(Long tenantId, AppUser currentUser) {
        requireSelfOrAdmin(tenantId, currentUser);
        return leases.findByTenant(users.require(tenantId));
    }

    @Transactional(readOnly = true)
    public List<Lease> forLandlord(Long landlordId, AppUser currentUser) {
        requireSelfOrAdmin(landlordId, currentUser);
        return leases.findByLandlord(users.require(landlordId));
    }

    @Transactional(readOnly = true)
    public List<Lease> forAgent(Long agentId, AppUser currentUser) {
        requireSelfOrAdmin(agentId, currentUser);
        return leases.findByProperty_Agent(users.require(agentId));
    }

    @Transactional(readOnly = true)
    public long countActive() {
        return leases.countByStatus(LeaseStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public long countAll() {
        return leases.count();
    }

    @Transactional(readOnly = true)
    public List<Lease> recentlySigned() {
        return leases.findTop10ByStatusOrderBySignedAtDesc(LeaseStatus.SIGNED);
    }

    @Transactional
    public Lease sign(Long id, AppUser signer) {
        Lease lease = require(id);
        if (signer.getId().equals(lease.getTenant().getId())) {
            lease.setTenantSignedAt(Instant.now());
        } else if (signer.getId().equals(lease.getLandlord().getId())
                || (lease.getProperty().getAgent() != null && signer.getId().equals(lease.getProperty().getAgent().getId()))) {
            lease.setLandlordSignedAt(Instant.now());
        } else {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Only the tenant, the landlord, or the agent representing this property can sign this lease");
        }
        if (lease.getTenantSignedAt() != null && lease.getLandlordSignedAt() != null) {
            lease.setStatus(LeaseStatus.SIGNED);
            if (lease.getSignedAt() == null) {
                lease.setSignedAt(Instant.now());
            }
        }
        trustScoreService.recompute(lease.getTenant());
        trustScoreService.recompute(lease.getLandlord());
        if (lease.getProperty().getAgent() != null) {
            trustScoreService.recompute(lease.getProperty().getAgent());
        }
        return lease;
    }

    private void requireSelfOrAdmin(Long id, AppUser currentUser) {
        if (!id.equals(currentUser.getId()) && !currentUser.getRoles().contains(UserRole.ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only view your own leases");
        }
    }

    private boolean isLandlordOrRepresentingAgent(Property property, AppUser currentUser) {
        if (property.getLandlord().getId().equals(currentUser.getId())) {
            return true;
        }
        return property.getAgent() != null && property.getAgent().getId().equals(currentUser.getId());
    }
}
