package com.example.estatex.referral;

import static com.example.estatex.common.AuditService.entry;

import com.example.estatex.analytics.market.AnalyticsSetting;
import com.example.estatex.analytics.market.AnalyticsSettingRepository;
import com.example.estatex.common.ApiException;
import com.example.estatex.common.AuditService;
import com.example.estatex.common.BusinessTime;
import com.example.estatex.notification.NotificationService;
import com.example.estatex.notification.NotificationType;
import com.example.estatex.payment.Payment;
import com.example.estatex.payment.PaymentPurpose;
import com.example.estatex.payment.PaymentSucceededEvent;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.UserRepository;
import com.example.estatex.wallet.WalletEntryType;
import com.example.estatex.wallet.WalletService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The referral programme (docs/admin-analytics-plan.md §7 and Phase R).
 *
 * <p>Someone signs up with a friend's code. Their first subscription payment is discounted by
 * the programme's percentage. When that payment succeeds, the friend gets a wallet credit, up to
 * a monthly limit. Rules that stop it being gamed:
 * <ul>
 *   <li>referring yourself is blocked: the same browser, the same ID number, or the same email
 *       (ignoring dots and +tags) means no discount and no reward;</li>
 *   <li>only the first successful subscription payment ever counts, and only once (idempotent);</li>
 *   <li>a referrer earns at most {@code monthlyCap} rewards per calendar month;</li>
 *   <li>a refunded payment takes the reward back (or flags it for staff if the wallet can't cover it);</li>
 *   <li>every step is in the audit log.</li>
 * </ul>
 * Everything is off until an administrator switches the programme on.
 */
@Service
public class ReferralService {
    public static final String MARKETING_ACCOUNT = "MARKETING_EXPENSE";
    static final String KEY_ENABLED = "referral.enabled";
    static final String KEY_DISCOUNT = "referral.discount_percent";
    static final String KEY_REWARD = "referral.reward_cents";
    static final String KEY_CAP = "referral.monthly_cap";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O, 1/I
    private static final Logger log = LoggerFactory.getLogger(ReferralService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ReferralCodeRepository codes;
    private final ReferralRepository referrals;
    private final ReferralDeviceMarkRepository devices;
    private final ReferralCodeIssuer issuer;
    private final AnalyticsSettingRepository settings;
    private final UserRepository users;
    private final WalletService wallet;
    private final AuditService audit;
    private final NotificationService notifications;
    private final JdbcTemplate jdbc;
    private final BusinessTime time;
    private final String salt;

    ReferralService(ReferralCodeRepository codes, ReferralRepository referrals, ReferralDeviceMarkRepository devices,
                    ReferralCodeIssuer issuer,
                    AnalyticsSettingRepository settings, UserRepository users, WalletService wallet, AuditService audit,
                    @org.springframework.context.annotation.Lazy NotificationService notifications, JdbcTemplate jdbc, BusinessTime time,
                    @Value("${app.referrals.device-salt:estatex-referrals}") String salt) {
        this.codes = codes;
        this.referrals = referrals;
        this.devices = devices;
        this.issuer = issuer;
        this.settings = settings;
        this.users = users;
        this.wallet = wallet;
        this.audit = audit;
        this.notifications = notifications;
        this.jdbc = jdbc;
        this.time = time;
        this.salt = salt;
    }

    // ---- Settings -------------------------------------------------------------------------

    public record Settings(boolean enabled, int discountPercent, long rewardCents, int monthlyCap, String currency) {
    }

    public Settings settings() {
        return new Settings(
                Boolean.parseBoolean(setting(KEY_ENABLED, "false")),
                Integer.parseInt(setting(KEY_DISCOUNT, "20")),
                Long.parseLong(setting(KEY_REWARD, "1000")),
                Integer.parseInt(setting(KEY_CAP, "10")),
                "USD");
    }

    @Transactional
    public Settings updateSettings(Settings s, AppUser admin) {
        if (s.discountPercent() < 0 || s.discountPercent() > 90) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The discount must be between 0% and 90%.");
        }
        if (s.rewardCents() < 0 || s.rewardCents() > 100_000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The reward must be between $0 and $1,000.");
        }
        if (s.monthlyCap() < 0 || s.monthlyCap() > 1000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The monthly limit must be between 0 and 1,000 rewards.");
        }
        Settings before = settings();
        save(KEY_ENABLED, String.valueOf(s.enabled()), admin);
        save(KEY_DISCOUNT, String.valueOf(s.discountPercent()), admin);
        save(KEY_REWARD, String.valueOf(s.rewardCents()), admin);
        save(KEY_CAP, String.valueOf(s.monthlyCap()), admin);
        audit.record(admin, entry("REFERRAL_SETTINGS_CHANGED", "ReferralProgramme", null)
                .transition(summary(before), summary(settings())));
        return settings();
    }

    /** Fits the audit log's 64-character state column, e.g. "on, 20%, $10.00, cap 10". */
    static String summary(Settings s) {
        return (s.enabled() ? "on" : "off") + ", " + s.discountPercent() + "%, " + WalletService.format(s.rewardCents(), s.currency())
                + ", cap " + s.monthlyCap();
    }

    private String setting(String key, String fallback) {
        return settings.findById(key).map(AnalyticsSetting::getValue).orElse(fallback);
    }

    private void save(String key, String value, AppUser admin) {
        AnalyticsSetting row = settings.findById(key).orElseGet(AnalyticsSetting::new);
        row.setKey(key);
        row.setValue(value);
        row.setUpdatedByUserId(admin.getId());
        row.setUpdatedAt(time.clock().instant());
        settings.save(row);
    }

    // ---- Codes ----------------------------------------------------------------------------

    public ReferralCode codeFor(AppUser user) {
        Optional<ReferralCode> existing = codes.findByUserId(user.getId());
        if (existing.isPresent()) return existing.get();
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = randomCode();
            if (codes.findByCode(candidate).isPresent()) continue;
            try {
                return issuer.create(user.getId(), candidate);
            } catch (DataIntegrityViolationException raced) {
                // A parallel request created this user's code first (or the random code collided): use theirs, or retry.
                Optional<ReferralCode> theirs = codes.findByUserId(user.getId());
                if (theirs.isPresent()) return theirs.get();
            }
        }
        throw new IllegalStateException("Could not generate a unique referral code");
    }

    static String normaliseCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String randomCode() {
        StringBuilder b = new StringBuilder(8);
        for (int i = 0; i < 8; i++) b.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return b.toString();
    }

    public record CodeInfo(boolean valid, boolean programmeActive, Integer discountPercent) {
    }

    /** Public: lets the sign-up page say "20% off your first subscription". Reveals nothing about the referrer. */
    public CodeInfo describe(String code) {
        boolean valid = codes.findByCode(normaliseCode(code)).isPresent();
        Settings s = settings();
        return new CodeInfo(valid, valid && s.enabled(), valid && s.enabled() ? s.discountPercent() : null);
    }

    /** Called before an account is created, so a mistyped code can be corrected instead of silently ignored. */
    public void requireValidCode(String code) {
        if (code == null || code.isBlank()) return;
        if (codes.findByCode(normaliseCode(code)).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That referral code doesn't exist. Check it, or leave it empty.");
        }
    }

    // ---- Sign-up --------------------------------------------------------------------------

    /** Records who referred a new account. Self-referrals are recorded as BLOCKED (no discount, no reward). */
    @Transactional
    public void onRegistered(AppUser referee, String code, String deviceId) {
        recordDevice(referee.getId(), deviceId);
        if (code == null || code.isBlank()) return;
        ReferralCode c = codes.findByCode(normaliseCode(code)).orElse(null);
        if (c == null) return;
        Referral r = new Referral();
        r.setCode(c.getCode());
        r.setReferrerUserId(c.getUserId());
        r.setRefereeUserId(referee.getId());
        AppUser referrer = users.findById(c.getUserId()).orElse(null);
        String reason = selfReferral(referrer, referee);
        if (reason != null) {
            r.setStatus(ReferralStatus.BLOCKED);
            r.setBlockReason(reason);
        }
        referrals.save(r);
        audit.record(referee, entry(reason == null ? "REFERRAL_REGISTERED" : "REFERRAL_BLOCKED", "Referral", r.getId())
                .metadata(reason == null ? Map.of("referrer", c.getUserId()) : Map.of("referrer", c.getUserId(), "reason", reason)));
    }

    /** Remembers (as a salted hash) that this account used this browser. */
    @Transactional
    public void recordDevice(Long userId, String deviceId) {
        if (userId == null || deviceId == null || deviceId.isBlank()) return;
        String hash = deviceHash(deviceId);
        try {
            if (!devices.existsByUserIdAndDeviceHash(userId, hash)) {
                ReferralDeviceMark m = new ReferralDeviceMark();
                m.setUserId(userId);
                m.setDeviceHash(hash);
                devices.saveAndFlush(m);
            }
        } catch (DataIntegrityViolationException raced) {
            // recorded by a parallel request
        }
    }

    /** Why this looks like someone referring themselves, or null if it doesn't. */
    String selfReferral(AppUser referrer, AppUser referee) {
        if (referrer == null) return "Referrer account no longer exists";
        if (Objects.equals(referrer.getId(), referee.getId())) return "Used their own code";
        Set<String> a = devices.findByUserId(referrer.getId()).stream().map(ReferralDeviceMark::getDeviceHash).collect(Collectors.toSet());
        if (devices.findByUserId(referee.getId()).stream().anyMatch(d -> a.contains(d.getDeviceHash()))) {
            return "Same browser as the referrer";
        }
        String idA = normaliseId(referrer.getNationalIdNumber());
        if (idA != null && idA.equals(normaliseId(referee.getNationalIdNumber()))) return "Same ID number as the referrer";
        String eA = normaliseEmail(referrer.getEmail());
        if (eA != null && eA.equals(normaliseEmail(referee.getEmail()))) return "Same email address as the referrer";
        return null;
    }

    static String normaliseEmail(String email) {
        if (email == null || !email.contains("@")) return null;
        String[] parts = email.trim().toLowerCase(Locale.ROOT).split("@", 2);
        String local = parts[0].replaceAll("\\+.*$", "");
        if (parts[1].equals("gmail.com") || parts[1].equals("googlemail.com")) {
            local = local.replace(".", "");
            parts[1] = "gmail.com";
        }
        return local + "@" + parts[1];
    }

    private static String normaliseId(String id) {
        if (id == null) return null;
        String s = id.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return s.isEmpty() ? null : s;
    }

    private String deviceHash(String deviceId) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((salt + ":" + deviceId.trim()).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- Checkout -------------------------------------------------------------------------

    public record Discount(long referralId, int percent, BigDecimal amount, BigDecimal payable) {
    }

    /**
     * The referral discount for this user's next subscription payment, if any: the programme is
     * on, they signed up with a code that wasn't blocked, and they've never paid for a
     * subscription before.
     */
    @Transactional(readOnly = true)
    public Optional<Discount> discountFor(AppUser user, BigDecimal price) {
        Settings s = settings();
        if (!s.enabled() || s.discountPercent() <= 0 || price == null || price.signum() <= 0) return Optional.empty();
        Optional<Referral> r = referrals.findByRefereeUserId(user.getId());
        if (r.isEmpty() || r.get().getStatus() != ReferralStatus.REGISTERED) return Optional.empty();
        if (hasPaidSubscription(user.getId())) return Optional.empty();
        BigDecimal off = price.multiply(BigDecimal.valueOf(s.discountPercent())).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return Optional.of(new Discount(r.get().getId(), s.discountPercent(), off, price.subtract(off)));
    }

    private boolean hasPaidSubscription(Long userId) {
        Integer n = jdbc.queryForObject("select count(*) from payments where payer_id = ? and purpose_type = 'SUBSCRIPTION' "
                + "and status = 'SUCCESSFUL'", Integer.class, userId);
        return n != null && n > 0;
    }

    // ---- Qualification and reward ---------------------------------------------------------

    /** The referee's first subscription payment succeeded: record the discount and reward the referrer. */
    @EventListener
    @Transactional
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        Payment p = event.payment();
        if (p.getPurposeType() != PaymentPurpose.SUBSCRIPTION) return;
        Long referralId = metadataLong(p.getMetadata(), "referralId");
        if (referralId == null) return;
        Referral r = referrals.findById(referralId).orElse(null);
        if (r == null || r.getStatus() != ReferralStatus.REGISTERED || !Objects.equals(r.getRefereeUserId(), p.getPayer().getId())) return;
        Settings s = settings();
        Instant now = time.clock().instant();
        r.setQualifyingPaymentId(p.getId());
        r.setQualifiedAt(now);
        r.setDiscountPercent(metadataLong(p.getMetadata(), "discountPercent") == null ? null
                : metadataLong(p.getMetadata(), "discountPercent").intValue());
        r.setDiscountCents(metadataLong(p.getMetadata(), "discountCents"));
        r.setCurrency(p.getCurrency());

        // Re-check self-referral now: ID numbers and devices may have been added since sign-up.
        AppUser referrer = users.findById(r.getReferrerUserId()).orElse(null);
        String reason = selfReferral(referrer, p.getPayer());
        if (reason != null) {
            r.setStatus(ReferralStatus.BLOCKED);
            r.setBlockReason(reason + " (found at first payment)");
            audit.record(null, entry("REFERRAL_BLOCKED", "Referral", r.getId()).metadata(Map.of("reason", r.getBlockReason())));
            return;
        }
        Instant monthStart = time.today().withDayOfMonth(1).atStartOfDay(time.zone()).toInstant();
        long thisMonth = referrals.countByReferrerUserIdAndStatusInAndRewardedAtGreaterThanEqual(r.getReferrerUserId(),
                Set.of(ReferralStatus.REWARDED, ReferralStatus.REVERSED, ReferralStatus.REVERSAL_PENDING), monthStart);
        if (s.rewardCents() <= 0 || thisMonth >= s.monthlyCap()) {
            r.setStatus(ReferralStatus.CAPPED);
            audit.record(null, entry("REFERRAL_CAPPED", "Referral", r.getId())
                    .metadata(Map.of("rewardsThisMonth", thisMonth, "cap", s.monthlyCap())));
            return;
        }
        wallet.post(r.getReferrerUserId(), s.currency(), s.rewardCents(), WalletEntryType.REFERRAL_REWARD, MARKETING_ACCOUNT,
                "Referral reward: a friend you invited subscribed", "Referral", r.getId(), "referral-reward:" + r.getId(), null);
        r.setStatus(ReferralStatus.REWARDED);
        r.setRewardCents(s.rewardCents());
        r.setRewardedAt(now);
        audit.record(null, entry("REFERRAL_REWARDED", "Referral", r.getId()).money(s.rewardCents(), s.currency()));
        if (referrer != null) {
            notifications.notify(referrer, NotificationType.WALLET, r.getId(), "Referral reward",
                    "A friend you invited subscribed to EstateX. " + WalletService.format(s.rewardCents(), s.currency())
                            + " has been added to your wallet.");
        }
    }

    /** A qualifying payment was refunded (published by the refund flow when it exists). */
    @EventListener
    @Transactional
    public void onPaymentRefunded(PaymentRefundedEvent event) {
        Referral r = referrals.findByQualifyingPaymentId(event.paymentId()).orElse(null);
        if (r != null && r.getStatus() == ReferralStatus.REWARDED) reverse(r, "Payment " + event.paymentId() + " was refunded", null);
    }

    /** Staff take a reward back (for example after refunding the payment outside EstateX). */
    @Transactional
    public Referral reverse(Long referralId, String reason, AppUser staff) {
        if (reason == null || reason.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Give a reason.");
        Referral r = referrals.findById(referralId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Referral not found"));
        if (r.getStatus() != ReferralStatus.REWARDED && r.getStatus() != ReferralStatus.REVERSAL_PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a paid reward can be reversed.");
        }
        reverse(r, reason.trim(), staff);
        return r;
    }

    private void reverse(Referral r, String reason, AppUser staff) {
        r.setReversalReason(reason);
        String currency = r.getCurrency() == null ? "USD" : r.getCurrency();
        // Check first: a failed debit inside this transaction would roll the whole request back.
        if (wallet.balanceCents(r.getReferrerUserId(), currency) >= r.getRewardCents()) {
            wallet.post(r.getReferrerUserId(), currency, -r.getRewardCents(), WalletEntryType.REFERRAL_REVERSAL, MARKETING_ACCOUNT,
                    "Referral reward reversed: " + reason, "Referral", r.getId(), "referral-reversal:" + r.getId(), staff);
            r.setStatus(ReferralStatus.REVERSED);
            r.setReversedAt(time.clock().instant());
        } else {
            // The referrer has already spent or withdrawn it. Don't block the refund; flag it for staff.
            r.setStatus(ReferralStatus.REVERSAL_PENDING);
            log.warn("Referral {} reward could not be taken back: wallet balance too low", r.getId());
        }
        audit.record(staff, entry("REFERRAL_" + r.getStatus().name(), "Referral", r.getId())
                .money(r.getRewardCents(), r.getCurrency() == null ? "USD" : r.getCurrency()).metadata(Map.of("reason", reason)));
    }

    static Long metadataLong(String metadata, String key) {
        if (metadata == null) return null;
        for (String part : metadata.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].trim().equals(key)) {
                try {
                    return Long.parseLong(kv[1].trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    // ---- Views ----------------------------------------------------------------------------

    public record Mine(String code, Settings programme, int invited, int subscribed, long earnedCents, int pending,
                       ReferredBy referredBy) {
    }

    public record ReferredBy(String status, Integer discountPercent, boolean discountAvailable) {
    }

    @Transactional(readOnly = true)
    public Mine mine(AppUser user) {
        ReferralCode c = codeFor(user);
        List<Referral> mineAsReferrer = referrals.findByReferrerUserIdOrderByRegisteredAtDesc(user.getId());
        int invited = (int) mineAsReferrer.stream().filter(r -> r.getStatus() != ReferralStatus.BLOCKED).count();
        int subscribed = (int) mineAsReferrer.stream().filter(r -> r.getQualifiedAt() != null && r.getStatus() != ReferralStatus.BLOCKED).count();
        long earned = mineAsReferrer.stream().filter(r -> r.getStatus() == ReferralStatus.REWARDED)
                .mapToLong(r -> r.getRewardCents() == null ? 0 : r.getRewardCents()).sum();
        int pending = (int) mineAsReferrer.stream().filter(r -> r.getStatus() == ReferralStatus.REGISTERED).count();
        Settings s = settings();
        ReferredBy by = referrals.findByRefereeUserId(user.getId())
                .map(r -> new ReferredBy(r.getStatus().name(), s.enabled() ? s.discountPercent() : null,
                        s.enabled() && r.getStatus() == ReferralStatus.REGISTERED && !hasPaidSubscription(user.getId())))
                .orElse(null);
        return new Mine(c.getCode(), s, invited, subscribed, earned, pending, by);
    }

    public record AdminRow(Long id, String code, Long referrerUserId, String referrerName, Long refereeUserId, String refereeName,
                           String status, String blockReason, Long discountCents, Long rewardCents, Instant registeredAt,
                           Instant qualifiedAt, Instant rewardedAt, String reversalReason) {
    }

    public record AdminView(Settings settings, long total, long blocked, long rewarded, long rewardsPaidCents,
                            long discountsGivenCents, long pendingReversals, List<AdminRow> recent) {
    }

    @Transactional(readOnly = true)
    public AdminView adminView() {
        List<Referral> all = referrals.findAll();
        List<Referral> recent = referrals.findByOrderByRegisteredAtDesc(PageRequest.of(0, 100));
        Map<Long, String> names = users.findAllById(recent.stream()
                        .flatMap(r -> java.util.stream.Stream.of(r.getReferrerUserId(), r.getRefereeUserId())).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(AppUser::getId, AppUser::getFullName));
        return new AdminView(settings(), all.size(),
                all.stream().filter(r -> r.getStatus() == ReferralStatus.BLOCKED).count(),
                all.stream().filter(r -> r.getStatus() == ReferralStatus.REWARDED).count(),
                all.stream().filter(r -> r.getStatus() == ReferralStatus.REWARDED).mapToLong(r -> r.getRewardCents() == null ? 0 : r.getRewardCents()).sum(),
                all.stream().filter(r -> r.getQualifiedAt() != null && r.getStatus() != ReferralStatus.BLOCKED)
                        .mapToLong(r -> r.getDiscountCents() == null ? 0 : r.getDiscountCents()).sum(),
                all.stream().filter(r -> r.getStatus() == ReferralStatus.REVERSAL_PENDING).count(),
                recent.stream().map(r -> new AdminRow(r.getId(), r.getCode(), r.getReferrerUserId(), names.get(r.getReferrerUserId()),
                        r.getRefereeUserId(), names.get(r.getRefereeUserId()), r.getStatus().name(), r.getBlockReason(),
                        r.getDiscountCents(), r.getRewardCents(), r.getRegisteredAt(), r.getQualifiedAt(), r.getRewardedAt(),
                        r.getReversalReason())).toList());
    }
}
