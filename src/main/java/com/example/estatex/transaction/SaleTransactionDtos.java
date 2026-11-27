package com.example.estatex.transaction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class SaleTransactionDtos {
    private SaleTransactionDtos() {
    }

    /** The owner or agent says the property sold. */
    public record ReportSaleRequest(
            @NotNull @DecimalMin(value = "0.01") BigDecimal price,
            String currency,
            @NotNull LocalDate completionDate) {
    }

    /** EstateX staff decision. approved=true needs a verifying source and an evidence reference. */
    public record VerifySaleRequest(
            @NotNull Boolean approved,
            SaleSource source,
            String evidenceReference,
            String note) {
    }

    public record SaleTransactionResponse(
            Long id, Long propertyId, BigDecimal price, String currency, LocalDate completionDate,
            SaleSource source, SaleVerification verification, Long reportedByUserId, Instant reportedAt,
            String evidenceReference, Long verifiedByUserId, Instant verifiedAt, String verificationNote,
            String city, String suburb, String category, Integer bedrooms, BigDecimal askingPrice) {
        public static SaleTransactionResponse from(SaleTransaction t) {
            return new SaleTransactionResponse(t.getId(), t.getPropertyId(), t.getPrice(), t.getCurrency(),
                    t.getCompletionDate(), t.getSource(), t.getVerification(), t.getReportedByUserId(),
                    t.getReportedAt(), t.getEvidenceReference(), t.getVerifiedByUserId(), t.getVerifiedAt(),
                    t.getVerificationNote(), t.getCity(), t.getSuburb(), t.getCategory(), t.getBedrooms(),
                    t.getAskingPrice());
        }
    }
}
