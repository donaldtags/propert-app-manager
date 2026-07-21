package com.example.primenestprop.timeline;

import java.time.Instant;

public final class TimelineDtos {
    private TimelineDtos() {
    }

    public record TimelineEvent(
            TimelineEventType type,
            String title,
            String description,
            String status,
            Instant occurredAt,
            Long relatedId
    ) {
    }
}
