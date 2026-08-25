package com.example.primenestprop.viewing;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.property.PropertyService;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.Permission;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewingService {
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no O/0/I/1 ambiguity
    private final SecureRandom random = new SecureRandom();

    private final ViewingRequestRepository viewings;
    private final PropertyService properties;

    public ViewingService(ViewingRequestRepository viewings, PropertyService properties) {
        this.viewings = viewings;
        this.properties = properties;
    }

    @Transactional
    public ViewingRequest create(ViewingDtos.CreateViewingRequest request, AppUser requester) {
        Property property = properties.require(request.propertyId());
        ViewingRequest viewing = new ViewingRequest();
        viewing.setProperty(property);
        viewing.setRequester(requester);
        viewing.setMode(request.mode());
        viewing.setPreferredDate(request.preferredDate());
        viewing.setPreferredTime(request.preferredTime());
        viewing.setNotes(request.notes());
        viewing.setCheckInCode(generateUniqueCode());
        return viewings.save(viewing);
    }

    @Transactional
    public ViewingRequest confirm(Long id, ViewingDtos.ConfirmViewingRequest request, AppUser currentUser) {
        ViewingRequest viewing = require(id);
        assertLandlordOrAgentOrAdmin(viewing, currentUser);
        if (viewing.getStatus() != ViewingStatus.REQUESTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only a requested viewing can be confirmed");
        }
        viewing.setStatus(ViewingStatus.CONFIRMED);
        viewing.setConfirmedAt(Instant.now());
        if (viewing.getMode() == ViewingMode.VIDEO_CALL) {
            viewing.setVideoCallLink(request.videoCallLink());
        }
        return viewing;
    }

    @Transactional
    public ViewingRequest decline(Long id, AppUser currentUser) {
        ViewingRequest viewing = require(id);
        assertLandlordOrAgentOrAdmin(viewing, currentUser);
        if (viewing.getStatus() != ViewingStatus.REQUESTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only a requested viewing can be declined");
        }
        viewing.setStatus(ViewingStatus.DECLINED);
        return viewing;
    }

    @Transactional
    public ViewingRequest cancel(Long id, AppUser currentUser) {
        ViewingRequest viewing = require(id);
        boolean isRequester = viewing.getRequester().getId().equals(currentUser.getId());
        if (!isRequester) {
            assertLandlordOrAgentOrAdmin(viewing, currentUser);
        }
        if (viewing.getStatus() == ViewingStatus.COMPLETED || viewing.getStatus() == ViewingStatus.CANCELLED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This viewing can no longer be cancelled");
        }
        viewing.setStatus(ViewingStatus.CANCELLED);
        viewing.setCancelledAt(Instant.now());
        return viewing;
    }

    @Transactional
    public ViewingRequest checkIn(Long id, ViewingDtos.CheckInRequest request, AppUser currentUser) {
        ViewingRequest viewing = require(id);
        boolean isParty = viewing.getRequester().getId().equals(currentUser.getId())
                || isLandlordOrAgent(viewing, currentUser)
                || currentUser.hasPermission(Permission.ADMIN_OVERRIDE);
        if (!isParty) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only someone involved in this viewing can check it in");
        }
        if (viewing.getStatus() != ViewingStatus.CONFIRMED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only a confirmed viewing can be checked in");
        }
        if (viewing.getCheckInCode() == null || !viewing.getCheckInCode().equalsIgnoreCase(request.code().trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Check-in code does not match");
        }
        viewing.setStatus(ViewingStatus.COMPLETED);
        viewing.setCompletedAt(Instant.now());
        return viewing;
    }

    @Transactional
    public ViewingRequest submitFeedback(Long id, ViewingDtos.FeedbackRequest request, AppUser currentUser) {
        ViewingRequest viewing = require(id);
        if (!viewing.getRequester().getId().equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the person who booked this viewing can leave feedback");
        }
        if (viewing.getStatus() != ViewingStatus.COMPLETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Feedback can only be left after the viewing is completed");
        }
        viewing.setFeedbackRating(request.rating());
        viewing.setFeedbackComment(request.comment());
        return viewing;
    }

    @Transactional(readOnly = true)
    public ViewingRequest require(Long id) {
        return viewings.findWithDetailsById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Viewing request not found"));
    }

    @Transactional(readOnly = true)
    public List<ViewingRequest> forRequester(AppUser requester) {
        return viewings.findByRequesterOrderByCreatedAtDesc(requester);
    }

    @Transactional(readOnly = true)
    public List<ViewingRequest> forLandlord(AppUser landlord) {
        return viewings.findByProperty_LandlordOrderByCreatedAtDesc(landlord);
    }

    @Transactional(readOnly = true)
    public List<ViewingRequest> forAgent(AppUser agent) {
        return viewings.findByProperty_AgentOrderByCreatedAtDesc(agent);
    }

    private void assertLandlordOrAgentOrAdmin(ViewingRequest viewing, AppUser currentUser) {
        if (isLandlordOrAgent(viewing, currentUser) || currentUser.hasPermission(Permission.ADMIN_OVERRIDE)) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "Only the landlord, representing agent, or an admin can manage this viewing");
    }

    private boolean isLandlordOrAgent(ViewingRequest viewing, AppUser currentUser) {
        Property property = viewing.getProperty();
        if (property.getLandlord().getId().equals(currentUser.getId())) {
            return true;
        }
        return property.getAgent() != null && property.getAgent().getId().equals(currentUser.getId());
    }

    private String generateUniqueCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            code = sb.toString();
        } while (viewings.existsByCheckInCode(code));
        return code;
    }
}
