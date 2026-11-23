package com.example.estatex.property;

import static com.example.estatex.common.AuditService.entry;

import com.example.estatex.common.ApiException;
import com.example.estatex.common.AuditService;
import com.example.estatex.notification.NotificationService;
import com.example.estatex.notification.NotificationType;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.Permission;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Report this listing". Reports reach the verification team; when several different people
 * report the same listing it loses its badge and goes to the top of the re-check queue straight
 * away, without waiting for staff.
 */
@Service
public class ListingReportService {
    public static final Set<String> REASONS = Set.of(
            "FAKE_OR_SCAM", "NOT_AVAILABLE", "WRONG_DETAILS", "STOLEN_PHOTOS", "OFFENSIVE", "OTHER");
    /** Distinct reporters that automatically send a listing for a re-check. */
    static final int AUTO_FLAG_THRESHOLD = 3;

    private final ListingReportRepository reports;
    private final PropertyRepository properties;
    private final AuditService audit;
    private final NotificationService notifications;

    public ListingReportService(ListingReportRepository reports, PropertyRepository properties, AuditService audit,
                                NotificationService notifications) {
        this.reports = reports;
        this.properties = properties;
        this.audit = audit;
        this.notifications = notifications;
    }

    @Transactional
    public ListingReport report(Long propertyId, String reason, String details, AppUser reporter, String clientIp) {
        Property property = properties.findById(propertyId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Property not found"));
        if (reason == null || !REASONS.contains(reason)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose what's wrong with this listing");
        }
        if (reporter != null && reporter.getId().equals(property.getLandlord().getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You can't report your own listing");
        }
        String fingerprint = sha256(clientIp == null ? "" : clientIp);
        List<ListingReport> open = reports.findByPropertyIdAndStatus(propertyId, ListingReport.OPEN);
        boolean alreadyReported = open.stream().anyMatch(r -> reporter != null
                ? reporter.getId().equals(r.getReporterUserId())
                : r.getReporterUserId() == null && fingerprint.equals(r.getReporterFingerprint()));
        if (alreadyReported) {
            throw new ApiException(HttpStatus.CONFLICT, "Thanks - you've already reported this listing. Our team is looking at it.");
        }

        ListingReport report = new ListingReport();
        report.setPropertyId(propertyId);
        report.setReporterUserId(reporter == null ? null : reporter.getId());
        report.setReporterFingerprint(fingerprint);
        report.setReason(reason);
        report.setDetails(details == null || details.isBlank() ? null : details.trim().substring(0, Math.min(details.trim().length(), 1000)));
        reports.save(report);
        open.add(report);

        audit.record(reporter, entry("listing.reported", "Property", propertyId)
                .metadata(Map.of("reason", reason, "reportId", report.getId())));

        Set<String> reporters = new HashSet<>();
        open.forEach(r -> reporters.add(r.getReporterUserId() != null ? "u" + r.getReporterUserId() : "ip" + r.getReporterFingerprint()));
        if (reporters.size() >= AUTO_FLAG_THRESHOLD && !property.isOrganisationListing()
                && (property.getVerificationStatus() == VerificationStatus.VERIFIED
                || property.getVerificationStatus() == VerificationStatus.UNVERIFIED)) {
            VerificationStatus from = property.getVerificationStatus();
            property.setVerificationStatus(VerificationStatus.PENDING);
            property.setVerifiedAt(null);
            audit.record(null, entry("fraud.reported_by_several", "Property", propertyId)
                    .transition(from, VerificationStatus.PENDING)
                    .metadata(Map.of("distinctReporters", reporters.size())));
        }
        return report;
    }

    @Transactional(readOnly = true)
    public List<ListingReport> open(AppUser staff) {
        requireStaff(staff);
        return reports.findByStatusOrderByCreatedAtAsc(ListingReport.OPEN);
    }

    /**
     * Staff decision. TAKE_DOWN removes the listing from the site (INACTIVE + REJECTED) and closes
     * every open report on it; DISMISS closes just this report.
     */
    @Transactional
    public ListingReport resolve(Long reportId, String action, String note, AppUser staff) {
        requireStaff(staff);
        ListingReport report = reports.findById(reportId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Report not found"));
        if (!ListingReport.OPEN.equals(report.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "This report was already handled");
        }
        boolean takeDown = "TAKE_DOWN".equals(action);
        if (!takeDown && !"DISMISS".equals(action)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "action must be TAKE_DOWN or DISMISS");
        }
        if (takeDown && (note == null || note.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Give the owner a reason for taking the listing down");
        }
        List<ListingReport> toClose = takeDown
                ? reports.findByPropertyIdAndStatus(report.getPropertyId(), ListingReport.OPEN)
                : List.of(report);
        for (ListingReport r : toClose) {
            r.setStatus(takeDown ? ListingReport.TAKEN_DOWN : ListingReport.DISMISSED);
            r.setResolvedAt(Instant.now());
            r.setResolvedByUserId(staff.getId());
            r.setResolutionNote(note == null || note.isBlank() ? null : note.trim());
        }
        if (takeDown) {
            Property property = properties.findById(report.getPropertyId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Property not found"));
            PropertyStatus fromStatus = property.getStatus();
            property.setStatus(PropertyStatus.INACTIVE);
            property.setVerificationStatus(VerificationStatus.REJECTED);
            property.setVerifiedAt(null);
            audit.record(staff, entry("listing.taken_down", "Property", property.getId())
                    .transition(fromStatus, PropertyStatus.INACTIVE)
                    .metadata(Map.of("note", note.trim(), "reports", toClose.size())));
            notifications.notify(property.getLandlord(), NotificationType.SECURITY_ALERT, property.getId(),
                    "Your listing was taken down",
                    "\"" + property.getTitle() + "\" was removed after reports from visitors: " + note.trim()
                            + " Contact EstateX support if you think this is a mistake.");
        } else {
            audit.record(staff, entry("listing.report_dismissed", "ListingReport", report.getId())
                    .metadata(Map.of("propertyId", report.getPropertyId())));
        }
        return report;
    }

    private static void requireStaff(AppUser user) {
        if (user == null || !user.hasPermission(Permission.PROPERTY_VERIFY)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only EstateX verification staff handle listing reports");
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
