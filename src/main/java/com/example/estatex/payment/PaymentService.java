package com.example.estatex.payment;

import com.example.estatex.common.ApiException;
import com.example.estatex.lease.Lease;
import com.example.estatex.lease.LeaseService;
import com.example.estatex.payment.gateway.CheckoutSession;
import com.example.estatex.payment.gateway.GatewayStatus;
import com.example.estatex.payment.gateway.PaymentGateway;
import com.example.estatex.payment.gateway.WebhookOutcome;
import com.example.estatex.property.PropertyService;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.Permission;
import com.example.estatex.user.UserService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private static final java.util.Set<PaymentPurpose> WALLET_PAYABLE = java.util.EnumSet.of(
            PaymentPurpose.RENT, PaymentPurpose.STAY_BOOKING, PaymentPurpose.INVESTMENT,
            PaymentPurpose.SUBSCRIPTION, PaymentPurpose.FEATURED_LISTING);

    private final PaymentRepository payments;
    private final UserService users;
    private final PropertyService properties;
    private final LeaseService leases;
    private final RentInvoiceService rentInvoices;
    private final ApplicationEventPublisher events;
    private final com.example.estatex.wallet.WalletService wallets;
    private final Map<String, PaymentGateway> gatewaysById;
    private final String frontendBaseUrl;

    public PaymentService(
            PaymentRepository payments,
            UserService users,
            PropertyService properties,
            LeaseService leases,
            RentInvoiceService rentInvoices,
            ApplicationEventPublisher events,
            com.example.estatex.wallet.WalletService wallets,
            List<PaymentGateway> gateways,
            @org.springframework.beans.factory.annotation.Value("${app.frontend-base-url:http://localhost:3000}") String frontendBaseUrl
    ) {
        this.payments = payments;
        this.users = users;
        this.properties = properties;
        this.leases = leases;
        this.rentInvoices = rentInvoices;
        this.events = events;
        this.wallets = wallets;
        this.gatewaysById = gateways.stream().collect(Collectors.toMap(PaymentGateway::id, g -> g));
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public Payment create(PaymentDtos.CreatePaymentRequest request, AppUser payer) {
        return create(request, payer, defaultPurposeFor(request.purpose()), null, null);
    }

    /** Used by domain services (escrow, subscriptions, featured listings, investments) that
     * need {@link PaymentSucceededEvent} to carry enough context to finish their own job. */
    @Transactional
    public Payment create(
            PaymentDtos.CreatePaymentRequest request,
            AppUser payer,
            PaymentPurpose purposeType,
            String metadata,
            Long escrowTransactionId
    ) {
        Lease lease = request.leaseId() == null ? null : leases.require(request.leaseId());
        if (lease != null && purposeType == PaymentPurpose.RENT) {
            if (!lease.getTenant().getId().equals(payer.getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Monthly rent must be paid by the lease tenant");
            }
            if (!lease.getLandlord().getId().equals(request.payeeId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Monthly rent must be paid to the lease landlord through EstateX");
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
        payment.setPurposeType(purposeType);
        payment.setMetadata(metadata);
        payment.setEscrowTransactionId(escrowTransactionId);
        payment.setReference("EX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        return payments.save(payment);
    }

    /** Starts a real checkout with the chosen gateway (Paynow/Stripe) and remembers how to
     * confirm it later. Only the payer can start their own checkout. */
    @Transactional
    public PaymentDtos.CheckoutResponse initiateCheckout(Long id, String provider, AppUser currentUser) {
        // Deliberately a plain lookup, not require()'s entity-graph fetch: initiateCheckout only
        // needs payer/payee ids (available on an uninitialized lazy proxy without a query), and
        // this call often runs moments after create() in the same transaction as another
        // service's pessimistic-locked read (e.g. investment checkout locks a Reit row) - an
        // entity-graph query here on top of that has triggered a Hibernate NPE in practice.
        Payment payment = payments.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found"));
        if (!payment.getPayer().getId().equals(currentUser.getId()) && !currentUser.hasPermission(Permission.ADMIN_OVERRIDE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the payer can start checkout for this payment");
        }
        if (payment.getStatus() != PaymentStatus.INITIATED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This payment has already been processed");
        }
        if (com.example.estatex.wallet.WalletService.WALLET_PROVIDER.equals(provider)) {
            return payFromWallet(payment, currentUser);
        }
        PaymentGateway gateway = gatewaysById.get(provider);
        if (gateway == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown payment provider: " + provider);
        }
        String returnUrl = frontendBaseUrl + "/payments/" + payment.getId() + "/return";
        CheckoutSession session = gateway.initiateCheckout(payment, returnUrl);
        payment.setProvider(gateway.id());
        payment.setProviderReference(session.providerReference());
        payment.setCheckoutUrl(session.redirectUrl());
        payment.setPollUrl(session.pollUrl());
        return new PaymentDtos.CheckoutResponse(payment.getId(), session.redirectUrl());
    }

    /**
     * "Pay from wallet": debits the payer's EstateX wallet and completes the payment on the spot,
     * through the same success path as a gateway - so a stay confirms, REIT units are issued and
     * rent is marked paid exactly as if Paynow had confirmed it. Debit and completion share one
     * transaction: if either fails, neither happens.
     */
    private PaymentDtos.CheckoutResponse payFromWallet(Payment payment, AppUser currentUser) {
        if (payment.getPurposeType() == PaymentPurpose.WALLET_TOP_UP) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A wallet top-up must be paid with Paynow or a card");
        }
        // Only payments whose money has a defined destination: the landlord/host is credited
        // (rent, stays) or EstateX itself is the payee (REIT units, plans, featured listings).
        // Escrow deposits stay on the gateway until escrow release can credit wallets.
        if (!WALLET_PAYABLE.contains(payment.getPurposeType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This payment can't be paid from a wallet. Use Paynow or a card.");
        }
        if (!payment.getPayer().getId().equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the payer can pay from their own wallet");
        }
        wallets.payForPayment(payment, currentUser);
        payment.setProvider(com.example.estatex.wallet.WalletService.WALLET_PROVIDER);
        finalizeSuccess(payment);
        return new PaymentDtos.CheckoutResponse(payment.getId(), frontendBaseUrl + "/payments/" + payment.getId() + "/return");
    }

    /** Actively asks the gateway for the current status - the primary confirmation path, since
     * it works without a publicly reachable webhook URL (fine for local/dev). */
    @Transactional
    public PaymentDtos.PaymentResponse confirmCheckout(Long id, AppUser currentUser) {
        Payment payment = requireVisible(id, currentUser);
        if (payment.getStatus() != PaymentStatus.SUCCESSFUL) {
            PaymentGateway gateway = gatewaysById.get(payment.getProvider());
            if (gateway == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "This payment was not started through a gateway checkout");
            }
            GatewayStatus status = gateway.checkStatus(payment);
            if (status == GatewayStatus.PAID) {
                finalizeSuccess(payment);
            } else if (status == GatewayStatus.CANCELLED || status == GatewayStatus.FAILED) {
                payment.setStatus(PaymentStatus.FAILED);
            }
        }
        return PaymentDtos.PaymentResponse.from(payment);
    }

    /** Production hardening: an inbound callback from the provider. Never trusted at face
     * value - it's only a signal to re-check status through the gateway itself. */
    @Transactional
    public void handleWebhook(String provider, String rawBody, Map<String, String> headers) {
        PaymentGateway gateway = gatewaysById.get(provider);
        if (gateway == null) {
            return;
        }
        WebhookOutcome outcome = gateway.handleWebhook(rawBody, headers);
        if (outcome == null) {
            return;
        }
        payments.findByProviderReference(outcome.providerReference()).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.SUCCESSFUL) {
                return;
            }
            GatewayStatus status = gateway.checkStatus(payment);
            if (status == GatewayStatus.PAID) {
                finalizeSuccess(payment);
            } else if (status == GatewayStatus.CANCELLED || status == GatewayStatus.FAILED) {
                payment.setStatus(PaymentStatus.FAILED);
            }
        });
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
        if (!isParty && !currentUser.hasPermission(Permission.ADMIN_OVERRIDE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not have access to this payment");
        }
        return payment;
    }

    @Transactional(readOnly = true)
    public List<Payment> forUser(Long userId, AppUser currentUser) {
        if (!userId.equals(currentUser.getId()) && !currentUser.hasPermission(Permission.ADMIN_OVERRIDE)) {
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
        if (!payment.getPayee().getId().equals(currentUser.getId()) && !currentUser.hasPermission(Permission.ADMIN_OVERRIDE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the payee can confirm this payment as successful");
        }
        if (gatewaysById.containsKey(payment.getProvider())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "This payment was started through " + payment.getProvider()
                            + " checkout - it can only be confirmed by the gateway, not self-reported");
        }
        // A wallet payment completes only by actually debiting the payer's wallet; letting the payee
        // "confirm" one would credit their wallet with money that never existed.
        if (com.example.estatex.wallet.WalletService.WALLET_PROVIDER.equals(payment.getProvider())
                || payment.getPurposeType() == PaymentPurpose.WALLET_TOP_UP) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This payment can only be completed by paying it, not confirmed by hand");
        }
        finalizeSuccess(payment);
        return payment;
    }

    /** Single choke point for flipping a payment to SUCCESSFUL, whichever path got it there
     * (manual self-report, gateway poll, or webhook) - so {@link PaymentSucceededEvent} always
     * fires exactly once and domain listeners never have to know which path was used. */
    private void finalizeSuccess(Payment payment) {
        if (payment.getStatus() == PaymentStatus.SUCCESSFUL) {
            return;
        }
        payment.setStatus(PaymentStatus.SUCCESSFUL);
        payment.setPaidAt(Instant.now());
        rentInvoices.markPaidIfMatching(payment);
        events.publishEvent(new PaymentSucceededEvent(payment));
    }

    private PaymentPurpose defaultPurposeFor(String purpose) {
        return isRentPayment(purpose) ? PaymentPurpose.RENT : PaymentPurpose.OTHER;
    }

    private boolean isRentPayment(String purpose) {
        if (purpose == null || purpose.isBlank()) {
            return true;
        }
        String normalized = purpose.toLowerCase();
        return normalized.contains("rent");
    }
}
