package com.example.primenestprop.ai;

import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.lease.Lease;
import com.example.primenestprop.lease.LeaseService;
import com.example.primenestprop.lease.LeaseStatus;
import com.example.primenestprop.maintenance.MaintenanceRequest;
import com.example.primenestprop.maintenance.MaintenanceService;
import com.example.primenestprop.maintenance.MaintenanceStatus;
import com.example.primenestprop.payment.RentInvoice;
import com.example.primenestprop.payment.RentInvoiceRepository;
import com.example.primenestprop.payment.RentInvoiceStatus;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.UserRole;
import org.springframework.http.HttpStatus;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Answers a tenant's questions about their own lease, rent, and maintenance history. */
@Service
public class HomeAssistantService {
    private static final Logger log = LoggerFactory.getLogger(HomeAssistantService.class);
    private static final List<RentInvoiceStatus> OUTSTANDING = List.of(RentInvoiceStatus.PENDING, RentInvoiceStatus.OVERDUE);

    private final LeaseService leases;
    private final RentInvoiceRepository invoices;
    private final MaintenanceService maintenance;
    private final AiAnthropicConfig anthropicConfig;

    public HomeAssistantService(
            LeaseService leases,
            RentInvoiceRepository invoices,
            MaintenanceService maintenance,
            AiAnthropicConfig anthropicConfig
    ) {
        this.leases = leases;
        this.invoices = invoices;
        this.maintenance = maintenance;
        this.anthropicConfig = anthropicConfig;
    }

    public boolean aiPowered() {
        return anthropicConfig.client() != null;
    }

    @Transactional(readOnly = true)
    public String answer(AppUser tenant, String question) {
        TenantContext context = buildContext(tenant);
        if (anthropicConfig.client() != null) {
            try {
                return answerWithClaude(question, context);
            } catch (Exception ex) {
                log.warn("Claude home-assistant answer failed, falling back to rule-based answer", ex);
            }
        }
        return ruleBasedAnswer(question, context);
    }

    @Transactional(readOnly = true)
    public String explainLease(Long leaseId, AppUser currentUser) {
        Lease lease = leases.require(leaseId);
        requireLeaseAccess(lease, currentUser);
        if (anthropicConfig.client() != null) {
            try {
                return explainLeaseWithClaude(lease);
            } catch (Exception ex) {
                log.warn("Claude lease explanation failed, falling back to plain-language summary", ex);
            }
        }
        return leaseAnswer(new TenantContext(List.of(lease), lease, null, List.of()));
    }

    private void requireLeaseAccess(Lease lease, AppUser currentUser) {
        boolean isTenant = lease.getTenant().getId().equals(currentUser.getId());
        boolean isLandlord = lease.getLandlord().getId().equals(currentUser.getId());
        Property property = lease.getProperty();
        boolean isAgent = property.getAgent() != null && property.getAgent().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRoles().contains(UserRole.ADMIN);
        if (!isTenant && !isLandlord && !isAgent && !isAdmin) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not have access to this lease");
        }
    }

    private String explainLeaseWithClaude(Lease lease) {
        String system = "You are PrimeNest's lease explainer. Explain the lease below in plain, simple language a "
                + "first-time renter would understand. Cover: who the parties are, the lease term and important dates, "
                + "the rent and deposit amounts, and a short bullet list of tenant responsibilities and landlord "
                + "responsibilities you can infer from the terms text. Never invent a fact not given below. "
                + "Keep it under 200 words.";
        String facts = "Property #%d. Tenant: %s. Landlord: %s. Status: %s. Term: %s to %s. Monthly rent: %s %s. "
                + "Deposit: %s %s.\nTerms text: %s".formatted(
                        lease.getProperty().getId(), lease.getTenant().getFullName(), lease.getLandlord().getFullName(),
                        lease.getStatus(), lease.getStartDate(), lease.getEndDate(),
                        lease.getMonthlyRent(), lease.getCurrency(), lease.getDepositAmount(), lease.getCurrency(),
                        lease.getTerms() == null || lease.getTerms().isBlank() ? "(none provided)" : lease.getTerms());

        MessageCreateParams params = MessageCreateParams.builder()
                .model(anthropicConfig.model())
                .maxTokens(500L)
                .system(system)
                .addUserMessage(facts)
                .build();

        return textOf(anthropicConfig.client().messages().create(params));
    }

    private TenantContext buildContext(AppUser tenant) {
        List<Lease> tenantLeases = leases.forTenant(tenant.getId(), tenant);
        Lease activeLease = tenantLeases.stream()
                .filter(l -> l.getStatus() == LeaseStatus.ACTIVE || l.getStatus() == LeaseStatus.SIGNED)
                .findFirst()
                .orElse(tenantLeases.stream().findFirst().orElse(null));
        List<RentInvoice> tenantInvoices = invoices.findByTenantOrderByPeriodStartDesc(tenant);
        RentInvoice nextDueInvoice = tenantInvoices.stream()
                .filter(i -> OUTSTANDING.contains(i.getStatus()))
                .min(Comparator.comparing(RentInvoice::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        List<MaintenanceRequest> tenantMaintenance = maintenance.forRequester(tenant);
        return new TenantContext(tenantLeases, activeLease, nextDueInvoice, tenantMaintenance);
    }

    private String answerWithClaude(String question, TenantContext context) {
        String system = "You are PrimeNest's Home Assistant for a tenant on a Zimbabwean rental platform. "
                + "Answer ONLY using the facts given below about this specific tenant's lease, rent, and maintenance "
                + "history. Never invent a date, amount, or status that isn't given. If the facts don't cover the "
                + "question, say what you do know and suggest where in the app they can find the rest (Leases, "
                + "Payments, or Maintenance pages). Keep the answer to 2-4 short sentences.\n\n" + factsSummary(context);

        MessageCreateParams params = MessageCreateParams.builder()
                .model(anthropicConfig.model())
                .maxTokens(400L)
                .system(system)
                .addUserMessage(question)
                .build();

        return textOf(anthropicConfig.client().messages().create(params));
    }

    private static String textOf(Message message) {
        return message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(block -> block.text())
                .collect(Collectors.joining("\n"))
                .trim();
    }

    private String factsSummary(TenantContext context) {
        StringBuilder sb = new StringBuilder();
        if (context.activeLease() == null) {
            sb.append("This tenant has no active lease.\n");
        } else {
            Lease lease = context.activeLease();
            sb.append("Lease #%d: property #%d, status %s, %s to %s, monthly rent %s %s, deposit %s %s.\n".formatted(
                    lease.getId(), lease.getProperty().getId(), lease.getStatus(),
                    lease.getStartDate(), lease.getEndDate(),
                    lease.getMonthlyRent(), lease.getCurrency(), lease.getDepositAmount(), lease.getCurrency()));
            if (lease.getTerms() != null && !lease.getTerms().isBlank()) {
                sb.append("Lease terms: ").append(lease.getTerms()).append("\n");
            }
        }
        if (context.nextDueInvoice() != null) {
            RentInvoice invoice = context.nextDueInvoice();
            sb.append("Next rent due: %s %s due on %s (status %s).\n".formatted(
                    invoice.getAmount(), invoice.getCurrency(), invoice.getDueDate(), invoice.getStatus()));
        } else {
            sb.append("No outstanding rent invoices are on file.\n");
        }
        if (context.maintenanceRequests().isEmpty()) {
            sb.append("No maintenance requests on file.\n");
        } else {
            sb.append("Maintenance requests:\n");
            context.maintenanceRequests().stream().limit(5).forEach(m -> sb.append("- #%d %s (%s), opened %s: %s\n".formatted(
                    m.getId(), m.getCategory(), m.getStatus(), m.getCreatedAt(),
                    m.getDescription() == null ? "" : m.getDescription())));
        }
        return sb.toString();
    }

    private String ruleBasedAnswer(String question, TenantContext context) {
        String q = question.toLowerCase(Locale.ROOT);
        boolean asksRent = q.contains("rent") && (q.contains("due") || q.contains("when") || q.contains("pay"));
        boolean asksLease = q.contains("lease") && (q.contains("explain") || q.contains("terms") || q.contains("what"));
        boolean asksMaintenance = q.contains("maintenance") || q.contains("repair") || q.contains("fix")
                || (q.contains("issue") && q.contains("landlord"));

        if (asksRent) {
            return rentAnswer(context);
        }
        if (asksLease) {
            return leaseAnswer(context);
        }
        if (asksMaintenance) {
            return maintenanceAnswer(context);
        }
        return "I can help with rent due dates, your lease terms, and maintenance status. "
                + "Try asking \"When is my rent due?\", \"Explain my lease\", or \"What's the status of my maintenance request?\"";
    }

    private String rentAnswer(TenantContext context) {
        if (context.nextDueInvoice() != null) {
            RentInvoice invoice = context.nextDueInvoice();
            String overdueNote = invoice.getStatus() == RentInvoiceStatus.OVERDUE ? " This payment is overdue." : "";
            return "Your rent of %s %s is due on %s.%s".formatted(
                    invoice.getAmount(), invoice.getCurrency(), invoice.getDueDate(), overdueNote);
        }
        if (context.activeLease() != null && context.activeLease().getStartDate() != null) {
            int day = context.activeLease().getStartDate().getDayOfMonth();
            return "You have no outstanding rent invoice right now. Based on your lease, rent is typically due around the "
                    + day + ordinalSuffix(day) + " of each month.";
        }
        return "You don't have an active lease on file yet, so there's no rent due date to show.";
    }

    private String leaseAnswer(TenantContext context) {
        Lease lease = context.activeLease();
        if (lease == null) {
            return "You don't have a lease on file yet. Once a landlord sends you one, it'll show up on the Leases page.";
        }
        String terms = lease.getTerms() != null && !lease.getTerms().isBlank()
                ? " Additional terms: " + lease.getTerms()
                : "";
        return "Your lease (#%d) runs from %s to %s, status %s, at %s %s/month with a deposit of %s %s.%s".formatted(
                lease.getId(), lease.getStartDate(), lease.getEndDate(), lease.getStatus(),
                lease.getMonthlyRent(), lease.getCurrency(), lease.getDepositAmount(), lease.getCurrency(), terms);
    }

    private String maintenanceAnswer(TenantContext context) {
        if (context.maintenanceRequests().isEmpty()) {
            return "You have no maintenance requests on file. You can report an issue from the Maintenance page.";
        }
        MaintenanceRequest latest = context.maintenanceRequests().get(0);
        if (latest.getStatus() != MaintenanceStatus.OPEN) {
            return "Your most recent maintenance request (#%d, %s) is currently %s.".formatted(
                    latest.getId(), latest.getCategory(), latest.getStatus());
        }
        long daysOpen = ChronoUnit.DAYS.between(latest.getCreatedAt(), java.time.Instant.now());
        String escalation = daysOpen >= 3
                ? " It's been open for " + daysOpen + " days — you can follow up with your landlord directly via Messages if it's urgent."
                : "";
        return "Your most recent maintenance request (#%d, %s) is still open.%s".formatted(
                latest.getId(), latest.getCategory(), escalation);
    }

    private String ordinalSuffix(int day) {
        if (day >= 11 && day <= 13) {
            return "th";
        }
        return switch (day % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    private record TenantContext(
            List<Lease> allLeases,
            Lease activeLease,
            RentInvoice nextDueInvoice,
            List<MaintenanceRequest> maintenanceRequests
    ) {
    }
}
