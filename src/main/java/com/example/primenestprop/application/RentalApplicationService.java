package com.example.primenestprop.application;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.property.PropertyService;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RentalApplicationService {
    private static final Set<ApplicationStatus> OWNER_REVIEW_STATUSES = Set.of(
            ApplicationStatus.UNDER_REVIEW, ApplicationStatus.APPROVED,
            ApplicationStatus.REJECTED, ApplicationStatus.LEASE_PREPARATION
    );

    private final RentalApplicationRepository applications;
    private final PropertyService properties;

    public RentalApplicationService(RentalApplicationRepository applications, PropertyService properties) {
        this.applications = applications;
        this.properties = properties;
    }

    @Transactional
    public RentalApplication create(RentalApplicationDtos.CreateApplicationRequest request, AppUser currentUser) {
        if (!currentUser.getRoles().contains(UserRole.TENANT) && !currentUser.getRoles().contains(UserRole.DIASPORA)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only tenants can apply for a property");
        }
        Property property = properties.require(request.propertyId());
        applications.findFirstByPropertyAndApplicantAndStatusNotOrderByCreatedAtDesc(
                        property, currentUser, ApplicationStatus.REJECTED)
                .ifPresent(existing -> {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "You already have an active application for this property");
                });

        RentalApplication application = new RentalApplication();
        application.setProperty(property);
        application.setApplicant(currentUser);
        application.setDesiredMoveInDate(request.desiredMoveInDate());
        application.setMonthlyIncome(request.monthlyIncome());
        application.setMessage(request.message());
        if (request.saveAsDraft()) {
            application.setStatus(ApplicationStatus.DRAFT);
        } else {
            advanceFromDraft(application, currentUser);
        }
        return applications.save(application);
    }

    @Transactional
    public RentalApplication submit(Long id, AppUser currentUser) {
        RentalApplication application = require(id);
        requireApplicant(application, currentUser);
        if (application.getStatus() != ApplicationStatus.DRAFT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only a draft application can be submitted");
        }
        advanceFromDraft(application, currentUser);
        return application;
    }

    @Transactional
    public RentalApplication review(Long id, AppUser currentUser, RentalApplicationDtos.ReviewApplicationRequest request) {
        RentalApplication application = require(id);
        requireOwnerOrAdmin(application, currentUser);
        if (!OWNER_REVIEW_STATUSES.contains(request.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "status must be one of " + OWNER_REVIEW_STATUSES);
        }
        application.setStatus(request.status());
        application.setReviewNote(request.reviewNote());
        application.setReviewedAt(Instant.now());
        return application;
    }

    @Transactional(readOnly = true)
    public RentalApplication require(Long id) {
        return applications.findWithDetailsById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Application not found"));
    }

    @Transactional(readOnly = true)
    public RentalApplication requireVisible(Long id, AppUser currentUser) {
        RentalApplication application = require(id);
        if (isApplicant(application, currentUser) || isOwner(application, currentUser) || isAdmin(currentUser)) {
            return application;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "You cannot view this application");
    }

    @Transactional(readOnly = true)
    public List<RentalApplication> mine(AppUser currentUser) {
        return applications.findByApplicantOrderByCreatedAtDesc(currentUser);
    }

    @Transactional(readOnly = true)
    public List<RentalApplication> received(AppUser currentUser) {
        return applications.findForOwner(currentUser.getId());
    }

    private void advanceFromDraft(RentalApplication application, AppUser applicant) {
        application.setSubmittedAt(Instant.now());
        application.setStatus(applicant.isIdentityVerified()
                ? ApplicationStatus.UNDER_REVIEW
                : ApplicationStatus.VERIFICATION_REQUIRED);
    }

    private void requireApplicant(RentalApplication application, AppUser currentUser) {
        if (!isApplicant(application, currentUser)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only manage your own application");
        }
    }

    private void requireOwnerOrAdmin(RentalApplication application, AppUser currentUser) {
        if (!isOwner(application, currentUser) && !isAdmin(currentUser)) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Only the property landlord or the agent representing this property can review this application");
        }
    }

    private boolean isApplicant(RentalApplication application, AppUser currentUser) {
        return application.getApplicant().getId().equals(currentUser.getId());
    }

    private boolean isOwner(RentalApplication application, AppUser currentUser) {
        Property property = application.getProperty();
        if (property.getLandlord().getId().equals(currentUser.getId())) {
            return true;
        }
        return property.getAgent() != null && property.getAgent().getId().equals(currentUser.getId());
    }

    private boolean isAdmin(AppUser currentUser) {
        return currentUser.getRoles().contains(UserRole.ADMIN);
    }
}
