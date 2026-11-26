package com.example.estatex.property;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

/**
 * Where a listing is in its life. Each state is reported on its own and never added to another
 * in analytics; see docs/admin-analytics-plan.md §4.1.
 *
 * <p>Verification is a separate axis ({@link VerificationStatus}). A listing can be AVAILABLE and
 * VERIFIED at the same time.
 *
 * <p>A <em>verified completed sale</em> is not a listing status at all. It is a
 * {@code SaleTransaction} that EstateX staff verified from conveyancer or deeds-registry
 * evidence. {@link #REPORTED_SOLD} only means someone said it sold.
 */
public enum PropertyStatus {
    DRAFT,
    AVAILABLE,
    /** An offer was accepted. This was called RESERVED before 2026-11 (migrated by V5). */
    UNDER_OFFER,
    OCCUPIED,
    /** The owner or agent says it sold. Not independently verified. This was SOLD before 2026-11. */
    REPORTED_SOLD,
    /** The owner or agent took it off the market. */
    WITHDRAWN,
    /** Taken off the market automatically because its mandate ran out. */
    EXPIRED,
    /** Deactivated by EstateX, for example after unpaid listing fees or a substantiated report. */
    INACTIVE;

    /** Accepts the pre-2026-11 names from older clients and queued offline edits. */
    @JsonCreator
    public static PropertyStatus fromJson(String value) {
        if (value == null) return null;
        String v = value.trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "SOLD" -> REPORTED_SOLD;
            case "RESERVED" -> UNDER_OFFER;
            default -> valueOf(v);
        };
    }

    /**
     * Not shown to the public, even by direct link. A withdrawn or expired listing is no longer
     * being marketed, so it is treated like a draft. Its owner, agent and staff still see it.
     */
    public boolean isHiddenFromPublic() {
        return this == DRAFT || this == WITHDRAWN || this == EXPIRED;
    }

    /** Off the market but able to go live again. */
    public boolean isRelistable() {
        return this == DRAFT || this == INACTIVE || this == WITHDRAWN || this == EXPIRED;
    }
}
