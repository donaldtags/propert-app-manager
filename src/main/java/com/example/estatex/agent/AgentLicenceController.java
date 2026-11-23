package com.example.estatex.agent;

import com.example.estatex.common.ApiException;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.UserService;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/agents")
public class AgentLicenceController {
    private final AgentLicenceService service;
    private final UserService users;

    public AgentLicenceController(AgentLicenceService service, UserService users) {
        this.service = service;
        this.users = users;
    }

    public record LicenceResponse(Long id, Long userId, String agentName, String agentEmail, String registrationNumber,
                                  String status, String documentFileName, String documentSha256, Instant submittedAt,
                                  Instant reviewedAt, String reviewNote) {}

    public record ReviewRequest(Boolean approved, String note) {}

    @PostMapping(value = "/licence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    LicenceResponse submit(@RequestParam String registrationNumber, @RequestParam MultipartFile certificate,
                           @AuthenticationPrincipal AppUser currentUser) {
        return toResponse(service.submit(currentUser, registrationNumber, certificate));
    }

    @GetMapping("/licence/me")
    ResponseEntity<LicenceResponse> mine(@AuthenticationPrincipal AppUser currentUser) {
        return service.mine(currentUser).map(l -> ResponseEntity.ok(toResponse(l)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/licences/pending")
    List<LicenceResponse> pending(@AuthenticationPrincipal AppUser currentUser) {
        if (currentUser == null || !currentUser.hasPermission(com.example.estatex.user.Permission.USER_VERIFY)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only EstateX verification staff review agent licences");
        }
        return service.pending().stream().map(this::toResponse).toList();
    }

    @PatchMapping("/licences/{id}")
    LicenceResponse review(@PathVariable Long id, @RequestBody ReviewRequest request, @AuthenticationPrincipal AppUser currentUser) {
        return toResponse(service.review(id, request.approved() == null || request.approved(), request.note(), currentUser));
    }

    @GetMapping("/licences/{id}/certificate")
    ResponseEntity<?> certificate(@PathVariable Long id, @AuthenticationPrincipal AppUser currentUser) {
        AgentLicenceService.Document doc = service.certificate(id, currentUser);
        if (doc.redirectUrl() != null) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(doc.redirectUrl())).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(new FileSystemResource(doc.file()));
    }

    private LicenceResponse toResponse(AgentLicence l) {
        AppUser agent = users.require(l.getUserId());
        return new LicenceResponse(l.getId(), l.getUserId(), agent.getFullName(), agent.getEmail(), l.getRegistrationNumber(),
                l.getStatus(), l.getDocumentFileName(), l.getDocumentSha256(), l.getSubmittedAt(), l.getReviewedAt(),
                l.getReviewNote());
    }
}
