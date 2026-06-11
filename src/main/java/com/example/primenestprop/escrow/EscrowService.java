package com.example.primenestprop.escrow;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.lease.Lease;
import com.example.primenestprop.lease.LeaseService;
import com.example.primenestprop.lease.LeaseStatus;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.property.PropertyService;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.UserService;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EscrowService {
    private final EscrowRepository escrows;
    private final PropertyService properties;
    private final LeaseService leases;
    private final UserService users;

    public EscrowService(EscrowRepository escrows, PropertyService properties, LeaseService leases, UserService users) {
        this.escrows = escrows;
        this.properties = properties;
        this.leases = leases;
        this.users = users;
    }

    @Transactional
    public EscrowTransaction create(EscrowDtos.CreateEscrowRequest request) {
        Property property = properties.require(request.propertyId());
        AppUser payer = users.require(request.payerId());
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

    public EscrowTransaction require(Long id) {
        return escrows.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Escrow transaction not found"));
    }

    public List<EscrowTransaction> forUser(Long userId) {
        AppUser user = users.require(userId);
        List<EscrowTransaction> payer = escrows.findByPayer(user);
        List<EscrowTransaction> beneficiary = escrows.findByBeneficiary(user);
        payer.addAll(beneficiary);
        return payer;
    }

    @Transactional
    public EscrowTransaction fund(Long id) {
        EscrowTransaction escrow = require(id);
        if (escrow.getStatus() != EscrowStatus.CREATED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only created escrow transactions can be funded");
        }
        escrow.setStatus(EscrowStatus.FUNDED);
        escrow.setFundedAt(Instant.now());
        return escrow;
    }

    @Transactional
    public EscrowTransaction release(Long id) {
        EscrowTransaction escrow = require(id);
        if (escrow.getStatus() != EscrowStatus.FUNDED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only funded escrow transactions can be released");
        }
        if (escrow.getLease() != null && escrow.getLease().getStatus() != LeaseStatus.SIGNED
                && escrow.getLease().getStatus() != LeaseStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Escrow can only be released after the lease is signed");
        }
        escrow.setStatus(EscrowStatus.RELEASED);
        escrow.setReleasedAt(Instant.now());
        return escrow;
    }

    @Transactional
    public EscrowTransaction dispute(Long id) {
        EscrowTransaction escrow = require(id);
        if (escrow.getStatus() != EscrowStatus.FUNDED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only funded escrow transactions can be disputed");
        }
        escrow.setStatus(EscrowStatus.DISPUTED);
        return escrow;
    }
}
