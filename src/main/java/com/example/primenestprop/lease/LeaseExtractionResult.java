package com.example.primenestprop.lease;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Lease fields Claude extracts from an uploaded lease document to pre-fill the create-lease form. */
public record LeaseExtractionResult(
        @JsonPropertyDescription("Lease start date in YYYY-MM-DD format if stated in the document, otherwise null.")
        LocalDate startDate,
        @JsonPropertyDescription("Lease end date in YYYY-MM-DD format if stated in the document, otherwise null.")
        LocalDate endDate,
        @JsonPropertyDescription("Monthly rent amount as a plain number if stated, otherwise null.")
        BigDecimal monthlyRent,
        @JsonPropertyDescription("Security/damage deposit amount as a plain number if stated, otherwise null.")
        BigDecimal depositAmount,
        @JsonPropertyDescription("Currency code (e.g. USD, ZWL, ZAR, GBP) if stated, otherwise null.")
        String currency,
        @JsonPropertyDescription("Tenant's full name if stated in the document, otherwise null. Informational only - "
                + "does not select a platform user.")
        String tenantFullName,
        @JsonPropertyDescription("The property address as written in the document if stated, otherwise null. "
                + "Informational only - does not select a platform property.")
        String propertyAddress,
        @JsonPropertyDescription("A short (1-3 sentence) plain-language summary of notable terms/clauses in the "
                + "document (e.g. pets, renewal, maintenance responsibilities), or null if nothing notable.")
        String notableTerms
) {
}
