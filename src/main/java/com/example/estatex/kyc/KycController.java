package com.example.estatex.kyc;

import com.example.estatex.auth.AuthService;
import com.example.estatex.kyc.KycDtos.KycSubmissionResponse;
import com.example.estatex.kyc.KycDtos.ReviewKycRequest;
import com.example.estatex.user.AppUser;
import java.time.LocalDate;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/kyc")
public class KycController {
    private final KycService service;
    private final AuthService authService;
    private final List<com.example.estatex.kyc.check.RegistryProvider> registryProviders;
    private final com.example.estatex.kyc.check.DiditDocumentCheck documentCheck;
    private final com.example.estatex.kyc.check.IdentityCheckService identityChecks;

    public KycController(KycService service, AuthService authService,
                         List<com.example.estatex.kyc.check.RegistryProvider> registryProviders,
                         com.example.estatex.kyc.check.DiditDocumentCheck documentCheck,
                         com.example.estatex.kyc.check.IdentityCheckService identityChecks) {
        this.service = service;
        this.authService = authService;
        this.registryProviders = registryProviders;
        this.documentCheck = documentCheck;
        this.identityChecks = identityChecks;
    }

    @PostMapping(value = "/submissions", consumes = "multipart/form-data")
    KycSubmissionResponse submit(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestParam String legalFullName,
            @RequestParam LocalDate dateOfBirth,
            @RequestParam String nationalIdNumber,
            @RequestParam IdDocumentType idDocumentType,
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam("documentTypes") List<KycDocumentType> documentTypes,
            @RequestParam(required = false) String issuingCountry,
            @RequestParam(defaultValue = "false") boolean registryConsent
    ) {
        AppUser currentUser = authService.currentUser(authorization);
        return KycSubmissionResponse.from(service.submit(currentUser, legalFullName, dateOfBirth, nationalIdNumber,
                idDocumentType, files, documentTypes, issuingCountry, registryConsent));
    }

    /** Which automatic checks are switched on, so the form can offer them. */
    @GetMapping("/checks/available")
    java.util.Map<String, Object> availableChecks() {
        return java.util.Map.of(
                "zimbabweRegister", registryProviders.stream()
                        .anyMatch(p -> p.supports("ZW", com.example.estatex.kyc.IdDocumentType.NATIONAL_ID)),
                "internationalPhotoId", documentCheck.isConfigured());
    }

    /** Starts the international photo-ID + selfie check; returns the hosted page to open. */
    @PostMapping("/document-check")
    com.example.estatex.kyc.check.DiditDocumentCheck.Session startDocumentCheck(
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        AppUser currentUser = authService.currentUser(authorization);
        Long pendingId = service.myList(currentUser).stream()
                .filter(s -> s.getStatus() == KycStatus.PENDING).map(com.example.estatex.kyc.KycSubmission::getId)
                .findFirst().orElse(null);
        return documentCheck.start(currentUser, pendingId);
    }

    /** Didit calls this with the result; trusted only with a valid signature (see DiditDocumentCheck). */
    @PostMapping("/webhooks/didit")
    ResponseEntity<Void> diditWebhook(@RequestBody byte[] body,
                                      @RequestHeader(name = "X-Signature", required = false) String signature,
                                      @RequestHeader(name = "X-Signature-Previous", required = false) String previous,
                                      @RequestHeader(name = "X-Timestamp", required = false) String timestamp) {
        documentCheck.handleWebhook(body, signature, previous, timestamp);
        return ResponseEntity.ok().build();
    }

    /** Reviewer: look the ID number up in the register again. */
    @PostMapping("/submissions/{id}/registry-check")
    KycSubmissionResponse recheckRegistry(@PathVariable Long id,
                                          @RequestHeader(name = "Authorization", required = false) String authorization) {
        AppUser reviewer = authService.currentUser(authorization);
        if (!reviewer.hasPermission(com.example.estatex.user.Permission.KYC_REVIEW)) {
            throw new com.example.estatex.common.ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "Only ID reviewers can run register checks");
        }
        identityChecks.recheck(id);
        com.example.estatex.kyc.KycSubmission s = service.require(id);
        return KycSubmissionResponse.from(s, service.checksFor(s));
    }

    @GetMapping("/submissions/me")
    List<KycSubmissionResponse> mine(@RequestHeader(name = "Authorization", required = false) String authorization) {
        AppUser currentUser = authService.currentUser(authorization);
        return service.myList(currentUser).stream().map(KycSubmissionResponse::from).toList();
    }

    @GetMapping("/submissions/{id}")
    KycSubmissionResponse get(
            @PathVariable Long id,
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        AppUser currentUser = authService.currentUser(authorization);
        return KycSubmissionResponse.from(service.requireOwnedOrAdmin(id, currentUser));
    }

    @GetMapping("/submissions")
    List<KycSubmissionResponse> listForAdmin(@RequestParam(required = false) KycStatus status) {
        return service.listForAdmin(status).stream()
                .map(sub -> KycSubmissionResponse.from(sub, service.checksFor(sub))).toList();
    }

    @GetMapping("/submissions/{submissionId}/documents/{documentId}/download")
    ResponseEntity<Resource> downloadDocument(
            @PathVariable Long submissionId,
            @PathVariable Long documentId,
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        AppUser currentUser = authService.currentUser(authorization);
        KycService.Download download = service.downloadDocument(submissionId, documentId, currentUser);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, download.document().getContentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.document().getFileName())
                        .build()
                        .toString())
                .body(download.resource());
    }

    @PatchMapping("/submissions/{id}/review")
    KycSubmissionResponse review(
            @PathVariable Long id,
            @RequestBody ReviewKycRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        AppUser currentAdmin = authService.currentUser(authorization);
        return KycSubmissionResponse.from(service.review(id, currentAdmin, request));
    }
}
