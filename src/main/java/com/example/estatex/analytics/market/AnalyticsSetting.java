package com.example.estatex.analytics.market;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An analytics assumption an administrator has set (e.g. the cost ratio behind net yield). */
@Entity
@Table(name = "analytics_settings")
@Getter
@Setter
@NoArgsConstructor
public class AnalyticsSetting {
    @Id
    @Column(name = "setting_key", length = 60)
    private String key;
    @Column(name = "setting_value", nullable = false, length = 200)
    private String value;
    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
