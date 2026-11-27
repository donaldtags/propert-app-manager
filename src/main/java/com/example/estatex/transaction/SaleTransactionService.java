package com.example.estatex.transaction;

import static com.example.estatex.common.AuditService.entry;

import com.example.estatex.common.ApiException;
import com.example.estatex.common.AuditService;
import com.example.estatex.property.ListingType;
import com.example.estatex.property.Property;
import com.example.estatex.property.PropertyService;
import com.example.estatex.property.PropertyStatus;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.Permission;
import com.example.estatex.user.UserRole;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reported and verified property sales. The rule that matters: an agent's or owner's report
 * never becomes a verified transaction on its own. Verification needs conveyancer or Deeds
 * Registry evidence, checked by EstateX staff who are not the person who reported the sale.
 */
@Service
public class SaleTransactionService {
    private static final Set<SaleVerification> OPEN = Set.of(SaleVerification.REPORTED, SaleVerification.VERIFIED);

    private final SaleTransactionRepository sales;
    private final PropertyService properties;
    private final AuditService audit;

    public SaleTransactionService(SaleTransactionRepository sales, PropertyService properties, AuditService audit) {
        this.sales = sales;
        this.properties = properties;
        this.audit = audit;
    }

    @Transactional
    public SaleTransaction report(Long propertyId, SaleTransactionDtos.ReportSaleRequest r, AppUser user) {
        Property p = properties.require(propertyId);
        properties.requireOwnerOrAdmin(p, user);
        if (p.getListingType() != ListingType.SALE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only properties listed for sale can be reported as sold.");
        }
        if (sales.existsByPropertyIdAndVerificationIn(propertyId, OPEN)) {
            throw new ApiException(HttpStatus.CONFLICT, "A sale has already been recorded for this property.");
        }
        if (r.completionDate().isAfter(LocalDate.now().plusDays(90))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The completion date is too far in the future.");
        }
        SaleTransaction t = new SaleTransaction();
        t.setPropertyId(p.getId());
        t.setPrice(r.price());
        t.setCurrency(r.currency() == null || r.currency().isBlank() ? p.getCurrency() : r.currency().trim().toUpperCase(Locale.ROOT));
        t.setCompletionDate(r.completionDate());
        t.setSource(isAgentFor(p, user) ? SaleSource.AGENT_REPORTED : SaleSource.OWNER_REPORTED);
        t.setReportedByUserId(user.getId());
        t.setCountry(p.getCountry());
        t.setCity(p.getCity());
        t.setSuburb(p.getSuburb());
        t.setCategory(p.getCategory() == null ? null : p.getCategory().name());
        t.setBedrooms(p.getBedrooms());
        t.setFloorAreaM2(p.getFloorAreaM2());
        t.setAskingPrice(p.getPrice());
        t.setOrganisationId(p.getOrganisationId());
        sales.save(t);
        if (p.getStatus() != PropertyStatus.REPORTED_SOLD) {
            PropertyStatus before = p.getStatus();
            p.setStatus(PropertyStatus.REPORTED_SOLD);
            audit.record(user, entry("LISTING_REPORTED_SOLD", "Property", p.getId()).organisation(p.getOrganisationId())
                    .transition(before, PropertyStatus.REPORTED_SOLD).metadata(Map.of("saleTransactionId", t.getId())));
        }
        audit.record(user, entry("SALE_REPORTED", "SaleTransaction", t.getId()).organisation(p.getOrganisationId())
                .money(t.getPrice().movePointRight(2).longValue(), t.getCurrency()));
        return t;
    }

    @Transactional
    public SaleTransaction verify(Long id, SaleTransactionDtos.VerifySaleRequest r, AppUser staff) {
        requireVerifier(staff);
        SaleTransaction t = sales.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Sale not found"));
        if (t.getVerification() != SaleVerification.REPORTED) {
            throw new ApiException(HttpStatus.CONFLICT, "This sale has already been " + t.getVerification().name().toLowerCase(Locale.ROOT) + ".");
        }
        if (Objects.equals(t.getReportedByUserId(), staff.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Someone other than the person who reported a sale must verify it.");
        }
        SaleVerification before = t.getVerification();
        if (Boolean.TRUE.equals(r.approved())) {
            if (r.source() == null || !r.source().canVerify()) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "A sale can only be verified from a conveyancer's confirmation or a Deeds Registry record.");
            }
            if (r.evidenceReference() == null || r.evidenceReference().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Give the conveyancer's reference or the deed of transfer number.");
            }
            t.setSource(r.source());
            t.setEvidenceReference(r.evidenceReference().trim());
            t.setVerification(SaleVerification.VERIFIED);
        } else {
            if (r.note() == null || r.note().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Say why the sale can't be verified.");
            }
            t.setVerification(SaleVerification.REJECTED);
        }
        t.setVerificationNote(r.note());
        t.setVerifiedByUserId(staff.getId());
        t.setVerifiedAt(Instant.now());
        audit.record(staff, entry("SALE_" + t.getVerification().name(), "SaleTransaction", t.getId())
                .organisation(t.getOrganisationId()).transition(before, t.getVerification())
                .metadata(t.getEvidenceReference() == null ? Map.of() : Map.of("evidence", t.getEvidenceReference())));
        return t;
    }

    @Transactional(readOnly = true)
    public List<SaleTransaction> forProperty(Long propertyId, AppUser user) {
        Property p = properties.require(propertyId);
        if (user == null || !user.hasPermission(Permission.PROPERTY_VERIFY)) {
            properties.requireOwnerOrAdmin(p, user);
        }
        return sales.findByPropertyIdOrderByReportedAtDesc(propertyId);
    }

    @Transactional(readOnly = true)
    public List<SaleTransaction> queue(AppUser staff) {
        requireVerifier(staff);
        return sales.findTop200ByVerificationOrderByReportedAtAsc(SaleVerification.REPORTED);
    }

    private static void requireVerifier(AppUser user) {
        if (user == null || !user.hasPermission(Permission.PROPERTY_VERIFY)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only EstateX verification staff can verify sales.");
        }
    }

    private static boolean isAgentFor(Property p, AppUser user) {
        if (p.getAgent() != null && Objects.equals(p.getAgent().getId(), user.getId())) return true;
        if (p.isOrganisationListing()) return true;
        return user.getRoles().contains(UserRole.AGENT);
    }
}
