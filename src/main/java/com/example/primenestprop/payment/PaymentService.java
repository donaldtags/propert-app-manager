package com.example.primenestprop.payment;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.lease.Lease;
import com.example.primenestprop.lease.LeaseService;
import com.example.primenestprop.property.PropertyService;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.UserRole;
import com.example.primenestprop.user.UserService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private final PaymentRepository payments;
    private final UserService users;
    private final PropertyService properties;
    private final LeaseService leases;
    private final RentInvoiceService rentInvoices;

    public PaymentService(
            PaymentRepository payments,
            UserService users,
            PropertyService properties,
            LeaseService leases,
            RentInvoiceService rentInvoices
    ) {
        this.payments = payments;
        this.users = users;
        this.properties = properties;
        this.leases = leases;
        this.rentInvoices = rentInvoices;
    }

    @Transactional
    public Payment create(PaymentDtos.CreatePaymentRequest request, AppUser payer) {
        Lease lease = request.leaseId() == null ? null : leases.require(request.leaseId());
        if (lease != null && isRentPayment(request.purpose())) {
            if (!lease.getTenant().getId().equals(payer.getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Monthly rent must be paid by the lease tenant");
            }
            if (!lease.getLandlord().getId().equals(request.payeeId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Monthly rent must be paid to the lease landlord through PrimeNest");
            }
            if (request.amount() != null && lease.getMonthlyRent() != null && request.amount().compareTo(lease.getMonthlyRent()) != 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Monthly rent amount must match the lease monthly rent");
            }
        }
        Payment payment = new Payment();
        payment.setPayer(payer);
        payment.setPayee(users.require(request.payeeId()));
        if (request.propertyId() != null) {
            payment.setProperty(properties.require(request.propertyId()));
        }
        payment.setLease(lease);
        payment.setAmount(request.amount());
        payment.setCurrency(request.currency() == null || request.currency().isBlank() ? "USD" : request.currency());
        payment.setProvider(request.provider() == null || request.provider().isBlank() ? "manual" : request.provider());
        payment.setPurpose(request.purpose() == null || request.purpose().isBlank() ? "Rent payment" : request.purpose());
        payment.setReference("PN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        return payments.save(payment);
    }

    @Transactional(readOnly = true)
    public Payment require(Long id) {
        return payments.findWithDetailsById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found"));
    }

    @Transactional(readOnly = true)
    public Payment requireVisible(Long id, AppUser currentUser) {
        Payment payment = require(id);
        boolean isParty = payment.getPayer().getId().equals(currentUser.getId())
                || payment.getPayee().getId().equals(currentUser.getId());
        if (!isParty && !currentUser.getRoles().contains(UserRole.ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not have access to this payment");
        }
        return payment;
    }

    @Transactional(readOnly = true)
    public List<Payment> forUser(Long userId, AppUser currentUser) {
        if (!userId.equals(currentUser.getId()) && !currentUser.getRoles().contains(UserRole.ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only view your own payments");
        }
        AppUser user = users.require(userId);
        List<Payment> result = new java.util.ArrayList<>(payments.findByPayer(user));
        result.addAll(payments.findByPayee(user));
        return result;
    }

    @Transactional(readOnly = true)
    public java.math.BigDecimal revenueBetween(java.time.Instant from, java.time.Instant to, String currency) {
        return payments.sumSuccessfulBetween(from, to, currency);
    }

    @Transactional(readOnly = true)
    public java.math.BigDecimal totalRevenueForPayee(Long payeeId, String currency) {
        return payments.sumSuccessfulByPayeeAndCurrency(payeeId, currency);
    }

    @Transactional(readOnly = true)
    public java.math.BigDecimal revenueForPayeeBetween(Long payeeId, java.time.Instant from, java.time.Instant to, String currency) {
        return payments.sumSuccessfulByPayeeBetween(payeeId, from, to, currency);
    }

    @Transactional(readOnly = true)
    public java.math.BigDecimal revenueForPayerBetween(Long payerId, java.time.Instant from, java.time.Instant to, String currency) {
        return payments.sumSuccessfulByPayerBetween(payerId, from, to, currency);
    }

    @Transactional(readOnly = true)
    public List<Payment> recentSuccessful() {
        return payments.findTop10ByStatusOrderByPaidAtDesc(PaymentStatus.SUCCESSFUL);
    }

    @Transactional
    public Payment markSuccessful(Long id, AppUser currentUser) {
        Payment payment = require(id);
        if (!payment.getPayee().getId().equals(currentUser.getId()) && !currentUser.getRoles().contains(UserRole.ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the payee can confirm this payment as successful");
        }
        payment.setStatus(PaymentStatus.SUCCESSFUL);
        payment.setPaidAt(Instant.now());
        rentInvoices.markPaidIfMatching(payment);
        return payment;
    }

    private boolean isRentPayment(String purpose) {
        if (purpose == null || purpose.isBlank()) {
            return true;
        }
        String normalized = purpose.toLowerCase();
        return normalized.contains("rent");
    }
}
