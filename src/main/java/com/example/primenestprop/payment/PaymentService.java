package com.example.primenestprop.payment;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.lease.Lease;
import com.example.primenestprop.lease.LeaseService;
import com.example.primenestprop.property.PropertyService;
import com.example.primenestprop.user.AppUser;
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

    public PaymentService(PaymentRepository payments, UserService users, PropertyService properties, LeaseService leases) {
        this.payments = payments;
        this.users = users;
        this.properties = properties;
        this.leases = leases;
    }

    @Transactional
    public Payment create(PaymentDtos.CreatePaymentRequest request) {
        Lease lease = request.leaseId() == null ? null : leases.require(request.leaseId());
        if (lease != null && isRentPayment(request.purpose())) {
            if (!lease.getTenant().getId().equals(request.payerId())) {
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
        payment.setPayer(users.require(request.payerId()));
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

    public Payment require(Long id) {
        return payments.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found"));
    }

    public List<Payment> forUser(Long userId) {
        AppUser user = users.require(userId);
        List<Payment> payer = payments.findByPayer(user);
        List<Payment> payee = payments.findByPayee(user);
        payer.addAll(payee);
        return payer;
    }

    @Transactional
    public Payment markSuccessful(Long id) {
        Payment payment = require(id);
        payment.setStatus(PaymentStatus.SUCCESSFUL);
        payment.setPaidAt(Instant.now());
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
