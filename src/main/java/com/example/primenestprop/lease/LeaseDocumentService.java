package com.example.primenestprop.lease;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.UserRole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
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
public class LeaseDocumentService {
    private static final long MAX_FILE_SIZE = 8L * 1024L * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg"
    );

    private final LeaseDocumentRepository documents;
    private final LeaseService leases;
    private final Path storageRoot;

    public LeaseDocumentService(
            LeaseDocumentRepository documents,
            LeaseService leases,
            @Value("${app.storage.lease-documents:storage/lease-documents}") String storageRoot
    ) {
        this.documents = documents;
        this.leases = leases;
        this.storageRoot = Path.of(storageRoot);
    }

    @Transactional
    public List<LeaseDocument> upload(Long leaseId, AppUser currentUser, List<MultipartFile> files, List<LeaseDocumentType> documentTypes) {
        Lease lease = leases.require(leaseId);
        assertLeasePartyOrAdmin(lease, currentUser);
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "At least one file is required");
        }
        if (documentTypes == null || files.size() != documentTypes.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "documentTypes must match files order and count");
        }
        try {
            Files.createDirectories(storageRoot.resolve(String.valueOf(leaseId)));
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not prepare document storage");
        }

        return java.util.stream.IntStream.range(0, files.size())
                .mapToObj(index -> saveOne(lease, currentUser, files.get(index), documentTypes.get(index)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaseDocument> list(Long leaseId, AppUser currentUser) {
        Lease lease = leases.require(leaseId);
        assertLeasePartyOrAdmin(lease, currentUser);
        return documents.findByLeaseOrderByUploadedAtDesc(lease);
    }

    @Transactional(readOnly = true)
    public Download download(Long leaseId, Long documentId, AppUser currentUser) {
        Lease lease = leases.require(leaseId);
        assertLeasePartyOrAdmin(lease, currentUser);
        LeaseDocument document = requireDocument(lease, documentId);
        Resource resource = new FileSystemResource(Path.of(document.getStoragePath()));
        if (!resource.exists()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Stored file not found");
        }
        return new Download(document, resource);
    }

    @Transactional
    public LeaseDocument review(Long leaseId, Long documentId, AppUser currentUser, LeaseDtos.ReviewDocumentRequest request) {
        Lease lease = leases.require(leaseId);
        if (!isAdmin(currentUser) && !currentUser.getId().equals(lease.getLandlord().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the landlord or an admin can review lease documents");
        }
        if (request.status() == LeaseDocumentStatus.SUBMITTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Review status must be APPROVED or REJECTED");
        }
        LeaseDocument document = requireDocument(lease, documentId);
        document.setStatus(request.status());
        document.setReviewNote(request.reviewNote());
        document.setReviewedBy(currentUser.getId());
        document.setReviewedAt(Instant.now());
        return document;
    }

    private LeaseDocument saveOne(Lease lease, AppUser currentUser, MultipartFile file, LeaseDocumentType documentType) {
        validate(file);
        String originalName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "document"
                : Path.of(file.getOriginalFilename()).getFileName().toString();
        Path target = storageRoot
                .resolve(String.valueOf(lease.getId()))
                .resolve(UUID.randomUUID() + "-" + originalName);
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store uploaded file");
        }

        LeaseDocument document = new LeaseDocument();
        document.setLease(lease);
        document.setUser(currentUser);
        document.setDocumentType(documentType);
        document.setFileName(originalName);
        document.setContentType(file.getContentType());
        document.setFileSize(file.getSize());
        document.setStoragePath(target.toString());
        document.setStorageKey(target.toString());
        return documents.save(document);
    }

    private LeaseDocument requireDocument(Lease lease, Long documentId) {
        return documents.findByIdAndLease(documentId, lease)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Lease document not found"));
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Uploaded file is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "File size must be 8MB or less");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only PDF, PNG, and JPEG files are allowed");
        }
    }

    private void assertLeasePartyOrAdmin(Lease lease, AppUser user) {
        if (isAdmin(user)
                || user.getId().equals(lease.getTenant().getId())
                || user.getId().equals(lease.getLandlord().getId())) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "Only the tenant, landlord, or admin can access these documents");
    }

    private boolean isAdmin(AppUser user) {
        return user.getRoles().contains(UserRole.ADMIN);
    }

    public record Download(LeaseDocument document, Resource resource) {
    }
}
