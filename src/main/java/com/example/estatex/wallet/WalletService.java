package com.example.estatex.wallet;

import static com.example.estatex.common.AuditService.entry;

import com.example.estatex.common.ApiException;
import com.example.estatex.common.AuditService;
import com.example.estatex.payment.Payment;
import com.example.estatex.user.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The wallet ledger. Every movement is a transfer of two immutable entries that sum to zero: one
 * on the user's wallet, one on a system account that says where the money came from or went.
 * A user wallet's balance only changes under a row lock, can never go below zero, and every
 * transfer carries an idempotency key so the same event can never move money twice.
 *
 * System accounts (the "other side" of each transfer):
 * <ul>
 *   <li>EXTERNAL_GATEWAY - money that arrived through Paynow/Stripe (top-ups; rent and stays paid by gateway)</li>
 *   <li>PLATFORM_COLLECTIONS - money EstateX collected from a wallet payment and owes onward (payouts, REIT sales)</li>
 *   <li>PLATFORM_REVENUE - EstateX's own fees</li>
 *   <li>WITHDRAWALS_IN_TRANSIT - money held for a requested withdrawal</li>
 *   <li>EXTERNAL_PAYOUTS - money that left EstateX to EcoCash or a bank</li>
 * </ul>
 */
@Service
public class WalletService {
    public static final String EXTERNAL_GATEWAY = "EXTERNAL_GATEWAY";
    public static final String PLATFORM_COLLECTIONS = "PLATFORM_COLLECTIONS";
    public static final String PLATFORM_REVENUE = "PLATFORM_REVENUE";
    public static final String WITHDRAWALS_IN_TRANSIT = "WITHDRAWALS_IN_TRANSIT";
    public static final String EXTERNAL_PAYOUTS = "EXTERNAL_PAYOUTS";

    public static final String DEFAULT_CURRENCY = "USD";
    public static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "ZWG");
    public static final String WALLET_PROVIDER = "wallet";

    private final WalletRepository wallets;
    private final WalletEntryRepository entries;
    private final EntityManager em;
    private final AuditService audit;
    private final TransactionTemplate newTransaction;
    /** Providers whose success is confirmed by the gateway itself (Paynow, Stripe) - never self-reported. */
    private final Set<String> gatewayIds;

    public WalletService(WalletRepository wallets, WalletEntryRepository entries, EntityManager em, AuditService audit,
                         PlatformTransactionManager transactionManager,
                         java.util.List<com.example.estatex.payment.gateway.PaymentGateway> gateways) {
        this.wallets = wallets;
        this.entries = entries;
        this.em = em;
        this.audit = audit;
        this.gatewayIds = gateways.stream().map(com.example.estatex.payment.gateway.PaymentGateway::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public boolean isGateway(String provider) {
        return provider != null && gatewayIds.contains(provider);
    }

    // --- money helpers ------------------------------------------------------------------------

    public static long toCents(BigDecimal amount) {
        if (amount == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Amount is required");
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    public static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    public static String format(long cents, String currency) {
        return ("USD".equals(currency) ? "$" : currency + " ") + fromCents(Math.abs(cents)).toPlainString();
    }

    public static String normaliseCurrency(String currency) {
        String c = currency == null || currency.isBlank() ? DEFAULT_CURRENCY : currency.trim().toUpperCase();
        if (!SUPPORTED_CURRENCIES.contains(c)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Wallets support " + String.join(" and ", SUPPORTED_CURRENCIES) + " only");
        }
        return c;
    }

    // --- reads ---------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Optional<Wallet> find(Long userId, String currency) {
        return wallets.findByUserIdAndCurrency(userId, normaliseCurrency(currency));
    }

    @Transactional(readOnly = true)
    public long balanceCents(Long userId, String currency) {
        return find(userId, currency).map(Wallet::getBalanceCents).orElse(0L);
    }

    @Transactional(readOnly = true)
    public Page<WalletEntry> history(Long walletId, int page, int size) {
        return entries.findByWalletIdOrderByIdDesc(walletId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    /** Ledger self-check: a user wallet's cached balance must equal the sum of its entries. */
    @Transactional(readOnly = true)
    public boolean balanceMatchesLedger(Long walletId) {
        Wallet w = wallets.findById(walletId).orElseThrow();
        return w.getBalanceCents() == entries.sumForWallet(walletId);
    }

    /** Sum of a system account (e.g. how much EstateX currently holds for withdrawals in transit). */
    @Transactional(readOnly = true)
    public long systemBalanceCents(String systemCode, String currency) {
        return wallets.findBySystemCodeAndCurrency(systemCode, normaliseCurrency(currency))
                .map(w -> entries.sumForWallet(w.getId())).orElse(0L);
    }

    // --- writes --------------------------------------------------------------------------------

    /**
     * Moves money between a user's wallet and a system account. Positive amount = into the
     * user's wallet. Returns the user-side entry; a repeated idempotency key returns the entry
     * already written instead of moving the money again.
     */
    @Transactional
    public WalletEntry post(Long userId, String currency, long amountCents, String type, String systemAccount,
                            String description, String relatedType, Long relatedId, String idempotencyKey, AppUser actor) {
        if (amountCents == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amount must not be zero");
        }
        String cur = normaliseCurrency(currency);
        Optional<WalletEntry> done = entries.findByIdempotencyKey(idempotencyKey);
        if (done.isPresent()) return done.get();

        Long walletId = accountId(userId, null, cur);
        Long systemId = accountId(null, systemAccount, cur);
        // Lock AND re-read: the wallet may already sit in this session from the lookup above, and a
        // lock on an already-loaded entity alone keeps its stale balance (lost updates under load).
        Wallet wallet = em.find(Wallet.class, walletId);
        em.refresh(wallet, LockModeType.PESSIMISTIC_WRITE);
        // Re-check under the lock: a concurrent replay of the same event may have just committed.
        done = entries.findByIdempotencyKey(idempotencyKey);
        if (done.isPresent()) return done.get();

        long newBalance = Math.addExact(wallet.getBalanceCents(), amountCents);
        if (newBalance < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Your wallet balance is " + format(wallet.getBalanceCents(), cur)
                    + ", which isn't enough for this " + format(amountCents, cur)
                    + " payment. Top up your wallet or choose another way to pay.");
        }
        wallet.setBalanceCents(newBalance);
        wallet.setUpdatedAt(Instant.now());

        String transferId = UUID.randomUUID().toString();
        WalletEntry leg = entries.save(new WalletEntry(walletId, transferId, amountCents, newBalance, cur, type, description,
                relatedType, relatedId, idempotencyKey));
        entries.save(new WalletEntry(systemId, transferId, -amountCents, null, cur, type, description,
                relatedType, relatedId, idempotencyKey + "#contra"));
        record(actor, type, walletId, amountCents, cur, idempotencyKey, transferId, relatedType, relatedId, newBalance);
        return leg;
    }

    /** A movement between two system accounts (e.g. a held withdrawal leaving EstateX, a fee). */
    @Transactional
    public void postBetweenSystemAccounts(String fromAccount, String toAccount, String currency, long amountCents, String type,
                                          String description, String relatedType, Long relatedId, String idempotencyKey,
                                          AppUser actor) {
        if (amountCents <= 0) return;
        String cur = normaliseCurrency(currency);
        if (entries.existsByIdempotencyKey(idempotencyKey)) return;
        Long fromId = accountId(null, fromAccount, cur);
        Long toId = accountId(null, toAccount, cur);
        String transferId = UUID.randomUUID().toString();
        entries.save(new WalletEntry(fromId, transferId, -amountCents, null, cur, type, description, relatedType, relatedId,
                idempotencyKey + "#from"));
        entries.save(new WalletEntry(toId, transferId, amountCents, null, cur, type, description, relatedType, relatedId,
                idempotencyKey));
        record(actor, type, toId, amountCents, cur, idempotencyKey, transferId, relatedType, relatedId, null);
    }

    /** Pays an EstateX payment from the payer's wallet. Throws 409 if the balance is too low. */
    @Transactional
    public WalletEntry payForPayment(Payment payment, AppUser payer) {
        long cents = toCents(payment.getAmount());
        if (cents <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Nothing to pay");
        return post(payer.getId(), payment.getCurrency(), -cents, WalletEntryType.PAYMENT, PLATFORM_COLLECTIONS,
                "Paid: " + payment.getPurpose(), "Payment", payment.getId(), paymentKey(payment.getId()), payer);
    }

    public static String paymentKey(Long paymentId) {
        return "pay:" + paymentId;
    }

    @Transactional(readOnly = true)
    public boolean paidFromWallet(Long paymentId) {
        return entries.existsByIdempotencyKey(paymentKey(paymentId));
    }

    /** Wallet (or system account) id, created on first use. Creation runs in its own transaction
     * so two first-time requests racing each other can't both insert; the loser re-reads. */
    Long accountId(Long userId, String systemCode, String currency) {
        Optional<Wallet> found = userId != null
                ? wallets.findByUserIdAndCurrency(userId, currency)
                : wallets.findBySystemCodeAndCurrency(systemCode, currency);
        if (found.isPresent()) return found.get().getId();
        try {
            return newTransaction.execute(status -> {
                Wallet w = new Wallet();
                w.setUserId(userId);
                w.setSystemCode(systemCode);
                w.setCurrency(currency);
                return wallets.saveAndFlush(w).getId();
            });
        } catch (DataIntegrityViolationException raced) {
            return (userId != null
                    ? wallets.findByUserIdAndCurrency(userId, currency)
                    : wallets.findBySystemCodeAndCurrency(systemCode, currency)).orElseThrow().getId();
        }
    }

    private void record(AppUser actor, String type, Long walletId, long cents, String currency, String key, String transferId,
                        String relatedType, Long relatedId, Long balanceAfter) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("idempotencyKey", key);
        meta.put("transferId", transferId);
        if (relatedType != null) meta.put("relatedType", relatedType);
        if (relatedId != null) meta.put("relatedId", relatedId);
        if (balanceAfter != null) meta.put("balanceAfterCents", balanceAfter);
        audit.record(actor, entry("wallet." + type.toLowerCase(), "Wallet", walletId).money(cents, currency).metadata(meta));
    }
}
