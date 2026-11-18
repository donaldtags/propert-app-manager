package com.example.estatex.listing;

import com.example.estatex.property.Property;
import com.example.estatex.property.TitleType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The publication gates for organisation listings, as a pure function so every rule is unit
 * testable and the same evaluation feeds both the API's compliance panel and the publish/approve
 * enforcement. A listing may be published only when every check is met:
 * <ol>
 *   <li>title type recorded,</li>
 *   <li>ownership/title verification VERIFIED,</li>
 *   <li>an ACTIVE mandate, signed off by the PREA, in date today (Africa/Harare),</li>
 *   <li>owner consent to show photos AND address,</li>
 *   <li>PREA approval given after the last material change (price, address, title type).</li>
 * </ol>
 */
public final class PublicationGuard {
    public static final String TITLE_TYPE = "TITLE_TYPE";
    public static final String TITLE_VERIFIED = "TITLE_VERIFIED";
    public static final String MANDATE = "MANDATE";
    public static final String OWNER_CONSENT = "OWNER_CONSENT";
    public static final String PREA_APPROVAL = "PREA_APPROVAL";

    private PublicationGuard() {
    }

    public record Check(String key, String label, boolean met, String detail) {
    }

    public record Report(List<Check> checks) {
        public boolean publishable() {
            return checks.stream().allMatch(Check::met);
        }

        /** Everything except PREA approval is met: the PREA may now approve. */
        public boolean readyForApproval() {
            return checks.stream().filter(c -> !c.key().equals(PREA_APPROVAL)).allMatch(Check::met);
        }

        public List<String> unmetDetails(boolean includeApproval) {
            return checks.stream()
                    .filter(c -> !c.met() && (includeApproval || !c.key().equals(PREA_APPROVAL)))
                    .map(Check::detail)
                    .toList();
        }
    }

    public static Report evaluate(Property property, ListingCompliance compliance, Mandate mandate, LocalDate today) {
        List<Check> checks = new ArrayList<>();

        TitleType titleType = property.getTitleType();
        checks.add(new Check(TITLE_TYPE, "Title type recorded", titleType != null,
                titleType != null ? label(titleType) : "Record whether this is a title deed, sectional title or cession."));

        TitleVerificationStatus tv = compliance.getTitleVerificationStatus();
        checks.add(new Check(TITLE_VERIFIED, "Ownership and title verified", tv == TitleVerificationStatus.VERIFIED,
                switch (tv) {
                    case VERIFIED -> "Verified" + (compliance.getTitleReference() == null ? "" : " (" + compliance.getTitleReference() + ")");
                    case FAILED -> "Title verification failed. Resolve it before marketing.";
                    case IN_PROGRESS -> "Title verification in progress.";
                    case NOT_STARTED -> "Verify the owner's title documents.";
                }));

        checks.add(mandateCheck(compliance, mandate, today));

        boolean consent = compliance.isOwnerConsentPhotos() && compliance.isOwnerConsentAddress();
        checks.add(new Check(OWNER_CONSENT, "Owner consents to photos and address being shown", consent,
                consent ? "Recorded"
                        : !compliance.isOwnerConsentPhotos() && !compliance.isOwnerConsentAddress()
                        ? "Record the owner's consent to show photos and the address."
                        : !compliance.isOwnerConsentPhotos() ? "Owner hasn't consented to photos being shown."
                        : "Owner hasn't consented to the address being shown."));

        boolean approved = compliance.getPreaApprovedAt() != null
                && (compliance.getMaterialChangedAt() == null
                || compliance.getPreaApprovedAt().isAfter(compliance.getMaterialChangedAt()));
        checks.add(new Check(PREA_APPROVAL, "Approved by the Principal Registered Estate Agent", approved,
                approved ? "Approved"
                        : compliance.getMaterialChangedAt() != null && compliance.getPreaApprovedAt() == null
                        && compliance.getPublishedAt() != null
                        ? "Price, address or title changed since approval. The PREA must approve again."
                        : "Waiting for PREA approval."));

        return new Report(List.copyOf(checks));
    }

    static String label(TitleType type) {
        return switch (type) {
            case TITLE_DEED -> "Title deed";
            case SECTIONAL_TITLE -> "Sectional title";
            case CESSION -> "Cession (higher risk: shown to buyers)";
        };
    }

    private static Check mandateCheck(ListingCompliance compliance, Mandate mandate, LocalDate today) {
        String label = "Signed mandate attached and in date";
        if (compliance.getActiveMandateId() == null || mandate == null) {
            return new Check(MANDATE, label, false, "Attach a signed mandate and have the PREA sign it off.");
        }
        if (mandate.isInForceOn(today)) {
            return new Check(MANDATE, label, true, "Valid until " + mandate.getExpiryDate());
        }
        String detail = switch (mandate.getStatus()) {
            case EXPIRED -> "The mandate expired on " + mandate.getExpiryDate() + ".";
            case CANCELLED, SUPERSEDED -> "The mandate is no longer in force.";
            case DRAFT, AWAITING_SIGN_OFF -> "The mandate still needs the owner's signature and PREA sign-off.";
            case ACTIVE -> today.isBefore(mandate.getStartDate())
                    ? "The mandate only starts on " + mandate.getStartDate() + "."
                    : "The mandate expired on " + mandate.getExpiryDate() + ".";
        };
        return new Check(MANDATE, label, false, detail);
    }
}
