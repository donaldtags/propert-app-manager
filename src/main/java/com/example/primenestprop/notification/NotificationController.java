package com.example.primenestprop.notification;

import com.example.primenestprop.notification.NotificationDtos.NotificationResponse;
import com.example.primenestprop.notification.NotificationDtos.UnreadCountResponse;
import com.example.primenestprop.user.AppUser;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping("/mine")
    List<NotificationResponse> mine(@AuthenticationPrincipal AppUser currentUser) {
        return service.mine(currentUser).stream().map(NotificationResponse::from).toList();
    }

    @GetMapping("/unread-count")
    UnreadCountResponse unreadCount(@AuthenticationPrincipal AppUser currentUser) {
        return new UnreadCountResponse(service.unreadCount(currentUser));
    }

    @PatchMapping("/{id}/read")
    NotificationResponse markRead(@PathVariable Long id, @AuthenticationPrincipal AppUser currentUser) {
        return NotificationResponse.from(service.markRead(id, currentUser));
    }

    @PatchMapping("/read-all")
    void markAllRead(@AuthenticationPrincipal AppUser currentUser) {
        service.markAllRead(currentUser);
    }
}
