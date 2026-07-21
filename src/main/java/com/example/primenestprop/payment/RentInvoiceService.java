package com.example.primenestprop.payment;

import com.example.primenestprop.lease.Lease;
import com.example.primenestprop.user.AppUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RentInvoiceService {
    private static final List<RentInvoiceStatus> OUTSTANDING_STATUSES = List.of(RentInvoiceStatus.PENDING, RentInvoiceStatus.OVERDUE);

    private final RentInvoiceRepository invoices;

    public RentInvoiceService(RentInvoiceRepository invoices) {
        this.invoices = invoices;
    }

    @Transactional
    public void ensureCurrentPeriodInvoices(AppUser landlord, List<Lease> activeLeases) {
        LocalDate periodStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        LocalDate periodEnd = periodStart.plusMonths(1).minusDays(1);
        for (Lease lease : activeLeases) {
            if (lease.getMonthlyRent() == null) {
                continue;
            }
            invoices.findByLeaseAndPeriodStart(lease, periodStart).orElseGet(() -> {
                RentInvoice invoice = new RentInvoice();
                invoice.setLease(lease);
                invoice.setTenant(lease.getTenant());
                invoice.setLandlord(landlord);
                invoice.setPeriodStart(periodStart);
                invoice.setPeriodEnd(periodEnd);
                invoice.setAmount(lease.getMonthlyRent());
                invoice.setCurrency(lease.getCurrency());
                invoice.setDueDate(periodStart);
                invoice.setStatus(RentInvoiceStatus.PENDING);
                return invoices.save(invoice);
            });
        }
    }

    @Transactional
    public void refreshOverdueStatuses(AppUser landlord) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (RentInvoice invoice : invoices.findByLandlordAndStatusIn(landlord, List.of(RentInvoiceStatus.PENDING))) {
            if (invoice.getDueDate() != null && invoice.getDueDate().isBefore(today)) {
                invoice.setStatus(RentInvoiceStatus.OVERDUE);
            }
        }
    }

    @Transactional(readOnly = true)
    public OutstandingSummary outstandingSummary(AppUser landlord) {
        BigDecimal total = invoices.sumAmountByLandlordAndStatusIn(landlord, OUTSTANDING_STATUSES);
        long overdueCount = invoices.countByLandlordAndStatus(landlord, RentInvoiceStatus.OVERDUE);
        return new OutstandingSummary(total, overdueCount);
    }

    @Transactional
    public void markPaidIfMatching(Payment payment) {
        Lease lease = payment.getLease();
        if (lease == null) {
            return;
        }
        List<RentInvoice> unpaid = invoices.findByLeaseAndStatusInOrderByPeriodStartAsc(lease, OUTSTANDING_STATUSES);
        if (unpaid.isEmpty()) {
            return;
        }
        RentInvoice invoice = unpaid.get(0);
        invoice.setStatus(RentInvoiceStatus.PAID);
        invoice.setPaidAt(payment.getPaidAt());
        invoice.setPayment(payment);
    }

    public record OutstandingSummary(BigDecimal totalOutstanding, long overdueCount) {
    }
}
