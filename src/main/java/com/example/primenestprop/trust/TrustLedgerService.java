package com.example.primenestprop.trust;

import static com.example.primenestprop.common.AuditService.entry;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.common.AuditService;
import com.example.primenestprop.common.BusinessTime;
import com.example.primenestprop.organisation.OrgAccessService;
import com.example.primenestprop.organisation.OrgAccessService.OrgContext;
import com.example.primenestprop.organisation.OrgPermission;
import com.example.primenestprop.organisation.OrganisationRepository;
import com.example.primenestprop.property.PropertyRepository;
import com.example.primenestprop.user.AppUser;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrustLedgerService {
    private final OrgAccessService access;
    private final OrganisationRepository organisations;
    private final TrustLedgerRepository entries;
    private final PropertyRepository properties;
    private final AuditService audit;
    private final BusinessTime time;

    public TrustLedgerService(OrgAccessService access, OrganisationRepository organisations, TrustLedgerRepository entries, PropertyRepository properties,
                              AuditService audit, BusinessTime time) {
        this.access = access;
        this.organisations = organisations;
        this.entries = entries;
        this.properties = properties;
        this.audit = audit;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public TrustLedgerDtos.LedgerResponse ledger(String slug, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.TRUST_LEDGER_VIEW);
        Long org = ctx.organisation().getId();
        Map<String, Long> balances = new LinkedHashMap<>();
        for (Object[] row : entries.balancesByCurrency(org)) {
            balances.put((String) row[0], ((Number) row[1]).longValue());
        }
        return new TrustLedgerDtos.LedgerResponse(ctx.organisation().getTrustAccountReference(), balances,
                entries.findByOrganisationIdOrderByOccurredOnDescIdDesc(org).stream()
                        .map(TrustLedgerDtos.EntryResponse::from).toList());
    }

    /**
     * Records one movement. Outgoing movements may not overdraw the money held for that property
     * (or the unallocated bucket), and a transfer to operating must say which fee it pays.
     * Idempotent on {@code clientId} for offline replays.
     */
    @Transactional
    public TrustLedgerEntry record(String slug, TrustLedgerDtos.RecordEntryRequest r, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.TRUST_LEDGER_WRITE);
        Long org = ctx.organisation().getId();
        if (r.clientId() != null) {
            TrustLedgerEntry existing = entries.findByClientId(r.clientId()).orElse(null);
            if (existing != null) {
                if (!existing.getOrganisationId().equals(org)) {
                    throw new ApiException(HttpStatus.CONFLICT, "clientId already used");
                }
                return existing;
            }
        }
        if (r.propertyId() != null && properties.findById(r.propertyId())
                .filter(p -> org.equals(p.getOrganisationId())).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "propertyId must be one of " + ctx.organisation().getName() + "'s listings");
        }
        if (r.type() == TrustEntryType.TRANSFER_TO_OPERATING && (r.reference() == null || r.reference().isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "A transfer to the operating account must reference the fee or commission it pays.");
        }
        String currency = r.currency() == null ? ctx.organisation().getDefaultCurrency() : r.currency();
        long signed = r.type().direction() * r.amountCents();
        organisations.lockById(org); // serialise writes so two withdrawals can't both pass the balance check
        if (signed < 0) {
            long balance = entries.bucketBalance(org, currency, r.propertyId());
            if (balance + signed < 0) {
                throw new ApiException(HttpStatus.CONFLICT, "Not enough client money held"
                        + (r.propertyId() == null ? " (unallocated)" : " for this property")
                        + ": balance is " + format(balance, currency) + ", this needs " + format(r.amountCents(), currency) + ".");
            }
        }
        TrustLedgerEntry e = new TrustLedgerEntry();
        e.setClientId(r.clientId());
        e.setOrganisationId(org);
        e.setType(r.type());
        e.setAmountCents(r.amountCents());
        e.setSignedAmountCents(signed);
        e.setCurrency(currency);
        e.setPropertyId(r.propertyId());
        e.setClientName(r.clientName().trim());
        e.setReference(r.reference());
        e.setNotes(r.notes());
        e.setEscrowTransactionId(r.escrowTransactionId());
        e.setPaymentId(r.paymentId());
        e.setCommissionId(r.commissionId());
        e.setOccurredOn(r.occurredOn() == null ? time.today() : r.occurredOn());
        e.setRecordedByUserId(user.getId());
        entries.save(e);
        audit.record(user, entry("TRUST_" + r.type().name(), "TrustLedgerEntry", e.getId())
                .organisation(org)
                .money(signed, currency)
                .metadata(Map.of("clientName", e.getClientName(),
                        "propertyId", String.valueOf(e.getPropertyId()),
                        "reference", String.valueOf(e.getReference()))));
        return e;
    }

    private static String format(long cents, String currency) {
        return currency + " " + String.format("%,.2f", cents / 100.0);
    }
}
