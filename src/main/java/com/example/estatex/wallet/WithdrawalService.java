package com.example.estatex.wallet;

import static com.example.estatex.common.AuditService.entry;

import com.example.estatex.common.ApiException;
import com.example.estatex.common.AuditService;
import com.example.estatex.notification.NotificationService;
import com.example.estatex.notification.NotificationType;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.Permission;
import com.example.estatex.user.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sending wallet money out. The amount is held (debited) when requested so it can't be spent
 * again; EstateX staff pay it to EcoCash or the bank by hand and record the reference, or reject
 * it and the money returns to the wallet. Withdrawals need a verified identity (KYC) and the
 * account password, so a stolen session alone can't empty a wallet.
 */
@Service
public class WithdrawalService {
    static final long MINIMUM_CENTS = 500;

    private final WithdrawalRepository withdrawals;
    private final WalletService wallets;
    private final UserService users;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notifications;
    private final AuditService audit;
    private final EntityManager em;

    public WithdrawalService(WithdrawalRepository withdrawals, WalletService wallets, UserService users,
                             PasswordEncoder passwordEncoder, NotificationService notifications, AuditService audit,
                             EntityManager em) {
        this.withdrawals = withdrawals;
        this.wallets = wallets;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.notifications = notifications;
        this.audit = audit;
        this.em = em;
    }

    @Transactional
    public Withdrawal request(AppUser currentUser, WalletDtos.WithdrawalRequest r) {
        AppUser user = users.require(currentUser.getId());
        requireStepUp(user, r.password());
        if (!user.isIdentityVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Verify your identity before withdrawing. Go to Profile → Verify identity; it usually takes a day.");
        }
        String currency = WalletService.normaliseCurrency(r.currency());
        long cents = WalletService.toCents(r.amount());
        if (cents < MINIMUM_CENTS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The minimum withdrawal is " + WalletService.format(MINIMUM_CENTS, currency));
        }
        Wallet wallet = wallets.find(user.getId(), currency).orElseThrow(() ->
                new ApiException(HttpStatus.CONFLICT, "Your wallet is empty, so there's nothing to withdraw yet."));

        Withdrawal w = new Withdrawal();
        w.setUserId(user.getId());
        w.setWalletId(wallet.getId());
        w.setAmountCents(cents);
        w.setCurrency(currency);
        String destination = applyDestination(w, r);
        withdrawals.save(w);

        // Holds the money now; throws (and rolls the request back) if the balance is too low.
        wallets.post(user.getId(), currency, -cents, WalletEntryType.WITHDRAWAL, WalletService.WITHDRAWALS_IN_TRANSIT,
                "Withdrawal to " + destination + " (processing)", "Withdrawal", w.getId(), "withdrawal:" + w.getId(), user);
        return w;
    }

    @Transactional(readOnly = true)
    public List<Withdrawal> mine(AppUser user) {
        return withdrawals.findByUserIdOrderByRequestedAtDesc(user.getId());
    }

    @Transactional(readOnly = true)
    public List<Withdrawal> forStaff(AppUser staff, String status) {
        requireStaff(staff);
        return status == null || status.isBlank()
                ? withdrawals.findAllByOrderByRequestedAtDesc()
                : withdrawals.findByStatusOrderByRequestedAtAsc(status.trim().toUpperCase());
    }

    @Transactional(readOnly = true)
    public long pendingCents(Long walletId) {
        return withdrawals.pendingForWallet(walletId);
    }

    /** Staff: "I've sent the money" (with the EcoCash/bank reference) or "reject" (money goes back). */
    @Transactional
    public Withdrawal decide(Long id, WalletDtos.WithdrawalDecision d, AppUser staff) {
        requireStaff(staff);
        Withdrawal w = em.find(Withdrawal.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (w == null) throw new ApiException(HttpStatus.NOT_FOUND, "Withdrawal not found");
        if (w.getUserId().equals(staff.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Another staff member must process your own withdrawal");
        }
        if (!Withdrawal.REQUESTED.equals(w.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "This withdrawal was already " + w.getStatus().toLowerCase());
        }
        String action = d.action() == null ? "" : d.action().trim().toUpperCase();
        AppUser owner = users.require(w.getUserId());
        String amount = WalletService.format(w.getAmountCents(), w.getCurrency());
        switch (action) {
            case "PAID" -> {
                if (d.reference() == null || d.reference().isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Enter the EcoCash or bank reference for this payout");
                }
                w.setStatus(Withdrawal.PAID);
                w.setPayoutReference(d.reference().trim());
                wallets.postBetweenSystemAccounts(WalletService.WITHDRAWALS_IN_TRANSIT, WalletService.EXTERNAL_PAYOUTS,
                        w.getCurrency(), w.getAmountCents(), WalletEntryType.WITHDRAWAL_PAID,
                        "Withdrawal paid, ref " + w.getPayoutReference(), "Withdrawal", w.getId(), "withdrawal-paid:" + w.getId(), staff);
                notifications.notify(owner, NotificationType.WALLET, w.getId(), "Your withdrawal was sent",
                        amount + " was sent to you. Reference: " + w.getPayoutReference() + ".");
            }
            case "REJECTED" -> {
                if (d.reason() == null || d.reason().isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Give the reason so the user can fix it");
                }
                w.setStatus(Withdrawal.REJECTED);
                w.setRejectReason(d.reason().trim());
                wallets.post(w.getUserId(), w.getCurrency(), w.getAmountCents(), WalletEntryType.WITHDRAWAL_REVERSAL,
                        WalletService.WITHDRAWALS_IN_TRANSIT, "Withdrawal returned: " + w.getRejectReason(),
                        "Withdrawal", w.getId(), "withdrawal-reversal:" + w.getId(), staff);
                notifications.notify(owner, NotificationType.WALLET, w.getId(), "Your withdrawal was not sent",
                        amount + " is back in your wallet. Reason: " + w.getRejectReason());
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "action must be PAID or REJECTED");
        }
        w.setDecidedAt(Instant.now());
        w.setDecidedByUserId(staff.getId());
        audit.record(staff, entry("wallet.withdrawal_" + action.toLowerCase(), "Withdrawal", w.getId())
                .transition(Withdrawal.REQUESTED, w.getStatus())
                .money(w.getAmountCents(), w.getCurrency())
                .metadata(Map.of("userId", w.getUserId())));
        return w;
    }

    /**
     * Re-confirms the account holder before money leaves the platform.
     * TODO(2FA): once two-factor sign-in lands, also require a fresh TOTP/SMS code here.
     */
    void requireStepUp(AppUser user, String password) {
        if (password == null || password.isBlank() || user.getPasswordHash() == null
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Enter your account password to confirm this withdrawal");
        }
    }

    private static void requireStaff(AppUser staff) {
        if (staff == null || !staff.hasPermission(Permission.ADMIN_OVERRIDE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only EstateX finance staff manage withdrawals");
        }
    }

    private static String applyDestination(Withdrawal w, WalletDtos.WithdrawalRequest r) {
        String method = r.method() == null ? "" : r.method().trim().toUpperCase();
        switch (method) {
            case Withdrawal.METHOD_ECOCASH -> {
                String phone = r.ecocashPhone() == null ? "" : r.ecocashPhone().replaceAll("[\\s-]", "");
                if (!phone.matches("^\\+?\\d{9,15}$")) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Enter the EcoCash number, e.g. 0771 234 567");
                }
                w.setMethod(Withdrawal.METHOD_ECOCASH);
                w.setEcocashPhone(phone);
                return "EcoCash " + phone;
            }
            case Withdrawal.METHOD_BANK -> {
                if (blank(r.bankAccountName()) || blank(r.bankAccountNumber()) || blank(r.bankName())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Enter the account holder's name, account number and bank");
                }
                w.setMethod(Withdrawal.METHOD_BANK);
                w.setBankAccountName(r.bankAccountName().trim());
                w.setBankAccountNumber(r.bankAccountNumber().trim());
                w.setBankName(r.bankName().trim());
                w.setBankBranch(blank(r.bankBranch()) ? null : r.bankBranch().trim());
                String number = w.getBankAccountNumber();
                return w.getBankName() + " ••" + number.substring(Math.max(0, number.length() - 4));
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Choose EcoCash or bank transfer");
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
