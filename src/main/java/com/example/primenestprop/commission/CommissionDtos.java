package com.example.primenestprop.commission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

public final class CommissionDtos {
    private CommissionDtos() {
    }

    /** Give amountCents, or salePriceCents and let the mandate's commission rate work it out. */
    public record CreateCommissionRequest(
            @NotNull Long propertyId,
            Long mandateId,
            Long negotiatorUserId,
            @Positive Long salePriceCents,
            @Positive Long amountCents
    ) {
    }

    public record ConveyancerConfirmationRequest(
            @NotBlank @Size(max = 160) String conveyancerName,
            @Size(max = 160) String conveyancerFirm,
            @NotBlank @Size(max = 120) String deedsReference,
            @NotNull LocalDate transferRegisteredOn,
            @Size(max = 1000) String notes
    ) {
    }

    public record ReceivedRequest(@NotNull LocalDate receivedOn, @NotBlank @Size(max = 160) String reference) {
    }

    public record CommissionResponse(Long id, Long propertyId, Long mandateId, Long negotiatorUserId, Long salePriceCents,
                                     long amountCents, String currency, CommissionStatus status, String conveyancerName,
                                     String conveyancerFirm, String deedsReference, LocalDate transferRegisteredOn,
                                     Instant confirmedAt, LocalDate receivedOn, String receivedReference,
                                     Instant createdAt, Long version) {
        public static CommissionResponse from(Commission c) {
            return new CommissionResponse(c.getId(), c.getPropertyId(), c.getMandateId(), c.getNegotiatorUserId(),
                    c.getSalePriceCents(), c.getAmountCents(), c.getCurrency(), c.getStatus(), c.getConveyancerName(),
                    c.getConveyancerFirm(), c.getDeedsReference(), c.getTransferRegisteredOn(), c.getConfirmedAt(),
                    c.getReceivedOn(), c.getReceivedReference(), c.getCreatedAt(), c.getVersion());
        }
    }
}
