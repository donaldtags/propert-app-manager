package com.example.primenestprop.notification;

import com.example.primenestprop.user.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserOrderByCreatedAtDesc(AppUser user);

    boolean existsByUserAndTypeAndRelatedId(AppUser user, NotificationType type, Long relatedId);

    Optional<Notification> findByIdAndUser(Long id, AppUser user);

    long countByUserAndReadFalse(AppUser user);

    List<Notification> findByUserAndReadFalse(AppUser user);
}
