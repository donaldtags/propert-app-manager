package com.example.estatex.commission;

import static com.example.estatex.common.AuditService.entry;

import com.example.estatex.common.ApiException;
import com.example.estatex.common.AuditService;
import com.example.estatex.common.BusinessTime;
import com.example.estatex.listing.Mandate;
import com.example.estatex.listing.MandateRepository;
import com.example.estatex.organisation.OrgAccessService;
import com.example.estatex.organisation.OrgAccessService.OrgContext;
import com.example.estatex.organisation.OrgPermission;
import com.example.estatex.property.Property;
import com.example.estatex.property.PropertyRepository;
import com.example.estatex.user.AppUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commission is recorded as EARNED only when the conveyancer confirms transfer is registered at
 * the Deeds Office. Nothing else moves a commission to EARNED, so scorecards and statements can
 * never count a sale that hasn't actually transferred.
 */
@Service
public class CommissionService {
    private final OrgAccessService access;
    private final CommissionRepository commissions;
    private final PropertyRepository properties;
    private final MandateRepository mandates;
    private final AuditService audit;
    private final BusinessTime time;

    public CommissionService(OrgAccessService access, CommissionRepository commissions, PropertyRepository properties,
                             MandateRepository mandates, AuditService audit, BusinessTime time) {
        this.access = access;
        this.commissions = commissions;
        this.properties = properties;
        this.mandates = mandates;
        this.audit = audit;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public List<Commission> list(String slug, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.ORG_VIEW);
        return commissions.findByOrganisationIdOrderByCreatedAtDesc(ctx.organisation().getId());
    }

    @Transactional
    public Commission create(String slug, CommissionDtos.CreateCommissionRequest r, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.LISTING_EDIT);
        Long org = ctx.organisation().getId();
        Property property = properties.findById(r.propertyId())
                .filter(p -> org.equals(p.getOrganisationId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Listing not found"));
        Mandate mandate = r.mandateId() == null ? null : mandates.findById(r.mandateId())
                .filter(m -> m.getPropertyId().equals(property.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "That mandate isn't for this listing"));
        long amount;
        if (r.amountCents() != null) {
            amount = r.amountCents();
        } else if (r.salePriceCents() != null && mandate != null && mandate.getCommissionBps() != null) {
            amount = BigDecimal.valueOf(r.salePriceCents())
                    .multiply(BigDecimal.valueOf(mandate.getCommissionBps()))
                    .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP)
                    .longValueExact();
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Give the commission amount, or the sale price and a mandate with a commission rate.");
        }
        Commission c = new Commission();
        c.setOrganisationId(org);
        c.setPropertyId(property.getId());
        c.setMandateId(mandate == null ? null : mandate.getId());
        c.setNegotiatorUserId(r.negotiatorUserId() == null ? user.getId() : r.negotiatorUserId());
        c.setSalePriceCents(r.salePriceCents());
        c.setAmountCents(amount);
        c.setCurrency(mandate != null ? mandate.getCurrency() : property.getCurrency());
        c.setCreatedByUserId(user.getId());
        commissions.save(c);
        audit.record(user, entry("COMMISSION_EXPECTED", "Commission", c.getId())
                .organisation(org)
                .transition(null, CommissionStatus.EXPECTED)
                .money(amount, c.getCurrency())
                .metadata(Map.of("propertyId", property.getId())));
        return c;
    }

    @Transactional
    public Commission confirmTransfer(String slug, Long id, CommissionDtos.ConveyancerConfirmationRequest r, AppUser user) {
        Commission c = load(slug, id, user, OrgPermission.COMMISSION_CONFIRM);
        if (c.getStatus() != CommissionStatus.EXPECTED) {
            throw new ApiException(HttpStatus.CONFLICT, "This commission is already " + c.getStatus() + ".");
        }
        if (r.transferRegisteredOn().isAfter(time.today())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Transfer can't be registered in the future.");
        }
        c.setConveyancerName(r.conveyancerName().trim());
        c.setConveyancerFirm(r.conveyancerFirm());
        c.setDeedsReference(r.deedsReference().trim());
        c.setTransferRegisteredOn(r.transferRegisteredOn());
        c.setConfirmationNotes(r.notes());
        c.setConfirmedByUserId(user.getId());
        c.setConfirmedAt(Instant.now());
        c.setStatus(CommissionStatus.EARNED);
        audit.record(user, entry("COMMISSION_EARNED", "Commission", c.getId())
                .organisation(c.getOrganisationId())
                .transition(CommissionStatus.EXPECTED, CommissionStatus.EARNED)
                .money(c.getAmountCents(), c.getCurrency())
                .metadata(Map.of("conveyancer", c.getConveyancerName(), "deedsReference", c.getDeedsReference(),
                        "transferRegisteredOn", c.getTransferRegisteredOn().toString())));
        return c;
    }

    @Transactional
    public Commission markReceived(String slug, Long id, CommissionDtos.ReceivedRequest r, AppUser user) {
        Commission c = load(slug, id, user, OrgPermission.COMMISSION_CONFIRM);
        if (c.getStatus() != CommissionStatus.EARNED) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Commission can only be marked received after the conveyancer has confirmed transfer.");
        }
        c.setReceivedOn(r.receivedOn());
        c.setReceivedReference(r.reference().trim());
        c.setReceivedRecordedByUserId(user.getId());
        c.setStatus(CommissionStatus.RECEIVED);
        audit.record(user, entry("COMMISSION_RECEIVED", "Commission", c.getId())
                .organisation(c.getOrganisationId())
                .transition(CommissionStatus.EARNED, CommissionStatus.RECEIVED)
                .money(c.getAmountCents(), c.getCurrency()));
        return c;
    }

    @Transactional
    public Commission cancel(String slug, Long id, AppUser user) {
        Commission c = load(slug, id, user, OrgPermission.COMMISSION_CONFIRM);
        if (c.getStatus() != CommissionStatus.EXPECTED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only an expected commission can be cancelled.");
        }
        c.setStatus(CommissionStatus.CANCELLED);
        audit.record(user, entry("COMMISSION_CANCELLED", "Commission", c.getId())
                .organisation(c.getOrganisationId())
                .transition(CommissionStatus.EXPECTED, CommissionStatus.CANCELLED)
                .money(c.getAmountCents(), c.getCurrency()));
        return c;
    }

    private Commission load(String slug, Long id, AppUser user, OrgPermission permission) {
        OrgContext ctx = access.require(user, slug, permission);
        return commissions.findById(id)
                .filter(c -> c.getOrganisationId().equals(ctx.organisation().getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Commission not found"));
    }
}
