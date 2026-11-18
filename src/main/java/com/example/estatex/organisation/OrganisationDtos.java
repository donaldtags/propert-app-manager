package com.example.estatex.organisation;

import com.example.estatex.common.AuditLog;
import com.example.estatex.user.AppUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;

public final class OrganisationDtos {
    private OrganisationDtos() {
    }

    private static final String HEX = "^#[0-9A-Fa-f]{6}$";

    public record ThemeDto(
            @Pattern(regexp = HEX) String lightPrimary,
            @Pattern(regexp = HEX) String lightSurface,
            @Pattern(regexp = HEX) String lightText,
            @Pattern(regexp = HEX) String lightMuted,
            @Pattern(regexp = HEX) String lightAccent,
            @Pattern(regexp = HEX) String darkPrimary,
            @Pattern(regexp = HEX) String darkSurface,
            @Pattern(regexp = HEX) String darkText,
            @Pattern(regexp = HEX) String darkMuted,
            @Pattern(regexp = HEX) String darkAccent,
            @Size(max = 40) String headingFont,
            @Size(max = 40) String bodyFont
    ) {
        static ThemeDto from(OrgTheme t) {
            if (t == null) {
                return null;
            }
            return new ThemeDto(t.getLightPrimary(), t.getLightSurface(), t.getLightText(), t.getLightMuted(),
                    t.getLightAccent(), t.getDarkPrimary(), t.getDarkSurface(), t.getDarkText(), t.getDarkMuted(),
                    t.getDarkAccent(), t.getHeadingFont(), t.getBodyFont());
        }

        void applyTo(OrgTheme t) {
            if (lightPrimary != null) t.setLightPrimary(lightPrimary);
            if (lightSurface != null) t.setLightSurface(lightSurface);
            if (lightText != null) t.setLightText(lightText);
            if (lightMuted != null) t.setLightMuted(lightMuted);
            if (lightAccent != null) t.setLightAccent(lightAccent);
            if (darkPrimary != null) t.setDarkPrimary(darkPrimary);
            if (darkSurface != null) t.setDarkSurface(darkSurface);
            if (darkText != null) t.setDarkText(darkText);
            if (darkMuted != null) t.setDarkMuted(darkMuted);
            if (darkAccent != null) t.setDarkAccent(darkAccent);
            if (headingFont != null) t.setHeadingFont(headingFont);
            if (bodyFont != null) t.setBodyFont(bodyFont);
        }
    }

    /**
     * Organisation profile. Fields after {@code timezone} are internal and only populated for staff
     * members of the organisation (null, and therefore omitted from JSON, for everyone else).
     */
    public record OrganisationResponse(
            Long id,
            String slug,
            String name,
            String slogan,
            Set<Division> divisions,
            String logoOnLightUrl,
            String logoOnDarkUrl,
            ThemeDto theme,
            String phone,
            String whatsappNumber,
            String instagramHandle,
            String email,
            String address,
            String website,
            String preaName,
            String eacRegistrationNumber,
            String defaultCurrency,
            String timezone,
            Long preaUserId,
            String trustAccountReference,
            String trustAccountBank,
            Integer saleCommissionBps,
            Integer managementFeeBps,
            Integer lettingFeeMonthsX100,
            Long version
    ) {
        public static OrganisationResponse from(Organisation o, boolean includeInternal) {
            return new OrganisationResponse(o.getId(), o.getSlug(), o.getName(), o.getSlogan(), o.getDivisions(),
                    o.getLogoOnLightUrl(), o.getLogoOnDarkUrl(), ThemeDto.from(o.getTheme()), o.getPhone(),
                    o.getWhatsappNumber(), o.getInstagramHandle(), o.getEmail(), o.getAddress(), o.getWebsite(),
                    o.getPreaName(), o.getEacRegistrationNumber(), o.getDefaultCurrency(), o.getTimezone(),
                    includeInternal ? o.getPreaUserId() : null,
                    includeInternal ? o.getTrustAccountReference() : null,
                    includeInternal ? o.getTrustAccountBank() : null,
                    includeInternal ? o.getSaleCommissionBps() : null,
                    includeInternal ? o.getManagementFeeBps() : null,
                    includeInternal ? o.getLettingFeeMonthsX100() : null,
                    includeInternal ? o.getVersion() : null);
        }
    }

    public record MyOrgContextResponse(
            OrganisationResponse organisation,
            Long membershipId,
            Set<OrgRole> roles,
            Set<Division> divisions,
            Set<OrgPermission> permissions,
            boolean platformOverride
    ) {
    }

    public record MemberResponse(
            Long id,
            Long userId,
            String fullName,
            String email,
            String phone,
            Set<OrgRole> roles,
            Set<Division> divisions,
            boolean active,
            Instant createdAt,
            Long version
    ) {
        public static MemberResponse from(OrganisationMembership m) {
            AppUser u = m.getUser();
            return new MemberResponse(m.getId(), u.getId(), u.getFullName(), u.getEmail(), u.getPhone(),
                    m.getRoles(), m.getDivisions(), m.isActive(), m.getCreatedAt(), m.getVersion());
        }
    }

    public record AddMemberRequest(
            @NotBlank @Email String email,
            @NotEmpty Set<OrgRole> roles,
            Set<Division> divisions
    ) {
    }

    public record UpdateMemberRequest(
            Set<OrgRole> roles,
            Set<Division> divisions,
            Boolean active
    ) {
    }

    public record CreateOrganisationRequest(
            @NotBlank @Pattern(regexp = "^[a-z0-9](?:[a-z0-9-]{1,58}[a-z0-9])$",
                    message = "slug must be 3-60 lowercase letters, digits or hyphens") String slug,
            @NotBlank @Size(max = 160) String name,
            Set<Division> divisions
    ) {
    }

    public record UpdateOrganisationRequest(
            @Size(max = 160) String name,
            @Size(max = 200) String slogan,
            Set<Division> divisions,
            @Size(max = 255) String logoOnLightUrl,
            @Size(max = 255) String logoOnDarkUrl,
            @Valid ThemeDto theme,
            @Size(max = 40) String phone,
            @Pattern(regexp = "^[0-9]{8,15}$", message = "whatsappNumber must be digits only, international format, no +") String whatsappNumber,
            @Size(max = 60) String instagramHandle,
            @Email @Size(max = 160) String email,
            @Size(max = 300) String address,
            @Size(max = 200) String website,
            @Size(max = 80) String eacRegistrationNumber,
            @Size(max = 120) String trustAccountReference,
            @Size(max = 120) String trustAccountBank,
            @Pattern(regexp = "^(USD|ZWG)$", message = "defaultCurrency must be USD or ZWG") String defaultCurrency,
            @Min(0) @Max(2000) Integer saleCommissionBps,
            @Min(0) @Max(3000) Integer managementFeeBps,
            @Min(0) @Max(1200) Integer lettingFeeMonthsX100,
            /** Optimistic-lock check: the version the editor loaded. Optional for now. */
            Long version
    ) {
    }

    public record AuditEntryResponse(
            Long id,
            Long actorUserId,
            String actorName,
            String action,
            String entityType,
            Long entityId,
            String fromState,
            String toState,
            Long amountCents,
            String currency,
            String metadataJson,
            Instant createdAt
    ) {
        public static AuditEntryResponse from(AuditLog log, String actorName) {
            return new AuditEntryResponse(log.getId(), log.getActorUserId(), actorName, log.getAction(),
                    log.getEntityType(), log.getEntityId(), log.getFromState(), log.getToState(),
                    log.getAmountCents(), log.getCurrency(), log.getMetadataJson(), log.getCreatedAt());
        }
    }
}
