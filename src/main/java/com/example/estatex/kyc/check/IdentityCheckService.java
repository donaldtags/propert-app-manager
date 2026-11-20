package com.example.estatex.kyc.check;

import com.example.estatex.common.ApiException;
import com.example.estatex.kyc.KycSubmission;
import com.example.estatex.kyc.KycSubmissionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

/** Runs national-register lookups for ID submissions and serves every check to the reviewer. */
@Service
public class IdentityCheckService {
    private final List<RegistryProvider> registries;
    private final IdentityCheckRepository checks;
    private final KycSubmissionRepository submissions;
    private final ObjectMapper objectMapper;

    public IdentityCheckService(List<RegistryProvider> registries, IdentityCheckRepository checks,
                                KycSubmissionRepository submissions, ObjectMapper objectMapper) {
        this.registries = registries;
        this.checks = checks;
        this.submissions = submissions;
        this.objectMapper = objectMapper;
    }

    /** After the submission is saved: look the number up, if the person agreed and a provider covers it. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSubmitted(KycSubmittedEvent event) {
        submissions.findWithDetailsById(event.submissionId())
                .filter(s -> Boolean.TRUE.equals(s.getRegistryConsent()))
                .ifPresent(this::lookup);
    }

    /** Admin "check with the register again" (e.g. after an outage or topping up the provider). */
    @Transactional
    public IdentityCheck recheck(Long submissionId) {
        KycSubmission s = submissions.findWithDetailsById(submissionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Submission not found"));
        if (!Boolean.TRUE.equals(s.getRegistryConsent())) {
            throw new ApiException(HttpStatus.CONFLICT, "This person didn't agree to a register check");
        }
        IdentityCheck check = lookup(s);
        if (check == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No ID register provider is set up for " + s.getIssuingCountryOrDefault() + " " + s.getIdDocumentType());
        }
        return check;
    }

    private IdentityCheck lookup(KycSubmission s) {
        String country = s.getIssuingCountryOrDefault();
        RegistryProvider provider = registries.stream().filter(p -> p.supports(country, s.getIdDocumentType())).findFirst().orElse(null);
        if (provider == null) return null;
        RegistryLookup result = provider.lookup(country, s.getIdDocumentType(), s.getNationalIdNumber(),
                s.getLegalFullName(), s.getDateOfBirth());
        IdentityCheck check = new IdentityCheck();
        check.setUserId(s.getUser().getId());
        check.setSubmissionId(s.getId());
        check.setKind(IdentityCheck.REGISTRY);
        check.setProvider(provider.name());
        check.setStatus(result.status());
        check.setSummary(result.detail());
        check.setDetailsJson(objectMapper.writeValueAsString(Map.of(
                "registryName", result.registryName() == null ? "" : result.registryName(),
                "registryDateOfBirth", result.registryDateOfBirth() == null ? "" : result.registryDateOfBirth().toString(),
                "registrySex", result.registrySex() == null ? "" : result.registrySex(),
                "country", country)));
        check.setCompletedAt(Instant.now());
        return checks.save(check);
    }

    /** Latest register check for this submission and latest photo-ID check for this person. */
    @Transactional(readOnly = true)
    public List<IdentityCheck> forSubmission(KycSubmission s) {
        List<IdentityCheck> all = checks.findByUserIdOrderByCreatedAtDesc(s.getUser().getId());
        IdentityCheck registry = all.stream()
                .filter(c -> IdentityCheck.REGISTRY.equals(c.getKind()) && s.getId().equals(c.getSubmissionId()))
                .findFirst().orElse(null);
        IdentityCheck document = all.stream().filter(c -> IdentityCheck.DOCUMENT.equals(c.getKind())).findFirst().orElse(null);
        return java.util.stream.Stream.of(registry, document).filter(java.util.Objects::nonNull).toList();
    }

    @Transactional(readOnly = true)
    public List<IdentityCheck> mine(Long userId) {
        return checks.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
