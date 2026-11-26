package com.example.estatex.analytics.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A named set of dashboard filters a staff member saved, optionally shared with other staff. */
@Entity
@Table(name = "analytics_saved_views", indexes = @Index(name = "idx_saved_view_page", columnList = "page, owner_user_id"))
@Getter
@Setter
@NoArgsConstructor
public class SavedView {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;
    @Column(nullable = false, length = 80)
    private String name;
    @Column(nullable = false, length = 30)
    private String page;
    @Column(name = "filters_json", nullable = false, length = 2000)
    private String filtersJson;
    @Column(nullable = false)
    private boolean shared;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
