package com.example.primenestprop.lease;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.UserRole;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaseActionService {
    private final LeaseActionRequestRepository actions;
    private final LeaseService leases;

    public LeaseActionService(LeaseActionRequestRepository actions, LeaseService leases) {
        this.actions = actions;
        this.leases = leases;
    }

    @Transactional
    public LeaseActionRequest create(Long leaseId, AppUser currentUser, LeaseActionDtos.CreateLeaseActionRequest request) {
        Lease lease = leases.require(leaseId);
        if (!lease.getTenant().getId().equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the tenant on this lease can request a renewal or termination");
        }
        if (lease.getStatus() != LeaseStatus.ACTIVE && lease.getStatus() != LeaseStatus.SIGNED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This lease is not in a state that can be renewed or terminated");
        }
        LeaseActionRequest action = new LeaseActionRequest();
        action.setLease(lease);
        action.setRequestedBy(currentUser);
        action.setType(request.type());
        action.setProposedEndDate(request.proposedEndDate());
        action.setNote(request.note());
        return actions.save(action);
    }

    @Transactional
    public LeaseActionRequest review(Long id, AppUser currentUser, LeaseActionDtos.ReviewLeaseActionRequest request) {
        LeaseActionRequest action = require(id);
        requireOwnerOrAdmin(action, currentUser);
        if (action.getStatus() != LeaseActionStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This request has already been reviewed");
        }
        if (request.status() == LeaseActionStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "status must be APPROVED or DECLINED");
        }
        action.setStatus(request.status());
        action.setReviewNote(request.reviewNote());
        action.setResolvedAt(Instant.now());

        if (request.status() == LeaseActionStatus.APPROVED) {
            Lease lease = action.getLease();
            if (action.getType() == LeaseActionType.RENEWAL) {
                if (action.getProposedEndDate() != null) {
                    lease.setEndDate(action.getProposedEndDate());
                }
                lease.setStatus(LeaseStatus.ACTIVE);
            } else {
                lease.setStatus(LeaseStatus.ENDED);
                if (action.getProposedEndDate() != null) {
                    lease.setEndDate(action.getProposedEndDate());
                }
            }
        }
        return action;
    }

    @Transactional(readOnly = true)
    public LeaseActionRequest require(Long id) {
        return actions.findWithDetailsById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Lease action request not found"));
    }

    @Transactional(readOnly = true)
    public List<LeaseActionRequest> mine(AppUser currentUser) {
        return actions.findByRequestedByOrderByCreatedAtDesc(currentUser);
    }

    @Transactional(readOnly = true)
    public List<LeaseActionRequest> received(AppUser currentUser) {
        return actions.findForOwner(currentUser.getId());
    }

    private void requireOwnerOrAdmin(LeaseActionRequest action, AppUser currentUser) {
        Property property = action.getLease().getProperty();
        boolean isLandlord = action.getLease().getLandlord().getId().equals(currentUser.getId());
        boolean isAgent = property.getAgent() != null && property.getAgent().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRoles().contains(UserRole.ADMIN);
        if (!isLandlord && !isAgent && !isAdmin) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Only the landlord or the agent representing this property can review this request");
        }
    }
}
