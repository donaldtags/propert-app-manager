package com.example.estatex.wallet;

import com.example.estatex.common.ApiException;
import com.example.estatex.common.PlatformAccountService;
import com.example.estatex.payment.Payment;
import com.example.estatex.payment.PaymentDtos;
import com.example.estatex.payment.PaymentPurpose;
import com.example.estatex.payment.PaymentService;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.UserService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WalletController {
    private static final BigDecimal MIN_TOP_UP = new BigDecimal("1.00");
    private static final BigDecimal MAX_TOP_UP = new BigDecimal("5000.00");

    private final WalletService wallets;
    private final WithdrawalService withdrawals;
    private final PaymentService payments;
    private final PlatformAccountService platformAccounts;
    private final UserService users;

    public WalletController(WalletService wallets, WithdrawalService withdrawals, PaymentService payments,
                            PlatformAccountService platformAccounts, UserService users) {
        this.wallets = wallets;
        this.withdrawals = withdrawals;
        this.payments = payments;
        this.platformAccounts = platformAccounts;
        this.users = users;
    }

    @GetMapping("/api/v1/wallet")
    WalletDtos.WalletResponse wallet(@RequestParam(required = false) String currency,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @AuthenticationPrincipal AppUser user) {
        String cur = WalletService.normaliseCurrency(currency);
        return wallets.find(user.getId(), cur).map(w -> {
            Page<WalletEntry> history = wallets.history(w.getId(), page, size);
            return new WalletDtos.WalletResponse(cur, WalletService.fromCents(w.getBalanceCents()),
                    WalletService.fromCents(withdrawals.pendingCents(w.getId())),
                    history.getContent().stream().map(WalletDtos.EntryResponse::from).toList(),
                    history.getTotalElements(), history.getNumber(), history.getSize());
        }).orElseGet(() -> new WalletDtos.WalletResponse(cur, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                List.of(), 0, Math.max(page, 0), size));
    }

    /** Starts a Paynow/card checkout; the wallet is credited only when the gateway confirms it. */
    @PostMapping("/api/v1/wallet/top-ups")
    @Transactional
    PaymentDtos.CheckoutResponse topUp(@RequestBody WalletDtos.TopUpRequest r, @AuthenticationPrincipal AppUser user) {
        String provider = r.provider() == null ? "" : r.provider().trim().toLowerCase();
        if (!wallets.isGateway(provider)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Top up with Paynow (EcoCash, OneMoney, bank) or a card");
        }
        if (r.amount() == null || r.amount().compareTo(MIN_TOP_UP) < 0 || r.amount().compareTo(MAX_TOP_UP) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Top up between $1 and $5,000 at a time");
        }
        String currency = WalletService.normaliseCurrency(r.currency());
        Payment payment = payments.create(
                new PaymentDtos.CreatePaymentRequest(platformAccounts.billingAccount().getId(), null, null,
                        r.amount().setScale(2, java.math.RoundingMode.HALF_UP), currency, provider, "Wallet top-up"),
                user, PaymentPurpose.WALLET_TOP_UP, "walletCurrency=" + currency, null);
        return payments.initiateCheckout(payment.getId(), provider, user);
    }

    @PostMapping("/api/v1/wallet/withdrawals")
    WalletDtos.WithdrawalResponse withdraw(@RequestBody WalletDtos.WithdrawalRequest r, @AuthenticationPrincipal AppUser user) {
        return WalletDtos.WithdrawalResponse.from(withdrawals.request(user, r), user.getFullName(), user.getEmail());
    }

    @GetMapping("/api/v1/wallet/withdrawals")
    List<WalletDtos.WithdrawalResponse> myWithdrawals(@AuthenticationPrincipal AppUser user) {
        return withdrawals.mine(user).stream()
                .map(w -> WalletDtos.WithdrawalResponse.from(w, user.getFullName(), user.getEmail())).toList();
    }

    @GetMapping("/api/v1/admin/wallet/withdrawals")
    List<WalletDtos.WithdrawalResponse> staffWithdrawals(@RequestParam(required = false) String status,
                                                        @AuthenticationPrincipal AppUser staff) {
        return withdrawals.forStaff(staff, status).stream().map(this::withOwner).toList();
    }

    @PatchMapping("/api/v1/admin/wallet/withdrawals/{id}")
    WalletDtos.WithdrawalResponse decide(@PathVariable Long id, @RequestBody WalletDtos.WithdrawalDecision d,
                                         @AuthenticationPrincipal AppUser staff) {
        return withOwner(withdrawals.decide(id, d, staff));
    }

    private WalletDtos.WithdrawalResponse withOwner(Withdrawal w) {
        AppUser owner = users.require(w.getUserId());
        return WalletDtos.WithdrawalResponse.from(w, owner.getFullName(), owner.getEmail());
    }
}
