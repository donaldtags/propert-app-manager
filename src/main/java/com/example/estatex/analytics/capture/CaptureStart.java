package com.example.estatex.analytics.capture;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** When an analytics capture table started recording (written once by migration V8). */
@Entity
@Table(name = "analytics_capture_starts")
@Getter
@Setter
@NoArgsConstructor
public class CaptureStart {
    @Id
    @Column(length = 40)
    private String source;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
}
