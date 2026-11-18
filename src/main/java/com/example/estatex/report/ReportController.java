package com.example.estatex.report;

import com.example.estatex.common.DocumentStore;
import com.example.estatex.user.AppUser;
import java.net.URI;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportController {
    private final SellerReportService reports;
    private final ShareImageService images;

    public ReportController(SellerReportService reports, ShareImageService images) {
        this.reports = reports;
        this.images = images;
    }

    @GetMapping("/api/v1/orgs/{slug}/listings/{propertyId}/seller-reports")
    List<SellerReportService.ReportSummary> list(@PathVariable String slug, @PathVariable Long propertyId,
                                                 @AuthenticationPrincipal AppUser user) {
        return reports.list(slug, propertyId, user);
    }

    @PostMapping("/api/v1/orgs/{slug}/listings/{propertyId}/seller-reports")
    SellerReportService.ReportSummary generate(@PathVariable String slug, @PathVariable Long propertyId,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekEnding,
                                               @AuthenticationPrincipal AppUser user) {
        return reports.generate(slug, propertyId, weekEnding, user);
    }

    /** Public: the owner opens this from WhatsApp/email. The token is the only credential. */
    @GetMapping("/api/v1/shared/seller-reports/{token}")
    ResponseEntity<Resource> shared(@PathVariable String token) {
        DocumentStore.Located file = reports.shared(token);
        if (file.redirectUrl() != null) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(file.redirectUrl())).build();
        }
        if (file.localPath() == null || !Files.exists(file.localPath())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header("X-Robots-Tag", "noindex")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("seller-report.pdf").build().toString())
                .body(new FileSystemResource(file.localPath()));
    }

    @GetMapping(value = "/api/v1/orgs/{slug}/listings/{propertyId}/share-image.png", produces = MediaType.IMAGE_PNG_VALUE)
    ResponseEntity<byte[]> shareImage(@PathVariable String slug, @PathVariable Long propertyId, @AuthenticationPrincipal AppUser user) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("listing-" + propertyId + "-square.png").build().toString())
                .body(images.render(slug, propertyId, user));
    }
}
