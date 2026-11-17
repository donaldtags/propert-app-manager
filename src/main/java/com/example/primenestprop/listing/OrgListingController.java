package com.example.primenestprop.listing;

import com.example.primenestprop.listing.ListingDtos.ListingDetail;
import com.example.primenestprop.listing.ListingDtos.MandateResponse;
import com.example.primenestprop.user.AppUser;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Organisation listings: compliance gates, PREA approval, publishing and mandates. */
@RestController
@RequestMapping("/api/v1/orgs/{slug}")
public class OrgListingController {
    private final OrgListingService listings;
    private final MandateService mandates;

    public OrgListingController(OrgListingService listings, MandateService mandates) {
        this.listings = listings;
        this.mandates = mandates;
    }

    @GetMapping("/listings")
    List<ListingDtos.ListingSummary> list(@PathVariable String slug, @AuthenticationPrincipal AppUser user) {
        return listings.list(slug, user);
    }

    @PostMapping("/listings")
    ListingDetail create(@PathVariable String slug, @Valid @RequestBody ListingDtos.CreateListingRequest request,
                         @AuthenticationPrincipal AppUser user) {
        Long id = listings.create(slug, request, user).getId();
        return listings.detail(slug, id, user);
    }

    @GetMapping("/listings/{propertyId}")
    ListingDetail detail(@PathVariable String slug, @PathVariable Long propertyId, @AuthenticationPrincipal AppUser user) {
        return listings.detail(slug, propertyId, user);
    }

    @PutMapping("/listings/{propertyId}/title")
    ListingDetail setTitle(@PathVariable String slug, @PathVariable Long propertyId,
                           @Valid @RequestBody ListingDtos.SetTitleRequest request, @AuthenticationPrincipal AppUser user) {
        return listings.setTitle(slug, propertyId, request, user);
    }

    @PutMapping("/listings/{propertyId}/verification-checklist")
    ListingDetail checklist(@PathVariable String slug, @PathVariable Long propertyId,
                            @Valid @RequestBody ListingDtos.ChecklistRequest request, @AuthenticationPrincipal AppUser user) {
        return listings.setChecklist(slug, propertyId, request.checked(), user);
    }

    @PostMapping("/listings/{propertyId}/title-verification")
    ListingDetail verifyTitle(@PathVariable String slug, @PathVariable Long propertyId,
                              @Valid @RequestBody ListingDtos.TitleVerificationRequest request,
                              @AuthenticationPrincipal AppUser user) {
        return listings.verifyTitle(slug, propertyId, request, user);
    }

    @PutMapping("/listings/{propertyId}/consent")
    ListingDetail consent(@PathVariable String slug, @PathVariable Long propertyId,
                          @Valid @RequestBody ListingDtos.ConsentRequest request, @AuthenticationPrincipal AppUser user) {
        return listings.recordConsent(slug, propertyId, request, user);
    }

    @PostMapping("/listings/{propertyId}/approve")
    ListingDetail approve(@PathVariable String slug, @PathVariable Long propertyId, @AuthenticationPrincipal AppUser user) {
        return listings.approve(slug, propertyId, user);
    }

    @PostMapping("/listings/{propertyId}/publish")
    ListingDetail publish(@PathVariable String slug, @PathVariable Long propertyId, @AuthenticationPrincipal AppUser user) {
        return listings.publish(slug, propertyId, user);
    }

    @PostMapping("/listings/{propertyId}/unpublish")
    ListingDetail unpublish(@PathVariable String slug, @PathVariable Long propertyId,
                            @RequestBody(required = false) ListingDtos.UnpublishRequest request,
                            @AuthenticationPrincipal AppUser user) {
        return listings.unpublish(slug, propertyId, request == null ? null : request.reason(), user);
    }

    @PostMapping("/listings/{propertyId}/mandates")
    MandateResponse createMandate(@PathVariable String slug, @PathVariable Long propertyId,
                                  @Valid @RequestBody ListingDtos.CreateMandateRequest request,
                                  @AuthenticationPrincipal AppUser user) {
        return MandateResponse.from(mandates.create(slug, propertyId, request, user), null);
    }

    @PostMapping(value = "/mandates/{mandateId}/signed-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    MandateResponse attachSignedDocument(@PathVariable String slug, @PathVariable Long mandateId,
                                         @RequestPart("file") MultipartFile file,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ownerSignedOn,
                                         @AuthenticationPrincipal AppUser user) {
        return MandateResponse.from(mandates.attachSignedDocument(slug, mandateId, file, ownerSignedOn, user), null);
    }

    @PostMapping("/mandates/{mandateId}/sign-off")
    MandateResponse signOff(@PathVariable String slug, @PathVariable Long mandateId, @AuthenticationPrincipal AppUser user) {
        return MandateResponse.from(mandates.signOff(slug, mandateId, user), user.getFullName());
    }

    @PostMapping("/mandates/{mandateId}/cancel")
    MandateResponse cancel(@PathVariable String slug, @PathVariable Long mandateId,
                           @RequestBody(required = false) ListingDtos.UnpublishRequest request,
                           @AuthenticationPrincipal AppUser user) {
        return MandateResponse.from(mandates.cancel(slug, mandateId, request == null ? null : request.reason(), user), null);
    }

    @GetMapping("/mandates/{mandateId}/signed-document")
    ResponseEntity<Resource> document(@PathVariable String slug, @PathVariable Long mandateId,
                                      @AuthenticationPrincipal AppUser user) throws IOException {
        MandateService.StoredDocument doc = mandates.document(slug, mandateId, user);
        if (doc.redirectUrl() != null) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(doc.redirectUrl())).build();
        }
        if (!Files.exists(doc.localPath())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(doc.fileName()).build().toString())
                .body(new FileSystemResource(doc.localPath()));
    }
}
