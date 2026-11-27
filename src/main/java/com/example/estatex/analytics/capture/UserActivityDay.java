package com.example.estatex.analytics.capture;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * "This account used EstateX on this day" (Harare date): one row per user per day, nothing more.
 * Sessions can't measure active users because they're deleted at sign-out.
 */
@Entity
@Table(name = "user_activity_days", indexes = {
        @Index(name = "uq_user_activity_day", columnList = "activity_date, user_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class UserActivityDay {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;
}
