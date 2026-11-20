package com.example.estatex.property;

import com.example.estatex.user.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ListingReportController {
    private final ListingReportService service;
    private final PropertyRepository properties;

    public ListingReportController(ListingReportService service, PropertyRepository properties) {
        this.service = service;
        this.properties = properties;
    }

    public record ReportRequest(String reason, String details) {}

    public record ResolveRequest(String action, String note) {}

    public record ReportResponse(Long id, Long propertyId, String propertyTitle, String reason, String details,
                                 String status, Instant createdAt, boolean signedInReporter) {}

    /** Public: no account needed (rate-limited per sender like the enquiry form). */
    @PostMapping("/api/v1/properties/{id}/reports")
    ReportResponse report(@PathVariable Long id, @RequestBody ReportRequest request,
                          @AuthenticationPrincipal AppUser currentUser, HttpServletRequest http) {
        return toResponse(service.report(id, request.reason(), request.details(), currentUser, http.getRemoteAddr()));
    }

    @GetMapping("/api/v1/listing-reports")
    List<ReportResponse> open(@AuthenticationPrincipal AppUser currentUser) {
        return service.open(currentUser).stream().map(this::toResponse).toList();
    }

    @PatchMapping("/api/v1/listing-reports/{id}")
    ReportResponse resolve(@PathVariable Long id, @RequestBody ResolveRequest request,
                           @AuthenticationPrincipal AppUser currentUser) {
        return toResponse(service.resolve(id, request.action(), request.note(), currentUser));
    }

    private ReportResponse toResponse(ListingReport r) {
        String title = properties.findById(r.getPropertyId()).map(Property::getTitle).orElse("(deleted listing)");
        return new ReportResponse(r.getId(), r.getPropertyId(), title, r.getReason(), r.getDetails(), r.getStatus(),
                r.getCreatedAt(), r.getReporterUserId() != null);
    }
}
