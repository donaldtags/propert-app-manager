package com.example.estatex.offer;

import com.example.estatex.contact.Financing;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class OfferDtos {
    private OfferDtos() {
    }

    public record RecordOfferRequest(
            @Size(max = 40) String clientId,
            @NotNull Long buyerContactId,
            @NotNull @Positive Long amountCents,
            @Positive Long depositCents,
            Financing financing,
            @Size(max = 2000) String conditions,
            LocalDate expiresOn
    ) {
    }

    public record PresentRequest(@NotNull @Pattern(regexp = "^(WHATSAPP|EMAIL|IN_PERSON|PHONE)$") String via) {
    }

    public enum Decision { ACCEPT, REJECT, COUNTER }

    public record OwnerResponseRequest(
            @NotNull Decision decision,
            @Positive Long counterAmountCents,
            /** Required to accept or counter below the mandate's minimum: the owner's written instruction. */
            @Size(max = 300) String ownerInstructionReference,
            @Size(max = 1000) String note
    ) {
    }

    public record WithdrawRequest(@Size(max = 1000) String note) {
    }

    public record EventResponse(OfferEventType type, Long amountCents, String note, Long actorUserId, Instant at) {
        static EventResponse from(OfferEvent e) {
            return new EventResponse(e.getType(), e.getAmountCents(), e.getNote(), e.getActorUserId(), e.getAt());
        }
    }

    public record OfferResponse(
            Long id, Long propertyId, String propertyTitle, Long mandateId, Long buyerContactId, String buyerName,
            long amountCents, String currency, Long depositCents, Long counterAmountCents, Financing financing,
            String conditions, LocalDate expiresOn, OfferStatus status, String presentedVia, Instant presentedAt,
            Instant decidedAt, String ownerInstructionReference,
            /** At or above the owner's minimum in the mandate (or no minimum set). */
            boolean withinAuthority, Long mandateMinimumCents, String ownerName, String ownerWhatsapp,
            Instant createdAt, List<EventResponse> events, Long version
    ) {
    }
}
