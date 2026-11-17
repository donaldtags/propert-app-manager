package com.example.primenestprop.trust;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class TrustLedgerDtos {
    private TrustLedgerDtos() {
    }

    public record RecordEntryRequest(
            @Size(max = 40) String clientId,
            @NotNull TrustEntryType type,
            @NotNull @Positive Long amountCents,
            @Pattern(regexp = "^(USD|ZWG)$", message = "currency must be USD or ZWG") String currency,
            Long propertyId,
            @NotBlank @Size(max = 160) String clientName,
            @Size(max = 160) String reference,
            @Size(max = 1000) String notes,
            Long escrowTransactionId,
            Long paymentId,
            Long commissionId,
            LocalDate occurredOn
    ) {
    }

    public record EntryResponse(Long id, TrustEntryType type, long amountCents, long signedAmountCents, String currency,
                                Long propertyId, String clientName, String reference, String notes,
                                Long escrowTransactionId, Long paymentId, Long commissionId, LocalDate occurredOn,
                                Long recordedByUserId, Instant recordedAt) {
        public static EntryResponse from(TrustLedgerEntry e) {
            return new EntryResponse(e.getId(), e.getType(), e.getAmountCents(), e.getSignedAmountCents(), e.getCurrency(),
                    e.getPropertyId(), e.getClientName(), e.getReference(), e.getNotes(), e.getEscrowTransactionId(),
                    e.getPaymentId(), e.getCommissionId(), e.getOccurredOn(), e.getRecordedByUserId(), e.getRecordedAt());
        }
    }

    public record LedgerResponse(String trustAccountReference, Map<String, Long> balancesCents, List<EntryResponse> entries) {
    }
}
