package com.example.primenestprop.enquiry;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class EnquiryDtos {
    private EnquiryDtos() {
    }

    public record CaptureEnquiryRequest(
            @Size(max = 40) String clientId,
            @NotNull EnquiryChannel channel,
            EnquiryIntent intent,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 40) String phone,
            @Email @Size(max = 160) String email,
            @Size(max = 2000) String message,
            Long propertyId,
            @Positive Long budgetCents,
            @Pattern(regexp = "^(USD|ZWG)$", message = "currency must be USD or ZWG") String currency,
            /** When it was recorded on the device; defaults to now. Can't be in the future. */
            Instant capturedAt
    ) {
    }

    public record UpdateStatusRequest(@NotNull EnquiryStatus status) {
    }

    public record EnquiryResponse(Long id, String clientId, EnquiryChannel channel, EnquiryIntent intent,
                                  EnquiryStatus status, String name, String phone, String email, String message,
                                  Long propertyId, String propertyTitle, Long budgetCents, String currency,
                                  Long capturedByUserId, Instant capturedAt, Long version) {
        public static EnquiryResponse from(Enquiry e, String propertyTitle) {
            return new EnquiryResponse(e.getId(), e.getClientId(), e.getChannel(), e.getIntent(), e.getStatus(),
                    e.getName(), e.getPhone(), e.getEmail(), e.getMessage(), e.getPropertyId(), propertyTitle,
                    e.getBudgetCents(), e.getCurrency(), e.getCapturedByUserId(), e.getCapturedAt(), e.getVersion());
        }
    }
}
