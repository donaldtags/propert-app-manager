package com.example.estatex.timeline;

import com.example.estatex.application.RentalApplication;
import com.example.estatex.application.RentalApplicationService;
import com.example.estatex.chat.ChatService;
import com.example.estatex.escrow.EscrowService;
import com.example.estatex.escrow.EscrowTransaction;
import com.example.estatex.lease.Lease;
import com.example.estatex.lease.LeaseDocument;
import com.example.estatex.lease.LeaseDocumentService;
import com.example.estatex.lease.LeaseService;
import com.example.estatex.maintenance.MaintenanceRequest;
import com.example.estatex.maintenance.MaintenanceService;
import com.example.estatex.payment.Payment;
import com.example.estatex.payment.PaymentService;
import com.example.estatex.timeline.TimelineDtos.TimelineEvent;
import com.example.estatex.user.AppUser;
import com.example.estatex.viewing.ViewingRequest;
import com.example.estatex.viewing.ViewingService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Combines a tenant's payments, maintenance, escrow, documents, viewings, messages, and lease
 * events into a single chronological Home Timeline. */
@Service
public class HomeTimelineService {
    private final LeaseService leases;
    private final PaymentService payments;
    private final MaintenanceService maintenance;
    private final EscrowService escrows;
    private final LeaseDocumentService leaseDocuments;
    private final ViewingService viewings;
    private final ChatService chat;
    private final RentalApplicationService applications;

    public HomeTimelineService(
            LeaseService leases,
            PaymentService payments,
            MaintenanceService maintenance,
            EscrowService escrows,
            LeaseDocumentService leaseDocuments,
            ViewingService viewings,
            ChatService chat,
            RentalApplicationService applications
    ) {
        this.leases = leases;
        this.payments = payments;
        this.maintenance = maintenance;
        this.escrows = escrows;
        this.leaseDocuments = leaseDocuments;
        this.viewings = viewings;
        this.chat = chat;
        this.applications = applications;
    }

    @Transactional(readOnly = true)
    public List<TimelineEvent> forTenant(AppUser tenant) {
        List<TimelineEvent> events = new ArrayList<>();

        List<Lease> tenantLeases = leases.forTenant(tenant.getId(), tenant);
        for (Lease lease : tenantLeases) {
            events.add(new TimelineEvent(TimelineEventType.LEASE, "Lease sent",
                    "Property #" + lease.getProperty().getId(), lease.getStatus().name(),
                    lease.getCreatedAt(), lease.getId()));
            if (lease.getSignedAt() != null) {
                events.add(new TimelineEvent(TimelineEventType.LEASE, "Lease signed",
                        "Property #" + lease.getProperty().getId(), lease.getStatus().name(),
                        lease.getSignedAt(), lease.getId()));
            }
            for (LeaseDocument doc : leaseDocuments.list(lease.getId(), tenant)) {
                events.add(new TimelineEvent(TimelineEventType.DOCUMENT,
                        "Document uploaded: " + doc.getDocumentType(), doc.getFileName(),
                        doc.getStatus().name(), doc.getUploadedAt(), doc.getId()));
            }
        }

        for (Payment payment : payments.forUser(tenant.getId(), tenant)) {
            events.add(new TimelineEvent(TimelineEventType.PAYMENT,
                    "Payment: " + (payment.getPurpose() == null ? "Rent" : payment.getPurpose()),
                    payment.getAmount() + " " + payment.getCurrency(), payment.getStatus().name(),
                    payment.getPaidAt() != null ? payment.getPaidAt() : payment.getCreatedAt(), payment.getId()));
        }

        for (MaintenanceRequest request : maintenance.forRequester(tenant)) {
            events.add(new TimelineEvent(TimelineEventType.MAINTENANCE,
                    "Maintenance reported: " + request.getCategory(), request.getDescription(),
                    request.getStatus().name(), request.getCreatedAt(), request.getId()));
            if (request.getResolvedAt() != null) {
                events.add(new TimelineEvent(TimelineEventType.MAINTENANCE,
                        "Maintenance resolved: " + request.getCategory(), request.getDescription(),
                        request.getStatus().name(), request.getResolvedAt(), request.getId()));
            }
        }

        for (EscrowTransaction escrow : escrows.forUser(tenant.getId(), tenant)) {
            events.add(new TimelineEvent(TimelineEventType.ESCROW, "Deposit paid into escrow",
                    escrow.getPurpose(), escrow.getStatus().name(), escrow.getCreatedAt(), escrow.getId()));
            if (escrow.getFundedAt() != null) {
                events.add(new TimelineEvent(TimelineEventType.ESCROW, "Escrow protected",
                        escrow.getPurpose(), escrow.getStatus().name(), escrow.getFundedAt(), escrow.getId()));
            }
            if (escrow.getReleasedAt() != null) {
                events.add(new TimelineEvent(TimelineEventType.ESCROW, "Deposit released",
                        escrow.getPurpose(), escrow.getStatus().name(), escrow.getReleasedAt(), escrow.getId()));
            }
        }

        for (ViewingRequest viewing : viewings.forRequester(tenant)) {
            String propertyLabel = viewing.getProperty() != null ? viewing.getProperty().getTitle() : "Property";
            events.add(new TimelineEvent(TimelineEventType.VIEWING, "Viewing requested",
                    propertyLabel, viewing.getStatus().name(), viewing.getCreatedAt(), viewing.getId()));
            if (viewing.getCompletedAt() != null) {
                events.add(new TimelineEvent(TimelineEventType.VIEWING, "Viewing completed",
                        propertyLabel, viewing.getStatus().name(), viewing.getCompletedAt(), viewing.getId()));
            }
        }

        for (var conversation : chat.conversationsFor(tenant.getId(), tenant)) {
            events.add(new TimelineEvent(TimelineEventType.MESSAGE,
                    "Conversation: " + (conversation.getSubject() == null ? "Message" : conversation.getSubject()),
                    conversation.getLastMessage(), null, conversation.getCreatedAt(), conversation.getId()));
        }

        for (RentalApplication application : applications.mine(tenant)) {
            events.add(new TimelineEvent(TimelineEventType.APPLICATION,
                    "Application " + application.getStatus().name().toLowerCase().replace('_', ' '),
                    application.getProperty().getTitle(), application.getStatus().name(),
                    application.getSubmittedAt() != null ? application.getSubmittedAt() : application.getCreatedAt(),
                    application.getId()));
        }

        events.sort(Comparator.comparing(TimelineEvent::occurredAt).reversed());
        return events;
    }
}
