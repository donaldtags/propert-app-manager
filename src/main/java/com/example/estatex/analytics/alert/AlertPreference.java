package com.example.estatex.analytics.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** How one staff member wants to hear about one kind of alert. No row = in-app on, email off. */
@Entity
@Table(name = "alert_preferences", indexes = @Index(name = "uq_alert_preference", columnList = "user_id, rule_key", unique = true))
@Getter
@Setter
@NoArgsConstructor
public class AlertPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "rule_key", nullable = false, length = 40)
    private String ruleKey;
    @Column(name = "in_app", nullable = false)
    private boolean inApp;
    @Column(nullable = false)
    private boolean email;
}
