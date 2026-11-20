package com.example.estatex.agent;

import static com.example.estatex.common.AuditService.entry;

import com.example.estatex.common.ApiException;
import com.example.estatex.common.AuditService;
import com.example.estatex.common.FileSignature;
import com.example.estatex.common.ObjectStorageService;
import com.example.estatex.notification.NotificationService;
import com.example.estatex.notification.NotificationType;
import com.example.estatex.property.Property;
import com.example.estatex.property.PropertyRepository;
import com.example.estatex.property.PropertyStatus;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.Permission;
import com.example.estatex.user.UserRole;
import com.example.estatex.user.UserService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Estate agents prove they're registered with the Estate Agents Council before anything they list
 * goes public. Until EstateX approves the licence their listings are saved as drafts; approval
 * publishes those drafts and gives the agent a "Licensed agent" badge.
 */
@Service
public class AgentLicenceService {
    private static final Set<String> CERTIFICATE_TYPES = Set.of(FileSignature.PDF, FileSignature.JPEG, FileSignature.PNG);
    private static final long MAX_CERTIFICATE_BYTES = 10L * 1024 * 1024;

    private final AgentLicenceRepository licences;
    private final UserService users;
    private final PropertyRepository properties;
    private final ObjectStorageService objectStorage;
    private final AuditService audit;
    private final NotificationService notifications;
    private final Path storageRoot;

    public AgentLicenceService(AgentLicenceRepository licences, UserService users, PropertyRepository properties,
                               ObjectStorageService objectStorage, AuditService audit, NotificationService notifications,
                               @Value("${app.storage.agent-licences:storage/agent-licences}") String storageRoot) {
        this.licences = licences;
        this.users = users;
        this.properties = properties;
        this.objectStorage = objectStorage;
        this.audit = audit;
        this.notifications = notifications;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    /** True when this user's own listings must wait for licence approval before going public. */
    public static boolean mustWaitForLicence(AppUser user) {
        return user != null
                && user.getRoles().contains(UserRole.AGENT)
                && !user.isLicensedAgent()
                && !user.hasPermission(Permission.ADMIN_OVERRIDE);
    }

    @Transactional
    public AgentLicence submit(AppUser user, String registrationNumber, MultipartFile certificate) {
        if (!user.getRoles().contains(UserRole.AGENT)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only agent accounts submit an agent licence");
        }
        String number = registrationNumber == null ? "" : registrationNumber.trim();
        if (number.length() < 3 || number.length() > 60) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter your Estate Agents Council registration number");
        }
        if (certificate == null || certificate.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Attach your registration certificate (PDF or photo)");
        }
        if (certificate.getSize() > MAX_CERTIFICATE_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The certificate must be 10 MB or smaller");
        }
        String mediaType = FileSignature.require(certificate, CERTIFICATE_TYPES,
                "The certificate must be a PDF, JPEG or PNG file");
        String key = "agent-licences/" + user.getId() + "/" + UUID.randomUUID() + FileSignature.extension(mediaType);
        store(key, certificate, mediaType);

        AgentLicence licence = licences.findByUserId(user.getId()).orElseGet(AgentLicence::new);
        String previous = licence.getId() == null ? null : licence.getStatus();
        licence.setUserId(user.getId());
        licence.setRegistrationNumber(number);
        licence.setStatus(AgentLicence.PENDING);
        licence.setDocumentStorageKey(key);
        licence.setDocumentContentType(mediaType);
        licence.setDocumentFileName(safeName(certificate.getOriginalFilename()));
        licence.setDocumentSha256(FileSignature.sha256(certificate));
        licence.setSubmittedAt(Instant.now());
        licence.setReviewedAt(null);
        licence.setReviewedByUserId(null);
        licence.setReviewNote(null);
        licences.save(licence);

        // New details need a new review: the badge only ever vouches for what staff actually checked.
        AppUser stored = users.require(user.getId());
        stored.setLicensedAgent(false);

        audit.record(user, entry("agent_licence.submitted", "AgentLicence", licence.getId())
                .transition(previous, AgentLicence.PENDING)
                .metadata(Map.of("registrationNumber", number, "sha256", licence.getDocumentSha256())));
        return licence;
    }

    @Transactional(readOnly = true)
    public Optional<AgentLicence> mine(AppUser user) {
        return licences.findByUserId(user.getId());
    }

    @Transactional(readOnly = true)
    public List<AgentLicence> pending() {
        return licences.findByStatusOrderBySubmittedAtAsc(AgentLicence.PENDING);
    }

    @Transactional
    public AgentLicence review(Long licenceId, boolean approved, String note, AppUser reviewer) {
        if (reviewer == null || !reviewer.hasPermission(Permission.USER_VERIFY)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only EstateX verification staff review agent licences");
        }
        AgentLicence licence = licences.findById(licenceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Licence not found"));
        if (licence.getUserId().equals(reviewer.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can't review your own licence");
        }
        if (!approved && (note == null || note.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Tell the agent why their licence was rejected");
        }
        String from = licence.getStatus();
        licence.setStatus(approved ? AgentLicence.APPROVED : AgentLicence.REJECTED);
        licence.setReviewedAt(Instant.now());
        licence.setReviewedByUserId(reviewer.getId());
        licence.setReviewNote(note == null || note.isBlank() ? null : note.trim());

        AppUser agent = users.require(licence.getUserId());
        agent.setLicensedAgent(approved);
        int published = 0;
        if (approved) {
            // Everything they listed while waiting goes live now.
            for (Property draft : properties.findByLandlord_IdAndStatusAndOrganisationIdIsNull(agent.getId(), PropertyStatus.DRAFT)) {
                draft.setStatus(PropertyStatus.AVAILABLE);
                published++;
            }
        }
        audit.record(reviewer, entry(approved ? "agent_licence.approved" : "agent_licence.rejected", "AgentLicence", licence.getId())
                .transition(from, licence.getStatus())
                .metadata(Map.of("agentUserId", agent.getId(), "listingsPublished", published,
                        "note", licence.getReviewNote() == null ? "" : licence.getReviewNote())));
        notifications.notify(agent, NotificationType.ACCOUNT_VERIFICATION, licence.getId(),
                approved ? "Your agent licence is approved" : "Your agent licence needs attention",
                approved
                        ? "You're now a Licensed agent on EstateX." + (published > 0 ? " " + published + " waiting listing(s) are now live." : "")
                        : "We couldn't approve your licence: " + licence.getReviewNote() + " You can upload it again from your profile.");
        return licence;
    }

    /** The certificate, for the agent themselves or verification staff. */
    @Transactional(readOnly = true)
    public Document certificate(Long licenceId, AppUser viewer) {
        AgentLicence licence = licences.findById(licenceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Licence not found"));
        boolean allowed = viewer != null && (viewer.getId().equals(licence.getUserId())
                || viewer.hasPermission(Permission.USER_VERIFY));
        if (!allowed) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Licence not found");
        }
        if (objectStorage.isConfigured()) {
            return new Document(null, objectStorage.presignedUrl(licence.getDocumentStorageKey()), licence.getDocumentContentType());
        }
        Path file = storageRoot.resolve(licence.getDocumentStorageKey()).normalize();
        if (!file.startsWith(storageRoot) || !Files.isRegularFile(file)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Certificate file is missing");
        }
        return new Document(file, null, licence.getDocumentContentType());
    }

    public record Document(Path file, String redirectUrl, String contentType) {}

    private void store(String key, MultipartFile file, String mediaType) {
        if (objectStorage.isConfigured()) {
            objectStorage.put(key, file, mediaType);
            return;
        }
        Path target = storageRoot.resolve(key).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid document path");
        }
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the certificate");
        }
    }

    private static String safeName(String name) {
        if (name == null || name.isBlank()) return "certificate";
        String base = Path.of(name).getFileName().toString().replaceAll("[^A-Za-z0-9._ -]", "_");
        return base.length() > 120 ? base.substring(base.length() - 120) : base;
    }
}
