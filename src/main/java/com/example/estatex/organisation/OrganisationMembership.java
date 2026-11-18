package com.example.estatex.organisation;

import com.example.estatex.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A user's place in one organisation: which org roles they hold and which divisions they work in. */
@Entity
@Table(name = "organisation_memberships",
        uniqueConstraints = @UniqueConstraint(name = "uq_org_membership", columnNames = {"organisation_id", "user_id"}),
        indexes = @Index(name = "idx_org_membership_user", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class OrganisationMembership {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Organisation organisation;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser user;

    @Convert(converter = OrgRole.SetConverter.class)
    @Column(length = 200, nullable = false)
    private Set<OrgRole> roles = EnumSet.noneOf(OrgRole.class);

    /** Empty means "all divisions the organisation has enabled". */
    @Convert(converter = Division.SetConverter.class)
    @Column(length = 200)
    private Set<Division> divisions = EnumSet.noneOf(Division.class);

    private boolean active = true;
    private Long invitedByUserId;
    private Instant createdAt = Instant.now();

    public Set<OrgPermission> permissions() {
        return active ? OrgRolePermissions.of(roles) : Set.of();
    }

    public boolean has(OrgPermission permission) {
        return permissions().contains(permission);
    }
}
