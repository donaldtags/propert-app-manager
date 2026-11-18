package com.example.estatex.organisation;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An agency (or other operator) running its business on the platform as its own workspace. */
@Entity
@Table(name = "organisations")
@Getter
@Setter
@NoArgsConstructor
public class Organisation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    /** URL key, e.g. "estatex". Immutable once created. */
    @Column(nullable = false, unique = true, length = 60)
    private String slug;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 200)
    private String slogan;

    @Convert(converter = Division.SetConverter.class)
    @Column(length = 200)
    private Set<Division> divisions = EnumSet.noneOf(Division.class);

    /** Public paths (under the frontend's /public) or storage URLs. */
    private String logoOnLightUrl;
    private String logoOnDarkUrl;

    @Embedded
    private OrgTheme theme = new OrgTheme();

    @Column(length = 40) private String phone;
    /** Digits only, international format without "+", as wa.me expects, e.g. 263784542297. */
    @Column(length = 20) private String whatsappNumber;
    @Column(length = 60) private String instagramHandle;
    @Column(length = 160) private String email;
    @Column(length = 300) private String address;
    @Column(length = 200) private String website;

    // Estate Agents Council registration
    private Long preaUserId;
    @Column(length = 160) private String preaName;
    @Column(length = 80) private String eacRegistrationNumber;
    @Column(length = 120) private String trustAccountReference;
    @Column(length = 120) private String trustAccountBank;

    @Column(length = 3, nullable = false)
    private String defaultCurrency = "USD";

    @Column(length = 40, nullable = false)
    private String timezone = "Africa/Harare";

    /** Fee defaults in basis points (500 = 5%), editable per org. */
    private int saleCommissionBps = 500;
    private int managementFeeBps = 1000;
    /** Letting fee expressed in months of rent, times 100 (100 = one month). */
    private int lettingFeeMonthsX100 = 100;

    private boolean active = true;
    private Instant createdAt = Instant.now();
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
