package com.example.primenestprop.escrow;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.lease.Lease;
import com.example.primenestprop.lease.LeaseService;
import com.example.primenestprop.lease.LeaseStatus;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.property.PropertyService;
import com.example.primenestprop.subscription.SubscriptionFeature;
import com.example.primenestprop.subscription.SubscriptionService;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.Permission;
import com.example.primenestprop.user.UserService;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EscrowService {
    private static final int REQUIRED_RELEASE_APPROVALS = 2;

    private final EscrowRepository escrows;
    private final PropertyService properties;
    private final LeaseService leases;
    private final UserService users;
    private final EscrowReleaseApprovalRepository releaseApprovals;
    private final SubscriptionService subscriptions;

    public EscrowService(
            EscrowRepository escrows,
            PropertyService properties,
            LeaseService leases,
            UserService users,
            EscrowReleaseApprovalRepository releaseApprovals,
            SubscriptionService subscriptions
    ) {
        this.escrows = escrows;
        this.properties = properties;
        this.leases = leases;
        this.users = users;
        this.releaseApprovals = releaseApprovals;
        this.subscriptions = subscriptions;
    }

    @Transactional
    public EscrowTransaction create(EscrowDtos.CreateEscrowRequest request, AppUser payer) {
        Property property = properties.require(request.propertyId());
        subscriptions.requireFeature(property.getLandlord(), SubscriptionFeature.ESCROW);
        EscrowTransaction escrow = new EscrowTransaction();
        escrow.setProperty(property);
        escrow.setPayer(payer);
        escrow.setBeneficiary(property.getLandlord());
        escrow.setAmount(request.amount());
        escrow.setCurrency(request.currency() == null || request.currency().isBlank() ? "USD" : request.currency());
        escrow.setPurpose(request.purpose() == null || request.purpose().isBlank() ? "Deposit protection" : request.purpose());
        if (request.leaseId() != null) {
            Lease lease = leases.require(request.leaseId());
            escrow.setLease(lease);
        }
        return escrows.save(escrow);
    }

    @Transactional(readOnly = true)
    public EscrowTransaction require(Long id) {
        return escrows.findWithDetailsById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Escrow transaction not found"));
    }

    @Transactional(readOnly = true)
    public List<EscrowTransaction> forUser(Long userId, AppUser currentUser) {
        if (!userId.equals(currentUser.getId()) && !currentUser.hasPermission(Permission.ADMIN_OVERRIDE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only view your own escrow transactions");
        }
        AppUser user = users.require(userId);
        List<EscrowTransaction> result = new java.util.ArrayList<>(escrows.findByPayer(user));
        result.addAll(escrows.findByBeneficiary(user));
        return result;
    }

    @Transactional(readOnly = true)
    public List<EscrowTransaction> allForAdmin(AppUser currentUser) {
        if (!currentUser.hasPermission(Permission.ADMIN_OVERRIDE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only admins can view all escrow transactions");
        }
        return escrows.findAllForAdmin();
    }

    @Transactional
    public EscrowTransaction fund(Long id, EscrowDtos.FundEscrowRequest request, AppUser currentUser) {
        EscrowTransaction escrow = require(id);
        requirePartyOrAdmin(escrow, currentUser, escrow.getPayer());
        if (escrow.getStatus() != EscrowStatus.CREATED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only created escrow transactions can be funded");
        }
        if (request.method() == FundingMethod.BANK_TRANSFER && (request.provider() == null || request.provider().isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Select a bank to continue");
        }
        escrow.setStatus(EscrowStatus.FUNDED);
        escrow.setFundedAt(Instant.now());
        escrow.setFundingMethod(request.method());
        escrow.setFundingProvider(request.provider() == null || request.provider().isBlank()
                ? request.method().displayName()
                : request.provider());
        return escrow;
    }

    /**
     * Maker-checker control: releasing funds requires {@value #REQUIRED_RELEASE_APPROVALS} distinct
     * admins to approve. The escrow stays FUNDED (with a growing approval count) until enough
     * distinct admins have signed off, at which point it flips to RELEASED.
     */
    @Transactional
    public EscrowTransaction release(Long id, AppUser currentUser) {
        if (!currentUser.hasPermission(Permission.ADMIN_OVERRIDE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only admins can approve escrow release");
        }
        EscrowTransaction escrow = require(id);
        if (escrow.getStatus() != EscrowStatus.FUNDED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only funded escrow transactions can be released");
        }
        if (escrow.getLease() != null && escrow.getLease().getStatus() != LeaseStatus.SIGNED
                && escrow.getLease().getStatus() != LeaseStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Escrow can only be released after the lease is signed");
        }
        if (releaseApprovals.existsByEscrowAndApprover(escrow, currentUser)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You have already approved release of this escrow");
        }
        releaseApprovals.save(new EscrowReleaseApproval(escrow, currentUser));
        long approvals = releaseApprovals.countByEscrow(escrow);
        if (approvals < REQUIRED_RELEASE_APPROVALS) {
            return escrow;
        }
        escrow.setStatus(EscrowStatus.RELEASED);
        escrow.setReleasedAt(Instant.now());
        return escrow;
    }

    @Transactional(readOnly = true)
    public int releaseApprovalCount(EscrowTransaction escrow) {
        return (int) releaseApprovals.countByEscrow(escrow);
    }

    @Transactional(readOnly = true)
    public boolean hasApprovedRelease(EscrowTransaction escrow, AppUser user) {
        return releaseApprovals.existsByEscrowAndApprover(escrow, user);
    }

    @Transactional
    public EscrowTransaction dispute(Long id, AppUser currentUser) {
        EscrowTransaction escrow = require(id);
        requirePartyOrAdmin(escrow, currentUser, escrow.getPayer(), escrow.getBeneficiary());
        if (escrow.getStatus() != EscrowStatus.FUNDED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only funded escrow transactions can be disputed");
        }
        escrow.setStatus(EscrowStatus.DISPUTED);
        return escrow;
    }

    @Transactional(readOnly = true)
    public boolean hasDisputedEscrow(AppUser user) {
        return escrows.existsByPayerAndStatus(user, EscrowStatus.DISPUTED)
                || escrows.existsByBeneficiaryAndStatus(user, EscrowStatus.DISPUTED);
    }

    @Transactional(readOnly = true)
    public EscrowBalanceSummary balanceSummary(String currency) {
        java.math.BigDecimal totalBalance = escrows.sumAmountByStatusAndCurrency(EscrowStatus.FUNDED, currency);
        long activeCount = escrows.countByStatus(EscrowStatus.FUNDED);
        long disputedCount = escrows.countByStatus(EscrowStatus.DISPUTED);
        return new EscrowBalanceSummary(totalBalance, activeCount, disputedCount);
    }

    public record EscrowBalanceSummary(java.math.BigDecimal totalBalance, long activeCount, long disputedCount) {
    }

    @Transactional(readOnly = true)
    public List<EscrowTransaction> recentlyReleased() {
        return escrows.findTop10ByStatusOrderByReleasedAtDesc(EscrowStatus.RELEASED);
    }

    @Transactional(readOnly = true)
    public LandlordEscrowSummary landlordBalanceSummary(AppUser landlord, String currency) {
        java.math.BigDecimal balance = escrows.sumAmountByBeneficiaryAndStatusAndCurrency(landlord, EscrowStatus.FUNDED, currency);
        long activeCount = escrows.countByBeneficiaryAndStatus(landlord, EscrowStatus.FUNDED);
        return new LandlordEscrowSummary(balance, activeCount, currency);
    }

    public record LandlordEscrowSummary(java.math.BigDecimal balance, long activeCount, String currency) {
    }

    private void requirePartyOrAdmin(EscrowTransaction escrow, AppUser currentUser, AppUser... allowedParties) {
        if (currentUser.hasPermission(Permission.ADMIN_OVERRIDE)) {
            return;
        }
        for (AppUser party : allowedParties) {
            if (party != null && party.getId().equals(currentUser.getId())) {
                return;
            }
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "You are not a party to this escrow transaction");
    }
}
