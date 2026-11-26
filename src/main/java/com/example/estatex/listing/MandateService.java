package com.example.estatex.listing;

import static com.example.estatex.common.AuditService.entry;

import com.example.estatex.common.ApiException;
import com.example.estatex.common.AuditService;
import com.example.estatex.common.BusinessTime;
import com.example.estatex.common.ObjectStorageService;
import com.example.estatex.listing.OrgListingService.Listing;
import com.example.estatex.organisation.OrgAccessService;
import com.example.estatex.organisation.OrgPermission;
import com.example.estatex.property.ListingType;
import com.example.estatex.property.Property;
import com.example.estatex.property.PropertyRepository;
import com.example.estatex.property.PropertyStatus;
import com.example.estatex.user.AppUser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MandateService {
    private static final Logger log = LoggerFactory.getLogger(MandateService.class);
    private static final Set<String> DOCUMENT_TYPES = Set.of("application/pdf", "image/jpeg", "image/png", "image/webp");
    private static final long MAX_DOCUMENT_BYTES = 15L * 1024 * 1024;
    private static final List<String> OPEN_STATUSES = List.of(
            MandateStatus.DRAFT.name(), MandateStatus.AWAITING_SIGN_OFF.name(), MandateStatus.ACTIVE.name());

    private final OrgListingService listings;
    private final OrgAccessService access;
    private final MandateRepository mandates;
    private final ListingComplianceRepository compliances;
    private final PropertyRepository properties;
    private final ObjectStorageService objectStorage;
    private final AuditService audit;
    private final BusinessTime time;
    private final Path storageRoot;

    public MandateService(OrgListingService listings, OrgAccessService access, MandateRepository mandates,
                          ListingComplianceRepository compliances, PropertyRepository properties,
                          ObjectStorageService objectStorage, AuditService audit, BusinessTime time,
                          @Value("${app.storage.mandate-documents:storage/mandate-documents}") String storageRoot) {
        this.listings = listings;
        this.access = access;
        this.mandates = mandates;
        this.compliances = compliances;
        this.properties = properties;
        this.objectStorage = objectStorage;
        this.audit = audit;
        this.time = time;
        this.storageRoot = Path.of(storageRoot);
    }

    @Transactional
    public Mandate create(String slug, Long propertyId, ListingDtos.CreateMandateRequest r, AppUser user) {
        if (r.clientId() != null) {
            Mandate existing = mandates.findByClientId(r.clientId()).orElse(null);
            if (existing != null) {
                return existing; // offline replay of a create that already reached the server
            }
        }
        Listing l = listings.load(slug, propertyId, user, OrgPermission.LISTING_EDIT);
        Property p = l.property();
        if (p.getStatus() == PropertyStatus.REPORTED_SOLD) {
            throw new ApiException(HttpStatus.CONFLICT, "This property is already sold.");
        }
        LocalDate today = time.today();
        if (r.expiryDate().isBefore(r.startDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The expiry date must be on or after the start date.");
        }
        if (r.expiryDate().isBefore(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That mandate has already expired.");
        }
        if (r.minimumPriceCents() != null && r.askingPriceCents() != null && r.minimumPriceCents() > r.askingPriceCents()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The minimum price can't be above the asking price.");
        }
        Mandate m = new Mandate();
        m.setClientId(r.clientId());
        m.setOrganisationId(l.ctx().organisation().getId());
        m.setPropertyId(propertyId);
        m.setType(r.type());
        m.setStartDate(r.startDate());
        m.setExpiryDate(r.expiryDate());
        m.setOwnerName(r.ownerName().trim());
        m.setOwnerPhone(r.ownerPhone());
        m.setOwnerEmail(r.ownerEmail());
        m.setAskingPriceCents(r.askingPriceCents() != null ? r.askingPriceCents() : OrgListingService.toCents(p.getPrice()));
        m.setMinimumPriceCents(r.minimumPriceCents());
        m.setCurrency(r.currency() != null ? r.currency() : p.getCurrency());
        m.setCommissionBps(r.commissionBps() != null ? r.commissionBps()
                : p.getListingType() == ListingType.SALE ? l.ctx().organisation().getSaleCommissionBps() : null);
        m.setOfferAuthority(r.offerAuthority());
        m.setCreatedByUserId(user.getId());
        mandates.save(m);
        audit.record(user, entry("MANDATE_CREATED", "Mandate", m.getId())
                .organisation(m.getOrganisationId())
                .transition(null, MandateStatus.DRAFT)
                .metadata(Map.of("propertyId", propertyId, "expiryDate", m.getExpiryDate().toString())));
        return m;
    }

    /** The owner has signed: attach the signed mandate. Allowed until the PREA signs it off. */
    @Transactional
    public Mandate attachSignedDocument(String slug, Long mandateId, MultipartFile file, LocalDate ownerSignedOn, AppUser user) {
        Mandate m = load(slug, mandateId, user, OrgPermission.LISTING_EDIT);
        if (m.getStatus() != MandateStatus.DRAFT && m.getStatus() != MandateStatus.AWAITING_SIGN_OFF) {
            throw new ApiException(HttpStatus.CONFLICT, "This mandate is " + m.getStatus() + "; its document can't be replaced.");
        }
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Attach the signed mandate (PDF or photo).");
        }
        if (file.getSize() > MAX_DOCUMENT_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The file is larger than 15 MB.");
        }
        String mediaType = com.example.estatex.common.FileSignature.require(file, DOCUMENT_TYPES,
                "The signed mandate must be a PDF, JPEG, PNG or WebP file.");
        LocalDate signed = ownerSignedOn == null ? time.today() : ownerSignedOn;
        if (signed.isAfter(time.today())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The signing date can't be in the future.");
        }
        String key = "mandates/" + m.getOrganisationId() + "/" + m.getId() + "/" + UUID.randomUUID() + com.example.estatex.common.FileSignature.extension(mediaType);
        store(key, file, mediaType);
        m.setDocumentSha256(com.example.estatex.common.FileSignature.sha256(file));
        m.setDocumentStorageKey(key);
        m.setDocumentFileName(safeName(file.getOriginalFilename()));
        m.setDocumentContentType(mediaType);
        m.setOwnerSignedOn(signed);
        MandateStatus before = m.getStatus();
        m.setStatus(MandateStatus.AWAITING_SIGN_OFF);
        audit.record(user, entry("MANDATE_OWNER_SIGNED", "Mandate", m.getId())
                .organisation(m.getOrganisationId())
                .transition(before, MandateStatus.AWAITING_SIGN_OFF));
        return m;
    }

    /** PREA only. Makes this the listing's mandate in force, superseding any earlier one. */
    @Transactional
    public Mandate signOff(String slug, Long mandateId, AppUser user) {
        Mandate m = load(slug, mandateId, user, OrgPermission.MANDATE_SIGN_OFF);
        if (m.getStatus() != MandateStatus.AWAITING_SIGN_OFF || m.getDocumentStorageKey() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a mandate signed by the owner, with the signed document attached, can be signed off.");
        }
        if (m.getExpiryDate().isBefore(time.today())) {
            throw new ApiException(HttpStatus.CONFLICT, "This mandate expired on " + m.getExpiryDate() + ".");
        }
        ListingCompliance c = compliances.findByPropertyId(m.getPropertyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Listing not found"));
        if (c.getActiveMandateId() != null && !c.getActiveMandateId().equals(m.getId())) {
            mandates.findById(c.getActiveMandateId()).filter(prev -> prev.getStatus() == MandateStatus.ACTIVE).ifPresent(prev -> {
                prev.setStatus(MandateStatus.SUPERSEDED);
                prev.setClosedAt(Instant.now());
                audit.record(user, entry("MANDATE_SUPERSEDED", "Mandate", prev.getId())
                        .organisation(prev.getOrganisationId())
                        .transition(MandateStatus.ACTIVE, MandateStatus.SUPERSEDED)
                        .metadata(Map.of("supersededBy", m.getId())));
            });
        }
        m.setStatus(MandateStatus.ACTIVE);
        m.setPreaSignedOffAt(Instant.now());
        m.setPreaSignedOffByUserId(user.getId());
        c.setActiveMandateId(m.getId());
        audit.record(user, entry("MANDATE_SIGNED_OFF", "Mandate", m.getId())
                .organisation(m.getOrganisationId())
                .transition(MandateStatus.AWAITING_SIGN_OFF, MandateStatus.ACTIVE)
                .metadata(Map.of("propertyId", m.getPropertyId(), "expiryDate", m.getExpiryDate().toString())));
        return m;
    }

    @Transactional
    public Mandate cancel(String slug, Long mandateId, String reason, AppUser user) {
        Mandate m = load(slug, mandateId, user, OrgPermission.LISTING_EDIT);
        if (!m.getStatus().isOpen()) {
            throw new ApiException(HttpStatus.CONFLICT, "This mandate is already " + m.getStatus() + ".");
        }
        MandateStatus before = m.getStatus();
        m.setStatus(MandateStatus.CANCELLED);
        m.setClosedAt(Instant.now());
        audit.record(user, entry("MANDATE_CANCELLED", "Mandate", m.getId())
                .organisation(m.getOrganisationId())
                .transition(before, MandateStatus.CANCELLED)
                .metadata(Map.of("reason", reason == null ? "" : reason)));
        compliances.findByPropertyId(m.getPropertyId()).ifPresent(c -> {
            Property p = properties.findById(m.getPropertyId()).orElseThrow();
            listings.enforceStillPublishable(p, c, user, "The mandate was cancelled");
        });
        return m;
    }

    @Transactional(readOnly = true)
    public StoredDocument document(String slug, Long mandateId, AppUser user) {
        Mandate m = load(slug, mandateId, user, OrgPermission.LISTING_EDIT);
        if (m.getDocumentStorageKey() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No signed document attached");
        }
        if (objectStorage.isConfigured()) {
            return new StoredDocument(null, objectStorage.presignedUrl(m.getDocumentStorageKey()), m.getDocumentFileName(),
                    m.getDocumentContentType());
        }
        return new StoredDocument(storageRoot.resolve(m.getDocumentStorageKey()), null, m.getDocumentFileName(),
                m.getDocumentContentType());
    }

    public record StoredDocument(Path localPath, String redirectUrl, String fileName, String contentType) {
    }

    /**
     * Expires every open mandate whose expiry date is before {@code today} and takes any listing
     * relying on one off the market, notifying its negotiator and the PREA. Idempotent.
     */
    @Transactional
    public int expireMandates(LocalDate today) {
        List<Mandate> due = mandates.findByStatusCodeInAndExpiryDateBefore(OPEN_STATUSES, today);
        for (Mandate m : due) {
            MandateStatus before = m.getStatus();
            m.setStatus(MandateStatus.EXPIRED);
            m.setClosedAt(Instant.now());
            audit.record(null, entry("MANDATE_EXPIRED", "Mandate", m.getId())
                    .organisation(m.getOrganisationId())
                    .transition(before, MandateStatus.EXPIRED)
                    .metadata(Map.of("expiryDate", m.getExpiryDate().toString())));
            for (ListingCompliance c : compliances.findByActiveMandateId(m.getId())) {
                Property p = properties.findById(c.getPropertyId()).orElse(null);
                if (p == null) {
                    continue;
                }
                if (p.getStatus() == PropertyStatus.AVAILABLE) {
                    listings.takeOffMarket(p, c, null, "Mandate expired on " + m.getExpiryDate(), true, PropertyStatus.EXPIRED);
                } else {
                    listings.notifyResponsible(c, p.getId(), "Mandate expired",
                            "The mandate for \"" + p.getTitle() + "\" expired on " + m.getExpiryDate() + ".");
                }
            }
        }
        if (!due.isEmpty()) {
            log.info("Expired {} mandate(s) as of {}", due.size(), today);
        }
        return due.size();
    }

    private Mandate load(String slug, Long mandateId, AppUser user, OrgPermission permission) {
        var ctx = access.require(user, slug, permission);
        return mandates.findById(mandateId)
                .filter(m -> m.getOrganisationId().equals(ctx.organisation().getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Mandate not found"));
    }

    private void store(String key, MultipartFile file, String mediaType) {
        if (objectStorage.isConfigured()) {
            objectStorage.put(key, file, mediaType);
            return;
        }
        Path target = storageRoot.resolve(key).normalize();
        if (!target.startsWith(storageRoot.normalize())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid document path");
        }
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the signed mandate");
        }
    }

    private static String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "signed-mandate";
        }
        String base = Path.of(name).getFileName().toString().replaceAll("[^A-Za-z0-9._ -]", "_");
        return base.length() > 200 ? base.substring(0, 200) : base;
    }
}
