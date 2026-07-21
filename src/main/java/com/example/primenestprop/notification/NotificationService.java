package com.example.primenestprop.notification;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.kyc.KycService;
import com.example.primenestprop.kyc.KycStatus;
import com.example.primenestprop.kyc.KycSubmission;
import com.example.primenestprop.lease.Lease;
import com.example.primenestprop.lease.LeaseService;
import com.example.primenestprop.lease.LeaseStatus;
import com.example.primenestprop.maintenance.MaintenanceRequest;
import com.example.primenestprop.maintenance.MaintenanceService;
import com.example.primenestprop.maintenance.MaintenanceStatus;
import com.example.primenestprop.payment.RentInvoice;
import com.example.primenestprop.payment.RentInvoiceRepository;
import com.example.primenestprop.payment.RentInvoiceStatus;
import com.example.primenestprop.user.AppUser;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Generates real, data-backed notifications (rent reminders, lease expiry, maintenance
 * updates, security alerts) the moment a tenant checks their notifications - no polling
 * or background scheduler required. */
@Service
public class NotificationService {
    private static final int RENT_REMINDER_WINDOW_DAYS = 5;
    private static final int LEASE_EXPIRY_WINDOW_DAYS = 30;

    private final NotificationRepository notifications;
    private final LeaseService leases;
    private final RentInvoiceRepository invoices;
    private final MaintenanceService maintenance;
    private final KycService kyc;

    public NotificationService(
            NotificationRepository notifications,
            LeaseService leases,
            RentInvoiceRepository invoices,
            MaintenanceService maintenance,
            KycService kyc
    ) {
        this.notifications = notifications;
        this.leases = leases;
        this.invoices = invoices;
        this.maintenance = maintenance;
        this.kyc = kyc;
    }

    @Transactional
    public List<Notification> mine(AppUser user) {
        refresh(user);
        return notifications.findByUserOrderByCreatedAtDesc(user);
    }

    @Transactional(readOnly = true)
    public long unreadCount(AppUser user) {
        return notifications.countByUserAndReadFalse(user);
    }

    @Transactional
    public Notification markRead(Long id, AppUser user) {
        Notification notification = notifications.findByIdAndUser(id, user)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Notification not found"));
        notification.setRead(true);
        return notification;
    }

    @Transactional
    public void markAllRead(AppUser user) {
        notifications.findByUserAndReadFalse(user).forEach(n -> n.setRead(true));
    }

    private void refresh(AppUser user) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        for (RentInvoice invoice : invoices.findByTenantOrderByPeriodStartDesc(user)) {
            boolean outstanding = invoice.getStatus() == RentInvoiceStatus.PENDING || invoice.getStatus() == RentInvoiceStatus.OVERDUE;
            boolean dueSoon = invoice.getDueDate() != null && !invoice.getDueDate().isAfter(today.plusDays(RENT_REMINDER_WINDOW_DAYS));
            if (outstanding && dueSoon) {
                create(user, NotificationType.RENT_REMINDER, invoice.getId(),
                        invoice.getStatus() == RentInvoiceStatus.OVERDUE ? "Rent overdue" : "Rent due soon",
                        "%s %s is due on %s.".formatted(invoice.getAmount(), invoice.getCurrency(), invoice.getDueDate()));
            }
        }

        for (Lease lease : leases.forTenant(user.getId(), user)) {
            boolean active = lease.getStatus() == LeaseStatus.ACTIVE || lease.getStatus() == LeaseStatus.SIGNED;
            boolean expiringSoon = lease.getEndDate() != null && !lease.getEndDate().isBefore(today)
                    && !lease.getEndDate().isAfter(today.plusDays(LEASE_EXPIRY_WINDOW_DAYS));
            if (active && expiringSoon) {
                create(user, NotificationType.LEASE_EXPIRY, lease.getId(), "Lease expiring soon",
                        "Your lease #%d ends on %s. Consider requesting a renewal.".formatted(lease.getId(), lease.getEndDate()));
            }
        }

        for (MaintenanceRequest request : maintenance.forRequester(user)) {
            if (request.getStatus() == MaintenanceStatus.RESOLVED) {
                create(user, NotificationType.MAINTENANCE_UPDATE, request.getId(), "Maintenance request resolved",
                        "Your %s maintenance request has been marked resolved.".formatted(request.getCategory()));
            }
        }

        for (KycSubmission submission : kyc.myList(user)) {
            if (submission.getStatus() == KycStatus.REJECTED) {
                create(user, NotificationType.SECURITY_ALERT, submission.getId(), "Identity verification rejected",
                        submission.getReviewNote() == null || submission.getReviewNote().isBlank()
                                ? "Your identity verification submission was rejected. Please resubmit with clearer documents."
                                : "Your identity verification was rejected: " + submission.getReviewNote());
            }
        }
    }

    private void create(AppUser user, NotificationType type, Long relatedId, String title, String message) {
        if (notifications.existsByUserAndTypeAndRelatedId(user, type, relatedId)) {
            return;
        }
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setRelatedId(relatedId);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setCreatedAt(Instant.now());
        notifications.save(notification);
    }
}
