package com.example.primenestprop.lease;

import static com.example.primenestprop.lease.LeaseDtos.LeaseResponse;

import com.example.primenestprop.auth.AuthService;
import com.example.primenestprop.lease.LeaseDocumentDtos.LeaseDocumentResponse;
import com.example.primenestprop.property.PropertyService;
import com.example.primenestprop.subscription.SubscriptionFeature;
import com.example.primenestprop.subscription.SubscriptionService;
import com.example.primenestprop.user.AppUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/v1/leases")
public class LeaseController {
    private final LeaseService service;
    private final LeaseDocumentService documentService;
    private final LeaseExtractionService extractionService;
    private final AuthService authService;
    private final LeaseActionService actionService;
    private final PropertyService properties;
    private final SubscriptionService subscriptions;

    public LeaseController(
            LeaseService service,
            LeaseDocumentService documentService,
            LeaseExtractionService extractionService,
            AuthService authService,
            LeaseActionService actionService,
            PropertyService properties,
            SubscriptionService subscriptions
    ) {
        this.service = service;
        this.documentService = documentService;
        this.extractionService = extractionService;
        this.authService = authService;
        this.actionService = actionService;
        this.properties = properties;
        this.subscriptions = subscriptions;
    }

    @PostMapping
    LeaseResponse create(@Valid @RequestBody LeaseDtos.CreateLeaseRequest request, @AuthenticationPrincipal AppUser currentUser) {
        subscriptions.requireFeature(properties.require(request.propertyId()).getLandlord(), SubscriptionFeature.DIGITAL_LEASES);
        return LeaseResponse.from(service.create(request, currentUser));
    }

    @PostMapping(value = "/extract", consumes = "multipart/form-data")
    LeaseExtractionResult extract(@RequestPart("file") MultipartFile file) {
        return extractionService.extract(file);
    }

    @PatchMapping("/{id}/sign")
    LeaseResponse sign(@PathVariable Long id, @AuthenticationPrincipal AppUser currentUser) {
        return LeaseResponse.from(service.sign(id, currentUser));
    }

    @GetMapping
    List<LeaseResponse> list(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long landlordId,
            @RequestParam(required = false) Long agentId,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        if (tenantId != null) {
            return service.forTenant(tenantId, currentUser).stream().map(LeaseResponse::from).toList();
        }
        if (landlordId != null) {
            return service.forLandlord(landlordId, currentUser).stream().map(LeaseResponse::from).toList();
        }
        if (agentId != null) {
            return service.forAgent(agentId, currentUser).stream().map(LeaseResponse::from).toList();
        }
        return List.of();
    }

    @PostMapping(value = "/{leaseId}/documents", consumes = "multipart/form-data")
    List<LeaseDocumentResponse> uploadDocuments(
            @PathVariable Long leaseId,
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam("documentTypes") List<LeaseDocumentType> documentTypes
    ) {
        AppUser currentUser = authService.currentUser(authorization);
        return documentService.upload(leaseId, currentUser, files, documentTypes).stream()
                .map(LeaseDocumentResponse::from)
                .toList();
    }

    @GetMapping("/{leaseId}/documents")
    List<LeaseDocumentResponse> listDocuments(
            @PathVariable Long leaseId,
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        AppUser currentUser = authService.currentUser(authorization);
        return documentService.list(leaseId, currentUser).stream()
                .map(LeaseDocumentResponse::from)
                .toList();
    }

    @GetMapping("/{leaseId}/documents/{documentId}/download")
    ResponseEntity<Resource> downloadDocument(
            @PathVariable Long leaseId,
            @PathVariable Long documentId,
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        AppUser currentUser = authService.currentUser(authorization);
        LeaseDocumentService.Download download = documentService.download(leaseId, documentId, currentUser);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, download.document().getContentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.document().getFileName())
                        .build()
                        .toString())
                .body(download.resource());
    }

    @PatchMapping("/{leaseId}/documents/{documentId}/review")
    LeaseDocumentResponse reviewDocument(
            @PathVariable Long leaseId,
            @PathVariable Long documentId,
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody LeaseDtos.ReviewDocumentRequest request
    ) {
        AppUser currentUser = authService.currentUser(authorization);
        return LeaseDocumentResponse.from(documentService.review(leaseId, documentId, currentUser, request));
    }

    @PostMapping("/{leaseId}/actions")
    LeaseActionDtos.LeaseActionResponse requestAction(
            @PathVariable Long leaseId,
            @Valid @RequestBody LeaseActionDtos.CreateLeaseActionRequest request,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        return LeaseActionDtos.LeaseActionResponse.from(actionService.create(leaseId, currentUser, request));
    }

    @PatchMapping("/actions/{id}/review")
    LeaseActionDtos.LeaseActionResponse reviewAction(
            @PathVariable Long id,
            @Valid @RequestBody LeaseActionDtos.ReviewLeaseActionRequest request,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        return LeaseActionDtos.LeaseActionResponse.from(actionService.review(id, currentUser, request));
    }

    @GetMapping("/actions/mine")
    List<LeaseActionDtos.LeaseActionResponse> myActions(@AuthenticationPrincipal AppUser currentUser) {
        return actionService.mine(currentUser).stream().map(LeaseActionDtos.LeaseActionResponse::from).toList();
    }

    @GetMapping("/actions/received")
    List<LeaseActionDtos.LeaseActionResponse> receivedActions(@AuthenticationPrincipal AppUser currentUser) {
        return actionService.received(currentUser).stream().map(LeaseActionDtos.LeaseActionResponse::from).toList();
    }
}
