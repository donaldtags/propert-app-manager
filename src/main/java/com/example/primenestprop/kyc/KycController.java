package com.example.primenestprop.kyc;

import com.example.primenestprop.auth.AuthService;
import com.example.primenestprop.kyc.KycDtos.KycSubmissionResponse;
import com.example.primenestprop.kyc.KycDtos.ReviewKycRequest;
import com.example.primenestprop.user.AppUser;
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

    public KycController(KycService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @PostMapping(value = "/submissions", consumes = "multipart/form-data")
    KycSubmissionResponse submit(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestParam String legalFullName,
            @RequestParam LocalDate dateOfBirth,
            @RequestParam String nationalIdNumber,
            @RequestParam IdDocumentType idDocumentType,
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam("documentTypes") List<KycDocumentType> documentTypes
    ) {
        AppUser currentUser = authService.currentUser(authorization);
        return KycSubmissionResponse.from(service.submit(
                currentUser, legalFullName, dateOfBirth, nationalIdNumber, idDocumentType, files, documentTypes));
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
        return service.listForAdmin(status).stream().map(KycSubmissionResponse::from).toList();
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
