package com.example.primenestprop.kyc;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.common.AuditLog;
import com.example.primenestprop.common.AuditLogRepository;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.UserRole;
import com.example.primenestprop.user.UserService;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KycService {
    private static final long MAX_FILE_SIZE = 8L * 1024L * 1024L;
    private static final Set<String> ID_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "application/pdf");
    private static final Set<String> SELFIE_CONTENT_TYPES = Set.of("image/png", "image/jpeg");
    private static final Set<KycDocumentType> ID_DOC_SET = Set.of(KycDocumentType.ID_FRONT, KycDocumentType.ID_BACK);
    private static final Set<KycDocumentType> PASSPORT_DOC_SET = Set.of(KycDocumentType.PASSPORT);
    private static final Set<KycDocumentType> REQUIRED_SELFIES = Set.of(KycDocumentType.SELFIE, KycDocumentType.SELFIE_WITH_ID);

    private final KycSubmissionRepository submissions;
    private final KycDocumentRepository documents;
    private final UserService users;
    private final AuditLogRepository auditLogs;
    private final ObjectMapper objectMapper;
    private final Path storageRoot;

    public KycService(
            KycSubmissionRepository submissions,
            KycDocumentRepository documents,
            UserService users,
            AuditLogRepository auditLogs,
            ObjectMapper objectMapper,
            @Value("${app.storage.kyc-documents:storage/kyc-documents}") String storageRoot
    ) {
        this.submissions = submissions;
        this.documents = documents;
        this.users = users;
        this.auditLogs = auditLogs;
        this.objectMapper = objectMapper;
        this.storageRoot = Path.of(storageRoot);
    }

    @Transactional
    public KycSubmission submit(
            AppUser currentUser,
            String legalFullName,
            LocalDate dateOfBirth,
            String nationalIdNumber,
            IdDocumentType idDocumentType,
            List<MultipartFile> files,
            List<KycDocumentType> documentTypes
    ) {
        if (currentUser.isIdentityVerified()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Your identity is already verified");
        }
        submissions.findByUserAndStatus(currentUser, KycStatus.PENDING).ifPresent(existing -> {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You already have a verification request under review");
        });
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "At least one document is required");
        }
        if (documentTypes == null || files.size() != documentTypes.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "documentTypes must match files order and count");
        }
        Set<KycDocumentType> submittedTypes = Set.copyOf(documentTypes);
        Set<KycDocumentType> requiredIdTypes = idDocumentType == IdDocumentType.PASSPORT ? PASSPORT_DOC_SET : ID_DOC_SET;
        if (!submittedTypes.containsAll(requiredIdTypes) || !submittedTypes.containsAll(REQUIRED_SELFIES)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Submission must include " + requiredIdTypes + " and " + REQUIRED_SELFIES);
        }

        KycSubmission submission = new KycSubmission();
        submission.setUser(currentUser);
        submission.setLegalFullName(legalFullName);
        submission.setDateOfBirth(dateOfBirth);
        submission.setNationalIdNumber(nationalIdNumber);
        submission.setIdDocumentType(idDocumentType);
        submission = submissions.save(submission);

        try {
            Files.createDirectories(storageRoot.resolve(String.valueOf(submission.getId())));
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not prepare document storage");
        }

        KycSubmission finalSubmission = submission;
        List<KycDocument> saved = java.util.stream.IntStream.range(0, files.size())
                .mapToObj(index -> saveOne(finalSubmission, files.get(index), documentTypes.get(index)))
                .toList();
        submission.getDocuments().addAll(saved);
        return submission;
    }

    @Transactional(readOnly = true)
    public List<KycSubmission> myList(AppUser currentUser) {
        return submissions.findByUserOrderBySubmittedAtDesc(currentUser);
    }

    @Transactional(readOnly = true)
    public KycSubmission require(Long id) {
        return submissions.findWithDetailsById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Verification request not found"));
    }

    @Transactional(readOnly = true)
    public KycSubmission requireOwnedOrAdmin(Long id, AppUser currentUser) {
        KycSubmission submission = require(id);
        if (!isAdmin(currentUser) && !submission.getUser().getId().equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only view your own verification request");
        }
        return submission;
    }

    @Transactional(readOnly = true)
    public List<KycSubmission> listForAdmin(KycStatus status) {
        return status == null ? submissions.findAllByOrderBySubmittedAtDesc() : submissions.findByStatusOrderBySubmittedAtDesc(status);
    }

    @Transactional(readOnly = true)
    public Download downloadDocument(Long submissionId, Long documentId, AppUser currentUser) {
        KycSubmission submission = requireOwnedOrAdmin(submissionId, currentUser);
        KycDocument document = documents.findByIdAndSubmission(documentId, submission)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));
        Resource resource = new FileSystemResource(Path.of(document.getStoragePath()));
        if (!resource.exists()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Stored file not found");
        }
        return new Download(document, resource);
    }

    @Transactional
    public KycSubmission review(Long submissionId, AppUser currentAdmin, KycDtos.ReviewKycRequest request) {
        if (!isAdmin(currentAdmin)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only admins can review verification requests");
        }
        if (request.status() != KycStatus.APPROVED && request.status() != KycStatus.REJECTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Review status must be APPROVED or REJECTED");
        }
        KycSubmission submission = require(submissionId);
        if (submission.getStatus() != KycStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This request has already been reviewed");
        }
        submission.setStatus(request.status());
        submission.setReviewNote(request.reviewNote());
        submission.setReviewedBy(currentAdmin.getId());
        submission.setReviewedAt(Instant.now());
        if (request.status() == KycStatus.APPROVED) {
            users.applyKycApproval(submission.getUser().getId());
        }
        writeAuditLog(currentAdmin, submission, request);
        return submission;
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return submissions.countByStatus(KycStatus.PENDING);
    }

    private void writeAuditLog(AppUser currentAdmin, KycSubmission submission, KycDtos.ReviewKycRequest request) {
        AuditLog log = new AuditLog();
        log.setActorUserId(currentAdmin.getId());
        log.setAction(request.status() == KycStatus.APPROVED ? "KYC_APPROVED" : "KYC_REJECTED");
        log.setEntityType("KycSubmission");
        log.setEntityId(submission.getId());
        try {
            log.setMetadataJson(objectMapper.writeValueAsString(Map.of(
                    "userId", submission.getUser().getId(),
                    "reviewNote", request.reviewNote() == null ? "" : request.reviewNote()
            )));
        } catch (Exception ex) {
            log.setMetadataJson("{}");
        }
        auditLogs.save(log);
    }

    private KycDocument saveOne(KycSubmission submission, MultipartFile file, KycDocumentType documentType) {
        validate(file, documentType);
        String originalName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "document"
                : Path.of(file.getOriginalFilename()).getFileName().toString();
        Path target = storageRoot
                .resolve(String.valueOf(submission.getId()))
                .resolve(UUID.randomUUID() + "-" + originalName);
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store uploaded file");
        }

        KycDocument document = new KycDocument();
        document.setSubmission(submission);
        document.setDocumentType(documentType);
        document.setFileName(originalName);
        document.setContentType(file.getContentType());
        document.setFileSize(file.getSize());
        document.setStoragePath(target.toString());
        document.setStorageKey(target.toString());
        return documents.save(document);
    }

    private void validate(MultipartFile file, KycDocumentType documentType) {
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Uploaded file is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "File size must be 8MB or less");
        }
        boolean isSelfie = REQUIRED_SELFIES.contains(documentType);
        Set<String> allowed = isSelfie ? SELFIE_CONTENT_TYPES : ID_CONTENT_TYPES;
        if (!allowed.contains(file.getContentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, isSelfie
                    ? "Selfies must be PNG or JPEG images"
                    : "Only PDF, PNG, and JPEG files are allowed for identity documents");
        }
    }

    private boolean isAdmin(AppUser user) {
        return user.getRoles().contains(UserRole.ADMIN);
    }

    public record Download(KycDocument document, Resource resource) {
    }
}
