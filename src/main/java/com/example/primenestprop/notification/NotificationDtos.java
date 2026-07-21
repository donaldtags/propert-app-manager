package com.example.primenestprop.notification;

import java.time.Instant;

public final class NotificationDtos {
    private NotificationDtos() {
    }

    public record NotificationResponse(
            Long id,
            NotificationType type,
            String title,
            String message,
            Long relatedId,
            boolean read,
            Instant createdAt
    ) {
        public static NotificationResponse from(Notification notification) {
            return new NotificationResponse(
                    notification.getId(),
                    notification.getType(),
                    notification.getTitle(),
                    notification.getMessage(),
                    notification.getRelatedId(),
                    notification.isRead(),
                    notification.getCreatedAt()
            );
        }
    }

    public record UnreadCountResponse(long unreadCount) {
    }
}
