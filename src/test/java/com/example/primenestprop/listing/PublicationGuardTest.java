package com.example.primenestprop.listing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.primenestprop.property.Property;
import com.example.primenestprop.property.TitleType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class PublicationGuardTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

    private record Fixture(Property property, ListingCompliance compliance, Mandate mandate) {
        PublicationGuard.Report report() {
            return PublicationGuard.evaluate(property, compliance, mandate, TODAY);
        }
    }

    /** A listing that passes every gate. Each test breaks exactly one thing. */
    private static Fixture compliant() {
        Property p = new Property();
        p.setTitleType(TitleType.TITLE_DEED);
        Mandate m = new Mandate();
        m.setId(9L);
        m.setStatus(MandateStatus.ACTIVE);
        m.setStartDate(TODAY.minusDays(10));
        m.setExpiryDate(TODAY.plusDays(80));
        m.setPreaSignedOffAt(Instant.parse("2026-11-01T08:00:00Z"));
        ListingCompliance c = new ListingCompliance();
        c.setTitleVerificationStatus(TitleVerificationStatus.VERIFIED);
        c.setActiveMandateId(9L);
        c.setOwnerConsentPhotos(true);
        c.setOwnerConsentAddress(true);
        c.setPreaApprovedAt(Instant.parse("2026-11-10T08:00:00Z"));
        return new Fixture(p, c, m);
    }

    private static List<String> unmetKeys(Consumer<Fixture> breakIt) {
        Fixture f = compliant();
        breakIt.accept(f);
        return f.report().checks().stream().filter(c -> !c.met()).map(PublicationGuard.Check::key).toList();
    }

    @Test
    void compliantListingIsPublishable() {
        assertTrue(compliant().report().publishable());
    }

    @Test
    void unverifiedTitleBlocksPublication() {
        assertEquals(List.of(PublicationGuard.TITLE_VERIFIED),
                unmetKeys(f -> f.compliance().setTitleVerificationStatus(TitleVerificationStatus.IN_PROGRESS)));
        assertEquals(List.of(PublicationGuard.TITLE_VERIFIED),
                unmetKeys(f -> f.compliance().setTitleVerificationStatus(TitleVerificationStatus.FAILED)));
    }

    @Test
    void missingTitleTypeBlocksPublication() {
        assertEquals(List.of(PublicationGuard.TITLE_TYPE), unmetKeys(f -> f.property().setTitleType(null)));
    }

    @Test
    void noMandateBlocksPublication() {
        assertEquals(List.of(PublicationGuard.MANDATE), unmetKeys(f -> f.compliance().setActiveMandateId(null)));
    }

    @Test
    void mandateMustBeSignedOffAndInDate() {
        assertEquals(List.of(PublicationGuard.MANDATE), unmetKeys(f -> f.mandate().setExpiryDate(TODAY.minusDays(1))));
        assertEquals(List.of(PublicationGuard.MANDATE), unmetKeys(f -> f.mandate().setStartDate(TODAY.plusDays(1))));
        assertEquals(List.of(PublicationGuard.MANDATE), unmetKeys(f -> f.mandate().setStatus(MandateStatus.AWAITING_SIGN_OFF)));
        assertEquals(List.of(PublicationGuard.MANDATE), unmetKeys(f -> f.mandate().setStatus(MandateStatus.EXPIRED)));
        assertEquals(List.of(PublicationGuard.MANDATE), unmetKeys(f -> f.mandate().setPreaSignedOffAt(null)));
        // The expiry date itself is the last valid day.
        assertTrue(unmetKeys(f -> f.mandate().setExpiryDate(TODAY)).isEmpty());
    }

    @Test
    void bothConsentsAreRequired() {
        assertEquals(List.of(PublicationGuard.OWNER_CONSENT), unmetKeys(f -> f.compliance().setOwnerConsentPhotos(false)));
        assertEquals(List.of(PublicationGuard.OWNER_CONSENT), unmetKeys(f -> f.compliance().setOwnerConsentAddress(false)));
    }

    @Test
    void preaApprovalMustPostDateTheLastMaterialChange() {
        assertEquals(List.of(PublicationGuard.PREA_APPROVAL), unmetKeys(f -> f.compliance().clearPreaApproval()));
        assertEquals(List.of(PublicationGuard.PREA_APPROVAL),
                unmetKeys(f -> f.compliance().setMaterialChangedAt(Instant.parse("2026-11-12T08:00:00Z"))));
        assertTrue(unmetKeys(f -> f.compliance().setMaterialChangedAt(Instant.parse("2026-11-05T08:00:00Z"))).isEmpty());
    }

    @Test
    void approvalReadinessIgnoresOnlyTheApprovalItself() {
        Fixture f = compliant();
        f.compliance().clearPreaApproval();
        assertTrue(f.report().readyForApproval());
        assertFalse(f.report().publishable());
        f.compliance().setOwnerConsentAddress(false);
        assertFalse(f.report().readyForApproval());
    }
}
